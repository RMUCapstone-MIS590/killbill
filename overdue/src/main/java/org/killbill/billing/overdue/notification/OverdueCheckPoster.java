/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.overdue.notification;

import java.util.Collection;

import org.joda.time.DateTime;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;

public class OverdueCheckPoster extends DefaultOverduePosterBase {

    @Inject
    public OverdueCheckPoster(final NotificationQueueService notificationQueueService,
                              final IDBI dbi, final Clock clock,
                              final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        super(notificationQueueService, dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    protected <T extends OverdueCheckNotificationKey> boolean cleanupFutureNotificationsFormTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                                                                        final Collection<NotificationEventWithMetadata<T>> futureNotifications,
                                                                                                        final DateTime futureNotificationTime, final NotificationQueue overdueQueue) {

        boolean shouldInsertNewNotification = true;
        if (!futureNotifications.isEmpty()) {
            // Results are ordered by effective date asc
            final DateTime earliestExistingNotificationDate = futureNotifications.iterator().next().getEffectiveDate();

            final int minIndexToDeleteFrom;
            if (earliestExistingNotificationDate.isBefore(futureNotificationTime)) {
                // We don't have to insert a new one. For sanity, delete any other future notification
                minIndexToDeleteFrom = 1;
                shouldInsertNewNotification = false;
            } else {
                // We win - we are before any other already recorded. Delete all others.
                minIndexToDeleteFrom = 0;
            }

            int index = 0;
            for (final NotificationEventWithMetadata<T> cur : futureNotifications) {
                if (minIndexToDeleteFrom <= index) {
                    overdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory.getSqlDao(), cur.getRecordId());
                }
                index++;
            }
        }
        return shouldInsertNewNotification;
    }
}

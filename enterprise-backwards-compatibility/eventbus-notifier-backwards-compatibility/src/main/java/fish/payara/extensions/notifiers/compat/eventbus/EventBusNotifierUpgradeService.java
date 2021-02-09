/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.extensions.notifiers.compat.eventbus;

import com.sun.enterprise.config.serverbeans.Config;
import fish.payara.extensions.notifiers.compat.BaseNotifierUpgradeService;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

@Service
@RunLevel(StartupRunLevel.VAL)
public class EventBusNotifierUpgradeService extends BaseNotifierUpgradeService {

    private static final String notifierName = "eventbus-notifier";

    @Override
    public void postConstruct() {
        for (Config config : configs.getConfig()) {
            // First up, get the notifier configuration for each config
            NotificationServiceConfiguration notificationServiceConfiguration = config.getExtensionByType(
                    NotificationServiceConfiguration.class);
            // If there is no notifier configuration whatsoever - just exit out. This **should** be an edge case -
            // default config tags for the notification service have existed for a long time
            if (notificationServiceConfiguration == null) {
                continue;
            }

            upgradeRequestTracingService(config, notifierName, EventBusNotifier.class);
            upgradeMonitoringService(config, notifierName, EventBusNotifier.class);
            upgradeHealthCheckService(config, notifierName, EventBusNotifier.class);
            upgradeAdminAuditService(config, notifierName, EventBusNotifier.class);
        }
    }
}

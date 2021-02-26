/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017-2021 Payara Foundation and/or its affiliates. All rights reserved.
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


import com.sun.enterprise.util.StringUtils;
import fish.payara.extensions.notifiers.compat.BaseSetNotifierConfigurationCommand;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.internal.api.Globals;
import org.jvnet.hk2.annotations.Service;

import java.util.logging.Level;

/**
 * Legacy command that now just invokes the command that replaced it.
 *
 * @deprecated folded into {@link fish.payara.notification.eventbus.core.SetEventbusNotifierConfigurationCommand}
 * @author mertcaliskan
 */
@Deprecated
@Service(name = "notification-eventbus-configure")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType(value = {CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTER, CommandTarget.CLUSTERED_INSTANCE, CommandTarget.CONFIG})
@RestEndpoints({
        @RestEndpoint(configBean = NotificationServiceConfiguration.class,
                opType = RestEndpoint.OpType.POST,
                path = "notification-eventbus-configure",
                description = "Configures Event Bus Notification Service")
})
public class EventBusNotificationConfigurer extends BaseSetNotifierConfigurationCommand {

    @Param(name = "topicName", defaultValue = "payara.notification.event", optional = true)
    private String topicName;

    @Override
    public void execute(final AdminCommandContext context) {
        configureNotifier(context, "set-eventbus-notifier-configuration");
    }

    @Override
    protected void configureNotifier(AdminCommandContext context, String commandName) {
        ParameterMap parameterMap = new ParameterMap();

        if (enabled != null) {
            parameterMap.insert("enabled", enabled.toString());
        }

        if (dynamic != null) {
            parameterMap.insert("dynamic", dynamic.toString());
        }

        if (StringUtils.ok(target)) {
            parameterMap.insert("target", target);
        }

        if (noisy != null) {
            parameterMap.insert("noisy", noisy.toString());
        }

        if (StringUtils.ok(topicName)) {
            parameterMap.insert("topicName", topicName);
        }

        try {
            Globals.getDefaultBaseServiceLocator().getService(CommandRunner.class)
                    .getCommandInvocation(commandName,
                            context.getActionReport().addSubActionsReport(), context.getSubject())
                    .parameters(parameterMap).execute();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, exception.getMessage());
            context.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
        }
    }

}

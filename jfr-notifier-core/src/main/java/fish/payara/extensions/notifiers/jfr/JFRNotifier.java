/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2021] Payara Foundation and/or its affiliates. All rights reserved.
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
package fish.payara.extensions.notifiers.jfr;

import fish.payara.internal.notification.PayaraConfiguredNotifier;
import fish.payara.internal.notification.PayaraNotification;
import fish.payara.notification.healthcheck.HealthCheckNotificationData;
import fish.payara.notification.requesttracing.RequestTracingNotificationData;
import jdk.jfr.*;
import org.jvnet.hk2.annotations.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Service(name = "jfr-notifier")
public class JFRNotifier extends PayaraConfiguredNotifier<JFRNotifierConfiguration> {

    private Logger logger;

    private List<ValueDescriptor> fields;

    @Override
    public void handleNotification(PayaraNotification notification) {
        if (!"*".equals(configuration.getFilterNames())) {
            if (!configuration.getFilterNames().contains(notification.getSubject())) {
                // Not wanted
                return;
            }
        }

        String[] category = {"Payara", notification.getSubject()};
        List<AnnotationElement> eventAnnotations = new ArrayList<>();

        eventAnnotations.add(new AnnotationElement(Name.class, generateFlightRecorderNameValue(notification)));
        eventAnnotations.add(new AnnotationElement(Label.class, "Payara JFR Notification"));
        eventAnnotations.add(new AnnotationElement(Description.class, "JFR Notification"));
        eventAnnotations.add(new AnnotationElement(Category.class, category));

        EventFactory eventFactory = EventFactory.create(eventAnnotations, fields);

        boolean knownData = false;
        if (notification.getData() instanceof HealthCheckNotificationData) {
            HealthCheckNotificationData data = (HealthCheckNotificationData) notification.getData();

            handleHealthCheckData(eventFactory, notification.getServerName(), data);
            knownData = true;
        }
        if (notification.getData() instanceof RequestTracingNotificationData) {
            RequestTracingNotificationData data = (RequestTracingNotificationData) notification.getData();
            handleRequestTracingData(eventFactory, notification.getServerName(), data);
            knownData = true;
        }
        if (!knownData) {
            String dataClassName = notification.getData() == null ? "null" : notification.getData().getClass().getName();
            logger.warning(String.format("Unknown Notification Data received; data class name '%s', message %s"
                    , dataClassName, notification.getMessage()));
        }


    }

    private void handleRequestTracingData(EventFactory eventFactory, String serverName, RequestTracingNotificationData data) {
        Event event = eventFactory.newEvent();
        event.set(0, serverName);

        event.set(1, "REQUEST TRACE");
        event.set(2, data.getRequestTrace().toString());
        event.commit();
    }

    private void handleHealthCheckData(EventFactory eventFactory, String serverName, HealthCheckNotificationData data) {
        if (data.getEntries() == null || data.getEntries().isEmpty()) {
            Event event = eventFactory.newEvent();
            event.set(0, serverName);
            event.set(1, "???");
            event.set(2, "Empty HealthCheckResultEntry");
            event.commit();

        } else {
            for (int i = 0; i < data.getEntries().size(); i++) {
                Event event = eventFactory.newEvent();
                event.set(0, serverName);
                event.set(1, data.getEntries().get(i).getStatus().name());
                event.set(2, data.getEntries().get(i).getMessage());
                event.commit();
            }
        }
    }

    private String generateFlightRecorderNameValue(PayaraNotification notification) {
        String suffix = notification.getSubject();
        // This needs to be a valid Java identifier
        suffix = suffix.replaceAll(" ", "_")
                .replaceAll(":", "_")
                .replaceAll("-", "_");
        return "payara." + suffix;
    }

    @Override
    public void bootstrap() {

        fields = new ArrayList<>();
        List<AnnotationElement> serverNameAnnotations = Collections.singletonList(new AnnotationElement(Label.class, "Server Name"));
        fields.add(new ValueDescriptor(String.class, "serverName", serverNameAnnotations));
        List<AnnotationElement> statusAnnotations = Collections.singletonList(new AnnotationElement(Label.class, "Status"));
        fields.add(new ValueDescriptor(String.class, "status", statusAnnotations));
        List<AnnotationElement> messageAnnotations = Collections.singletonList(new AnnotationElement(Label.class, "Message"));
        fields.add(new ValueDescriptor(String.class, "message", messageAnnotations));

        logger = Logger.getLogger(JFRNotifier.class.getName());
        logger.info("Starting JFR notifier");
    }

    @Override
    public void destroy() {
        logger.info("Destroying JFR notifier");
    }

}
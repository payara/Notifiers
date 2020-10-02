/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
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
package fish.payara.extensions.notifiers.teams;

import org.jvnet.hk2.annotations.Service;

import fish.payara.internal.notification.PayaraConfiguredNotifier;
import fish.payara.internal.notification.PayaraNotification;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

/**
 * Notifier to integrate with Microsoft Teams
 * @author jonathan coustick
 */
@Service(name = "teams-notifier")
public class TeamsNotifier extends PayaraConfiguredNotifier<TeamsNotifierConfiguration> {
    
    private static final Logger LOGGER = Logger.getLogger(TeamsNotifier.class.getPackage().toString());

    private URL url;
    
    @Override
    public void handleNotification(PayaraNotification event) {
        if (url == null) {
            LOGGER.fine("Teams notifier received notification, but no URL was available.");
            return;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod(HttpMethod.POST);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            
            connection.connect();
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(eventToCard(event));
                outputStream.flush();
            }
            
            if (connection.getResponseCode() == 200) {
                LOGGER.log(Level.FINE, "Message sent successfully");
                if (LOGGER.isLoggable(Level.FINEST)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = reader.lines().collect(Collectors.joining("\n"));
                    LOGGER.log(Level.FINEST, response);
                }
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                String response = reader.lines().collect(Collectors.joining("\n"));
                LOGGER.log(Level.WARNING, response);
                LOGGER.log(Level.SEVERE, "Error occurred while connecting to Microsoft Teams. Check your tokens. HTTP response code: {0}", connection.getResponseCode());
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while accessing URL: " + url.toString(), e);
        } catch (ProtocolException e) {
            LOGGER.log(Level.SEVERE, "Specified URL is not accepting protocol defined: " + HttpMethod.POST, e);
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Check your network connection. Cannot access URL: " + url.toString(), e);
        } catch (ConnectException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while connecting URL: " + url.toString(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO Error while accessing URL: " + url.toString(), e);
        }
    }

    @Override
    public void bootstrap() {
        try {
            this.url = new URL(configuration.getWebhookUrl());
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while accessing URL: " + url, e);
        }
    }
    
    private byte[] eventToCard(PayaraNotification event) {
        JsonObjectBuilder mainBuilder = Json.createObjectBuilder();
        
        mainBuilder.add("@type", "MessageCard");
        mainBuilder.add("@context", "http://schema.org/extensions");
        mainBuilder.add("themeColor", "004462");
        if (event.getEventType() != null) {
            mainBuilder.add("summary", event.getEventType());
        } else {
            mainBuilder.add("summary", event.getSubject());
        }
        
        mainBuilder.add("text", String.format("%s. (host: %s, server: %s, domain: %s, instance: %s)\n%s", 
                event.getSubject(),
                event.getHostName(),
                event.getServerName(),
                event.getDomainName(),
                event.getInstanceName(),
                event.getMessage()));
        return mainBuilder.build().toString().getBytes();
    }

}

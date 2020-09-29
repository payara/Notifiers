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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Notifier to integrate with Microsoft Teams
 * @author jonathan coustick
 */
@Service(name = "teams-notifier")
public class TeamsNotifier extends PayaraConfiguredNotifier<TeamsNotifierConfiguration> {
    
    private static final Logger LOGGER = Logger.getLogger(TeamsNotifier.class.getPackage().toString());

    private static final String AZURE_LOGIN_ENDPOINT_BASE = "https://login.microsoftonline.com/";
    private static final String AZURE_LOGIN_ENDPOINT_END = "/oauth2/v2.0/token";
    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String SCOPE = "scope";
    private static final String SCOPE_VALUE = "https://graph.microsoft.com/.default";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    
    private static final String TEAMS_BASE_ENDPOINT = "https://graph.microsoft.com/v1.0/teams/";
    private static final String CHANNELS = "/channels/";
    private static final String MESSAGES = "/messages";
    
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String POST = "POST";
    
    private String token;
    private Instant expires;
    
    @Override
    public void handleNotification(PayaraNotification event) {
        
        try {
            URL url = new URL(TEAMS_BASE_ENDPOINT + configuration.getGroupID() + CHANNELS + configuration.getChannelID() + MESSAGES);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(POST);
            connection.setRequestProperty(AUTHORIZATION, BEARER + token);
            connection.setRequestProperty(CONTENT_TYPE, MediaType.APPLICATION_JSON);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            
            
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(messageBody(event));
                stream.flush();
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode == Response.Status.OK.getStatusCode()) {
                JsonReader reader = Json.createReader(connection.getInputStream());
                JsonObject response = reader.readObject();
                int expireSeconds = response.getInt("expires_in");
                expires = Instant.now().plusSeconds(expireSeconds);
                token = response.getString("acess_token");
                LOGGER.log(Level.INFO, response.toString());
            } else {
                JsonReader reader = Json.createReader(connection.getErrorStream());
                JsonObject response = reader.readObject();
                LOGGER.log(Level.SEVERE, response.toString());
            }
            
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }    
        
    }

    @Override
    public void bootstrap() {
        LOGGER.log(Level.SEVERE, "Bootstrapping teams notifier");
        try {
            URL url = new URL(AZURE_LOGIN_ENDPOINT_BASE + configuration.getTenantID() + AZURE_LOGIN_ENDPOINT_END);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(POST);
            connection.setRequestProperty(CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(requestTokenBytes());
                stream.flush();
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == Response.Status.OK.getStatusCode()) {
                JsonReader reader = Json.createReader(connection.getInputStream());
                JsonObject response = reader.readObject();
                int expireSeconds = response.getInt("expires_in");
                expires = Instant.now().plusSeconds(expireSeconds);
                token = response.getString("access_token");
                LOGGER.log(Level.INFO, response.toString());
            } else {
                JsonReader reader = Json.createReader(connection.getErrorStream());
                JsonObject response = reader.readObject();
                LOGGER.log(Level.SEVERE, response.toString());
            }
            
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }    
        
        
    }
    
    private byte[] requestTokenBytes() {
        String parameters = GRANT_TYPE + "=" + CLIENT_CREDENTIALS + "&" + SCOPE + "=" + SCOPE_VALUE + "&";
        parameters += CLIENT_ID + "=" + configuration.getApplicationID() + "&" + CLIENT_SECRET + "=" + configuration.getApplicationSecret();
        return parameters.getBytes();
    }
    
    private byte[] messageBody(PayaraNotification event) {
        JsonObjectBuilder contentBuilder = Json.createObjectBuilder();
        contentBuilder.add("content", event.getMessage());
        JsonObjectBuilder body = Json.createObjectBuilder();
        body.add("body", contentBuilder);
        return body.build().toString().getBytes();
    }

}
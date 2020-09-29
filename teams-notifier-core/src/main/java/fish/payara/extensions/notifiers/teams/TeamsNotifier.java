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
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    Client client;

    private static final String TEAMS_BASE_ENDPOINT = "https://graph.microsoft.com/v1.0/teams/";
    private static final String CHANNELS = "/channels/";
    private static final String MESSAGES = "/messages";
    
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";
    
    @Override
    public void handleNotification(PayaraNotification event) {
        WebTarget target = client.target(TEAMS_BASE_ENDPOINT + configuration.getGroupID() + CHANNELS + configuration.getChannelID() + MESSAGES);
        Builder targetBuilder = target.request(MediaType.APPLICATION_JSON);
        targetBuilder.header(AUTHORIZATION, BASIC + configuration.getAuthorization());
        Response response = targetBuilder.post(Entity.json(event.getData()));
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            LOGGER.log(Level.INFO, response.readEntity(String.class));
        } else {
            LOGGER.log(Level.SEVERE, response.readEntity(String.class));
        }
    }

    @Override
    public void bootstrap() {
        LOGGER.log(Level.SEVERE, "Bootstrapping teams notifier");
        client = ClientBuilder.newClient();
    }

}
/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.workshop.continuousdelivery;

import java.io.IOException;
import java.util.Collection;

public class ContinuousDeliveryInfrastructureDocsGenerator {

    public static void main(String[] args) {
        final WorkshopInfrastructure workshopInfrastructure = WorkshopInfrastructure.create()
                .withGithubGuestInfo("xebia-guest", "xebia-guest", "xxx")
                .withNexusDomainName("nexus.xebia-tech-event.info")
                .build();

        try {
            ContinuousDeliveryInfrastructureCreator creator = new ContinuousDeliveryInfrastructureCreator();
            Collection<TeamInfrastructure> teamInfrastructures = creator.discoverInfrastructureTopology(workshopInfrastructure);
            creator.generateDocs(teamInfrastructures, "/tmp/continuous-delivery/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

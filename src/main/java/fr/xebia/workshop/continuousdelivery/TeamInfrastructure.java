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

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * <p>
 * Team infrastructure for the lab.
 * </p>
 * 
 */
public class TeamInfrastructure {
    public static final Function<String, TeamInfrastructure> FUNCTION_TEAM_IDENTIFIER_TO_TEAM_INFRASTRUCTURE = new Function<String, TeamInfrastructure>() {

        @Override
        public TeamInfrastructure apply(String teamIdentifier) {
            return new TeamInfrastructure(teamIdentifier);
        }
    };

    public static String getJenkinsUrl(Instance jenkins) {
        Preconditions.checkState(jenkins.getPublicDnsName() != null && !jenkins.getPublicDnsName().isEmpty(),
                "Given jenkins is not yet initialized, it publicDnsNAme is null: %s", jenkins);
        return "http://" + jenkins.getPublicDnsName() + ":8080/";
    }

    public static String getRundeckUrl(Instance rundeck) {
        Preconditions.checkState(rundeck.getPublicDnsName() != null, "Given rundeck is not yet initialized, it publicDnsNAme is null: %s",
                rundeck);
        return "http://" + rundeck.getPublicDnsName() + ":4440/";
    }

    static final String ROLE_JENKINS_RUNDECK = "jenkins,rundeck";

    static final String ROLE_NEXUS = "nexus";

    static final String ROLE_TOMCAT = "tomcat";

    private Instance devTomcat;

    private String devTomcatName = "#devTomcat#";

    private final String identifier;

    private Instance jenkins;

    private String jenkinsName = "#jenkins#";

    private Instance nexus;

    private Instance rundeck;

    private String rundeckName = "#rundeck#";

    private Instance validTomcat1;

    private String validTomcat1Name = "#validTomcat1#";

    private Instance validTomcat2;

    private String validTomcat2Name = "#validTomcat2#";

    public TeamInfrastructure(String identifier) {
        super();
        this.identifier = Preconditions.checkNotNull(identifier);
    }

    public void addInstance(Instance instance, Map<String, String> tags) {
        String name = tags.get("Name");
        String role = tags.get("Role");

        if (ROLE_JENKINS_RUNDECK.equals(role)) {
            this.jenkins = instance;
            this.rundeck = instance;
            this.jenkinsName = name;
            this.rundeckName = name;
        } else if (role.startsWith((ROLE_TOMCAT + "-"))) {
            String environment = role.substring((ROLE_TOMCAT + "-").length());
            if ("dev".equals(environment)) {
                devTomcat = instance;
                devTomcatName = name;
            } else if ("valid".equals(environment)) {
                if (validTomcat1 == null) {
                    validTomcat1 = instance;
                    validTomcat1Name = name;
                } else if (validTomcat2 == null) {
                    validTomcat2 = instance;
                    validTomcat2Name = name;
                } else {
                    throw new IllegalStateException("Valid tomcats already set");
                }
            } else {
                throw new IllegalStateException("Dev tomcat already set");
            }

        }

    }

    /**
     * 
     * FIXME cleanup this dirty code (CLC)
     * 
     * @param environment
     *            like "dev" or "valid"
     */
    public void addTomcat(@Nonnull String environment, @Nonnull Instance tomcatInstance, @Nonnull String tomcatServerName) {
        if ("dev".equals(environment)) {
            devTomcat = tomcatInstance;
            devTomcatName = tomcatServerName;
        } else if ("valid".equals(environment)) {
            if (validTomcat1 == null) {
                validTomcat1 = tomcatInstance;
                validTomcat1Name = tomcatServerName;
            } else if (validTomcat2 == null) {
                validTomcat2 = tomcatInstance;
                validTomcat2Name = tomcatServerName;
            } else {
                throw new IllegalStateException("Valid tomcats already set");
            }
        } else {
            throw new IllegalStateException("Dev tomcat already set");
        }
    }

    public Instance getDevTomcat() {
        return devTomcat;
    }

    public String getDevTomcatName() {
        return devTomcatName;
    }

    /**
     * Team identifier like a trigram or a number.
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The Jenkins server
     */
    @Nullable
    public Instance getJenkins() {
        return jenkins;
    }

    public String getJenkinsName() {
        return jenkinsName;
    }

    public String getJenkinsUrl() {
        return TeamInfrastructure.getJenkinsUrl(jenkins);
    }

    public Instance getNexus() {
        return nexus;
    }

    public String getProjectMavenGroupId() {
        return "fr.xebia.demo.petclinic-" + identifier;
    }

    public Instance getRundeck() {
        return rundeck;
    }

    public String getRundeckName() {
        return rundeckName;
    }

    public String getRundeckUrl() {
        return getRundeckUrl(rundeck);
    }

    public Instance getValidTomcat1() {
        return validTomcat1;
    }

    public String getValidTomcat1Name() {
        return validTomcat1Name;
    }

    public Instance getValidTomcat2() {
        return validTomcat2;
    }

    public String getValidTomcat2Name() {
        return validTomcat2Name;
    }

    public void setDevTomcat(Instance devTomcat) {
        this.devTomcat = devTomcat;
    }

    public void setDevTomcatName(String devTomcatName) {
        this.devTomcatName = devTomcatName;
    }

    public void setJenkins(Instance jenkins) {
        this.jenkins = jenkins;
    }

    public void setJenkinsName(String jenkinsName) {
        this.jenkinsName = jenkinsName;
    }

    public void setNexus(Instance nexus) {
        this.nexus = nexus;
    }

    public void setRundeck(Instance rundeck) {
        this.rundeck = rundeck;
    }

    public void setRundeckName(String rundeckName) {
        this.rundeckName = rundeckName;
    }

    public void setValidTomcat1(Instance validTomcat1) {
        this.validTomcat1 = validTomcat1;
    }

    public void setValidTomcat1Name(String validTomcat1Name) {
        this.validTomcat1Name = validTomcat1Name;
    }

    public void setValidTomcat2(Instance validTomcat2) {
        this.validTomcat2 = validTomcat2;
    }

    public void setValidTomcat2Name(String validTomcat2Name) {
        this.validTomcat2Name = validTomcat2Name;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this) //
                .add("id", identifier) //
                .add(jenkinsName, jenkins) //
                .add(devTomcatName, devTomcat) //
                .add(validTomcat1Name, validTomcat1) //
                .add(validTomcat2Name, validTomcat2) //
                .add("nexus", nexus) //
                .toString();
    }
}
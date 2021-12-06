/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.net.was.rest.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class Permissions
{
    private final String actions;
    private final String administration;
    private final String blocking;
    private final String checks;
    private final String contents;
    private final String deployments;
    private final String discussions;
    private final String emails;
    private final String followers;
    private final String gpgKeys;
    private final String interactionLimits;
    private final String issues;
    private final String keys;
    private final String members;
    private final String metadata;
    private final String organizationAdministration;
    private final String organizationEvents;
    private final String organizationHooks;
    private final String organizationPackages;
    private final String organizationProjects;
    private final String organizationUserBlocking;
    private final String packages;
    private final String pages;
    private final String pullRequests;
    private final String profile;
    private final String repositoryHooks;
    private final String repositoryProjects;
    private final String secrets;
    private final String secretScanningAlerts;
    private final String securityEvents;
    private final String selfHostedRunners;
    private final String singleFile;
    private final String starring;
    private final String statuses;
    private final String teamDiscussions;
    private final String workflows;
    private final String vulnerabilityAlerts;

    public Permissions(
            @JsonProperty("actions") String actions,
            @JsonProperty("administration") String administration,
            @JsonProperty("blocking") String blocking,
            @JsonProperty("checks") String checks,
            @JsonProperty("contents") String contents,
            @JsonProperty("deployments") String deployments,
            @JsonProperty("discussions") String discussions,
            @JsonProperty("emails") String emails,
            @JsonProperty("followers") String followers,
            @JsonProperty("gpg_keys") String gpgKeys,
            @JsonProperty("interaction_limits") String interactionLimits,
            @JsonProperty("issues") String issues,
            @JsonProperty("keys") String keys,
            @JsonProperty("members") String members,
            @JsonProperty("metadata") String metadata,
            @JsonProperty("organization_administration") String organizationAdministration,
            @JsonProperty("organization_events") String organizationEvents,
            @JsonProperty("organization_hooks") String organizationHooks,
            @JsonProperty("organization_packages") String organizationPackages,
            @JsonProperty("organization_projects") String organizationProjects,
            @JsonProperty("organization_user_blocking") String organizationUserBlocking,
            @JsonProperty("packages") String packages,
            @JsonProperty("pages") String pages,
            @JsonProperty("pull_requests") String pullRequests,
            @JsonProperty("profile") String profile,
            @JsonProperty("repository_hooks") String repositoryHooks,
            @JsonProperty("repository_projects") String repositoryProjects,
            @JsonProperty("secrets") String secrets,
            @JsonProperty("secret_scanning_alerts") String secretScanningAlerts,
            @JsonProperty("security_events") String securityEvents,
            @JsonProperty("self_hosted_runners") String selfHostedRunners,
            @JsonProperty("single_file") String singleFile,
            @JsonProperty("starring") String starring,
            @JsonProperty("statuses") String statuses,
            @JsonProperty("team_discussions") String teamDiscussions,
            @JsonProperty("workflows") String workflows,
            @JsonProperty("vulnerability_alerts") String vulnerabilityAlerts)
    {
        this.actions = actions;
        this.administration = administration;
        this.blocking = blocking;
        this.checks = checks;
        this.contents = contents;
        this.deployments = deployments;
        this.discussions = discussions;
        this.emails = emails;
        this.followers = followers;
        this.gpgKeys = gpgKeys;
        this.interactionLimits = interactionLimits;
        this.issues = issues;
        this.keys = keys;
        this.members = members;
        this.metadata = metadata;
        this.organizationAdministration = organizationAdministration;
        this.organizationEvents = organizationEvents;
        this.organizationHooks = organizationHooks;
        this.organizationPackages = organizationPackages;
        this.organizationProjects = organizationProjects;
        this.organizationUserBlocking = organizationUserBlocking;
        this.packages = packages;
        this.pages = pages;
        this.pullRequests = pullRequests;
        this.profile = profile;
        this.repositoryHooks = repositoryHooks;
        this.repositoryProjects = repositoryProjects;
        this.secrets = secrets;
        this.secretScanningAlerts = secretScanningAlerts;
        this.securityEvents = securityEvents;
        this.selfHostedRunners = selfHostedRunners;
        this.singleFile = singleFile;
        this.starring = starring;
        this.statuses = statuses;
        this.teamDiscussions = teamDiscussions;
        this.workflows = workflows;
        this.vulnerabilityAlerts = vulnerabilityAlerts;
    }

    public String getActions()
    {
        return actions;
    }

    public String getAdministration()
    {
        return administration;
    }

    public String getBlocking()
    {
        return blocking;
    }

    public String getChecks()
    {
        return checks;
    }

    public String getContents()
    {
        return contents;
    }

    public String getDeployments()
    {
        return deployments;
    }

    public String getDiscussions()
    {
        return discussions;
    }

    public String getEmails()
    {
        return emails;
    }

    public String getFollowers()
    {
        return followers;
    }

    public String getGpgKeys()
    {
        return gpgKeys;
    }

    public String getInteractionLimits()
    {
        return interactionLimits;
    }

    public String getIssues()
    {
        return issues;
    }

    public String getKeys()
    {
        return keys;
    }

    public String getMembers()
    {
        return members;
    }

    public String getMetadata()
    {
        return metadata;
    }

    public String getOrganizationAdministration()
    {
        return organizationAdministration;
    }

    public String getOrganizationEvents()
    {
        return organizationEvents;
    }

    public String getOrganizationHooks()
    {
        return organizationHooks;
    }

    public String getOrganizationPackages()
    {
        return organizationPackages;
    }

    public String getOrganizationProjects()
    {
        return organizationProjects;
    }

    public String getOrganizationUserBlocking()
    {
        return organizationUserBlocking;
    }

    public String getPackages()
    {
        return packages;
    }

    public String getPages()
    {
        return pages;
    }

    public String getPullRequests()
    {
        return pullRequests;
    }

    public String getProfile()
    {
        return profile;
    }

    public String getRepositoryHooks()
    {
        return repositoryHooks;
    }

    public String getRepositoryProjects()
    {
        return repositoryProjects;
    }

    public String getSecrets()
    {
        return secrets;
    }

    public String getSecretScanningAlerts()
    {
        return secretScanningAlerts;
    }

    public String getSecurityEvents()
    {
        return securityEvents;
    }

    public String getSelfHostedRunners()
    {
        return selfHostedRunners;
    }

    public String getSingleFile()
    {
        return singleFile;
    }

    public String getStarring()
    {
        return starring;
    }

    public String getStatuses()
    {
        return statuses;
    }

    public String getTeamDiscussions()
    {
        return teamDiscussions;
    }

    public String getWorkflows()
    {
        return workflows;
    }

    public String getVulnerabilityAlerts()
    {
        return vulnerabilityAlerts;
    }
}

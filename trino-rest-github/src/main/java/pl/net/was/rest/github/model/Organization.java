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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.trino.spi.block.BlockBuilder;

import java.time.ZonedDateTime;
import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization
        extends BaseBlockWriter
{
    private final String login;
    private final long id;
    private final String description;
    private final String name;
    private final String company;
    private final String blog;
    private final String location;
    private final String email;
    private final String twitterUsername;
    private final boolean isVerified;
    private final boolean hasOrganizationProjects;
    private final boolean hasRepositoryProjects;
    private final long publicRepos;
    private final long publicGists;
    private final long followers;
    private final long following;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final String type;
    private final long totalPrivateRepos;
    private final long ownedPrivateRepos;
    private final long privateGists;
    private final long diskUsage;
    private final long collaborators;
    private final String billingEmail;
    private final String defaultRepositoryPermission;
    private final boolean membersCanCreateRepositories;
    private final boolean twoFactorRequirementEnabled;
    private final String membersAllowedRepositoryCreationType;
    private final boolean membersCanCreatePublicRepositories;
    private final boolean membersCanCreatePrivateRepositories;
    private final boolean membersCanCreateInternalRepositories;
    private final boolean membersCanCreatePages;

    public Organization(
            @JsonProperty("login") String login,
            @JsonProperty("id") long id,
            @JsonProperty("description") String description,
            @JsonProperty("name") String name,
            @JsonProperty("company") String company,
            @JsonProperty("blog") String blog,
            @JsonProperty("location") String location,
            @JsonProperty("email") String email,
            @JsonProperty("twitter_username") String twitterUsername,
            @JsonProperty("is_verified") boolean isVerified,
            @JsonProperty("has_organization_projects") boolean hasOrganizationProjects,
            @JsonProperty("has_repository_projects") boolean hasRepositoryProjects,
            @JsonProperty("public_repos") long publicRepos,
            @JsonProperty("public_gists") long publicGists,
            @JsonProperty("followers") long followers,
            @JsonProperty("following") long following,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("type") String type,
            @JsonProperty("total_private_repos") long totalPrivateRepos,
            @JsonProperty("owned_private_repos") long ownedPrivateRepos,
            @JsonProperty("private_gists") long privateGists,
            @JsonProperty("disk_usage") long diskUsage,
            @JsonProperty("collaborators") long collaborators,
            @JsonProperty("billing_email") String billingEmail,
            @JsonProperty("default_repository_permission") String defaultRepositoryPermission,
            @JsonProperty("members_can_create_repositories") boolean membersCanCreateRepositories,
            @JsonProperty("two_factor_requirement_enabled") boolean twoFactorRequirementEnabled,
            @JsonProperty("members_allowed_repository_creation_type") String membersAllowedRepositoryCreationType,
            @JsonProperty("members_can_create_public_repositories") boolean membersCanCreatePublicRepositories,
            @JsonProperty("members_can_create_private_repositories") boolean membersCanCreatePrivateRepositories,
            @JsonProperty("members_can_create_internal_repositories") boolean membersCanCreateInternalRepositories,
            @JsonProperty("members_can_create_pages") boolean membersCanCreatePages)
    {
        this.login = login;
        this.id = id;
        this.description = description;
        this.name = name;
        this.company = company;
        this.blog = blog;
        this.location = location;
        this.email = email;
        this.twitterUsername = twitterUsername;
        this.isVerified = isVerified;
        this.hasOrganizationProjects = hasOrganizationProjects;
        this.hasRepositoryProjects = hasRepositoryProjects;
        this.publicRepos = publicRepos;
        this.publicGists = publicGists;
        this.followers = followers;
        this.following = following;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.type = type;
        this.totalPrivateRepos = totalPrivateRepos;
        this.ownedPrivateRepos = ownedPrivateRepos;
        this.privateGists = privateGists;
        this.diskUsage = diskUsage;
        this.collaborators = collaborators;
        this.billingEmail = billingEmail;
        this.defaultRepositoryPermission = defaultRepositoryPermission;
        this.membersCanCreateRepositories = membersCanCreateRepositories;
        this.twoFactorRequirementEnabled = twoFactorRequirementEnabled;
        this.membersAllowedRepositoryCreationType = membersAllowedRepositoryCreationType;
        this.membersCanCreatePublicRepositories = membersCanCreatePublicRepositories;
        this.membersCanCreatePrivateRepositories = membersCanCreatePrivateRepositories;
        this.membersCanCreateInternalRepositories = membersCanCreateInternalRepositories;
        this.membersCanCreatePages = membersCanCreatePages;
    }

    public List<?> toRow()
    {
        // TODO allow nulls
        return ImmutableList.of(
                login,
                id,
                description != null ? description : "",
                name,
                company != null ? company : "",
                blog != null ? blog : "",
                location != null ? location : "",
                email != null ? email : "",
                twitterUsername != null ? twitterUsername : "",
                isVerified,
                hasOrganizationProjects,
                hasRepositoryProjects,
                publicRepos,
                publicGists,
                followers,
                following,
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                type,
                totalPrivateRepos,
                ownedPrivateRepos,
                privateGists,
                diskUsage,
                collaborators,
                billingEmail != null ? billingEmail : "",
                defaultRepositoryPermission != null ? defaultRepositoryPermission : "",
                membersCanCreateRepositories,
                twoFactorRequirementEnabled,
                membersAllowedRepositoryCreationType != null ? membersAllowedRepositoryCreationType : "",
                membersCanCreatePublicRepositories,
                membersCanCreatePrivateRepositories,
                membersCanCreateInternalRepositories,
                membersCanCreatePages);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        writeString(rowBuilder, login);
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, description);
        writeString(rowBuilder, name);
        writeString(rowBuilder, company);
        writeString(rowBuilder, blog);
        writeString(rowBuilder, location);
        writeString(rowBuilder, email);
        writeString(rowBuilder, twitterUsername);
        BOOLEAN.writeBoolean(rowBuilder, isVerified);
        BOOLEAN.writeBoolean(rowBuilder, hasOrganizationProjects);
        BOOLEAN.writeBoolean(rowBuilder, hasRepositoryProjects);
        BIGINT.writeLong(rowBuilder, publicRepos);
        BIGINT.writeLong(rowBuilder, publicGists);
        BIGINT.writeLong(rowBuilder, followers);
        BIGINT.writeLong(rowBuilder, following);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeString(rowBuilder, type);
        BIGINT.writeLong(rowBuilder, totalPrivateRepos);
        BIGINT.writeLong(rowBuilder, ownedPrivateRepos);
        BIGINT.writeLong(rowBuilder, privateGists);
        BIGINT.writeLong(rowBuilder, diskUsage);
        BIGINT.writeLong(rowBuilder, collaborators);
        writeString(rowBuilder, billingEmail);
        writeString(rowBuilder, defaultRepositoryPermission);
        BOOLEAN.writeBoolean(rowBuilder, membersCanCreateRepositories);
        BOOLEAN.writeBoolean(rowBuilder, twoFactorRequirementEnabled);
        writeString(rowBuilder, membersAllowedRepositoryCreationType);
        BOOLEAN.writeBoolean(rowBuilder, membersCanCreatePublicRepositories);
        BOOLEAN.writeBoolean(rowBuilder, membersCanCreatePrivateRepositories);
        BOOLEAN.writeBoolean(rowBuilder, membersCanCreateInternalRepositories);
        BOOLEAN.writeBoolean(rowBuilder, membersCanCreatePages);
    }
}

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
import com.google.common.collect.ImmutableList;
import io.trino.spi.block.BlockBuilder;

import java.time.ZonedDateTime;
import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;

@SuppressWarnings("unused")
public class User
        extends BaseBlockWriter
{
    private final String login;
    private final long id;
    private final String nodeId;
    private final String avatarUrl;
    private final String gravatarId;
    private final String url;
    private final String htmlUrl;
    private final String followersUrl;
    private final String followingUrl;
    private final String gistsUrl;
    private final String starredUrl;
    private final String subscriptionsUrl;
    private final String organizationsUrl;
    private final String reposUrl;
    private final String eventsUrl;
    private final String receivedEventsUrl;
    private final String type;
    private final boolean siteAdmin;
    private final String name;
    private final String company;
    private final String blog;
    private final String location;
    private final String email;
    private final boolean hireable;
    private final String bio;
    private final String twitterUsername;
    private final long publicRepos;
    private final long publicGists;
    private final long followers;
    private final long following;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;

    public User(
            @JsonProperty("login") String login,
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("gravatar_id") String gravatarId,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("followers_url") String followersUrl,
            @JsonProperty("following_url") String followingUrl,
            @JsonProperty("gists_url") String gistsUrl,
            @JsonProperty("starred_url") String starredUrl,
            @JsonProperty("subscriptions_url") String subscriptionsUrl,
            @JsonProperty("organizations_url") String organizationsUrl,
            @JsonProperty("repos_url") String reposUrl,
            @JsonProperty("events_url") String eventsUrl,
            @JsonProperty("received_events_url") String receivedEventsUrl,
            @JsonProperty("type") String type,
            @JsonProperty("site_admin") boolean siteAdmin,
            @JsonProperty("name") String name,
            @JsonProperty("company") String company,
            @JsonProperty("blog") String blog,
            @JsonProperty("location") String location,
            @JsonProperty("email") String email,
            @JsonProperty("hireable") boolean hireable,
            @JsonProperty("bio") String bio,
            @JsonProperty("twitter_username") String twitterUsername,
            @JsonProperty("public_repos") long publicRepos,
            @JsonProperty("public_gists") long publicGists,
            @JsonProperty("followers") long followers,
            @JsonProperty("following") long following,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt)
    {
        this.login = login;
        this.id = id;
        this.nodeId = nodeId;
        this.avatarUrl = avatarUrl;
        this.gravatarId = gravatarId;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.followersUrl = followersUrl;
        this.followingUrl = followingUrl;
        this.gistsUrl = gistsUrl;
        this.starredUrl = starredUrl;
        this.subscriptionsUrl = subscriptionsUrl;
        this.organizationsUrl = organizationsUrl;
        this.reposUrl = reposUrl;
        this.eventsUrl = eventsUrl;
        this.receivedEventsUrl = receivedEventsUrl;
        this.type = type;
        this.siteAdmin = siteAdmin;
        this.name = name;
        this.company = company;
        this.blog = blog;
        this.location = location;
        this.email = email;
        this.hireable = hireable;
        this.bio = bio;
        this.twitterUsername = twitterUsername;
        this.publicRepos = publicRepos;
        this.publicGists = publicGists;
        this.followers = followers;
        this.following = following;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId()
    {
        return id;
    }

    public String getLogin()
    {
        return login;
    }

    public List<?> toRow()
    {
        // TODO allow nulls
        return ImmutableList.of(
                login,
                id,
                avatarUrl != null ? avatarUrl : "",
                gravatarId != null ? gravatarId : "",
                type,
                siteAdmin,
                name,
                company != null ? company : "",
                blog != null ? blog : "",
                location != null ? location : "",
                email != null ? email : "",
                hireable,
                bio != null ? bio : "",
                twitterUsername != null ? twitterUsername : "",
                publicRepos,
                publicGists,
                followers,
                following,
                packTimestamp(createdAt),
                packTimestamp(updatedAt));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        writeString(rowBuilder, login);
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, avatarUrl);
        writeString(rowBuilder, gravatarId);
        writeString(rowBuilder, type);
        BOOLEAN.writeBoolean(rowBuilder, siteAdmin);
        writeString(rowBuilder, name);
        writeString(rowBuilder, company);
        writeString(rowBuilder, blog);
        writeString(rowBuilder, location);
        writeString(rowBuilder, email);
        BOOLEAN.writeBoolean(rowBuilder, hireable);
        writeString(rowBuilder, bio);
        writeString(rowBuilder, twitterUsername);
        BIGINT.writeLong(rowBuilder, publicRepos);
        BIGINT.writeLong(rowBuilder, publicGists);
        BIGINT.writeLong(rowBuilder, followers);
        BIGINT.writeLong(rowBuilder, following);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
    }
}

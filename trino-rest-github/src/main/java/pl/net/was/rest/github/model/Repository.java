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
import io.trino.spi.type.MapType;
import io.trino.spi.type.TypeOperators;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class Repository
        extends BaseBlockWriter
{
    private final long id;
    private final String name;
    private final String fullName;
    private final User owner;
    private final boolean isPrivate;
    private final String description;
    private final boolean fork;
    private final String homepage;
    private final String url;
    private final long forksCount;
    private final long stargazersCount;
    private final long watchersCount;
    private final long size;
    private final String defaultBranch;
    private final long openIssuesCount;
    private final boolean isTemplate;
    private final String[] topics;
    private final boolean hasIssues;
    private final boolean hasProjects;
    private final boolean hasWiki;
    private final boolean hasPages;
    private final boolean hasDownloads;
    private final boolean archived;
    private final boolean disabled;
    private final String visibility;
    private final ZonedDateTime pushedAt;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final Map<String, Boolean> permissions;

    public Repository(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("owner") User owner,
            @JsonProperty("private") boolean isPrivate,
            @JsonProperty("description") String description,
            @JsonProperty("fork") boolean fork,
            @JsonProperty("homepage") String homepage,
            @JsonProperty("url") String url,
            @JsonProperty("forks_count") long forksCount,
            @JsonProperty("stargazers_count") long stargazersCount,
            @JsonProperty("watchers_count") long watchersCount,
            @JsonProperty("size") long size,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("open_issues_count") long openIssuesCount,
            @JsonProperty("is_template") boolean isTemplate,
            @JsonProperty("topics") String[] topics,
            @JsonProperty("has_issues") boolean hasIssues,
            @JsonProperty("has_projects") boolean hasProjects,
            @JsonProperty("has_wiki") boolean hasWiki,
            @JsonProperty("has_pages") boolean hasPages,
            @JsonProperty("has_downloads") boolean hasDownloads,
            @JsonProperty("archived") boolean archived,
            @JsonProperty("disabled") boolean disabled,
            @JsonProperty("visibility") String visibility,
            @JsonProperty("pushed_at") ZonedDateTime pushedAt,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("permissions") Map<String, Boolean> permissions)
    {
        this.id = id;
        this.name = name;
        this.fullName = fullName;
        this.owner = owner;
        this.isPrivate = isPrivate;
        this.description = description;
        this.fork = fork;
        this.homepage = homepage;
        this.url = url;
        this.forksCount = forksCount;
        this.stargazersCount = stargazersCount;
        this.watchersCount = watchersCount;
        this.size = size;
        this.defaultBranch = defaultBranch;
        this.openIssuesCount = openIssuesCount;
        this.isTemplate = isTemplate;
        this.topics = topics;
        this.hasIssues = hasIssues;
        this.hasProjects = hasProjects;
        this.hasWiki = hasWiki;
        this.hasPages = hasPages;
        this.hasDownloads = hasDownloads;
        this.archived = archived;
        this.disabled = disabled;
        this.visibility = visibility;
        this.pushedAt = pushedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.permissions = permissions;
    }

    public long getOpenIssuesCount()
    {
        return openIssuesCount;
    }

    public List<?> toRow()
    {
        BlockBuilder topics = VARCHAR.createBlockBuilder(null, this.topics != null ? this.topics.length : 0);
        if (this.topics != null) {
            for (String topic : this.topics) {
                VARCHAR.writeString(topics, topic);
            }
        }
        MapType mapType = new MapType(VARCHAR, BOOLEAN, new TypeOperators());
        BlockBuilder permissions = mapType.createBlockBuilder(null, this.permissions != null ? this.permissions.size() : 0);
        if (this.permissions != null) {
            BlockBuilder builder = permissions.beginBlockEntry();
            for (Map.Entry<String, Boolean> permission : this.permissions.entrySet()) {
                VARCHAR.writeString(builder, permission.getKey());
                BOOLEAN.writeBoolean(builder, permission.getValue());
            }
            permissions.closeEntry();
        }
        // TODO allow nulls
        return ImmutableList.of(
                id,
                name != null ? name : "",
                fullName != null ? fullName : "",
                owner.getId(),
                owner.getLogin(),
                isPrivate,
                description != null ? description : "",
                fork,
                homepage != null ? homepage : "",
                url != null ? url : "",
                forksCount,
                stargazersCount,
                watchersCount,
                size,
                defaultBranch,
                openIssuesCount,
                isTemplate,
                topics.build(),
                hasIssues,
                hasProjects,
                hasWiki,
                hasPages,
                hasDownloads,
                archived,
                disabled,
                visibility != null ? visibility : "",
                packTimestamp(pushedAt),
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                mapType.getObject(permissions, 0));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, name);
        writeString(rowBuilder, fullName);
        BIGINT.writeLong(rowBuilder, owner.getId());
        writeString(rowBuilder, owner.getLogin());
        BOOLEAN.writeBoolean(rowBuilder, isPrivate);
        writeString(rowBuilder, description);
        BOOLEAN.writeBoolean(rowBuilder, fork);
        writeString(rowBuilder, homepage);
        writeString(rowBuilder, url);
        BIGINT.writeLong(rowBuilder, forksCount);
        BIGINT.writeLong(rowBuilder, stargazersCount);
        BIGINT.writeLong(rowBuilder, watchersCount);
        BIGINT.writeLong(rowBuilder, size);
        writeString(rowBuilder, defaultBranch);
        BIGINT.writeLong(rowBuilder, openIssuesCount);
        BOOLEAN.writeBoolean(rowBuilder, isTemplate);

        BlockBuilder topics = VARCHAR.createBlockBuilder(null, this.topics != null ? this.topics.length : 0);
        if (this.topics != null) {
            for (String topic : this.topics) {
                VARCHAR.writeString(topics, topic);
            }
        }
        rowBuilder.appendStructure(topics.build());
        BOOLEAN.writeBoolean(rowBuilder, hasIssues);
        BOOLEAN.writeBoolean(rowBuilder, hasProjects);
        BOOLEAN.writeBoolean(rowBuilder, hasWiki);
        BOOLEAN.writeBoolean(rowBuilder, hasPages);
        BOOLEAN.writeBoolean(rowBuilder, hasDownloads);
        BOOLEAN.writeBoolean(rowBuilder, archived);
        BOOLEAN.writeBoolean(rowBuilder, disabled);
        writeString(rowBuilder, visibility);
        writeTimestamp(rowBuilder, pushedAt);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);

        MapType mapType = new MapType(VARCHAR, BOOLEAN, new TypeOperators());
        BlockBuilder permissions = mapType.createBlockBuilder(null, this.permissions != null ? this.permissions.size() : 0);
        if (this.permissions != null) {
            BlockBuilder builder = permissions.beginBlockEntry();
            for (Map.Entry<String, Boolean> permission : this.permissions.entrySet()) {
                VARCHAR.writeString(builder, permission.getKey());
                BOOLEAN.writeBoolean(builder, permission.getValue());
            }
            permissions.closeEntry();
            rowBuilder.appendStructure(mapType.getObject(permissions, 0));
        }
        else {
            rowBuilder.appendNull();
        }
    }
}

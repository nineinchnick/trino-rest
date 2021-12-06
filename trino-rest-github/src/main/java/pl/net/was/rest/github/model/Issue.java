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
import static io.trino.spi.type.VarcharType.VARCHAR;

@SuppressWarnings("unused")
public class Issue
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String url;
    private final String eventsUrl;
    private final String htmlUrl;
    private final long number;
    private final String state;
    private final String title;
    private final String body;
    private final User user;
    private final List<Label> labels;
    private final User assignee;
    private final Milestone milestone;
    private final Boolean locked;
    private final String activeLockReason;
    private final long comments;
    private final ZonedDateTime closedAt;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final String authorAssociation;

    public Issue(
            @JsonProperty("id") long id,
            @JsonProperty("url") String url,
            @JsonProperty("events_url") String eventsUrl,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("number") long number,
            @JsonProperty("state") String state,
            @JsonProperty("title") String title,
            @JsonProperty("body") String body,
            @JsonProperty("user") User user,
            @JsonProperty("labels") List<Label> labels,
            @JsonProperty("assignee") User assignee,
            @JsonProperty("milestone") Milestone milestone,
            @JsonProperty("locked") Boolean locked,
            @JsonProperty("active_lock_reason") String activeLockReason,
            @JsonProperty("comments") long comments,
            @JsonProperty("closed_at") ZonedDateTime closedAt,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("author_association") String authorAssociation)
    {
        this.id = id;
        this.url = url;
        this.eventsUrl = eventsUrl;
        this.htmlUrl = htmlUrl;
        this.number = number;
        this.state = state;
        this.title = title;
        this.body = body;
        this.user = user;
        this.labels = labels;
        this.assignee = assignee;
        this.milestone = milestone;
        this.locked = locked;
        this.activeLockReason = activeLockReason;
        this.comments = comments;
        this.closedAt = closedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.authorAssociation = authorAssociation;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public List<?> toRow()
    {
        // TODO allow nulls

        BlockBuilder labelIds = BIGINT.createBlockBuilder(null, labels.size());
        BlockBuilder labelNames = VARCHAR.createBlockBuilder(null, labels.size());
        for (Label label : labels) {
            BIGINT.writeLong(labelIds, label.getId());
            VARCHAR.writeString(labelNames, label.getName());
        }

        return ImmutableList.of(
                owner,
                repo,
                id,
                number,
                state,
                title != null ? title : "",
                body != null ? body : "",
                user.getId(),
                user.getLogin(),
                labelIds.build(),
                labelNames.build(),
                assignee != null ? assignee.getId() : 0,
                assignee != null ? assignee.getLogin() : "",
                milestone != null ? milestone.getId() : 0,
                milestone != null ? milestone.getTitle() : "",
                locked,
                activeLockReason != null ? activeLockReason : "",
                comments,
                packTimestamp(closedAt),
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                authorAssociation);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        BIGINT.writeLong(rowBuilder, number);
        writeString(rowBuilder, state);
        writeString(rowBuilder, title);
        writeString(rowBuilder, body);
        BIGINT.writeLong(rowBuilder, user.getId());
        writeString(rowBuilder, user.getLogin());

        if (labels == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            // labels array
            BlockBuilder labelIds = BIGINT.createBlockBuilder(null, labels.size());
            for (Label label : labels) {
                BIGINT.writeLong(labelIds, label.getId());
            }
            rowBuilder.appendStructure(labelIds.build());

            BlockBuilder labelNames = VARCHAR.createBlockBuilder(null, labels.size());
            for (Label label : labels) {
                writeString(labelNames, label.getName());
            }
            rowBuilder.appendStructure(labelNames.build());
        }

        if (assignee == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, assignee.getId());
            writeString(rowBuilder, assignee.getLogin());
        }
        if (milestone == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, milestone.getId());
            writeString(rowBuilder, milestone.getTitle());
        }
        BOOLEAN.writeBoolean(rowBuilder, locked);
        writeString(rowBuilder, activeLockReason);
        BIGINT.writeLong(rowBuilder, comments);
        writeTimestamp(rowBuilder, closedAt);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeString(rowBuilder, authorAssociation);
    }
}

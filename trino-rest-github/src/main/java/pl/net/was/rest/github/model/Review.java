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

@SuppressWarnings("unused")
public class Review
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private long pullNumber;
    private final long id;
    private final String nodeId;
    private final User user;
    private final String body;
    private final String htmlUrl;
    private final String pullRequestUrl;
    private final String authorAssociation;
    private final String state;
    private final ZonedDateTime submittedAt;
    private final String commitId;

    public Review(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("user") User user,
            @JsonProperty("body") String body,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("pull_request_url") String pullRequestUrl,
            @JsonProperty("author_association") String authorAssociation,
            @JsonProperty("_links") Object unusedLinks,
            @JsonProperty("state") String state,
            @JsonProperty("submitted_at") ZonedDateTime submittedAt,
            @JsonProperty("commit_id") String commitId)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.user = user;
        this.body = body;
        this.htmlUrl = htmlUrl;
        this.pullRequestUrl = pullRequestUrl;
        this.authorAssociation = authorAssociation;
        this.state = state;
        this.submittedAt = submittedAt;
        this.commitId = commitId;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public void setPullNumber(long pullNumber)
    {
        this.pullNumber = pullNumber;
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                id,
                pullNumber,
                user.getId(),
                user.getLogin(),
                body != null ? body : "",
                state != null ? state : "",
                packTimestamp(submittedAt),
                commitId,
                authorAssociation);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        BIGINT.writeLong(rowBuilder, pullNumber);
        BIGINT.writeLong(rowBuilder, user.getId());
        writeString(rowBuilder, user.getLogin());
        writeString(rowBuilder, body);
        writeString(rowBuilder, state);
        writeTimestamp(rowBuilder, submittedAt);
        writeString(rowBuilder, commitId);
        writeString(rowBuilder, authorAssociation);
    }
}

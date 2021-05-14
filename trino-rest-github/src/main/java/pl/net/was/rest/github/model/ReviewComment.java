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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewComment
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final String url;
    private final long pullRequestReviewId;
    private final long id;
    private final String diffHunk;
    private final String path;
    private final long position;
    private final long originalPosition;
    private final String commitId;
    private final String originalCommitId;
    private final long inReplyToId;
    private final User user;
    private final String body;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final String htmlUrl;
    private final String pullRequestUrl;
    private final String authorAssociation;
    private final long startLine;
    private final long originalStartLine;
    private final String startSide;
    private final long line;
    private final long originalLine;
    private final String side;

    public ReviewComment(
            @JsonProperty("url") String url,
            @JsonProperty("pull_request_review_id") long pullRequestReviewId,
            @JsonProperty("id") long id,
            @JsonProperty("diff_hunk") String diffHunk,
            @JsonProperty("path") String path,
            @JsonProperty("position") long position,
            @JsonProperty("original_position") long originalPosition,
            @JsonProperty("commit_id") String commitId,
            @JsonProperty("original_commit_id") String originalCommitId,
            @JsonProperty("in_reply_to_id") long inReplyToId,
            @JsonProperty("user") User user,
            @JsonProperty("body") String body,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("pull_request_url") String pullRequestUrl,
            @JsonProperty("author_association") String authorAssociation,
            @JsonProperty("start_line") long startLine,
            @JsonProperty("original_start_line") long originalStartLine,
            @JsonProperty("start_side") String startSide,
            @JsonProperty("line") long line,
            @JsonProperty("original_line") long originalLine,
            @JsonProperty("side") String side)
    {
        this.url = url;
        this.pullRequestReviewId = pullRequestReviewId;
        this.id = id;
        this.diffHunk = diffHunk;
        this.path = path;
        this.position = position;
        this.originalPosition = originalPosition;
        this.commitId = commitId;
        this.originalCommitId = originalCommitId;
        this.inReplyToId = inReplyToId;
        this.user = user;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.htmlUrl = htmlUrl;
        this.pullRequestUrl = pullRequestUrl;
        this.authorAssociation = authorAssociation;
        this.startLine = startLine;
        this.originalStartLine = originalStartLine;
        this.startSide = startSide;
        this.line = line;
        this.originalLine = originalLine;
        this.side = side;
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
        return ImmutableList.of(
                owner,
                repo,
                pullRequestReviewId,
                id,
                diffHunk,
                path,
                position,
                originalPosition,
                commitId,
                originalCommitId,
                inReplyToId,
                user.getId(),
                user.getLogin(),
                body,
                createdAt,
                updatedAt,
                authorAssociation,
                startLine,
                originalStartLine,
                startSide,
                line,
                originalLine,
                side);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, pullRequestReviewId);
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, diffHunk);
        writeString(rowBuilder, path);
        BIGINT.writeLong(rowBuilder, position);
        BIGINT.writeLong(rowBuilder, originalPosition);
        writeString(rowBuilder, commitId);
        writeString(rowBuilder, originalCommitId);
        BIGINT.writeLong(rowBuilder, inReplyToId);
        BIGINT.writeLong(rowBuilder, user.getId());
        writeString(rowBuilder, user.getLogin());
        writeString(rowBuilder, body);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeString(rowBuilder, authorAssociation);
        BIGINT.writeLong(rowBuilder, startLine);
        BIGINT.writeLong(rowBuilder, originalStartLine);
        writeString(rowBuilder, startSide);
        BIGINT.writeLong(rowBuilder, line);
        BIGINT.writeLong(rowBuilder, originalLine);
        writeString(rowBuilder, side);
    }
}

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
public class Pull
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String url;
    private final String htmlUrl;
    private final String diffUrl;
    private final String patchUrl;
    private final String issueUrl;
    private final String commitsUrl;
    private final String reviewCommentsUrl;
    private final String reviewCommentUrl;
    private final String commentsUrl;
    private final String statusesUrl;
    private final long number;
    private final String state;
    private final Boolean locked;
    private final String title;
    private final User user;
    private final String body;
    private final List<Label> labels;
    private final Milestone milestone;
    private final String activeLockReason;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime closedAt;
    private final ZonedDateTime mergedAt;
    private final String mergedCommitSha;
    private final User assignee;
    private final List<User> requestedReviewers;
    private final String headRef;
    private final String headSha;
    private final String baseRef;
    private final String baseSha;
    private final String authorAssociation;
    private final Boolean draft;

    public Pull(
            @JsonProperty("id") long id,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("diff_url") String diffUrl,
            @JsonProperty("patch_url") String patchUrl,
            @JsonProperty("issue_url") String issueUrl,
            @JsonProperty("commits_url") String commitsUrl,
            @JsonProperty("review_comments_url") String reviewCommentsUrl,
            @JsonProperty("review_comment_url") String reviewCommentUrl,
            @JsonProperty("comments_url") String commentsUrl,
            @JsonProperty("statuses_url") String statusesUrl,
            @JsonProperty("number") long number,
            @JsonProperty("state") String state,
            @JsonProperty("locked") Boolean locked,
            @JsonProperty("title") String title,
            @JsonProperty("user") User user,
            @JsonProperty("body") String body,
            @JsonProperty("labels") List<Label> labels,
            @JsonProperty("milestone") Milestone milestone,
            @JsonProperty("active_lock_reason") String activeLockReason,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("closed_at") ZonedDateTime closedAt,
            @JsonProperty("merged_at") ZonedDateTime mergedAt,
            @JsonProperty("merged_commit_sha") String mergedCommitSha,
            @JsonProperty("assignee") User assignee,
            @JsonProperty("requested_reviewers") List<User> requestedReviewers,
            @JsonProperty("head") Ref head,
            @JsonProperty("base") Ref base,
            @JsonProperty("author_association") String authorAssociation,
            @JsonProperty("draft") Boolean draft)
    {
        this.id = id;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.diffUrl = diffUrl;
        this.patchUrl = patchUrl;
        this.issueUrl = issueUrl;
        this.commitsUrl = commitsUrl;
        this.reviewCommentsUrl = reviewCommentsUrl;
        this.reviewCommentUrl = reviewCommentUrl;
        this.commentsUrl = commentsUrl;
        this.statusesUrl = statusesUrl;
        this.number = number;
        this.state = state;
        this.locked = locked;
        this.title = title;
        this.user = user;
        this.body = body;
        this.labels = labels;
        this.milestone = milestone;
        this.activeLockReason = activeLockReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.mergedAt = mergedAt;
        this.mergedCommitSha = mergedCommitSha;
        this.assignee = assignee;
        this.requestedReviewers = requestedReviewers;
        this.headRef = head.getRef();
        this.headSha = head.getSha();
        this.baseRef = base.getRef();
        this.baseSha = base.getSha();
        this.authorAssociation = authorAssociation;
        this.draft = draft;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public long getNumber()
    {
        return number;
    }

    public List<?> toRow()
    {
        BlockBuilder labelIds = BIGINT.createBlockBuilder(null, labels.size());
        BlockBuilder labelNames = VARCHAR.createBlockBuilder(null, labels.size());
        for (Label label : labels) {
            BIGINT.writeLong(labelIds, label.getId());
            VARCHAR.writeString(labelNames, label.getName());
        }

        BlockBuilder requestedReviewerIds = BIGINT.createBlockBuilder(null, requestedReviewers.size());
        BlockBuilder requestedReviewerLogins = VARCHAR.createBlockBuilder(null, requestedReviewers.size());
        for (User requestedReviewer : requestedReviewers) {
            BIGINT.writeLong(requestedReviewerIds, requestedReviewer.getId());
            VARCHAR.writeString(requestedReviewerLogins, requestedReviewer.getLogin());
        }

        return ImmutableList.of(
                owner,
                repo,
                id,
                number,
                state,
                locked,
                title != null ? title : "",
                user != null ? user.getId() : 0,
                user != null ? user.getLogin() : "",
                body != null ? body : "",
                labelIds.build(),
                labelNames.build(),
                milestone != null ? milestone.getId() : 0,
                milestone != null ? milestone.getTitle() : "",
                activeLockReason != null ? activeLockReason : "",
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                packTimestamp(closedAt),
                packTimestamp(mergedAt),
                mergedCommitSha != null ? mergedCommitSha : "",
                assignee != null ? assignee.getId() : 0,
                assignee != null ? assignee.getLogin() : "",
                requestedReviewerIds.build(),
                requestedReviewerLogins.build(),
                headRef,
                headSha,
                baseRef,
                baseSha,
                authorAssociation,
                draft);
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
        BOOLEAN.writeBoolean(rowBuilder, locked);
        writeString(rowBuilder, title);
        BIGINT.writeLong(rowBuilder, user.getId());
        writeString(rowBuilder, user.getLogin());
        writeString(rowBuilder, body);

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

        if (milestone == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, milestone.getId());
            writeString(rowBuilder, milestone.getTitle());
        }
        writeString(rowBuilder, activeLockReason);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeTimestamp(rowBuilder, closedAt);
        writeTimestamp(rowBuilder, mergedAt);
        writeString(rowBuilder, mergedCommitSha);
        if (assignee == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, assignee.getId());
            writeString(rowBuilder, assignee.getLogin());
        }

        if (requestedReviewers == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BlockBuilder reviewerIds = BIGINT.createBlockBuilder(null, requestedReviewers.size());
            for (User reviewer : requestedReviewers) {
                BIGINT.writeLong(reviewerIds, reviewer.getId());
            }
            rowBuilder.appendStructure(reviewerIds.build());

            BlockBuilder reviewerLogins = VARCHAR.createBlockBuilder(null, requestedReviewers.size());
            for (User reviewer : requestedReviewers) {
                writeString(reviewerLogins, reviewer.getLogin());
            }
            rowBuilder.appendStructure(reviewerLogins.build());
        }

        writeString(rowBuilder, headRef);
        writeString(rowBuilder, headSha);
        writeString(rowBuilder, baseRef);
        writeString(rowBuilder, baseSha);
        writeString(rowBuilder, authorAssociation);
        BOOLEAN.writeBoolean(rowBuilder, draft);
    }
}

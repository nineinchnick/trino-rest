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
public class PullStatistics
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private long pullNumber;
    private final long comments;
    private final long reviewComments;
    private final long commits;
    private final long additions;
    private final long deletions;
    private final long changedFiles;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime closedAt;
    private final ZonedDateTime mergedAt;

    public PullStatistics(
            @JsonProperty("comments") long comments,
            @JsonProperty("review_comments") long reviewComments,
            @JsonProperty("commits") long commits,
            @JsonProperty("additions") long additions,
            @JsonProperty("deletions") long deletions,
            @JsonProperty("changed_files") long changedFiles,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("closed_at") ZonedDateTime closedAt,
            @JsonProperty("merged_at") ZonedDateTime mergedAt)
    {
        this.comments = comments;
        this.reviewComments = reviewComments;
        this.commits = commits;
        this.additions = additions;
        this.deletions = deletions;
        this.changedFiles = changedFiles;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.mergedAt = mergedAt;
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
                pullNumber,
                comments,
                reviewComments,
                commits,
                additions,
                deletions,
                changedFiles,
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                packTimestamp(closedAt),
                packTimestamp(mergedAt));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, pullNumber);
        BIGINT.writeLong(rowBuilder, comments);
        BIGINT.writeLong(rowBuilder, reviewComments);
        BIGINT.writeLong(rowBuilder, commits);
        BIGINT.writeLong(rowBuilder, additions);
        BIGINT.writeLong(rowBuilder, deletions);
        BIGINT.writeLong(rowBuilder, changedFiles);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeTimestamp(rowBuilder, closedAt);
        writeTimestamp(rowBuilder, mergedAt);
    }
}

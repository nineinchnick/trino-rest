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
import static io.trino.spi.type.VarcharType.VARCHAR;

public class CheckSuite
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private String ref;
    private final long id;
    private final String headBranch;
    private final String headSha;
    private final String status;
    private final String conclusion;
    private final String url;
    private final String before;
    private final String after;
    private final List<Pull> pullRequests;
    private final App app;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final long latestCheckRunsCount;
    private final String checkRunsUrl;

    public CheckSuite(
            @JsonProperty("id") long id,
            @JsonProperty("head_branch") String headBranch,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("url") String url,
            @JsonProperty("before") String before,
            @JsonProperty("after") String after,
            @JsonProperty("pull_requests") List<Pull> pullRequests,
            @JsonProperty("app") App app,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("latest_check_runs_count") long latestCheckRunsCount,
            @JsonProperty("check_runs_url") String checkRunsUrl)
    {
        this.id = id;
        this.headBranch = headBranch;
        this.headSha = headSha;
        this.status = status;
        this.conclusion = conclusion;
        this.url = url;
        this.before = before;
        this.after = after;
        this.pullRequests = pullRequests;
        this.app = app;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.latestCheckRunsCount = latestCheckRunsCount;
        this.checkRunsUrl = checkRunsUrl;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public void setRef(String ref)
    {
        this.ref = ref;
    }

    public long getId()
    {
        return id;
    }

    public List<?> toRow()
    {
        BlockBuilder pullRequests = BIGINT.createBlockBuilder(null, this.pullRequests != null ? this.pullRequests.size() : 0);
        if (this.pullRequests != null) {
            for (Pull pr : this.pullRequests) {
                BIGINT.writeLong(pullRequests, pr.getNumber());
            }
        }
        return ImmutableList.of(
                owner,
                repo,
                ref,
                id,
                headBranch,
                headSha,
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                url,
                before != null ? before : "",
                after != null ? after : "",
                pullRequests.build(),
                app != null ? app.getId() : 0,
                app != null ? app.getSlug() : "",
                app != null ? app.getName() : "",
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                latestCheckRunsCount,
                checkRunsUrl);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        writeString(rowBuilder, ref);
        BIGINT.writeLong(rowBuilder, id);
        VARCHAR.writeString(rowBuilder, headBranch);
        VARCHAR.writeString(rowBuilder, headSha);
        writeString(rowBuilder, status);
        writeString(rowBuilder, conclusion);
        writeString(rowBuilder, url);
        writeString(rowBuilder, before);
        writeString(rowBuilder, after);
        BlockBuilder pullRequests = BIGINT.createBlockBuilder(null, this.pullRequests != null ? this.pullRequests.size() : 0);
        if (this.pullRequests != null) {
            for (Pull pr : this.pullRequests) {
                BIGINT.writeLong(pullRequests, pr.getNumber());
            }
        }
        rowBuilder.appendStructure(pullRequests.build());
        BIGINT.writeLong(rowBuilder, app.getId());
        VARCHAR.writeString(rowBuilder, app.getSlug());
        VARCHAR.writeString(rowBuilder, app.getName());
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        BIGINT.writeLong(rowBuilder, latestCheckRunsCount);
        writeString(rowBuilder, checkRunsUrl);
    }
}

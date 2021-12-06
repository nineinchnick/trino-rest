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
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;

@SuppressWarnings("unused")
public class Run
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String name;
    private final String nodeId;
    private final long checkSuiteId;
    private final String checkSuiteNodeId;
    private final String headBranch;
    private final String headSha;
    private final long runNumber;
    private final int runAttempt;
    private final String event;
    private final String status;
    private final String conclusion;
    private final long workflowId;
    private final String url;
    private final String htmlUrl;
    private final List<Pull> pullRequests;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime runStartedAt;
    private final String jobsUrl;
    private final String logsUrl;
    private final String checkSuiteUrl;
    private final String artifactsUrl;
    private final String cancelUrl;
    private final String rerunUrl;
    private final String workflowUrl;
    private final String previousAttemptUrl;
    private final Commit headCommit;
    private final Repository repository;
    private final Repository headRepository;

    public Run(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("check_suite_id") long checkSuiteId,
            @JsonProperty("check_suite_node_id") String checkSuiteNodeId,
            @JsonProperty("head_branch") String headBranch,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("run_number") long runNumber,
            @JsonProperty("run_attempt") int runAttempt,
            @JsonProperty("event") String event,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("workflow_id") long workflowId,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("pull_requests") List<Pull> pullRequests,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("run_started_at") ZonedDateTime runStartedAt,
            @JsonProperty("jobs_url") String jobsUrl,
            @JsonProperty("logs_url") String logsUrl,
            @JsonProperty("check_suite_url") String checkSuiteUrl,
            @JsonProperty("artifacts_url") String artifactsUrl,
            @JsonProperty("cancel_url") String cancelUrl,
            @JsonProperty("rerun_url") String rerunUrl,
            @JsonProperty("workflow_url") String workflowUrl,
            @JsonProperty("previous_attempt_url") String previousAttemptUrl,
            @JsonProperty("head_commit") Commit headCommit,
            @JsonProperty("repository") Repository repository,
            @JsonProperty("head_repository") Repository headRepository)
    {
        this.id = id;
        this.name = name;
        this.nodeId = nodeId;
        this.checkSuiteId = checkSuiteId;
        this.checkSuiteNodeId = checkSuiteNodeId;
        this.headBranch = headBranch;
        this.headSha = headSha;
        this.runNumber = runNumber;
        this.runAttempt = runAttempt;
        this.event = event;
        this.status = status;
        this.conclusion = conclusion;
        this.workflowId = workflowId;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.pullRequests = pullRequests;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.runStartedAt = runStartedAt;
        this.jobsUrl = jobsUrl;
        this.logsUrl = logsUrl;
        this.checkSuiteUrl = checkSuiteUrl;
        this.artifactsUrl = artifactsUrl;
        this.cancelUrl = cancelUrl;
        this.rerunUrl = rerunUrl;
        this.workflowUrl = workflowUrl;
        this.previousAttemptUrl = previousAttemptUrl;
        this.headCommit = headCommit;
        this.repository = repository;
        this.headRepository = headRepository;
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
                id,
                name,
                nodeId,
                checkSuiteId,
                checkSuiteNodeId,
                headBranch,
                headSha,
                runNumber,
                runAttempt,
                event != null ? event : "",
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                workflowId,
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                packTimestamp(runStartedAt));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        VARCHAR.writeString(rowBuilder, name);
        VARCHAR.writeString(rowBuilder, nodeId);
        BIGINT.writeLong(rowBuilder, checkSuiteId);
        VARCHAR.writeString(rowBuilder, checkSuiteNodeId);
        VARCHAR.writeString(rowBuilder, headBranch);
        VARCHAR.writeString(rowBuilder, headSha);
        BIGINT.writeLong(rowBuilder, runNumber);
        INTEGER.writeLong(rowBuilder, runAttempt);
        VARCHAR.writeString(rowBuilder, event);
        writeString(rowBuilder, status);
        writeString(rowBuilder, conclusion);
        BIGINT.writeLong(rowBuilder, workflowId);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeTimestamp(rowBuilder, runStartedAt);
    }
}

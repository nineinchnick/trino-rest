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
public class Job
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final long runId;
    private final String runUrl;
    private final String nodeId;
    private final String headSha;
    private final String url;
    private final String htmlUrl;
    private final String status;
    private final String conclusion;
    private final ZonedDateTime startedAt;
    private final ZonedDateTime completedAt;
    private final String name;
    private final List<Step> steps;
    private final String checkRunUrl;

    public Job(
            @JsonProperty("id") long id,
            @JsonProperty("run_id") long runId,
            @JsonProperty("run_url") String runUrl,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("started_at") ZonedDateTime startedAt,
            @JsonProperty("completed_at") ZonedDateTime completedAt,
            @JsonProperty("name") String name,
            @JsonProperty("steps") List<Step> steps,
            @JsonProperty("check_run_url") String checkRunUrl)
    {
        this.id = id;
        this.runId = runId;
        this.runUrl = runUrl;
        this.nodeId = nodeId;
        this.headSha = headSha;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.status = status;
        this.conclusion = conclusion;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.name = name;
        steps.forEach(s -> s.setJobId(id));
        steps.forEach(s -> s.setRunId(runId));
        this.steps = steps;
        this.checkRunUrl = checkRunUrl;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
        steps.forEach(s -> s.setOwner(owner));
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
        steps.forEach(s -> s.setRepo(repo));
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                id,
                runId,
                nodeId,
                headSha,
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                packTimestamp(startedAt),
                packTimestamp(completedAt),
                name);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        BIGINT.writeLong(rowBuilder, runId);
        writeString(rowBuilder, nodeId);
        writeString(rowBuilder, headSha);
        writeString(rowBuilder, status);
        writeString(rowBuilder, conclusion);
        writeTimestamp(rowBuilder, startedAt);
        writeTimestamp(rowBuilder, completedAt);
        writeString(rowBuilder, name);
        // not writing steps
    }

    public List<Step> getSteps()
    {
        return steps;
    }
}

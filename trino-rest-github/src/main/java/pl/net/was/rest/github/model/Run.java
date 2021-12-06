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

public class Run
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String name;
    private final String nodeId;
    private final String headBranch;
    private final String headSha;
    private final long runNumber;
    private final String event;
    private final String status;
    private final String conclusion;
    private final long workflowId;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;

    public Run(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("head_branch") String headBranch,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("run_number") long runNumber,
            @JsonProperty("event") String event,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("workflow_id") long workflowId,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt)
    {
        this.id = id;
        this.name = name;
        this.nodeId = nodeId;
        this.headBranch = headBranch;
        this.headSha = headSha;
        this.runNumber = runNumber;
        this.event = event;
        this.status = status;
        this.conclusion = conclusion;
        this.workflowId = workflowId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
                headBranch,
                headSha,
                runNumber,
                event != null ? event : "",
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                workflowId,
                packTimestamp(createdAt),
                packTimestamp(updatedAt));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        VARCHAR.writeString(rowBuilder, name);
        VARCHAR.writeString(rowBuilder, nodeId);
        VARCHAR.writeString(rowBuilder, headBranch);
        VARCHAR.writeString(rowBuilder, headSha);
        BIGINT.writeLong(rowBuilder, runNumber);
        VARCHAR.writeString(rowBuilder, event);
        writeString(rowBuilder, status);
        writeString(rowBuilder, conclusion);
        BIGINT.writeLong(rowBuilder, workflowId);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
    }
}

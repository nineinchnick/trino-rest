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
import static io.trino.spi.type.VarcharType.VARCHAR;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Step
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private long jobId;
    private final String name;
    private final String status;
    private final String conclusion;
    private final long number;
    private final ZonedDateTime startedAt;
    private final ZonedDateTime completedAt;

    public Step(
            @JsonProperty("name") String name,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("number") long number,
            @JsonProperty("started_at") ZonedDateTime startedAt,
            @JsonProperty("completed_at") ZonedDateTime completedAt)
    {
        this.name = name;
        this.status = status;
        this.conclusion = conclusion;
        this.number = number;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public void setJobId(long jobId)
    {
        this.jobId = jobId;
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                jobId,
                name,
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                number,
                packTimestamp(startedAt),
                packTimestamp(completedAt));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, jobId);
        VARCHAR.writeString(rowBuilder, name);
        writeString(rowBuilder, status);
        writeString(rowBuilder, conclusion);
        BIGINT.writeLong(rowBuilder, number);
        writeTimestamp(rowBuilder, startedAt);
        writeTimestamp(rowBuilder, completedAt);
    }
}

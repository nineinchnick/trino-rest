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

@SuppressWarnings("unused")
public class CheckRun
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private String ref;
    private final long id;
    private final String nodeId;
    private final String headSha;
    private final String externalId;
    private final String url;
    private final String htmlUrl;
    private final String detailsUrl;
    private final String status;
    private final String conclusion;
    private final ZonedDateTime startedAt;
    private final ZonedDateTime completedAt;
    private final CheckOutput output;
    private final String name;
    private final Long checkSuiteId;
    private final App app;
    private final List<Pull> pullRequests;

    public CheckRun(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("external_id") String externalId,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("details_url") String detailsUrl,
            @JsonProperty("status") String status,
            @JsonProperty("conclusion") String conclusion,
            @JsonProperty("started_at") ZonedDateTime startedAt,
            @JsonProperty("completed_at") ZonedDateTime completedAt,
            @JsonProperty("output") CheckOutput output,
            @JsonProperty("name") String name,
            @JsonProperty("check_suite") CheckSuite checkSuite,
            @JsonProperty("app") App app,
            @JsonProperty("pull_requests") List<Pull> pullRequests)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.headSha = headSha;
        this.externalId = externalId;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.detailsUrl = detailsUrl;
        this.status = status;
        this.conclusion = conclusion;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.output = output;
        this.name = name;
        this.checkSuiteId = checkSuite != null ? checkSuite.getId() : null;
        this.app = app;
        this.pullRequests = pullRequests;
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

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                ref,
                id,
                headSha,
                externalId,
                url,
                htmlUrl,
                detailsUrl,
                status != null ? status : "",
                conclusion != null ? conclusion : "",
                packTimestamp(startedAt),
                packTimestamp(completedAt),
                output != null && output.getTitle() != null ? output.getTitle() : "",
                output != null && output.getSummary() != null ? output.getSummary() : "",
                output != null && output.getText() != null ? output.getText() : "",
                output != null ? output.getAnnotationsCount() : 0,
                output != null && output.getAnnotationsUrl() != null ? output.getAnnotationsUrl() : "",
                name != null ? name : "",
                checkSuiteId != null ? checkSuiteId : 0,
                app != null ? app.getId() : 0,
                app != null ? app.getSlug() : "",
                app != null ? app.getName() : "");
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        writeString(rowBuilder, ref);
        BIGINT.writeLong(rowBuilder, id);
        VARCHAR.writeString(rowBuilder, headSha);
        VARCHAR.writeString(rowBuilder, externalId);
        VARCHAR.writeString(rowBuilder, url);
        VARCHAR.writeString(rowBuilder, htmlUrl);
        VARCHAR.writeString(rowBuilder, detailsUrl);
        VARCHAR.writeString(rowBuilder, status);
        VARCHAR.writeString(rowBuilder, conclusion);
        writeTimestamp(rowBuilder, startedAt);
        writeTimestamp(rowBuilder, completedAt);
        VARCHAR.writeString(rowBuilder, output.getTitle());
        VARCHAR.writeString(rowBuilder, output.getSummary());
        VARCHAR.writeString(rowBuilder, output.getText());
        BIGINT.writeLong(rowBuilder, output.getAnnotationsCount());
        VARCHAR.writeString(rowBuilder, output.getAnnotationsUrl());
        VARCHAR.writeString(rowBuilder, name);
        BIGINT.writeLong(rowBuilder, checkSuiteId);
        BIGINT.writeLong(rowBuilder, app.getId());
        VARCHAR.writeString(rowBuilder, app.getSlug());
        VARCHAR.writeString(rowBuilder, app.getName());
    }
}

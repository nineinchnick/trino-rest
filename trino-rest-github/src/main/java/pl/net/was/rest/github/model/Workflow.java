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

public class Workflow
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String nodeId;
    private final String name;
    private final String path;
    private final String state;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final String url;
    private final String htmlUrl;
    private final String badgeUrl;

    public Workflow(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("name") String name,
            @JsonProperty("path") String path,
            @JsonProperty("state") String state,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("badge_url") String badgeUrl)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.name = name;
        this.path = path;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.badgeUrl = badgeUrl;
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
                nodeId,
                name,
                path,
                state,
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                url,
                htmlUrl,
                badgeUrl);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        VARCHAR.writeString(rowBuilder, nodeId);
        VARCHAR.writeString(rowBuilder, name);
        VARCHAR.writeString(rowBuilder, path);
        VARCHAR.writeString(rowBuilder, state);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        VARCHAR.writeString(rowBuilder, url);
        VARCHAR.writeString(rowBuilder, htmlUrl);
        VARCHAR.writeString(rowBuilder, badgeUrl);
    }
}

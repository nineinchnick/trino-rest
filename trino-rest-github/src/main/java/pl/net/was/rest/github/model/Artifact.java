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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.trino.spi.block.BlockBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Artifact
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private long runId;
    private final long id;
    private final long sizeInBytes;
    private final String name;
    private final String url;
    private final String archiveDownloadUrl;
    private final boolean expired;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime expiresAt;
    private final ZonedDateTime updatedAt;
    private String contents;

    public Artifact(
            @JsonProperty("id") long id,
            @JsonProperty("size_in_bytes") long sizeInBytes,
            @JsonProperty("name") String name,
            @JsonProperty("url") String url,
            @JsonProperty("archive_download_url") String archiveDownloadUrl,

            @JsonProperty("expired") boolean expired,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("expires_at") ZonedDateTime expiresAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt)
    {
        this.id = id;
        this.sizeInBytes = sizeInBytes;
        this.name = name;
        this.url = url;
        this.archiveDownloadUrl = archiveDownloadUrl;
        this.expired = expired;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public long getId()
    {
        return id;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public void setRunId(long runId)
    {
        this.runId = runId;
    }

    public void setContents(InputStream zipContents)
            throws IOException
    {
        Map<String, String> map = new HashMap<>();

        ZipInputStream zis = new ZipInputStream(zipContents);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            // TODO how to detect binary files?
            String text = new BufferedReader(
                    new InputStreamReader(zis, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            map.put(entry.getName(), text);
        }
        zis.close();

        ObjectMapper mapper = new ObjectMapper();
        contents = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(map);
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                runId,
                id,
                sizeInBytes,
                name,
                url,
                archiveDownloadUrl,
                expired,
                packTimestamp(createdAt),
                packTimestamp(expiresAt),
                packTimestamp(updatedAt),
                contents != null ? contents : "");
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, runId);
        BIGINT.writeLong(rowBuilder, id);
        BIGINT.writeLong(rowBuilder, sizeInBytes);
        writeString(rowBuilder, name);
        writeString(rowBuilder, url);
        writeString(rowBuilder, archiveDownloadUrl);
        BOOLEAN.writeBoolean(rowBuilder, expired);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, expiresAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeString(rowBuilder, contents);
    }
}

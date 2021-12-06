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
import io.airlift.slice.Slices;
import io.trino.spi.block.BlockBuilder;

import java.time.ZonedDateTime;
import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;

@SuppressWarnings("unused")
public class Artifact
        extends BaseBlockWriter
        implements Cloneable
{
    private String owner;
    private String repo;
    private long runId;
    private final long id;
    private final String nodeId;
    private final long sizeInBytes;
    private final String name;
    private final String url;
    private final String archiveDownloadUrl;
    private final boolean expired;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime expiresAt;
    private final ZonedDateTime updatedAt;
    private String filename;
    private String path;
    private String mimetype;
    private long fileSizeInBytes;
    private int partNumber;
    private byte[] contents;

    public Artifact(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
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
        this.nodeId = nodeId;
        this.sizeInBytes = sizeInBytes;
        this.name = name;
        this.url = url;
        this.archiveDownloadUrl = archiveDownloadUrl;
        this.expired = expired;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public String getOwner()
    {
        return owner;
    }

    public String getRepo()
    {
        return repo;
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

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public void setMimetype(String mimetype)
    {
        this.mimetype = mimetype;
    }

    public void setPartNumber(int partNumber)
    {
        this.partNumber = partNumber;
    }

    public void setFileSizeInBytes(long fileSizeInBytes)
    {
        this.fileSizeInBytes = fileSizeInBytes;
    }

    public void setContents(byte[] contents)
    {
        this.contents = contents;
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
                filename,
                path,
                mimetype,
                fileSizeInBytes,
                partNumber,
                Slices.wrappedBuffer(contents != null ? contents : new byte[0]));
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
        writeString(rowBuilder, filename);
        writeString(rowBuilder, path);
        writeString(rowBuilder, mimetype);
        BIGINT.writeLong(rowBuilder, fileSizeInBytes);
        INTEGER.writeLong(rowBuilder, partNumber);
        writeBytes(rowBuilder, contents);
    }

    @Override
    public Artifact clone()
    {
        try {
            return (Artifact) super.clone();
        }
        catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
}

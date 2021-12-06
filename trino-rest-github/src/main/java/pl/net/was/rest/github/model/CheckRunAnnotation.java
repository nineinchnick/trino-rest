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

import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class CheckRunAnnotation
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private long checkRunId;
    private final String path;
    private final int startLine;
    private final int endLine;
    private final int startColumn;
    private final int endColumn;
    private final String annotationLevel;
    private final String title;
    private final String message;
    private final String rawDetails;
    private final String blobHref;

    public CheckRunAnnotation(
            @JsonProperty("path") String path,
            @JsonProperty("start_line") int startLine,
            @JsonProperty("end_line") int endLine,
            @JsonProperty("start_column") int startColumn,
            @JsonProperty("end_column") int endColumn,
            @JsonProperty("annotation_level") String annotationLevel,
            @JsonProperty("title") String title,
            @JsonProperty("message") String message,
            @JsonProperty("raw_details") String rawDetails,
            @JsonProperty("blob_href") String blobHref)
    {
        this.path = path;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.annotationLevel = annotationLevel;
        this.title = title;
        this.message = message;
        this.rawDetails = rawDetails;
        this.blobHref = blobHref;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public void setCheckRunId(long checkRunId)
    {
        this.checkRunId = checkRunId;
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                owner,
                repo,
                checkRunId,
                path,
                startLine,
                endLine,
                startColumn,
                endColumn,
                annotationLevel != null ? annotationLevel : "",
                title != null ? title : "",
                message != null ? message : "",
                rawDetails != null ? rawDetails : "",
                blobHref != null ? blobHref : "");
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, checkRunId);
        writeString(rowBuilder, path);
        INTEGER.writeLong(rowBuilder, startLine);
        INTEGER.writeLong(rowBuilder, endLine);
        INTEGER.writeLong(rowBuilder, startColumn);
        INTEGER.writeLong(rowBuilder, endColumn);
        VARCHAR.writeString(rowBuilder, annotationLevel);
        VARCHAR.writeString(rowBuilder, title);
        VARCHAR.writeString(rowBuilder, message);
        VARCHAR.writeString(rowBuilder, rawDetails);
        VARCHAR.writeString(rowBuilder, blobHref);
    }
}

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

import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Repository
        extends BaseBlockWriter
{
    private final long id;
    private final String name;
    private final String fullName;
    private final User owner;
    private final boolean isPrivate;
    private final String description;
    private final boolean fork;
    private final String url;

    public Repository(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("owner") User owner,
            @JsonProperty("private") boolean isPrivate,
            @JsonProperty("description") String description,
            @JsonProperty("fork") boolean fork,
            @JsonProperty("url") String url)
    {
        this.id = id;
        this.name = name;
        this.fullName = fullName;
        this.owner = owner;
        this.isPrivate = isPrivate;
        this.description = description;
        this.fork = fork;
        this.url = url;
    }


    public List<?> toRow()
    {
        // TODO allow nulls
        return ImmutableList.of(
                id,
                name != null ? name : "",
                fullName != null ? fullName : "",
                owner.getId(),
                owner.getLogin(),
                isPrivate,
                description != null ? description : "",
                fork,
                url);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, name);
        writeString(rowBuilder, fullName);
        BIGINT.writeLong(rowBuilder, owner.getId());
        writeString(rowBuilder, owner.getLogin());
        BOOLEAN.writeBoolean(rowBuilder, isPrivate);
        writeString(rowBuilder, description);
        BOOLEAN.writeBoolean(rowBuilder, fork);
        writeString(rowBuilder, url);
    }
}

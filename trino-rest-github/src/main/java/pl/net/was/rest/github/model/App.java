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

import java.time.ZonedDateTime;
import java.util.List;

@SuppressWarnings("unused")
public class App
{
    private final long id;
    private final String slug;
    private final String nodeId;
    private final User owner;
    private final String name;
    private final String description;
    private final String externalUrl;
    private final String htmlUrl;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final Permissions permissions;
    private final List<String> events;

    public App(
            @JsonProperty("id") long id,
            @JsonProperty("slug") String slug,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("owner") User owner,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("external_url") String externalUrl,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("permissions") Permissions permissions,
            @JsonProperty("events") List<String> events)
    {
        this.id = id;
        this.slug = slug;
        this.nodeId = nodeId;
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.externalUrl = externalUrl;
        this.htmlUrl = htmlUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.permissions = permissions;
        this.events = events;
    }

    public long getId()
    {
        return id;
    }

    public String getSlug()
    {
        return slug;
    }

    public String getName()
    {
        return name;
    }
}

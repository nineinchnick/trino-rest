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

@SuppressWarnings("unused")
public class Reaction
{
    private final long id;
    private final String nodeId;
    private final User user;
    private final String content;
    private final ZonedDateTime createdAt;

    public Reaction(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("user") User user,
            @JsonProperty("content") String content,
            @JsonProperty("created_at") ZonedDateTime createdAt)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.user = user;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId()
    {
        return id;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public User getUser()
    {
        return user;
    }

    public String getContent()
    {
        return content;
    }

    public ZonedDateTime getCreatedAt()
    {
        return createdAt;
    }
}

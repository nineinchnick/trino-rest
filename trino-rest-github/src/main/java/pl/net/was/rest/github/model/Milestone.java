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
public class Milestone
{
    private final String url;
    private final String htmlUrl;
    private final String labelsUrl;
    private final long id;
    private final String nodeId;
    private final long number;
    private final String state;
    private final String title;
    private final String description;
    private final User creator;
    private final int openIssues;
    private final int closedIssues;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime closedAt;
    private final ZonedDateTime dueOn;

    public Milestone(

            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("labels_url") String labelsUrl,
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("number") long number,
            @JsonProperty("state") String state,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("creator") User creator,
            @JsonProperty("open_issues") int openIssues,
            @JsonProperty("closed_issues") int closedIssues,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("closed_at") ZonedDateTime closedAt,
            @JsonProperty("due_on") ZonedDateTime dueOn)
    {
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.labelsUrl = labelsUrl;
        this.id = id;
        this.nodeId = nodeId;
        this.number = number;
        this.state = state;
        this.title = title;
        this.description = description;
        this.creator = creator;
        this.openIssues = openIssues;
        this.closedIssues = closedIssues;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.dueOn = dueOn;
    }

    public String getUrl()
    {
        return url;
    }

    public String getHtmlUrl()
    {
        return htmlUrl;
    }

    public String getLabelsUrl()
    {
        return labelsUrl;
    }

    public long getId()
    {
        return id;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public long getNumber()
    {
        return number;
    }

    public String getState()
    {
        return state;
    }

    public String getTitle()
    {
        return title;
    }

    public String getDescription()
    {
        return description;
    }

    public User getCreator()
    {
        return creator;
    }

    public int getOpenIssues()
    {
        return openIssues;
    }

    public int getClosedIssues()
    {
        return closedIssues;
    }

    public ZonedDateTime getCreatedAt()
    {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt()
    {
        return updatedAt;
    }

    public ZonedDateTime getClosedAt()
    {
        return closedAt;
    }

    public ZonedDateTime getDueOn()
    {
        return dueOn;
    }
}

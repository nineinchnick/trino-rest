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

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RunsList
    implements Envelope<Run>
{
    private final long totalCount;
    private final List<Run> workflowRuns;

    public RunsList(
            @JsonProperty("total_count") long totalCount,
            @JsonProperty("workflow_runs") List<Run> workflowRuns)
    {
        requireNonNull(workflowRuns, "workflowRuns are null");
        this.totalCount = totalCount;
        this.workflowRuns = workflowRuns;
    }

    public long getTotalCount()
    {
        return totalCount;
    }

    public List<Run> getItems()
    {
        return workflowRuns;
    }
}

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

public class Milestone
{
    private final long id;
    private final long number;
    private final String title;

    public Milestone(
            @JsonProperty("id") long id,
            @JsonProperty("number") long number,
            @JsonProperty("title") String title)
    {
        this.id = id;
        this.number = number;
        this.title = title;
    }

    public long getId()
    {
        return id;
    }

    public long getNumber()
    {
        return number;
    }

    public String getTitle()
    {
        return title;
    }
}

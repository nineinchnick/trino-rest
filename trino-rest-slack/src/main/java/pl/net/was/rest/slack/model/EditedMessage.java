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

package pl.net.was.rest.slack.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EditedMessage
{
    private final String user;
    private final String ts;

    @JsonCreator
    public EditedMessage(
            @JsonProperty("user") String user,
            @JsonProperty("ts") String ts)
    {
        this.user = user;
        this.ts = ts;
    }

    public String getUser()
    {
        return user;
    }

    public String getTs()
    {
        return ts;
    }
}

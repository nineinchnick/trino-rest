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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class Users
        extends SlackResponse
        implements Envelope<User>
{
    private final List<User> items;

    public Users(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("error") String error,
            @JsonProperty("response_metadata") ResponseMetadata responseMetadata,
            @JsonProperty("members") List<User> users)
    {
        super(ok, error, responseMetadata);
        requireNonNull(users, "users are null");
        this.items = users;
    }

    @Override
    public List<User> getItems()
    {
        return items;
    }
}

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

public class SlackResponse
{
    private final boolean ok;
    private final String error;
    private final ResponseMetadata responseMetadata;

    public SlackResponse(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("error") String error,
            @JsonProperty("response_metadata") ResponseMetadata responseMetadata)
    {
        this.ok = ok;
        this.error = error;
        this.responseMetadata = responseMetadata;
    }

    public boolean isOk()
    {
        return ok;
    }

    public String getError()
    {
        return error;
    }

    public String getNextCursor()
    {
        return responseMetadata != null ? responseMetadata.getNextCursor() : "";
    }
}

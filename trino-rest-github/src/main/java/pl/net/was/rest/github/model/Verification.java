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

public class Verification
{
    private final Boolean verified;
    private final String reason;
    private final String signature;
    private final String payload;

    public Verification(
            @JsonProperty("verified") Boolean verified,
            @JsonProperty("reason") String reason,
            @JsonProperty("signature") String signature,
            @JsonProperty("payload") String payload)
    {
        this.verified = verified;
        this.reason = reason;
        this.signature = signature;
        this.payload = payload;
    }

    public Boolean getVerified()
    {
        return verified;
    }

    public String getReason()
    {
        return reason;
    }

    public String getSignature()
    {
        return signature;
    }

    public String getPayload()
    {
        return payload;
    }
}

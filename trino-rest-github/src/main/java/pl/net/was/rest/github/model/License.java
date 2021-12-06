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

@SuppressWarnings("unused")
public class License
{
    private final String key;
    private final String name;
    private final String spdxId;
    private final String url;
    private final String nodeId;

    public License(
            @JsonProperty("key") String key,
            @JsonProperty("name") String name,
            @JsonProperty("spdx_id") String spdxId,
            @JsonProperty("url") String url,
            @JsonProperty("node_id") String nodeId)
    {
        this.key = key;
        this.name = name;
        this.spdxId = spdxId;
        this.url = url;
        this.nodeId = nodeId;
    }

    public String getKey()
    {
        return key;
    }

    public String getName()
    {
        return name;
    }

    public String getSpdxId()
    {
        return spdxId;
    }

    public String getUrl()
    {
        return url;
    }

    public String getNodeId()
    {
        return nodeId;
    }
}

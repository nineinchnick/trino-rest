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

@JsonIgnoreProperties(ignoreUnknown = true)
public class Ref
{
    private final String label;
    private final String url;
    private final String ref;
    private final String sha;

    public Ref(
            @JsonProperty("label") String label,
            @JsonProperty("url") String url,
            @JsonProperty("ref") String ref,
            @JsonProperty("sha") String sha)
    {
        this.label = label;
        this.url = url;
        this.ref = ref;
        this.sha = sha;
    }

    public String getLabel()
    {
        return label;
    }

    public String getUrl()
    {
        return url;
    }

    public String getRef()
    {
        return ref;
    }

    public String getSha()
    {
        return sha;
    }
}

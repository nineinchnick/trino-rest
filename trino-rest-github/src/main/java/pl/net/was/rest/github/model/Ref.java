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
public class Ref
{
    private final String label;
    private final String url;
    private final String htmlUrl;
    private final String ref;
    private final String sha;
    private final Repository repo;
    private final User user;

    public Ref(
            @JsonProperty("label") String label,
            @JsonProperty("url") String url,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("ref") String ref,
            @JsonProperty("sha") String sha,
            @JsonProperty("repo") Repository repo,
            @JsonProperty("user") User user)
    {
        this.label = label;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.ref = ref;
        this.sha = sha;
        this.repo = repo;
        this.user = user;
    }

    public String getLabel()
    {
        return label;
    }

    public String getUrl()
    {
        return url;
    }

    public String getHtmlUrl()
    {
        return htmlUrl;
    }

    public String getRef()
    {
        return ref;
    }

    public String getSha()
    {
        return sha;
    }

    public Repository getRepo()
    {
        return repo;
    }

    public User getUser()
    {
        return user;
    }
}

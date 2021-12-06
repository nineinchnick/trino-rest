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

public class CheckOutput
{
    private final String title;
    private final String summary;
    private final String text;
    private final long annotationsCount;
    private final String annotationsUrl;

    public CheckOutput(
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("text") String text,
            @JsonProperty("annotations_count") long annotationsCount,
            @JsonProperty("annotations_url") String annotationsUrl)
    {
        this.title = title;
        this.summary = summary;
        this.text = text;
        this.annotationsCount = annotationsCount;
        this.annotationsUrl = annotationsUrl;
    }

    public String getTitle()
    {
        return title;
    }

    public String getSummary()
    {
        return summary;
    }

    public String getText()
    {
        return text;
    }

    public long getAnnotationsCount()
    {
        return annotationsCount;
    }

    public String getAnnotationsUrl()
    {
        return annotationsUrl;
    }
}

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
public class Reactions
{
    private final String url;
    private final int totalCount;
    private final int plusOne;
    private final int minusOne;
    private final int laugh;
    private final int hooray;
    private final int confused;
    private final int heart;
    private final int rocket;
    private final int eyes;

    public Reactions(
            @JsonProperty("url") String url,
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("+1") int plusOne,
            @JsonProperty("-1") int minusOne,
            @JsonProperty("laugh") int laugh,
            @JsonProperty("hooray") int hooray,
            @JsonProperty("confused") int confused,
            @JsonProperty("heart") int heart,
            @JsonProperty("rocket") int rocket,
            @JsonProperty("eyes") int eyes)
    {
        this.url = url;
        this.totalCount = totalCount;
        this.plusOne = plusOne;
        this.minusOne = minusOne;
        this.laugh = laugh;
        this.hooray = hooray;
        this.confused = confused;
        this.heart = heart;
        this.rocket = rocket;
        this.eyes = eyes;
    }

    public String getUrl()
    {
        return url;
    }

    public int getTotalCount()
    {
        return totalCount;
    }

    public int getPlusOne()
    {
        return plusOne;
    }

    public int getMinusOne()
    {
        return minusOne;
    }

    public int getLaugh()
    {
        return laugh;
    }

    public int getHooray()
    {
        return hooray;
    }

    public int getConfused()
    {
        return confused;
    }

    public int getHeart()
    {
        return heart;
    }

    public int getRocket()
    {
        return rocket;
    }

    public int getEyes()
    {
        return eyes;
    }
}

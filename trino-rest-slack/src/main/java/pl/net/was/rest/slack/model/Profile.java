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
import com.google.common.collect.ImmutableList;

import java.util.List;

public class Profile
{
    private final String avatarHash;
    private final String statusText;
    private final String statusEmoji;
    private final String realName;
    private final String displayName;
    private final String realNameNormalized;
    private final String displayNameNormalized;
    private final String email;
    private final String image24;
    private final String image32;
    private final String image48;
    private final String image72;
    private final String image192;
    private final String image512;
    private final String team;

    @JsonCreator
    public Profile(
            @JsonProperty("avatar_hash") String avatarHash,
            @JsonProperty("status_text") String statusText,
            @JsonProperty("status_emoji") String statusEmoji,
            @JsonProperty("real_name") String realName,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("real_name_normalized") String realNameNormalized,
            @JsonProperty("display_name_normalized") String displayNameNormalized,
            @JsonProperty("email") String email,
            @JsonProperty("image_24") String image24,
            @JsonProperty("image_32") String image32,
            @JsonProperty("image_48") String image48,
            @JsonProperty("image_72") String image72,
            @JsonProperty("image_192") String image192,
            @JsonProperty("image_512") String image512,
            @JsonProperty("team") String team)
    {
        this.avatarHash = avatarHash;
        this.statusText = statusText;
        this.statusEmoji = statusEmoji;
        this.realName = realName;
        this.displayName = displayName;
        this.realNameNormalized = realNameNormalized;
        this.displayNameNormalized = displayNameNormalized;
        this.email = email;
        this.image24 = image24;
        this.image32 = image32;
        this.image48 = image48;
        this.image72 = image72;
        this.image192 = image192;
        this.image512 = image512;
        this.team = team;
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                avatarHash != null ? avatarHash : "",
                statusText != null ? statusText : "",
                statusEmoji != null ? statusEmoji : "",
                realName != null ? realName : "",
                displayName != null ? displayName : "",
                realNameNormalized != null ? realNameNormalized : "",
                displayNameNormalized != null ? displayNameNormalized : "",
                email != null ? email : "",
                image24 != null ? image24 : "",
                image32 != null ? image32 : "",
                image48 != null ? image48 : "",
                image72 != null ? image72 : "",
                image192 != null ? image192 : "",
                image512 != null ? image512 : "",
                team != null ? team : "");
    }
}

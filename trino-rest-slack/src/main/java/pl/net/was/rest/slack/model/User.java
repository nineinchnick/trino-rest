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
import io.trino.spi.type.DateTimeEncoding;

import java.util.Date;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class User
{
    private final String id;
    private final String teamId;
    private final String name;
    private final boolean deleted;
    private final String color;
    private final String realName;
    private final String timeZone;
    private final String timeZoneLabel;
    private final int timeZoneOffset;
    private final Profile profile;
    private final boolean isAdmin;
    private final boolean isOwner;
    private final boolean isPrimaryOwner;
    private final boolean isRestricted;
    private final boolean isUltraRestricted;
    private final boolean isBot;
    private final Date updated;
    private final boolean isAppUser;
    private final boolean has2FactorAuth;

    @JsonCreator
    public User(
            @JsonProperty("id") String id,
            @JsonProperty("team_id") String teamId,
            @JsonProperty("name") String name,
            @JsonProperty("deleted") boolean deleted,
            @JsonProperty("color") String color,
            @JsonProperty("real_name") String realName,
            @JsonProperty("tz") String timeZone,
            @JsonProperty("tz_label") String timeZoneLabel,
            @JsonProperty("tz_offset") int timeZoneOffset,
            @JsonProperty("profile") Profile profile,
            @JsonProperty("is_admin") boolean isAdmin,
            @JsonProperty("is_owner") boolean isOwner,
            @JsonProperty("is_primary_owner") boolean isPrimaryOwner,
            @JsonProperty("is_restricted") boolean isRestricted,
            @JsonProperty("is_ultra_restricted") boolean isUltraRestricted,
            @JsonProperty("is_bot") boolean isBot,
            @JsonProperty("updated") Date updated,
            @JsonProperty("is_app_user") boolean isAppUser,
            @JsonProperty("has_2fa") boolean has2FactorAuth)
    {
        requireNonNull(profile, "profile is null");
        this.id = id;
        this.teamId = teamId;
        this.name = name;
        this.deleted = deleted;
        this.color = color;
        this.realName = realName;
        this.timeZone = timeZone;
        this.timeZoneLabel = timeZoneLabel;
        this.timeZoneOffset = timeZoneOffset;
        this.profile = profile;
        this.isAdmin = isAdmin;
        this.isOwner = isOwner;
        this.isPrimaryOwner = isPrimaryOwner;
        this.isRestricted = isRestricted;
        this.isUltraRestricted = isUltraRestricted;
        this.isBot = isBot;
        this.updated = updated;
        this.isAppUser = isAppUser;
        this.has2FactorAuth = has2FactorAuth;
    }

    public String getName()
    {
        return name;
    }

    public String getId()
    {
        return id;
    }

    public List<?> toRow()
    {
        return ImmutableList.builder()
                .add(
                        id,
                        teamId != null ? teamId : "",
                        name,
                        deleted,
                        color,
                        realName,
                        timeZone,
                        timeZoneLabel,
                        timeZoneOffset)
                .addAll(profile.toRow())
                .add(
                        isAdmin,
                        isOwner,
                        isPrimaryOwner,
                        isRestricted,
                        isUltraRestricted,
                        isBot,
                        updated != null ? DateTimeEncoding.packDateTimeWithZone(updated.getTime(), 0) : 0,
                        isAppUser,
                        has2FactorAuth)
                .build();
    }
}

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
import com.google.common.collect.ImmutableList;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.MapBlockBuilder;
import io.trino.spi.type.LongTimestampWithTimeZone;
import io.trino.spi.type.MapType;
import io.trino.spi.type.TypeOperators;

import java.time.Instant;
import java.util.List;

import static io.trino.spi.type.DateTimeEncoding.packDateTimeWithZone;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.LongTimestampWithTimeZone.fromEpochMillisAndFraction;
import static io.trino.spi.type.Timestamps.NANOSECONDS_PER_MILLISECOND;
import static io.trino.spi.type.Timestamps.PICOSECONDS_PER_NANOSECOND;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class Message
{
    private final String type;
    private final String subtype;
    private final boolean hidden;
    private String channel;
    private final String user;
    private final String text;
    private final Instant threadTs;
    private final int replyCount;
    private final boolean subscribed;
    private final Instant lastRead;
    private final int unreadCount;
    private final String parentUserId;
    private final Instant ts;
    private final EditedMessage edited;
    private final Instant deletedTs;
    private final Instant eventTs;
    private final boolean isStarred;
    private final List<String> pinnedTo;
    private final List<Reaction> reactions;

    public Message(
            @JsonProperty("type") String type,
            @JsonProperty("subtype") String subtype,
            @JsonProperty("hidden") boolean hidden,
            @JsonProperty("channel") String channel,
            @JsonProperty("user") String user,
            @JsonProperty("text") String text,
            @JsonProperty("thread_ts") Instant threadTs,
            @JsonProperty("reply_count") int replyCount,
            @JsonProperty("subscribed") boolean subscribed,
            @JsonProperty("last_read") Instant lastRead,
            @JsonProperty("unread_count") int unreadCount,
            @JsonProperty("parent_user_id") String parentUserId,
            @JsonProperty("ts") Instant ts,
            @JsonProperty("edited") EditedMessage edited,
            @JsonProperty("deleted_ts") Instant deletedTs,
            @JsonProperty("event_ts") Instant eventTs,
            @JsonProperty("is_starred") boolean isStarred,
            @JsonProperty("pinned_to") List<String> pinnedTo,
            @JsonProperty("reactions") List<Reaction> reactions)
    {
        this.type = type;
        this.subtype = subtype;
        this.hidden = hidden;
        this.channel = channel;
        this.user = user;
        this.text = text;
        this.threadTs = threadTs;
        this.replyCount = replyCount;
        this.subscribed = subscribed;
        this.lastRead = lastRead;
        this.unreadCount = unreadCount;
        this.parentUserId = parentUserId;
        this.ts = ts;
        this.edited = edited;
        this.deletedTs = deletedTs;
        this.eventTs = eventTs;
        this.isStarred = isStarred;
        this.pinnedTo = pinnedTo;
        this.reactions = reactions;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public List<?> toRow()
    {
        BlockBuilder pinnedToList = VARCHAR.createBlockBuilder(null, pinnedTo != null ? pinnedTo.size() : 0);
        if (pinnedTo != null) {
            for (String name : pinnedTo) {
                VARCHAR.writeString(pinnedToList, name);
            }
        }
        MapType mapType = new MapType(VARCHAR, INTEGER, new TypeOperators());
        MapBlockBuilder reactions = mapType.createBlockBuilder(null, this.reactions != null ? this.reactions.size() : 0);
        if (this.reactions != null) {
            reactions.buildEntry((keyBuilder, valueBuilder) -> {
                for (Reaction reaction : this.reactions) {
                    VARCHAR.writeString(keyBuilder, reaction.getName());
                    INTEGER.writeLong(valueBuilder, reaction.getCount());
                }
            });
        }
        else {
            reactions.appendNull();
        }
        return ImmutableList.of(
                type != null ? type : "",
                subtype != null ? subtype : "",
                hidden,
                channel != null ? channel : "",
                user != null ? user : "",
                text != null ? text : "",
                ofInstant(threadTs),
                replyCount,
                subscribed,
                lastRead != null ? packDateTimeWithZone(lastRead.toEpochMilli(), 0) : 0,
                unreadCount,
                parentUserId != null ? parentUserId : "",
                ofInstant(ts),
                edited != null ? edited.getUser() : "",
                ofInstant(edited != null ? edited.getTs() : null),
                ofInstant(deletedTs),
                ofInstant(eventTs),
                isStarred,
                pinnedToList.build(),
                mapType.getObject(reactions.build(), 0));
    }

    private static LongTimestampWithTimeZone ofInstant(Instant instant)
    {
        if (instant == null) {
            return fromEpochMillisAndFraction(0, 0, (short) 0);
        }

        return fromEpochMillisAndFraction(instant.toEpochMilli(), (instant.getNano() % NANOSECONDS_PER_MILLISECOND) * PICOSECONDS_PER_NANOSECOND, (short) 0);
    }
}

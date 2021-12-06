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
import io.trino.spi.type.MapType;
import io.trino.spi.type.TypeOperators;

import java.util.List;

import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class Message
{
    private final String type;
    private final String subtype;
    private final boolean hidden;
    private final String channel;
    private final String user;
    private final String text;
    private final String threadTs;
    private final int replyCount;
    private final boolean subscribed;
    private final String lastRead;
    private final int unreadCount;
    private final String parentUserId;
    private final String ts;
    private final EditedMessage edited;
    private final String deletedTs;
    private final String eventTs;
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
            @JsonProperty("thread_ts") String threadTs,
            @JsonProperty("reply_count") int replyCount,
            @JsonProperty("subscribed") boolean subscribed,
            @JsonProperty("last_read") String lastRead,
            @JsonProperty("unread_count") int unreadCount,
            @JsonProperty("parent_user_id") String parentUserId,
            @JsonProperty("ts") String ts,
            @JsonProperty("edited") EditedMessage edited,
            @JsonProperty("deleted_ts") String deletedTs,
            @JsonProperty("event_ts") String eventTs,
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

    public List<?> toRow()
    {
        BlockBuilder pinnedToList = VARCHAR.createBlockBuilder(null, pinnedTo != null ? pinnedTo.size() : 0);
        if (pinnedTo != null) {
            for (String name : pinnedTo) {
                VARCHAR.writeString(pinnedToList, name);
            }
        }
        MapType mapType = new MapType(VARCHAR, INTEGER, new TypeOperators());
        BlockBuilder reactions = mapType.createBlockBuilder(null, this.reactions != null ? this.reactions.size() : 0);
        BlockBuilder builder = reactions.beginBlockEntry();
        if (this.reactions != null) {
            for (Reaction reaction : this.reactions) {
                VARCHAR.writeString(builder, reaction.getName());
                INTEGER.writeLong(builder, reaction.getCount());
            }
        }
        reactions.closeEntry();
        return ImmutableList.of(
                type != null ? type : "",
                subtype != null ? subtype : "",
                hidden,
                channel != null ? channel : "",
                user != null ? user : "",
                text != null ? text : "",
                threadTs != null ? threadTs : "",
                replyCount,
                subscribed,
                lastRead != null ? lastRead : "",
                unreadCount,
                parentUserId != null ? parentUserId : "",
                ts,
                edited != null ? edited.getUser() : "",
                edited != null ? edited.getTs() : "",
                deletedTs != null ? deletedTs : "",
                eventTs != null ? eventTs : "",
                isStarred,
                pinnedToList.build(),
                mapType.getObject(reactions, 0));
    }
}

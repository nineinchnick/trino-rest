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
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.type.DateTimeEncoding;

import java.util.Date;
import java.util.List;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class Channel
{
    private String type;
    private final String id;
    private final String name;
    private final boolean isChannel;
    private final boolean isGroup;
    private final boolean isIm;
    private final Date created;
    private final String creator;
    private final boolean isArchived;
    private final boolean isGeneral;
    private final int unlinked;
    private final String nameNormalized;
    private final boolean isShared;
    private final boolean isExtShared;
    private final boolean isOrgShared;
    private final List<String> pendingShared;
    private final boolean isPendingExtShared;
    private final boolean isMember;
    private final boolean isPrivate;
    private final boolean isMpim;
    private final Tag topic;
    private final Tag purpose;
    private final List<String> previousNames;
    private final int numMembers;

    @JsonCreator
    public Channel(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("is_channel") boolean isChannel,
            @JsonProperty("is_group") boolean isGroup,
            @JsonProperty("is_im") boolean isIm,
            @JsonProperty("created") Date created,
            @JsonProperty("creator") String creator,
            @JsonProperty("is_archived") boolean isArchived,
            @JsonProperty("is_general") boolean isGeneral,
            @JsonProperty("unlinked") int unlinked,
            @JsonProperty("name_normalized") String nameNormalized,
            @JsonProperty("is_shared") boolean isShared,
            @JsonProperty("is_ext_shared") boolean isExtShared,
            @JsonProperty("is_org_shared") boolean isOrgShared,
            @JsonProperty("pending_shared") List<String> pendingShared,
            @JsonProperty("is_pending_ext_shared") boolean isPendingExtShared,
            @JsonProperty("is_member") boolean isMember,
            @JsonProperty("is_private") boolean isPrivate,
            @JsonProperty("is_mpim") boolean isMpim,
            @JsonProperty("topic") Tag topic,
            @JsonProperty("purpose") Tag purpose,
            @JsonProperty("previous_names") List<String> previousNames,
            @JsonProperty("num_members") int numMembers)
    {
        this.id = id;
        this.name = name;
        this.isChannel = isChannel;
        this.isGroup = isGroup;
        this.isIm = isIm;
        this.created = created;
        this.creator = creator;
        this.isArchived = isArchived;
        this.isGeneral = isGeneral;
        this.unlinked = unlinked;
        this.nameNormalized = nameNormalized;
        this.isShared = isShared;
        this.isExtShared = isExtShared;
        this.isOrgShared = isOrgShared;
        this.pendingShared = pendingShared;
        this.isPendingExtShared = isPendingExtShared;
        this.isMember = isMember;
        this.isPrivate = isPrivate;
        this.isMpim = isMpim;
        this.topic = topic;
        this.purpose = purpose;
        this.previousNames = previousNames;
        this.numMembers = numMembers;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public List<?> toRow()
    {
        BlockBuilder pendingSharedList = VARCHAR.createBlockBuilder(null, pendingShared != null ? pendingShared.size() : 0);
        if (pendingShared != null) {
            for (String name : pendingShared) {
                VARCHAR.writeString(pendingSharedList, name);
            }
        }
        BlockBuilder previousNamesList = VARCHAR.createBlockBuilder(null, previousNames != null ? previousNames.size() : 0);
        if (previousNames != null) {
            for (String name : previousNames) {
                VARCHAR.writeString(previousNamesList, name);
            }
        }
        return ImmutableList.of(
                type,
                id != null ? id : "",
                name != null ? name : "",
                isChannel,
                isGroup,
                isIm,
                created != null ? DateTimeEncoding.packDateTimeWithZone(created.getTime(), 0) : 0,
                creator != null ? creator : "",
                isArchived,
                isGeneral,
                unlinked,
                nameNormalized != null ? nameNormalized : "",
                isShared,
                isExtShared,
                isOrgShared,
                pendingSharedList.build(),
                isPendingExtShared,
                isMember,
                isPrivate,
                isMpim,
                topic != null ? topic.getValue() : "",
                topic != null ? topic.getCreator() : "",
                topic != null && topic.getLastSet() != null ? DateTimeEncoding.packDateTimeWithZone(topic.getLastSet().getTime(), 0) : 0,
                purpose != null ? purpose.getValue() : "",
                purpose != null ? purpose.getCreator() : "",
                purpose != null && purpose.getLastSet() != null ? DateTimeEncoding.packDateTimeWithZone(purpose.getLastSet().getTime(), 0) : 0,
                previousNamesList.build(),
                numMembers);
    }
}

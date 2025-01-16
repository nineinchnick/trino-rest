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

package pl.net.was.rest.slack;

import pl.net.was.rest.slack.model.ChannelMembers;
import pl.net.was.rest.slack.model.Channels;
import pl.net.was.rest.slack.model.Messages;
import pl.net.was.rest.slack.model.Users;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface SlackService
{
    @GET("users.list")
    Call<Users> listUsers(
            @Header("Authorization") String auth,
            @Query("cursor") String cursor,
            @Query("limit") int limit);

    @GET("conversations.list")
    Call<Channels> listChannels(
            @Header("Authorization") String auth,
            @Query("cursor") String cursor,
            @Query("limit") int limit,
            @Query("types") String types);

    @GET("conversations.members")
    Call<ChannelMembers> listChannelMembers(
            @Header("Authorization") String auth,
            @Query("cursor") String cursor,
            @Query("limit") int limit,
            @Query("channel") String channel);

    @GET("conversations.history?include_all_metadata=true")
    Call<Messages> listMessages(
            @Header("Authorization") String auth,
            @Query("cursor") String cursor,
            @Query("limit") int limit,
            @Query("channel") String channel,
            @Query("latest") String latest,
            @Query("oldest") String oldest);

    @GET("conversations.replies?include_all_metadata=true")
    Call<Messages> listReplies(
            @Header("Authorization") String auth,
            @Query("cursor") String cursor,
            @Query("limit") int limit,
            @Query("channel") String channel,
            @Query("ts") String ts);
}

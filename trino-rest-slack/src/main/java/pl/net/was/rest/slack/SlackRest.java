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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.slack.model.Channel;
import pl.net.was.rest.slack.model.Channels;
import pl.net.was.rest.slack.model.History;
import pl.net.was.rest.slack.model.Im;
import pl.net.was.rest.slack.model.Ims;
import pl.net.was.rest.slack.model.SlackResponse;
import pl.net.was.rest.slack.model.User;
import pl.net.was.rest.slack.model.Users;
import pl.net.was.rest.slack.rest.SlackService;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SlackRest
        implements Rest
{
    private static final String CHANNEL_SCHEMA = "channel";
    private static final String IM_SCHEMA = "im";

    private final SlackService service = new Retrofit.Builder()
            .baseUrl("https://slack.com/api/")
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(SlackService.class);

    private final Map<String, Channel> channels;
    private final Map<String, User> users;
    private final Map<String, Im> ims;
    private final String token;

    private final List<ColumnMetadata> columns = ImmutableList.of(
            new ColumnMetadata("type", createUnboundedVarcharType()),
            new ColumnMetadata("user", createUnboundedVarcharType()),
            new ColumnMetadata("text", createUnboundedVarcharType()));

    public SlackRest(String token)
    {
        this.token = token;
        try {
            Channels channels = service.listChannels(token).execute().body();
            if (channels.getError() != null) {
                throw new IllegalStateException("Error during communication with slack: " + channels.getError());
            }
            this.channels = channels.getChannels().stream()
                    .filter(Channel::isMember)
                    .collect(toMap(Channel::getName, identity()));

            Users users = service.listUsers(token).execute().body();
            if (users.getError() != null) {
                throw new IllegalStateException("Error during communication with slack: " + channels.getError());
            }
            this.users = users.getUsers().stream()
                    .collect(toMap(User::getName, identity()));

            Ims ims = service.listIms(token).execute().body();
            if (ims.getError() != null) {
                throw new IllegalStateException("Error during communication with slack: " + channels.getError());
            }
            this.ims = ims.getIms().stream()
                    .collect(toMap(Im::getUser, identity()));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(SchemaTableName schemaTableName)
    {
        return new ConnectorTableMetadata(
                schemaTableName,
                columns);
    }

    @Override
    public List<String> listSchemas()
    {
        return ImmutableList.of(CHANNEL_SCHEMA, IM_SCHEMA);
    }

    @Override
    public List<SchemaTableName> listTables(String schema)
    {
        if (CHANNEL_SCHEMA.equalsIgnoreCase(schema)) {
            return channels.keySet().stream()
                    .map(tableName -> new SchemaTableName(CHANNEL_SCHEMA, tableName))
                    .collect(toList());
        }
        if (IM_SCHEMA.equalsIgnoreCase(schema)) {
            return users.keySet().stream()
                    .map(tableName -> new SchemaTableName(IM_SCHEMA, tableName))
                    .collect(toList());
        }
        return ImmutableList.of();
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(SchemaTablePrefix schemaTablePrefix)
    {
        return null;
    }

    @Override
    public Collection<? extends List<?>> getRows(RestTableHandle table)
    {
        SchemaTableName schemaTableName = table.getSchemaTableName();
        History history = getHistory(schemaTableName);

        if (history.getError() != null) {
            throw new IllegalStateException("Unable to read from '" + schemaTableName + "' dues: " + history.getError());
        }
        return history
                .getMessages().stream()
                .map(message -> asList(message.getType(), message.getUser(), message.getText()))
                .collect(toList());
    }

    private History getHistory(SchemaTableName schemaTableName)
    {
        String schemaName = schemaTableName.getSchemaName();
        try {
            if (CHANNEL_SCHEMA.equalsIgnoreCase(schemaName)) {
                return service.channelHistory(token, getChannelId(schemaTableName))
                        .execute()
                        .body();
            }
            if (IM_SCHEMA.equalsIgnoreCase(schemaName)) {
                return service.imHistory(token, getChannelId(schemaTableName))
                        .execute()
                        .body();
            }
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return new History(true, "no such schema", ImmutableList.of());
    }

    private String getChannelId(SchemaTableName schemaTableName)
    {
        String schemaName = schemaTableName.getSchemaName();
        String tableName = schemaTableName.getTableName();
        if (CHANNEL_SCHEMA.equalsIgnoreCase(schemaName)) {
            return channels.get(tableName).getId();
        }
        if (IM_SCHEMA.equalsIgnoreCase(schemaName)) {
            String userId = users.get(tableName).getId();
            return ims.get(userId).getId();
        }
        throw new IllegalArgumentException("Unknown schema: " + schemaName);
    }

    @Override
    public Consumer<List> createRowSink(SchemaTableName schemaTableName)
    {
        return list -> {
            try {
                SlackResponse body = service.postMessage(token, getChannelId(schemaTableName), (String) list.get(2)).execute().body();
                if (body.getError() != null) {
                    throw new IllegalStateException("Unable to write to '" + schemaTableName + "' dues: " + body.getError());
                }
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
        };
    }
}

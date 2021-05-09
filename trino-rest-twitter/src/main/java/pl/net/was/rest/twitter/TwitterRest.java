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

package pl.net.was.rest.twitter;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.twitter.model.SearchResult;
import pl.net.was.rest.twitter.model.Status;
import pl.net.was.rest.twitter.rest.TwitterService;
import retrofit2.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class TwitterRest
        implements Rest
{
    private static final String SCHEMA = "default";

    private final TwitterService service;

    private final List<ColumnMetadata> columns = ImmutableList.of(
            new ColumnMetadata("id", createUnboundedVarcharType()),
            new ColumnMetadata("text", createUnboundedVarcharType()),
            new ColumnMetadata("retweet_count", BIGINT),
            new ColumnMetadata("user_name", createUnboundedVarcharType()),
            new ColumnMetadata("user_screen_name", createUnboundedVarcharType()));

    public TwitterRest(String consumerKey, String consumerSecret, String token, String secret)
    {
        service = TwitterService.create(consumerKey, consumerSecret, token, secret);
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
        return ImmutableList.of(SCHEMA);
    }

    @Override
    public List<SchemaTableName> listTables(String schema)
    {
        if (schema.equalsIgnoreCase(SCHEMA)) {
            return ImmutableList.of(
                    new SchemaTableName(SCHEMA, "whug"),
                    new SchemaTableName(SCHEMA, "trino"),
                    new SchemaTableName(SCHEMA, "teradata"),
                    new SchemaTableName(SCHEMA, "hive"));
        }
        return ImmutableList.of();
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(SchemaTablePrefix schemaTablePrefix)
    {
        return ImmutableMap.of(
                new SchemaTableName(SCHEMA, "whug"), columns,
                new SchemaTableName(SCHEMA, "trino"), columns,
                new SchemaTableName(SCHEMA, "teradata"), columns,
                new SchemaTableName(SCHEMA, "hive"), columns);
    }

    @Override
    public Collection<? extends List<?>> getRows(RestTableHandle table)
    {
        SchemaTableName schemaTableName = table.getSchemaTableName();
        return searchTweets("#" + schemaTableName.getTableName());
    }

    private Collection<? extends List<?>> searchTweets(String query)
    {
        try {
            Response<SearchResult> response = service.searchTweets(query, 100, "recent").execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Unable to search tweets for '" + query + "' dues: " + response.message());
            }
            List<Status> statuses = response.body().getStatuses();
            return statuses.stream()
                    .map(status -> asList(status.getId(), status.getText(), status.getRetweetCount(), status.getUser().getName(), status.getUser().getScreenName()))
                    .collect(toList());
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Consumer<List> createRowSink(SchemaTableName schemaTableName)
    {
        throw new UnsupportedOperationException("not yet implemented");
    }
}

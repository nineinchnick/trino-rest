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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.LongTimestampWithTimeZone;
import io.trino.spi.type.MapType;
import io.trino.spi.type.TypeOperators;
import jakarta.inject.Inject;
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestConfig;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.filter.FilterApplier;
import pl.net.was.rest.slack.filter.ChannelFilter;
import pl.net.was.rest.slack.filter.ChannelMemberFilter;
import pl.net.was.rest.slack.filter.MessageFilter;
import pl.net.was.rest.slack.filter.ReplyFilter;
import pl.net.was.rest.slack.model.Envelope;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.StandardErrorCode.INVALID_ROW_FILTER;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.LongTimestampWithTimeZone.fromEpochMillisAndFraction;
import static io.trino.spi.type.TimestampWithTimeZoneType.createTimestampWithTimeZoneType;
import static io.trino.spi.type.Timestamps.MICROSECONDS_PER_MILLISECOND;
import static io.trino.spi.type.Timestamps.MILLISECONDS_PER_SECOND;
import static io.trino.spi.type.Timestamps.PICOSECONDS_PER_MICROSECOND;
import static io.trino.spi.type.Timestamps.roundDiv;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.lang.Math.floorDiv;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static pl.net.was.rest.RestModule.getService;

public class SlackRest
        implements Rest
{
    public static final String SCHEMA_NAME = "default";
    private static final int PER_PAGE = 200;
    public static final String THREAD_NOT_FOUND = "thread_not_found";

    private static String token;
    private static SlackService service;

    public static final Map<SlackTable, List<ColumnMetadata>> columns = new ImmutableMap.Builder<SlackTable, List<ColumnMetadata>>()
            .put(SlackTable.USERS, ImmutableList.of(
                    new ColumnMetadata("id", VARCHAR),
                    new ColumnMetadata("team_id", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("deleted", BOOLEAN),
                    new ColumnMetadata("color", VARCHAR),
                    new ColumnMetadata("real_name", VARCHAR),
                    new ColumnMetadata("tz", VARCHAR),
                    new ColumnMetadata("tz_label", VARCHAR),
                    new ColumnMetadata("tz_offset", INTEGER),
                    new ColumnMetadata("avatar_hash", VARCHAR),
                    new ColumnMetadata("status_text", VARCHAR),
                    new ColumnMetadata("status_emoji", VARCHAR),
                    new ColumnMetadata("profile_real_name", VARCHAR),
                    new ColumnMetadata("display_name", VARCHAR),
                    new ColumnMetadata("real_name_normalized", VARCHAR),
                    new ColumnMetadata("display_name_normalized", VARCHAR),
                    new ColumnMetadata("email", VARCHAR),
                    new ColumnMetadata("image_24", VARCHAR),
                    new ColumnMetadata("image_32", VARCHAR),
                    new ColumnMetadata("image_48", VARCHAR),
                    new ColumnMetadata("image_72", VARCHAR),
                    new ColumnMetadata("image_192", VARCHAR),
                    new ColumnMetadata("image_512", VARCHAR),
                    new ColumnMetadata("team", VARCHAR),
                    new ColumnMetadata("is_admin", BOOLEAN),
                    new ColumnMetadata("is_owner", BOOLEAN),
                    new ColumnMetadata("is_primary_owner", BOOLEAN),
                    new ColumnMetadata("is_restricted", BOOLEAN),
                    new ColumnMetadata("is_ultra_restricted", BOOLEAN),
                    new ColumnMetadata("is_bot", BOOLEAN),
                    new ColumnMetadata("updated", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("is_app_user", BOOLEAN),
                    new ColumnMetadata("has_2fa", BOOLEAN)))
            .put(SlackTable.CHANNELS, ImmutableList.of(
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("id", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("is_channel", BOOLEAN),
                    new ColumnMetadata("is_group", BOOLEAN),
                    new ColumnMetadata("is_im", BOOLEAN),
                    new ColumnMetadata("created", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("creator", VARCHAR),
                    new ColumnMetadata("is_archived", BOOLEAN),
                    new ColumnMetadata("is_general", BOOLEAN),
                    new ColumnMetadata("unlinked", INTEGER),
                    new ColumnMetadata("name_normalized", VARCHAR),
                    new ColumnMetadata("is_shared", BOOLEAN),
                    new ColumnMetadata("is_ext_shared", BOOLEAN),
                    new ColumnMetadata("is_org_shared", BOOLEAN),
                    new ColumnMetadata("pending_shared", new ArrayType(VARCHAR)),
                    new ColumnMetadata("is_pending_ext_shared", BOOLEAN),
                    new ColumnMetadata("is_member", BOOLEAN),
                    new ColumnMetadata("is_private", BOOLEAN),
                    new ColumnMetadata("is_mpim", BOOLEAN),
                    new ColumnMetadata("topic_value", VARCHAR),
                    new ColumnMetadata("topic_creatro", VARCHAR),
                    new ColumnMetadata("topic_last_set", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("purpose_value", VARCHAR),
                    new ColumnMetadata("purpose_creatro", VARCHAR),
                    new ColumnMetadata("purpose_last_set", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("previous_names", new ArrayType(VARCHAR)),
                    new ColumnMetadata("num_members", INTEGER)))
            .put(SlackTable.CHANNEL_MEMBERS, ImmutableList.of(
                    new ColumnMetadata("channel", VARCHAR),
                    new ColumnMetadata("member", VARCHAR)))
            .put(SlackTable.MESSAGES, ImmutableList.of(
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("subtype", VARCHAR),
                    new ColumnMetadata("hidden", BOOLEAN),
                    new ColumnMetadata("channel", VARCHAR),
                    new ColumnMetadata("user", VARCHAR),
                    new ColumnMetadata("text", VARCHAR),
                    new ColumnMetadata("thread_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("reply_count", INTEGER),
                    new ColumnMetadata("subscribed", BOOLEAN),
                    new ColumnMetadata("last_read", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("unread_count", INTEGER),
                    new ColumnMetadata("parent_user_id", VARCHAR),
                    new ColumnMetadata("ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("edited_user", VARCHAR),
                    new ColumnMetadata("edited_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("deleted_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("event_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("is_starred", BOOLEAN),
                    new ColumnMetadata("pinned_to", new ArrayType(VARCHAR)),
                    new ColumnMetadata("reactions", new MapType(VARCHAR, INTEGER, new TypeOperators()))))
            .put(SlackTable.REPLIES, ImmutableList.of(
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("subtype", VARCHAR),
                    new ColumnMetadata("hidden", BOOLEAN),
                    new ColumnMetadata("channel", VARCHAR),
                    new ColumnMetadata("user", VARCHAR),
                    new ColumnMetadata("text", VARCHAR),
                    new ColumnMetadata("thread_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("reply_count", INTEGER),
                    new ColumnMetadata("subscribed", BOOLEAN),
                    new ColumnMetadata("last_read", createTimestampWithTimeZoneType(0)),
                    new ColumnMetadata("unread_count", INTEGER),
                    new ColumnMetadata("parent_user_id", VARCHAR),
                    new ColumnMetadata("ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("edited_user", VARCHAR),
                    new ColumnMetadata("edited_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("deleted_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("event_ts", createTimestampWithTimeZoneType(6)),
                    new ColumnMetadata("is_starred", BOOLEAN),
                    new ColumnMetadata("pinned_to", new ArrayType(VARCHAR)),
                    new ColumnMetadata("reactions", new MapType(VARCHAR, INTEGER, new TypeOperators()))))
            .build();

    private final Map<SlackTable, Function<RestTableHandle, Iterable<List<?>>>> rowGetters = new ImmutableMap.Builder<SlackTable, Function<RestTableHandle, Iterable<List<?>>>>()
            .put(SlackTable.USERS, this::getUsers)
            .put(SlackTable.CHANNELS, this::getChannels)
            .put(SlackTable.CHANNEL_MEMBERS, this::getChannelMembers)
            .put(SlackTable.MESSAGES, this::getMessages)
            .put(SlackTable.REPLIES, this::getReplies)
            .build();

    private final Map<SlackTable, Map<String, ColumnHandle>> columnHandles;

    private final Map<SlackTable, ? extends FilterApplier> filterAppliers = new ImmutableMap.Builder<SlackTable, FilterApplier>()
            .put(SlackTable.CHANNELS, new ChannelFilter())
            .put(SlackTable.CHANNEL_MEMBERS, new ChannelMemberFilter())
            .put(SlackTable.MESSAGES, new MessageFilter())
            .put(SlackTable.REPLIES, new ReplyFilter())
            .build();

    @Inject
    public SlackRest(RestConfig config)
    {
        requireNonNull(config, "config is null");
        SlackRest.token = config.getToken();
        SlackRest.service = getService(SlackService.class, "https://slack.com/api/", config.getClientBuilder());

        columnHandles = columns.keySet()
                .stream()
                .collect(Collectors.toMap(
                        tableName -> tableName,
                        tableName -> columns.get(tableName)
                                .stream()
                                .collect(toMap(
                                        ColumnMetadata::getName,
                                        column -> new RestColumnHandle(column.getName(), column.getType())))));
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(SchemaTableName schemaTableName)
    {
        SlackTable tableName = SlackTable.valueOf(schemaTableName);
        return new ConnectorTableMetadata(
                schemaTableName,
                columns.get(tableName));
    }

    @Override
    public List<String> listSchemas()
    {
        return ImmutableList.of(SCHEMA_NAME);
    }

    @Override
    public List<SchemaTableName> listTables(String schema)
    {
        return columns
                .keySet()
                .stream()
                .map(table -> new SchemaTableName(SCHEMA_NAME, table.getName()))
                .collect(toList());
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(SchemaTablePrefix schemaTablePrefix)
    {
        return columns.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> new SchemaTableName(schemaTablePrefix.getSchema().orElse(""), e.getKey().getName()),
                        Map.Entry::getValue));
    }

    @Override
    public Iterable<List<?>> getRows(RestTableHandle table)
    {
        SlackTable tableName = SlackTable.valueOf(table);
        return rowGetters.get(tableName).apply(table);
    }

    private Iterable<List<?>> getUsers(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        return getRowsFromPagesEnvelope(
                cursor -> service.listUsers(
                        "Bearer " + token,
                        cursor,
                        PER_PAGE),
                item -> Stream.of(item.toRow()),
                table.getLimit());
    }

    private Iterable<List<?>> getChannels(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        SlackTable tableName = SlackTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String type = (String) filter.getFilter((RestColumnHandle) columns.get("type"), constraint, "public_channel");
        return getRowsFromPagesEnvelope(
                cursor -> service.listChannels(
                        "Bearer " + token,
                        cursor,
                        PER_PAGE,
                        type),
                item -> {
                    item.setType(type);
                    return Stream.of(item.toRow());
                },
                table.getLimit());
    }

    private Iterable<List<?>> getChannelMembers(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        SlackTable tableName = SlackTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String channel = (String) filter.getFilter((RestColumnHandle) columns.get("channel"), constraint);
        requirePredicate(channel, "channel_members.channel");
        return getRowsFromPagesEnvelope(
                cursor -> service.listChannelMembers(
                        "Bearer " + token,
                        cursor,
                        PER_PAGE,
                        channel),
                item -> Stream.of(ImmutableList.of(channel, item)),
                table.getLimit());
    }

    private Iterable<List<?>> getMessages(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        SlackTable tableName = SlackTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String channel = (String) filter.getFilter((RestColumnHandle) columns.get("channel"), constraint);
        requirePredicate(channel, "messages.channel");
        String latest;
        String oldest;
        if (constraint.getDomains().isPresent() && constraint.getDomains().get().get(columns.get("ts")) != null) {
            Range span = constraint.getDomains().get().get(columns.get("ts")).getValues().getRanges().getSpan();
            if (span.isHighUnbounded()) {
                latest = formatEpochMicros(fromEpochMillisAndFraction(System.currentTimeMillis(), 0, (short) 0));
                oldest = formatEpochMicros((LongTimestampWithTimeZone) span.getLowBoundedValue());
            }
            else if (span.isLowUnbounded()) {
                latest = formatEpochMicros((LongTimestampWithTimeZone) span.getHighBoundedValue());
                oldest = "0";
            }
            else {
                latest = formatEpochMicros((LongTimestampWithTimeZone) span.getHighBoundedValue());
                oldest = formatEpochMicros((LongTimestampWithTimeZone) span.getLowBoundedValue());
            }
        }
        else {
            latest = formatEpochMicros(fromEpochMillisAndFraction(System.currentTimeMillis(), 0, (short) 0));
            oldest = "0";
        }
        return getRowsFromPagesEnvelope(
                cursor -> service.listMessages(
                        "Bearer " + token,
                        cursor,
                        PER_PAGE,
                        channel,
                        latest,
                        oldest),
                item -> {
                    item.setChannel(channel);
                    return Stream.of(item.toRow());
                },
                table.getLimit());
    }

    private Iterable<List<?>> getReplies(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        SlackTable tableName = SlackTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String channel = (String) filter.getFilter((RestColumnHandle) columns.get("channel"), constraint);
        // cannot use getFilter here since we require the value to be encoded as an epoch
        String ts = epochMicros(columns.get("thread_ts"), constraint, null);
        requirePredicate(channel, "replies.channel");
        requirePredicate(ts, "replies.thread_ts");
        return getRowsFromPagesEnvelope(
                cursor -> service.listReplies(
                        "Bearer " + token,
                        cursor,
                        PER_PAGE,
                        channel,
                        ts),
                item -> {
                    item.setChannel(channel);
                    return Stream.of(item.toRow());
                },
                table.getLimit());
    }

    private static String epochMicros(ColumnHandle column, TupleDomain<ColumnHandle> constraint, String defaultValue)
    {
        if (!constraint.getDomains().isPresent() || constraint.getDomains().get().get(column) == null) {
            return defaultValue;
        }
        return formatEpochMicros((LongTimestampWithTimeZone) constraint.getDomains().get().get(column).getValues().getRanges().getSpan().getLowBoundedValue());
    }

    private static String formatEpochMicros(LongTimestampWithTimeZone value)
    {
        return "%d.%06d".formatted(floorDiv(value.getEpochMillis(), MILLISECONDS_PER_SECOND), (value.getEpochMillis() % MILLISECONDS_PER_SECOND) * MICROSECONDS_PER_MILLISECOND + roundDiv(value.getPicosOfMilli(), PICOSECONDS_PER_MICROSECOND));
    }

    private void requirePredicate(Object value, String name)
    {
        if (value == null) {
            throw new TrinoException(INVALID_ROW_FILTER, "Missing required constraint for " + name);
        }
    }

    private <T, E extends Envelope<T>> Iterable<List<?>> getRowsFromPagesEnvelope(
            Function<String, Call<E>> fetcher,
            Function<T, Stream<List<?>>> mapper,
            int limit)
    {
        return () -> new Iterator<>()
        {
            int resultSize;
            String cursor = "";
            Iterator<List<?>> rows;

            @Override
            public boolean hasNext()
            {
                if (rows != null && rows.hasNext()) {
                    return true;
                }
                if (resultSize >= limit) {
                    return false;
                }
                Response<E> response;
                try {
                    response = fetcher.apply(cursor).execute();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (response.code() == HTTP_NOT_FOUND) {
                    return false;
                }
                Rest.checkServiceResponse(response);
                E envelope = requireNonNull(response.body(), "response body is null");
                if (!envelope.isOk()) {
                    if (envelope.getError().equals(THREAD_NOT_FOUND)) {
                        return false;
                    }
                    throw new TrinoException(GENERIC_INTERNAL_ERROR, envelope.getError());
                }
                List<T> items = envelope.getItems();
                if (items.size() == 0) {
                    return false;
                }
                int itemsToUse = Math.min(
                        Math.max(0, limit - resultSize),
                        items.size());
                List<List<?>> rows = items.subList(0, itemsToUse).stream().flatMap(mapper).collect(toList());

                // mapper can produce 1 or more rows per item, so subList them again
                rows = rows.subList(0, itemsToUse);
                this.rows = rows.iterator();
                resultSize += itemsToUse;
                cursor = envelope.getNextCursor();
                if (cursor.isEmpty()) {
                    resultSize = limit;
                }
                return true;
            }

            @Override
            public List<?> next()
            {
                return rows.next();
            }
        };
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(
            ConnectorSession session,
            ConnectorTableHandle table,
            Constraint constraint)
    {
        RestTableHandle restTable = (RestTableHandle) table;
        SlackTable tableName = SlackTable.valueOf(restTable);

        FilterApplier filterApplier = filterAppliers.get(tableName);
        if (filterApplier == null) {
            return Optional.empty();
        }
        return filterApplier.applyFilter(
                restTable,
                columnHandles.get(tableName),
                filterApplier.getSupportedFilters(),
                constraint);
    }

    @Override
    public Consumer<List> createRowSink(SchemaTableName schemaTableName)
    {
        throw new IllegalStateException("This connector does not support write");
    }
}

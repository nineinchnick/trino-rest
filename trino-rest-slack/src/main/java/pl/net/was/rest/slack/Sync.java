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

import java.net.NoRouteToHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Throwables.getRootCause;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class Sync
{
    private static final Logger log;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
        log = Logger.getLogger(Sync.class.getName());
    }

    private Sync() {}

    public static void main(String[] args)
    {
        String url = "jdbc:trino://localhost:8080/slack/default";
        String username = "admin";
        String password = "";

        Map<String, String> env = System.getenv();

        // TODO combine with System.Properties
        List<String> names = Arrays.asList(
                "SYNC_CHANNELS",
                "SYNC_TABLES",
                "LOG_LEVEL",
                "TRINO_DEST_SCHEMA",
                "TRINO_SRC_SCHEMA",
                "TRINO_URL",
                "TRINO_USERNAME",
                "TRINO_PASSWORD",
                "QUERY_DELAY_MS");
        Map<String, String> defaults = Map.of(
                "TRINO_URL", url,
                "TRINO_USERNAME", username,
                "TRINO_PASSWORD", password,
                "SYNC_CHANNELS", "general",
                "SYNC_TABLES", "users,channels,channel_members,messages,replies",
                "LOG_LEVEL", "INFO",
                "QUERY_DELAY_MS", "0");
        for (String name : names) {
            String value = env.getOrDefault(name, defaults.getOrDefault(name, ""));
            if (!value.isEmpty() && name.equals("TRINO_PASSWORD")) {
                value = "***";
            }
            log.info(format("%s=%s", name, value));
        }

        if (env.containsKey("TRINO_URL")) {
            url = env.get("TRINO_URL");
        }
        if (env.containsKey("TRINO_USERNAME")) {
            username = env.get("TRINO_USERNAME");
        }
        if (env.containsKey("TRINO_PASSWORD")) {
            password = env.get("TRINO_PASSWORD");
        }

        String destSchema = System.getenv("TRINO_DEST_SCHEMA");
        String srcSchema = System.getenv("TRINO_SRC_SCHEMA");
        String channels = System.getenv("SYNC_CHANNELS");
        if (channels == null) {
            channels = defaults.get("SYNC_CHANNELS");
        }
        Set<String> joinedChannels = new LinkedHashSet<>(Arrays.asList(channels.split(",")));
        String tables = System.getenv("SYNC_TABLES");
        if (tables == null) {
            tables = defaults.get("SYNC_TABLES");
        }
        Set<String> enabledTables = new LinkedHashSet<>(Arrays.asList(tables.split(",")));

        Level level = Level.parse(Optional.ofNullable(System.getenv("LOG_LEVEL")).orElse("INFO"));
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            h.setLevel(level);
        }
        log.setLevel(level);
        root.setLevel(level);
        Logger.getLogger("jdk.internal").setLevel(Level.INFO);

        requireNonNull(destSchema, "TRINO_DEST_SCHEMA environmental variable must be set");
        requireNonNull(srcSchema, "TRINO_SRC_SCHEMA environmental variable must be set");

        Options options = new Options(null, joinedChannels, destSchema, srcSchema);

        // Note that the order in which these functions are called is determined by enabledTables, not availableTables
        Map<String, Function<Options, Boolean>> availableTables = new LinkedHashMap<>();
        availableTables.put("users", Sync::syncUsers);
        availableTables.put("channels", Sync::syncChannels);
        availableTables.put("channel_members", Sync::syncMembers);
        availableTables.put("messages", Sync::syncMessages);
        availableTables.put("replies", Sync::syncReplies);

        boolean result = true;
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            options.conn = conn;
            for (String table : enabledTables) {
                Function<Options, Boolean> syncFunc = availableTables.get(table);
                if (syncFunc == null) {
                    throw new IllegalArgumentException(format(
                            "Unknown table %s, must be one of: %s",
                            table,
                            String.join(", ", availableTables.keySet())));
                }
                result = syncFunc.apply(options) && result;
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
        exit(!result ? 1 : 0);
    }

    private static class Options
    {
        Connection conn;
        Set<String> channels;
        String destSchema;
        String srcSchema;

        public Options(Connection conn, Set<String> channels, String destSchema, String srcSchema)
        {
            this.conn = conn;
            this.channels = channels;
            this.destSchema = destSchema;
            this.srcSchema = srcSchema;
        }
    }

    private static boolean syncUsers(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".users AS SELECT * FROM " + srcSchema + ".users WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON users(id);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM users a USING users b WHERE a.updated < b.updated AND a.id = b.id;
            // or use the unique_users view (from `trino-rest-slack/sql/views.sql`) that ignores duplicates

            // there's no "oldest" filter, but we can sort by updated, so keep inserting records where this is greater than max
            PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                    "SELECT COALESCE(MAX(updated), TIMESTAMP '0000-01-01') AS latest FROM " + destSchema + ".users");
            ResultSet result = lastUpdatedStatement.executeQuery();
            result.next();
            String lastUpdated = result.getString(1);

            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".users " +
                            "SELECT * FROM " + srcSchema + ".users WHERE updated > CAST(? AS TIMESTAMP)");
            statement.setString(1, lastUpdated);

            log.info("Fetching users");
            long startTime = System.currentTimeMillis();
            int rows = retryExecute(statement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncChannels(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".channels AS SELECT * FROM " + srcSchema + ".channels WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON channels(id);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM channels a USING channels b WHERE a.updated < b.updated AND a.id = b.id;
            // or use the unique_channels view (from `trino-rest-slack/sql/views.sql`) that ignores duplicates

            // there's no "oldest" filter, but we can sort by updated, so keep inserting records where this is greater than max
            PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                    "SELECT COALESCE(MAX(topic_last_set), TIMESTAMP '0000-01-01') AS latest FROM " + destSchema + ".channels");
            ResultSet result = lastUpdatedStatement.executeQuery();
            result.next();
            String lastUpdated = result.getString(1);

            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".channels " +
                            "SELECT * FROM " + srcSchema + ".channels WHERE topic_last_set > CAST(? AS TIMESTAMP)");
            statement.setString(1, lastUpdated);

            log.info("Fetching channels");
            long startTime = System.currentTimeMillis();
            int rows = retryExecute(statement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncMembers(Options options)
    {
        boolean result = true;
        for (String channel : options.channels) {
            if (!syncChannelMembers(options, channel)) {
                result = false;
            }
        }
        return result;
    }

    private static boolean syncChannelMembers(Options options, String channel)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".channel_members AS SELECT *, cast(now() as timestamp(3)) AS joined_at, cast(now() as timestamp(3)) AS removed_at, cast('' AS VARCHAR) AS source FROM " + srcSchema + ".channel_members WITH NO DATA");
            String id = channelId(conn, destSchema, channel);
            String query = "INSERT INTO " + destSchema + ".channel_members " +
                    "WITH members AS (" +
                    "  SELECT *, row_number() OVER (PARTITION BY channel, member ORDER BY joined_at DESC, removed_at) AS rownum" +
                    "  FROM " + destSchema + ".channel_members" +
                    "  WHERE channel = ? " +
                    "), dst AS (" +
                    "  SELECT channel, member, joined_at, coalesce(removed_at, now()) AS removed_at" +
                    "  FROM members" +
                    "  WHERE rownum = 1" +
                    "), src AS (SELECT * FROM " + srcSchema + " .channel_members WHERE channel = ?) " +
                    "SELECT" +
                    "  src.*," +
                    "  cast(now() as timestamp(3)) AS joined_at," +
                    "  NULL AS removed_at," +
                    "  'sync' AS source " +
                    "FROM src " +
                    "LEFT JOIN dst ON (dst.channel, dst.member) = (src.channel, src.member) " +
                    "WHERE dst.member IS NULL OR dst.removed_at != now() " +
                    "UNION ALL " +
                    "SELECT" +
                    "  dst.*," +
                    "  'sync' AS source " +
                    "FROM dst " +
                    "LEFT JOIN src ON (src.channel, src.member) = (dst.channel, dst.member) " +
                    "WHERE src.member IS NULL AND dst.removed_at = now()";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, id);
            insertStatement.setString(2, id);

            log.info("Fetching %s channel members".formatted(channel));
            long startTime = System.currentTimeMillis();
            int rows = retryExecute(insertStatement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static String channelId(Connection conn, String destSchema, String name)
            throws SQLException
    {
        String value = null;
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement("SELECT id FROM " + destSchema + ".channels WHERE name = ?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                value = rs.getString(1);
            }
        }
        finally {
            if (stmt != null) {
                stmt.close();
            }
        }

        return value;
    }

    private static boolean syncMessages(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".messages AS SELECT * FROM " + srcSchema + ".messages WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON messages(ts);
            // CREATE INDEX ON messages(type);
            // CREATE INDEX ON messages(channel);
            // CREATE INDEX ON messages(user);
            // note that the first one is NOT a primary key

            for (String channel : options.channels) {
                String channelId = channelId(conn, destSchema, channel);
                syncNewChannelMessages(options, channel, channelId);
                syncOldChannelMessages(options, channel, channelId, "1970-01-01");
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static void syncNewChannelMessages(Options options, String channel, String channelId)
            throws SQLException
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        PreparedStatement latestStatement = conn.prepareStatement(
                "SELECT COALESCE(MAX(" + "ts" + "), TIMESTAMP '1970-01-01') AS latest " +
                        "FROM " + destSchema + "." + "messages " +
                        "WHERE channel = ?");
        latestStatement.setString(1, channelId);
        log.info(format("Checking latest %s messages", channel));
        long startTime = System.currentTimeMillis();
        ResultSet result = latestStatement.executeQuery();
        result.next();
        String latest = result.getString(1);
        log.info(format("Found %s, took %s", latest, Duration.ofMillis(System.currentTimeMillis() - startTime)));

        syncOldChannelMessages(options, channel, channelId, latest);
    }

    private static void syncOldChannelMessages(Options options, String channel, String channelId, String latest)
            throws SQLException
    {
        Connection conn = options.conn;
        String srcSchema = options.srcSchema;
        String destSchema = options.destSchema;
        PreparedStatement oldestStatement = conn.prepareStatement(
                "SELECT COALESCE(MIN(" + "ts" + "), CURRENT_TIMESTAMP) AS oldest " +
                        "FROM " + destSchema + "." + "messages " +
                        "WHERE channel = ? AND ts > CAST(? AS TIMESTAMP(6) WITH TIME ZONE)");
        oldestStatement.setString(1, channelId);
        oldestStatement.setString(2, latest);
        PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO " + destSchema + "." + "messages" + " " +
                        "SELECT * FROM " + srcSchema + "." + "messages" + " src " +
                        "WHERE src.channel = ? AND src.ts > CAST(? AS TIMESTAMP(6) WITH TIME ZONE) AND src.ts < CAST(? AS TIMESTAMP(6) WITH TIME ZONE) " +
                        "LIMIT 10000");
        statement.setString(1, channelId);
        statement.setString(2, latest);

        int page = 1;
        while (true) {
            log.info(format("Checking oldest %s messages for batch number %d", channel, page++));
            long startTime = System.currentTimeMillis();
            ResultSet result = oldestStatement.executeQuery();
            result.next();
            String oldest = result.getString(1);
            statement.setString(3, oldest);
            log.info(format("Fetching %s messages older than %s", channel, oldest));
            int rows = retryExecute(statement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            if (rows == 0) {
                break;
            }
        }
    }

    private static boolean syncReplies(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            options.conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + options.destSchema + ".replies AS SELECT * FROM " + options.srcSchema + ".replies WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON replies(ts);
            // CREATE INDEX ON replies(user);
            // CREATE INDEX ON replies(channel);

            for (String channel : options.channels) {
                syncChannelReplies(options, channelId(conn, destSchema, channel));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static void syncChannelReplies(Options options, String channelId)
            throws SQLException
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        // only get replies for threads older than 14 days, that don't have any replies saved yet,
        // because once we get any, we won't get new replies added later
        String threadQuery = "SELECT m.ts " +
                "FROM " + destSchema + ".messages m " +
                "LEFT JOIN " + destSchema + ".replies r ON (m.channel, m.ts) = (r.channel, r.thread_ts) " +
                "WHERE m.channel = ? AND NOT m.hidden AND m.reply_count > 0 AND m.ts < CURRENT_TIMESTAMP - INTERVAL '14' DAY " +
                "GROUP BY m.ts " +
                "HAVING COUNT(r.ts) = 0 " +
                "ORDER BY m.ts DESC";
        String insertQuery =
                "INSERT INTO " + destSchema + ".replies " +
                        "SELECT src.* " +
                        "FROM " + srcSchema + ".replies src " +
                        "WHERE src.channel = ? AND src.thread_ts = CAST(? AS TIMESTAMP(6) WITH TIME ZONE)";
        PreparedStatement threadStatement = conn.prepareStatement(threadQuery);
        threadStatement.setString(1, channelId);
        if (!threadStatement.execute()) {
            log.info("No results!");
            return;
        }
        ResultSet resultSet = threadStatement.getResultSet();
        PreparedStatement insertStatement = conn.prepareStatement(insertQuery);
        insertStatement.setString(1, channelId);

        while (resultSet.next()) {
            String thread = resultSet.getString(1);
            log.info(format("Fetching replies for thread: %s", thread));
            insertStatement.setString(2, thread);

            long startTime = System.currentTimeMillis();
            int rows = retryExecute(insertStatement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
        }
    }

    private static int retryExecute(PreparedStatement statement)
            throws SQLException
    {
        int breaker = 3;
        while (true) {
            try {
                long startTime = System.currentTimeMillis();
                int queryResult = statement.executeUpdate();
                MILLISECONDS.sleep(getQueryDelayMillis() - (System.currentTimeMillis() - startTime));
                return queryResult;
            }
            catch (SQLException e) {
                // NoRouteToHostException can happend if the Trino server crashes
                if (getRootCause(e) instanceof NoRouteToHostException || breaker-- == 1) {
                    throw e;
                }
                log.severe(e.getMessage());
                log.severe(format("Retrying %d more times", breaker));
            }
            catch (InterruptedException e) {
                // Do nothing
            }
        }
    }

    private static long getQueryDelayMillis()
    {
        String queryDelay = System.getenv("QUERY_DELAY_MS");
        if (queryDelay == null) {
            return 0L;
        }
        return Long.parseLong(queryDelay);
    }
}

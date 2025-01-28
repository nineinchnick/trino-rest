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

package pl.net.was.rest.github;

import com.google.common.collect.ImmutableList;

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
import java.util.Random;
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
        String url = "jdbc:trino://localhost:8080/github/default";
        String username = "admin";
        String password = "";

        Map<String, String> env = System.getenv();

        // TODO combine with System.Properties
        List<String> names = Arrays.asList(
                "GITHUB_OWNER",
                "GITHUB_REPO",
                "SYNC_TABLES",
                "LOG_LEVEL",
                "TRINO_DEST_SCHEMA",
                "TRINO_SRC_SCHEMA",
                "TRINO_URL",
                "TRINO_USERNAME",
                "TRINO_PASSWORD",
                "EMPTY_INSERT_LIMIT",
                "CHECK_STEPS_DUPLICATES",
                "CHECK_ARTIFACTS_DUPLICATES",
                "QUERY_DELAY_MS");
        Map<String, String> defaults = Map.of(
                "TRINO_URL", url,
                "TRINO_USERNAME", username,
                "TRINO_PASSWORD", password,
                // notice that jog_logs and artifacts are not enabled by default
                "SYNC_TABLES", "repos,commits,issues,issue_comments,pulls,pull_commits,review_comments,reviews,workflows,runs,jobs,steps,check_suites,check_runs,check_run_annotations",
                "LOG_LEVEL", "INFO",
                "EMPTY_INSERT_LIMIT", "1",
                "CHECK_STEPS_DUPLICATES", "false",
                "CHECK_ARTIFACTS_DUPLICATES", "false",
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

        String owner = System.getenv("GITHUB_OWNER");
        String repo = System.getenv("GITHUB_REPO");
        String destSchema = System.getenv("TRINO_DEST_SCHEMA");
        String srcSchema = System.getenv("TRINO_SRC_SCHEMA");
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

        requireNonNull(owner, "GITHUB_OWNER environmental variable must be set");
        requireNonNull(repo, "GITHUB_REPO environmental variable must be set");
        requireNonNull(destSchema, "TRINO_DEST_SCHEMA environmental variable must be set");
        requireNonNull(srcSchema, "TRINO_SRC_SCHEMA environmental variable must be set");

        Options options = new Options(null, owner, repo, destSchema, srcSchema);

        // Note that the order in which these functions are called is determined by enabledTables, not availableTables
        Map<String, Function<Options, Boolean>> availableTables = new LinkedHashMap<>();
        availableTables.put("repos", Sync::syncRepos);
        availableTables.put("commits", Sync::syncCommits);
        availableTables.put("issues", Sync::syncIssues);
        availableTables.put("issue_comments", Sync::syncIssueComments);
        availableTables.put("pulls", Sync::syncPulls);
        availableTables.put("pull_commits", Sync::syncPullCommits);
        availableTables.put("pull_stats", Sync::syncPullStats);
        availableTables.put("reviews", Sync::syncReviews);
        availableTables.put("review_comments", Sync::syncReviewComments);
        availableTables.put("workflows", Sync::syncWorkflows);
        availableTables.put("runs", Sync::syncRuns);
        availableTables.put("jobs", Sync::syncJobs);
        availableTables.put("job_logs", Sync::syncJobLogs);
        availableTables.put("steps", Sync::syncSteps);
        availableTables.put("artifacts", Sync::syncArtifacts);
        availableTables.put("check_suites", Sync::syncCheckSuites);
        availableTables.put("check_runs", Sync::syncCheckRuns);
        availableTables.put("check_run_annotations", Sync::syncCheckRunAnnotations);
        availableTables.put("teams", Sync::syncTeams);
        availableTables.put("members", Sync::syncMembers);
        availableTables.put("collaborators", Sync::syncCollaborators);

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
        String owner;
        String repo;
        String destSchema;
        String srcSchema;

        public Options(Connection conn, String owner, String repo, String destSchema, String srcSchema)
        {
            this.conn = conn;
            this.owner = owner;
            this.repo = repo;
            this.destSchema = destSchema;
            this.srcSchema = srcSchema;
        }
    }

    private static boolean syncRepos(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            // sync all columns except permissions because not all connectors support maps and permissions only apply to currently used token anyway
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".repos AS SELECT " +
                            "id, name, full_name, owner_id, owner_login, private, description, fork, homepage, url, forks_count, stargazers_count, watchers_count, size, default_branch, open_issues_count, " +
                            "is_template, topics, has_issues, has_projects, has_wiki, has_pages, has_downloads, archived, disabled, visibility, pushed_at, created_at, updated_at " +
                            "FROM " + srcSchema + ".repos WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON repos(id);
            // CREATE INDEX ON repos(name);
            // CREATE INDEX ON repos(full_name);
            // CREATE INDEX ON repos(owner_id);
            // CREATE INDEX ON repos(owner_login);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM repos a USING repos b WHERE a.updated_at < b.updated_at AND a.id = b.id;
            // or use the unique_repos view (from `trino-rest-github/sql/views.sql`) that ignores duplicates

            // there's no "since" filter, but we can sort by updated_at, so keep inserting records where this is greater than max
            PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                    "SELECT COALESCE(MAX(updated_at), TIMESTAMP '0000-01-01') AS latest FROM " + destSchema + ".repos WHERE owner_login = ?");
            lastUpdatedStatement.setString(1, options.owner);
            ResultSet result = lastUpdatedStatement.executeQuery();
            result.next();
            String lastUpdated = result.getString(1);

            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".repos " +
                            "SELECT " +
                            "id, name, full_name, owner_id, owner_login, private, description, fork, homepage, url, forks_count, stargazers_count, watchers_count, size, default_branch, open_issues_count, " +
                            "is_template, topics, has_issues, has_projects, has_wiki, has_pages, has_downloads, archived, disabled, visibility, pushed_at, created_at, updated_at " +
                            "FROM repos WHERE owner_login = ? AND updated_at > CAST(? AS TIMESTAMP)");
            statement.setString(1, options.owner);
            statement.setString(2, lastUpdated);

            log.info("Fetching repos");
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

    private static boolean syncCommits(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".commits AS SELECT * FROM " + srcSchema + ".commits WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE runs ADD PRIMARY KEY (owner, repo, sha);

            syncSince(options, "commits", "committer_date");
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncIssues(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".issues AS SELECT * FROM " + srcSchema + ".issues WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON issues(owner, repo);
            // CREATE INDEX ON issues(id);
            // CREATE INDEX ON issues(user_id);
            // CREATE INDEX ON issues(milestone_id);
            // CREATE INDEX ON issues(assignee_id);
            // CREATE INDEX ON issues(state);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM issues a USING issues b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            syncSince(options, "issues");
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncIssueComments(Options options)
    {
        try {
            options.conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + options.destSchema + ".issue_comments AS SELECT * FROM " + options.srcSchema + ".issue_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON issue_comments(owner, repo);
            // CREATE INDEX ON issue_comments(id);
            // CREATE INDEX ON issue_comments(user_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM issue_comments a USING issue_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            try {
                syncSince(options, "issue_comments");
            }
            catch (Exception e) {
                // fetching comments since the epoch can fail with a Server Error, so fall back to fetching them for every issue and pull request
                // TODO how to distinguish rate limit errors?
                log.severe("Failed to get latest updated issue comments, falling back to checking every issue and pull request: " + e.getMessage());

                syncAllIssueComments(options);
                syncAllPullComments(options);
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncAllIssueComments(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".issue_comments AS SELECT * FROM " + srcSchema + ".issue_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON issue_comments(owner, repo);
            // CREATE INDEX ON issue_comments(id);
            // CREATE INDEX ON issue_comments(user_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM issue_comments a USING issue_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            // only fetch issue comments from up to 30 issues without any comments
            String runsQuery = format(
                    "SELECT r.id, r.number " +
                            "FROM " + destSchema + ".issues r " +
                            "LEFT JOIN " + destSchema + ".issue_comments c ON c.issue_url = r.url " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.id > ? " +
                            "GROUP BY r.id, r.number " +
                            "HAVING COUNT(c.id) = 0 " +
                            "ORDER BY r.id ASC LIMIT %d", 30);
            // since there's no difference between issues without comments and those we have not checked or yet,
            // we need to know the last checked issue id and add a condition to fetch lesser numbers
            PreparedStatement idStatement = conn.prepareStatement("SELECT max(r.id) FROM (" + runsQuery + ") r");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".issue_comments " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") r " +
                            "CROSS JOIN unnest(issue_comments(?, ?, r.number)) src " +
                            "LEFT JOIN " + destSchema + ".issue_comments dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = 0;
            while (true) {
                insertStatement.setLong(3, previousId);
                log.info(format("Fetching comments for issues with id greater than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));

                idStatement.setLong(3, previousId);
                log.info("Checking for next issues without comments");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncAllPullComments(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".issue_comments AS SELECT * FROM " + srcSchema + ".issue_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON issue_comments(owner, repo);
            // CREATE INDEX ON issue_comments(id);
            // CREATE INDEX ON issue_comments(user_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM issue_comments a USING issue_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            // only fetch pull comments from up to 30 pulls without any comments
            String runsQuery = format(
                    "SELECT r.id, r.number " +
                            "FROM " + destSchema + ".pulls r " +
                            "LEFT JOIN " + destSchema + ".issue_comments c ON c.issue_url = r.url " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.id > ? " +
                            "GROUP BY r.id, r.number " +
                            "HAVING COUNT(c.id) = 0 " +
                            "ORDER BY r.id ASC LIMIT %d", 30);
            // since there's no difference between pulls without comments and those we have not checked or yet,
            // we need to know the last checked pull id and add a condition to fetch lesser numbers
            PreparedStatement idStatement = conn.prepareStatement("SELECT max(r.id) FROM (" + runsQuery + ") r");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".issue_comments " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") r " +
                            "CROSS JOIN unnest(issue_comments(?, ?, r.number)) src " +
                            "LEFT JOIN " + destSchema + ".issue_comments dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = 0;
            while (true) {
                insertStatement.setLong(3, previousId);
                log.info(format("Fetching comments for pulls with id greater than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));

                idStatement.setLong(3, previousId);
                log.info("Checking for next pulls without comments");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncReviewComments(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".review_comments AS SELECT * FROM " + srcSchema + ".review_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON review_comments(id);
            // CREATE INDEX ON review_comments(owner, repo);
            // CREATE INDEX ON review_comments(user_id);
            // CREATE INDEX ON review_comments(pull_request_review_id);
            // CREATE INDEX ON review_comments(in_reply_to_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM review_comments a USING review_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            try {
                return syncSince(options, "review_comments");
            }
            catch (Exception e) {
                // fetching comments since the epoch can fail with a Server Error, so fall back to fetching them for every review
                // TODO how to distinguish rate limit errors?
                log.severe("Failed to get latest updated review comments, falling back to checking every review: " + e.getMessage());

                return syncAllReviewComments(options);
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean syncAllReviewComments(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".review_comments AS SELECT * FROM " + srcSchema + ".review_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON review_comments(id);
            // CREATE INDEX ON review_comments(owner, repo);
            // CREATE INDEX ON review_comments(user_id);
            // CREATE INDEX ON review_comments(pull_request_review_id);
            // CREATE INDEX ON review_comments(in_reply_to_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM review_comments a USING review_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            // only fetch review comments from up to 30 reviews without any comments
            String runsQuery = format(
                    "SELECT r.id, r.pull_number " +
                            "FROM " + destSchema + ".reviews r " +
                            "LEFT JOIN " + destSchema + ".review_comments c ON c.pull_request_review_id = r.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.id > ? " +
                            "GROUP BY r.id, r.pull_number " +
                            "HAVING COUNT(c.id) = 0 " +
                            "ORDER BY r.id ASC LIMIT %d", 30);
            // since there's no difference between reviews without comments and those we have not checked or yet,
            // we need to know the last checked review id and add a condition to fetch lesser numbers
            PreparedStatement idStatement = conn.prepareStatement("SELECT max(r.id) FROM (" + runsQuery + ") r");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".review_comments " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") r " +
                            "CROSS JOIN unnest(review_comments(?, ?, r.pull_number)) src " +
                            "LEFT JOIN " + destSchema + ".review_comments dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = 0;
            while (true) {
                insertStatement.setLong(3, previousId);
                log.info(format("Fetching comments for reviews with id greater than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));

                idStatement.setLong(3, previousId);
                log.info("Checking for next reviews without comments");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncSince(Options options, String name)
            throws SQLException
    {
        return syncSince(options, name, "updated_at");
    }

    private static boolean syncSince(Options options, String name, String columnName)
            throws SQLException
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                "SELECT COALESCE(MAX(" + columnName + "), TIMESTAMP '1970-01-01') AS latest FROM " + destSchema + "." + name + " WHERE owner = ? AND repo = ?");
        lastUpdatedStatement.setString(1, options.owner);
        lastUpdatedStatement.setString(2, options.repo);
        ResultSet result = lastUpdatedStatement.executeQuery();
        result.next();
        String lastUpdated = result.getString(1);
        PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO " + destSchema + "." + name + " " +
                        "SELECT * FROM unnest(" + name + "(?, ?, ?, CAST(? AS TIMESTAMP))) src");
        statement.setString(1, options.owner);
        statement.setString(2, options.repo);
        statement.setString(4, lastUpdated);

        int page = 1;
        while (true) {
            log.info(format("Fetching %s page number %d", name, page));
            long startTime = System.currentTimeMillis();
            statement.setInt(3, page++);
            int rows = retryExecute(statement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            if (rows == 0) {
                break;
            }
        }
        return true;
    }

    private static boolean syncPulls(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".pulls AS SELECT * FROM " + srcSchema + ".pulls WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON pulls(owner, repo);
            // CREATE INDEX ON pulls(id);
            // CREATE INDEX ON pulls(user_id);
            // CREATE INDEX ON pulls(milestone_id);
            // CREATE INDEX ON pulls(assignee_id);
            // CREATE INDEX ON pulls(state);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM pulls a USING pulls b WHERE a.updated_at < b.updated_at AND a.id = b.id;
            // or use the unique_pulls view (from `trino-rest-github/sql/views.sql`) that ignores duplicates

            // there's no "since" filter, but we can sort by updated_at, so keep inserting records where this is greater than max
            PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                    "SELECT COALESCE(MAX(updated_at), TIMESTAMP '0000-01-01') AS latest FROM " + destSchema + ".pulls WHERE owner = ? AND repo = ?");
            lastUpdatedStatement.setString(1, options.owner);
            lastUpdatedStatement.setString(2, options.repo);
            ResultSet result = lastUpdatedStatement.executeQuery();
            result.next();
            String lastUpdated = result.getString(1);

            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".pulls " +
                            "SELECT * FROM unnest(pulls(?, ?, ?)) WHERE updated_at > CAST(? AS TIMESTAMP)");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);
            statement.setString(4, lastUpdated);

            int page = 1;
            while (true) {
                log.info(format("Fetching pulls page number %d", page));
                long startTime = System.currentTimeMillis();
                statement.setInt(3, page++);
                int rows = retryExecute(statement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncPullCommits(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".pull_commits AS SELECT * FROM " + srcSchema + ".pull_commits WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE pull_commits ADD PRIMARY KEY (owner, repo, pull_number, sha);

            // only fetch pull commits from up to 30 new or updated commits without any in the last update period, if it was updated less than 3 days ago
            String runsQuery = format(
                    "WITH pulls AS (" +
                            "  SELECT number, lag(updated_at, 1, timestamp '1970-01-01 00:00:00') over (partition by number order by updated_at) AS prev_updated_at, updated_at" +
                            "  FROM " + destSchema + ".pulls" +
                            "  WHERE owner = ? AND repo = ? AND number > ?" +
                            "), " +
                            "latest AS (" +
                            "  SELECT number, max_by(prev_updated_at, updated_at) AS prev_updated_at, max(updated_at) AS updated_at " +
                            "  FROM pulls" +
                            "  GROUP BY number" +
                            ") " +
                            "SELECT p.number " +
                            "FROM latest p " +
                            "LEFT JOIN " + destSchema + ".pull_commits c ON c.pull_number = p.number " +
                            "GROUP BY p.number, p.prev_updated_at, p.updated_at " +
                            "HAVING COUNT(c.sha) = 0 OR (MAX(c.committer_date) < p.prev_updated_at AND p.updated_at > CURRENT_DATE - INTERVAL '3' DAY) " +
                            "ORDER BY p.number ASC LIMIT %d", 30);
            // since there's no difference between pulls without commits and those we have not checked or yet,
            // we need to know the last checked pull number and add a condition to fetch lesser numbers
            PreparedStatement idStatement = conn.prepareStatement("SELECT max(p.number) FROM (" + runsQuery + ") p");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".pull_commits " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") p " +
                            "CROSS JOIN unnest(pull_commits(?, ?, p.number)) src " +
                            "LEFT JOIN " + destSchema + ".pull_commits dst ON (dst.pull_number, dst.sha) = (src.pull_number, src.sha) " +
                            "WHERE dst.sha IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = 0;
            while (true) {
                insertStatement.setLong(3, previousId);
                log.info(format("Fetching commits for pulls with number greater than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));

                idStatement.setLong(3, previousId);
                log.info("Checking for next pulls without commits");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncPullStats(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".pull_stats AS SELECT * FROM " + srcSchema + ".pull_stats WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON pull_stats(owner, repo, pull_number);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM pulls a USING pull_stats b WHERE a.updated_at < b.updated_at AND a.id = b.id;
            // or use the unique_pull_stats view (from `trino-rest-github/sql/views.sql`) that ignores duplicates

            // only fetch pull stats from up to 100 pulls without any in the last update period
            String runsQuery = format(
                    "WITH latest AS (" +
                            "  SELECT owner, repo, number, max(updated_at) AS updated_at " +
                            "  FROM " + destSchema + ".pulls" +
                            "  WHERE owner = ? AND repo = ?" +
                            "  GROUP BY owner, repo, number" +
                            ") " +
                            "SELECT p.number " +
                            "FROM latest p " +
                            "LEFT JOIN " + destSchema + ".pull_stats s ON (s.owner, s.repo, s.pull_number, s.updated_at) = (p.owner, p.repo, p.number, p.updated_at) " +
                            "WHERE s.pull_number IS NULL " +
                            "ORDER BY p.number ASC LIMIT %d", 100);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".pull_stats " +
                            "SELECT src.* " +
                            "FROM (SELECT pull_stats(?, ?, p.number).* FROM (" + runsQuery + ") p) src " +
                            "LEFT JOIN " + destSchema + ".pull_stats dst ON (dst.owner, dst.repo, dst.pull_number, dst.updated_at) = (src.owner, src.repo, src.pull_number, src.updated_at) " +
                            "WHERE dst.pull_number IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(3, options.owner);
            insertStatement.setString(4, options.repo);

            while (true) {
                log.info("Fetching stats for pulls without any");
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncReviews(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".reviews AS SELECT * FROM " + srcSchema + ".reviews WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON reviews(owner, repo);
            // CREATE INDEX ON reviews(id);
            // CREATE INDEX ON reviews(user_id);
            // CREATE INDEX ON reviews(pull_number);
            // CREATE INDEX ON reviews(state);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using:
            // DELETE FROM reviews a USING reviews b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            // would be better to just get new reviews but there's no endpoint to get them for the whole repo
            // so assuming that review comments might have been fetched first, fetch missing reviews

            // check if there are any review comments at all, and if not, call instead syncAllReviews()
            ResultSet comments = conn.prepareStatement("SELECT * FROM " + destSchema + ".review_comments LIMIT 1")
                    .executeQuery();
            if (!comments.next()) {
                return syncAllReviews(options);
            }

            // only fetch up to 10 pulls for comments without reviews
            String prQuery = format(
                    "SELECT p.number " +
                            "FROM " + destSchema + ".unique_review_comments c " +
                            "LEFT JOIN " + destSchema + ".reviews r ON r.id = c.pull_request_review_id " +
                            "JOIN " + destSchema + ".unique_pulls p ON p.url = c.pull_request_url " +
                            "WHERE c.owner = ? AND c.repo = ? AND r.id IS NULL " +
                            "GROUP BY p.number " +
                            "ORDER BY p.number ASC LIMIT %d", 10);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".reviews " +
                            "SELECT src.* " +
                            "FROM (" + prQuery + ") p " +
                            "CROSS JOIN unnest(reviews(?, ?, p.number)) src " +
                            "LEFT JOIN " + destSchema + ".reviews dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(3, options.owner);
            insertStatement.setString(4, options.repo);

            while (true) {
                log.info("Checking for next review comments without reviews");

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncAllReviews(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".reviews AS SELECT * FROM " + srcSchema + ".reviews WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON reviews(owner, repo);
            // CREATE INDEX ON reviews(id);
            // CREATE INDEX ON reviews(user_id);
            // CREATE INDEX ON reviews(pull_number);
            // CREATE INDEX ON reviews(state);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using:
            // DELETE FROM reviews a USING reviews b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            // TODO only fall back to this if there are no reviews at all
            // only fetch reviews from up to 30 pulls without any reviews
            String runsQuery = format(
                    "SELECT p.number " +
                            "FROM " + destSchema + ".unique_pulls p " +
                            "LEFT JOIN " + destSchema + ".reviews r ON r.pull_number = p.number " +
                            "WHERE p.owner = ? AND p.repo = ? AND p.number < ? " +
                            "GROUP BY p.number " +
                            "HAVING COUNT(r.id) = 0 " +
                            "ORDER BY p.number DESC LIMIT %d", 30);
            // since there's no difference between pulls without reviews and those we have not checked or yet,
            // we need to know the last checked pull number and add a condition to fetch lesser numbers
            PreparedStatement idStatement = conn.prepareStatement("SELECT min(p.number) FROM (" + runsQuery + ") p");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            // TODO this only gets missing reviews - doesn't allow to fetch updated reviews
            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".reviews " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") p " +
                            "CROSS JOIN unnest(reviews(?, ?, p.number)) src " +
                            "LEFT JOIN " + destSchema + ".reviews dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = Long.MAX_VALUE;
            while (true) {
                idStatement.setLong(3, previousId);
                insertStatement.setLong(3, previousId);

                log.info("Checking for next pulls without reviews");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }

                log.info(format("Fetching reviews for pulls with number less than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncWorkflows(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".workflows AS SELECT * FROM " + srcSchema + ".workflows WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON workflows(owner, repo);
            // CREATE INDEX ON workflows(id);
            // note that the first one is NOT a primary key, so updated records can be inserted
            // and then removed as duplicates by running this in the target database (not supported in Trino):
            // DELETE FROM workflows a USING workflows b WHERE a.updated_at < b.updated_at AND a.id = b.id;
            // or use the unique_workflows view (from `trino-rest-github/sql/views.sql`) that ignores duplicates

            // there's no "since" filter, but we can sort by updated_at, so keep inserting records where this is greater than max
            PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                    "SELECT COALESCE(MAX(updated_at), TIMESTAMP '0000-01-01') AS latest FROM " + destSchema + ".workflows WHERE owner = ? AND repo = ?");
            lastUpdatedStatement.setString(1, options.owner);
            lastUpdatedStatement.setString(2, options.repo);
            ResultSet result = lastUpdatedStatement.executeQuery();
            result.next();
            String lastUpdated = result.getString(1);

            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".workflows " +
                            "SELECT * FROM " + srcSchema + ".workflows WHERE owner = ? AND repo = ? AND updated_at > CAST(? AS TIMESTAMP)");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);
            statement.setString(3, lastUpdated);

            log.info("Fetching workflows");
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

    private static boolean syncRuns(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".runs AS SELECT * FROM " + srcSchema + ".runs WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE runs ADD PRIMARY KEY (id);
            // CREATE INDEX ON runs(owner, repo);
            // CREATE INDEX ON runs(workflow_id);
            // CREATE INDEX ON runs(node_id);

            // save only completed runs to avoid having to update them later
            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".runs " +
                            "SELECT src.* FROM unnest(runs(?, ?, ?, 'completed')) src " +
                            "LEFT JOIN " + destSchema + ".runs dst ON (dst.id, dst.run_attempt) = (src.id, src.run_attempt) " +
                            "WHERE dst.id IS NULL");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);

            int page = 1;
            int breaker = getEmptyLimit();
            while (true) {
                log.info(format("Fetching runs page number %d", page));
                long startTime = System.currentTimeMillis();
                statement.setInt(3, page++);
                int rows = retryExecute(statement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    if (breaker-- == 0) {
                        break;
                    }
                }
                else {
                    breaker = getEmptyLimit();
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncJobs(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".jobs AS SELECT * FROM " + srcSchema + ".jobs WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE jobs ADD PRIMARY KEY (id);
            // CREATE INDEX ON jobs(owner, repo);
            // CREATE INDEX ON jobs(run_id);
            // CREATE INDEX ON jobs(node_id);
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return syncNewJobs(options) && syncRerunJobs(options);
    }

    private static boolean syncNewJobs(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        try {
            // only fetch jobs for up to 20 completed runs not older than 2 months, without any jobs
            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".jobs " +
                            "WITH jobs AS (" +
                            "  SELECT id, run_id " +
                            "  FROM " + destSchema + ".jobs " +
                            "  WHERE owner = ? AND repo = ? " +
                            ") " +
                            "SELECT src.* " +
                            "FROM (" +
                            "SELECT r.id " +
                            "FROM " + destSchema + ".runs r " +
                            "LEFT JOIN jobs j ON j.run_id = r.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                            "GROUP BY r.id " +
                            "HAVING COUNT(j.id) = 0 " +
                            "ORDER BY r.id DESC LIMIT 60" +
                            ") r " +
                            "CROSS JOIN unnest(jobs(?, ?, r.id)) src " +
                            "LEFT JOIN jobs dst ON (dst.run_id, dst.id) = (src.run_id, src.id) " +
                            "WHERE dst.id IS NULL");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);
            statement.setString(3, options.owner);
            statement.setString(4, options.repo);
            statement.setString(5, options.owner);
            statement.setString(6, options.repo);

            while (true) {
                log.info("Fetching jobs");
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(statement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    break;
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncRerunJobs(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        int batchSize = 10;
        try {
            // first get updated runs where run_attempt is higher than it was
            String runsQuery =
                    "SELECT r.id " +
                            "FROM unnest(runs(?, ?, ?, 'completed')) ru " +
                            "JOIN " + destSchema + ".runs r ON (r.owner, r.repo, r.id) = (ru.owner, ru.repo, ru.id) " +
                            "WHERE ru.updated_at > r.updated_at AND ru.run_attempt != r.run_attempt";
            PreparedStatement idStatement = conn.prepareStatement(runsQuery);
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".jobs " +
                            "SELECT src.* " +
                            "FROM " + srcSchema + ".jobs src " +
                            "LEFT JOIN " + destSchema + ".jobs dst ON (dst.run_id, dst.id) = (src.run_id, src.id) " +
                            "WHERE src.owner = ? AND src.repo = ? AND src.run_id IN (" + batchPlaceholders + ") " +
                            "AND (dst.id IS NULL OR dst.run_attempt IS DISTINCT FROM src.run_attempt)");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            int page = 1;
            int breaker = getEmptyLimit();
            while (true) {
                log.info(format("Fetching updated runs page number %d", page));
                long startTime = System.currentTimeMillis();
                idStatement.setInt(3, page++);
                int rows = 0;
                if (!idStatement.execute()) {
                    log.info("No results!");
                    return true;
                }
                ResultSet resultSet = idStatement.getResultSet();
                while (true) {
                    List<Long> ids = getLongBatch(resultSet, batchSize);
                    log.info(format("Fetching jobs for run ids: %s", ids));
                    if (ids.isEmpty()) {
                        break;
                    }
                    for (int i = 0; i < ids.size(); i++) {
                        insertStatement.setLong(3 + i, ids.get(i));
                    }
                    rows += retryExecute(insertStatement);
                    MILLISECONDS.sleep(getQueryDelayMillis());
                }
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    if (breaker-- == 0) {
                        break;
                    }
                }
                else {
                    breaker = getEmptyLimit();
                }
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncSteps(Options options)
    {
        int batchSize = 2;
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".steps AS SELECT * FROM " + srcSchema + ".steps WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE steps ADD PRIMARY KEY (job_id, number);
            // CREATE INDEX ON steps(owner, repo);

            if (batchSize > 2) {
                return syncStepsBatches(options, batchSize);
            }
            // if the batchSize is small, it's completely ignored and runs will be processed one by one

            // only fetch steps from jobs from completed runs not older than 2 months, without any job steps
            String runsQuery =
                    "SELECT r.id " +
                            "FROM " + destSchema + ".runs r " +
                            "JOIN " + destSchema + ".jobs j ON j.run_id = r.id " +
                            "LEFT JOIN " + destSchema + ".steps s ON s.job_id = j.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND j.steps_count != 0 " +
                            "GROUP BY r.id " +
                            "HAVING COUNT(s.number) = 0 " +
                            "ORDER BY r.id DESC";
            // since there's no difference between jobs without steps and those we have not checked or yet,
            // we need to know the last checked run id and add a condition to fetch lesser ids
            PreparedStatement idStatement = conn.prepareStatement(runsQuery);
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String insertQuery =
                    "INSERT INTO " + destSchema + ".steps " +
                            "SELECT src.* " +
                            "FROM unnest(steps(?, ?, ?)) src " +
                            "LEFT JOIN " + destSchema + ".steps dst ON dst.run_id = ? AND (dst.job_id, dst.number) = (src.job_id, src.number) " +
                            "WHERE dst.number IS NULL";
            // the LEFT JOIN used to avoid duplicate errors always fetches all steps
            // and gets costly if there are many (>1 million) of those
            // allow to disable it, which should be safe when dst database supports transactions
            // WARNING: ensure that steps table has a primary key or an unique index on job_id and number columns
            String checkDuplicates = System.getenv("CHECK_STEPS_DUPLICATES");
            if (checkDuplicates != null && checkDuplicates.equals("false")) {
                insertQuery =
                        "INSERT INTO " + destSchema + ".steps " +
                                "SELECT * FROM unnest(steps(?, ?, ?)) src";
            }
            PreparedStatement insertStatement = conn.prepareStatement(insertQuery);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching run ids to get steps for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (resultSet.next()) {
                long runId = resultSet.getLong(1);
                insertStatement.setLong(3, runId);
                insertStatement.setLong(4, runId);

                log.info(format("Fetching steps for jobs of run %d", runId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncStepsBatches(Options options, int batchSize)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        try {
            // only fetch steps from jobs from up to 2 completed runs not older than 2 months, without any job steps
            String runsQuery = format(
                    "SELECT r.id " +
                            "FROM " + destSchema + ".runs r " +
                            "JOIN " + destSchema + ".jobs j ON j.run_id = r.id " +
                            "LEFT JOIN " + destSchema + ".steps s ON s.job_id = j.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.id < ? " +
                            "GROUP BY r.id " +
                            "HAVING COUNT(s.number) = 0 " +
                            "ORDER BY r.id DESC LIMIT %d", batchSize);
            // since there's no difference between jobs without steps and those we have not checked or yet,
            // we need to know the last checked run id and add a condition to fetch lesser ids
            PreparedStatement idStatement = conn.prepareStatement("SELECT min(r.id) FROM (" + runsQuery + ") r");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".steps " +
                            "SELECT src.* " +
                            "FROM (" + runsQuery + ") r " +
                            "CROSS JOIN unnest(steps(?, ?, r.id)) src " +
                            "LEFT JOIN " + destSchema + ".steps dst ON dst.run_id = r.id AND (dst.job_id, dst.number) = (src.job_id, src.number) " +
                            "WHERE dst.number IS NULL");
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(4, options.owner);
            insertStatement.setString(5, options.repo);

            long previousId = Long.MAX_VALUE;
            while (true) {
                idStatement.setLong(3, previousId);
                insertStatement.setLong(3, previousId);

                log.info("Checking for next runs with jobs without steps");
                ResultSet resultSet = idStatement.executeQuery();
                if (!resultSet.next()) {
                    break;
                }
                previousId = resultSet.getLong(1);
                if (resultSet.wasNull()) {
                    break;
                }
                log.info(format("Fetching steps for jobs of runs with id less than %d", previousId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncArtifacts(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".artifacts AS SELECT * FROM " + srcSchema + ".artifacts WITH NO DATA");
            // artifacts can be very big, so to avoid scanning the whole table create another one with only the identifiers
            // also allow nulls in this table to know which runs don't have any artifacts to avoid checking them more than once
            conn.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS " + destSchema + ".runs_artifacts AS " +
                    "SELECT DISTINCT r.owner, r.repo, r.id AS run_id, a.id, a.path, a.part_number " +
                    "FROM " + destSchema + ".runs r" +
                    "LEFT JOIN " + destSchema + ".artifacts a ON (r.owner, r.repo, r.id) = (a.owner, a.repo, a.run_id)");
            // consider adding some indexes:
            // ALTER TABLE artifacts ADD PRIMARY KEY (id, path, part_number);
            // CREATE INDEX ON artifacts(owner, repo);
            // CREATE INDEX ON artifacts(run_id);

            // assuming that all runs should finish in at least 2 hours
            // find the last run id at least 2 hours old with a check_run present
            // and move up
            String runsQuery = "SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".runs_artifacts a ON a.run_id = r.id " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "GROUP BY r.id " +
                    "HAVING COUNT(a.run_id) != 0 " +
                    "ORDER BY r.id DESC LIMIT 1";
            PreparedStatement idStatement = conn.prepareStatement("SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.id > COALESCE((" + runsQuery + "), 0) AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                    "ORDER BY r.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);
            idStatement.setString(3, options.owner);
            idStatement.setString(4, options.repo);

            String tempTable = destSchema + ".tmp_artifacts_" + randomString();
            PreparedStatement createStatement = conn.prepareStatement("CREATE TABLE " + tempTable + " AS " +
                    "SELECT * FROM " + destSchema + ".artifacts WITH NO DATA");
            PreparedStatement fetchStatement = conn.prepareStatement("INSERT INTO " + tempTable + " " +
                    "SELECT src.* " +
                    "FROM " + srcSchema + ".artifacts src " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.run_id = ?");
            fetchStatement.setString(1, options.owner);
            fetchStatement.setString(2, options.repo);
            PreparedStatement copyDataStatement = conn.prepareStatement("INSERT INTO " + destSchema + ".artifacts " +
                    "SELECT src.* " +
                    "FROM " + tempTable + " src " +
                    "LEFT JOIN " + destSchema + ".runs_artifacts dst ON (dst.owner, dst.repo, dst.run_id, dst.id, dst.path, dst.part_number) = (src.owner, src.repo, src.run_id, src.id, src.path, src.part_number) " +
                    "WHERE dst.run_id IS NULL");
            PreparedStatement nullIdsStatement = conn.prepareStatement("INSERT INTO " + destSchema + ".runs_artifacts " +
                    "SELECT ? AS owner, ? AS repo, ? AS run_id, NULL AS id, NULL AS path, NULL AS part_number");
            nullIdsStatement.setString(1, options.owner);
            nullIdsStatement.setString(2, options.repo);
            PreparedStatement copyIdsStatement = conn.prepareStatement("INSERT INTO " + destSchema + ".runs_artifacts " +
                    "SELECT src.owner, src.repo, src.run_id, src.id, src.path, src.part_number " +
                    "FROM " + tempTable + " src");
            PreparedStatement cleanupStatement = conn.prepareStatement("DROP TABLE " + tempTable);

            log.info("Fetching run ids to get artifacts for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (resultSet.next()) {
                long runId = resultSet.getLong(1);
                fetchStatement.setLong(3, runId);

                log.info(format("Fetching artifacts for jobs of run %d", runId));
                long startTime = System.currentTimeMillis();
                retryExecute(createStatement);
                int rows = retryExecute(fetchStatement);
                log.info(format("Fetched %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                if (rows == 0) {
                    nullIdsStatement.setLong(3, runId);
                    rows = retryExecute(nullIdsStatement);
                    log.info(format("Indexed %d null rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                }
                else {
                    rows = retryExecute(copyDataStatement);
                    log.info(format("Saved %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                    rows = retryExecute(copyIdsStatement);
                    log.info(format("Indexed %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
                }
                retryExecute(cleanupStatement);
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static String randomString()
    {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static boolean syncJobLogs(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".job_logs AS SELECT * FROM " + srcSchema + ".job_logs WITH NO DATA");
            // ALTER TABLE job_logs ADD PRIMARY KEY (job_id, part_number);
            // CREATE INDEX ON job_logs(owner, repo);

            // get largest job id of those with a log, older than 2 hours and move up
            // TODO because dynamic filtering is not supported yet, CROSS JOIN LATERAL between jobs and logs would not push down filter on job_id
            String runsQuery = "SELECT j.id " +
                    "FROM " + destSchema + ".jobs j " +
                    "LEFT JOIN " + destSchema + ".job_logs l ON l.job_id = j.id " +
                    "WHERE j.owner = ? AND j.repo = ? AND j.status = 'completed' AND j.conclusion != 'success' AND j.started_at > NOW() - INTERVAL '2' MONTH AND j.started_at < NOW() - INTERVAL '2' HOUR " +
                    "GROUP BY j.id " +
                    "HAVING COUNT(l.job_id) != 0 " +
                    "ORDER BY j.id DESC LIMIT 1";
            PreparedStatement idStatement = conn.prepareStatement("SELECT j.id " +
                    "FROM " + destSchema + ".jobs j " +
                    "WHERE j.owner = ? AND j.repo = ? AND j.id > COALESCE((" + runsQuery + "), 0) AND j.status = 'completed' AND j.started_at > NOW() - INTERVAL '2' MONTH " +
                    "ORDER BY j.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);
            idStatement.setString(3, options.owner);
            idStatement.setString(4, options.repo);

            String query = "INSERT INTO " + destSchema + ".job_logs " +
                    "SELECT src.* " +
                    "FROM " + srcSchema + ".job_logs src " +
                    "LEFT JOIN " + destSchema + ".job_logs dst ON dst.job_id = ? AND dst.part_number = src.part_number " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.job_id = ? AND dst.job_id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(2, options.owner);
            insertStatement.setString(3, options.repo);

            log.info("Fetching job ids to get logs for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (resultSet.next()) {
                long jobId = resultSet.getLong(1);
                insertStatement.setLong(1, jobId);
                insertStatement.setLong(4, jobId);

                log.info(format("Fetching logs of job %d", jobId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncCheckSuites(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        int batchSize = 10;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".check_suites AS SELECT * FROM " + srcSchema + ".check_suites WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE check_suites ADD PRIMARY KEY (id, ref);
            // CREATE INDEX ON check_suites(owner, repo);

            // note the lower time limit is only 15 days, GH API throws 500 HTTP errors when fetching older suites
            String runsQuery = "SELECT r.check_suite_id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".check_suites c ON c.id = r.check_suite_id " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '15' DAY AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "AND c.id IS NULL AND r.check_suite_id IS NOT NULL " +
                    "ORDER BY r.id ASC";
            PreparedStatement idStatement = conn.prepareStatement(runsQuery);
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_suites " +
                    "SELECT src.* " +
                    "FROM " + srcSchema + ".check_suites src " +
                    "LEFT JOIN " + destSchema + ".check_suites dst ON dst.ref = src.ref AND dst.id = src.id " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.id IN (" + batchPlaceholders + ") AND src.status = 'completed' AND dst.id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching check suite ids from runs without any");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (true) {
                List<Long> ids = getLongBatch(resultSet, batchSize);
                log.info(format("Fetching check suites for ids: %s", ids));
                if (ids.isEmpty()) {
                    break;
                }
                for (int i = 0; i < ids.size(); i++) {
                    insertStatement.setLong(3 + i, ids.get(i));
                }

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                MILLISECONDS.sleep(getQueryDelayMillis());
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncCheckRuns(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        int batchSize = 10;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".check_runs AS SELECT * FROM " + srcSchema + ".check_runs WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE check_runs ADD PRIMARY KEY (id, ref);
            // CREATE INDEX ON check_runs(owner, repo);
            // CREATE INDEX ON check_runs(check_suite_id);

            String runsQuery = "SELECT r.check_suite_id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".check_runs c ON c.check_suite_id = r.check_suite_id " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "AND c.check_suite_id IS NULL " +
                    "ORDER BY r.id ASC";
            PreparedStatement idStatement = conn.prepareStatement(runsQuery);
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_runs " +
                    "SELECT src.* " +
                    "FROM " + srcSchema + ".check_runs src " +
                    "LEFT JOIN " + destSchema + ".check_runs dst ON dst.ref = src.ref AND dst.id = src.id " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.check_suite_id IN (" + batchPlaceholders + ") AND src.status = 'completed' AND dst.id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching check suite ids from runs without any check runs");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (true) {
                List<Long> ids = getLongBatch(resultSet, batchSize);
                log.info(format("Fetching check runs for check suite ids: %s", ids));
                if (ids.isEmpty()) {
                    break;
                }
                for (int i = 0; i < ids.size(); i++) {
                    insertStatement.setLong(3 + i, ids.get(i));
                }

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                MILLISECONDS.sleep(getQueryDelayMillis());
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncCheckRunAnnotations(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        int batchSize = 10;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".check_run_annotations AS SELECT * FROM " + srcSchema + ".check_run_annotations WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE check_run_annotations ADD PRIMARY KEY (check_run_id, path, start_line, end_line, start_column, end_column, title);
            // CREATE INDEX ON check_run_annotations(owner, repo);
            // CREATE INDEX ON check_run_annotations(check_run_id);

            // get the smallest check run id of those with missing annotations and move up
            PreparedStatement idStatement = conn.prepareStatement("SELECT c.id " +
                    "FROM " + destSchema + ".check_runs c " +
                    "LEFT JOIN " + destSchema + ".check_run_annotations a ON a.check_run_id = c.id " +
                    "WHERE c.owner = ? AND c.repo = ? AND c.annotations_count != 0 AND c.started_at > NOW() - INTERVAL '2' MONTH " +
                    "GROUP BY c.id " +
                    "HAVING COUNT(a.check_run_id) = 0 " +
                    "ORDER BY c.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_run_annotations " +
                    "SELECT owner, repo, check_run_id, path, start_line, end_line, start_column, end_column, annotation_level, title, replace(message, U&'\\0000', ' '), replace(raw_details, U&'\\0000', ' '), blob_href " +
                    "FROM " + srcSchema + ".check_run_annotations src " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.check_run_id IN (" + batchPlaceholders + ")";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching check ids to get annotations for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return true;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (true) {
                List<Long> ids = getLongBatch(resultSet, batchSize);
                log.info(format("Fetching annotations for check ids: %s", ids));
                if (ids.isEmpty()) {
                    break;
                }
                for (int i = 0; i < ids.size(); i++) {
                    insertStatement.setLong(3 + i, ids.get(i));
                }

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                MILLISECONDS.sleep(getQueryDelayMillis());
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean syncTeams(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".timestamped_teams AS SELECT *, cast(now() as timestamp(3)) AS created_at, cast(now() as timestamp(3)) AS removed_at FROM " + srcSchema + ".teams WITH NO DATA");
            String query = "INSERT INTO " + destSchema + ".timestamped_teams " +
                    "WITH teams AS (" +
                    "  SELECT *, row_number() OVER (PARTITION BY org, id ORDER BY created_at DESC, removed_at) AS rownum" +
                    "  FROM " + destSchema + ".timestamped_teams" +
                    "  WHERE org = ?" +
                    "), dst AS (" +
                    "  SELECT org, id, node_id, url, html_url, name, slug, description, privacy, permission, members_url, repositories_url, parent_id, parent_slug, created_at, coalesce(removed_at, now()) AS removed_at" +
                    "  FROM teams" +
                    "  WHERE rownum = 1" +
                    "), src AS (" +
                    "  SELECT * FROM " + srcSchema + " .teams" +
                    "  WHERE org = ?" +
                    ") " +
                    "SELECT " +
                    "  src.*," +
                    "  cast(now() as timestamp(3)) AS created_at," +
                    "  NULL AS removed_at " +
                    "FROM src " +
                    "LEFT JOIN dst ON (dst.org, dst.id) = (src.org, src.id) " +
                    "WHERE dst.id IS NULL OR dst.removed_at != now() " +
                    "UNION ALL " +
                    "SELECT " +
                    "  dst.* " +
                    "FROM dst " +
                    "LEFT JOIN src ON (src.org, src.id) = (dst.org, dst.id) " +
                    "WHERE src.id IS NULL AND dst.removed_at = now()";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.owner);

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

    private static boolean syncMembers(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".timestamped_members AS SELECT *, cast(now() as timestamp(3)) AS joined_at, cast(now() as timestamp(3)) AS removed_at, cast('' AS VARCHAR) AS source FROM " + srcSchema + ".members WITH NO DATA");
            String query = "INSERT INTO " + destSchema + ".timestamped_members " +
                    "WITH members AS (" +
                    "  SELECT *, row_number() OVER (PARTITION BY org, team_slug, id ORDER BY joined_at DESC, removed_at) AS rownum" +
                    "  FROM " + destSchema + ".timestamped_members" +
                    "  WHERE org = ? " +
                    "), dst AS (" +
                    "  SELECT org, team_slug, login, id, avatar_url, gravatar_id, type, site_admin, joined_at, coalesce(removed_at, now()) AS removed_at" +
                    "  FROM members" +
                    "  WHERE rownum = 1" +
                    "), src AS (" +
                    "   SELECT * FROM " + srcSchema + " .members WHERE org = ?" +
                    "   UNION ALL" +
                    "   SELECT * FROM " + srcSchema + " .members WHERE org = ? AND team_slug IN (SELECT slug FROM " + srcSchema + ".teams WHERE org = ?)" +
                    ") " +
                    "SELECT" +
                    "  src.*," +
                    "  cast(now() as timestamp(3)) AS created_at," +
                    "  NULL AS removed_at," +
                    "  'sync' AS source " +
                    "FROM src " +
                    "LEFT JOIN dst ON (dst.org, coalesce(dst.team_slug, ''), dst.id) = (src.org, coalesce(src.team_slug, ''), src.id) " +
                    "WHERE dst.id IS NULL OR dst.removed_at != now() " +
                    "UNION ALL " +
                    "SELECT" +
                    "  dst.*," +
                    "  'sync' AS source " +
                    "FROM dst " +
                    "LEFT JOIN src ON (src.org, coalesce(src.team_slug, ''), src.id) = (dst.org, coalesce(dst.team_slug, ''), dst.id) " +
                    "WHERE src.id IS NULL AND dst.removed_at = now()";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.owner);
            insertStatement.setString(3, options.owner);
            insertStatement.setString(4, options.owner);

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

    private static boolean syncCollaborators(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".timestamped_collaborators AS SELECT *, cast(current_timestamp as timestamp(3)) AS joined_at, cast(current_timestamp as timestamp(3)) AS removed_at, cast('' AS VARCHAR) AS source FROM " + srcSchema + ".collaborators WITH NO DATA");
            String query = "INSERT INTO " + destSchema + ".timestamped_collaborators" +
                    " SELECT" +
                    "  coalesce(src.owner, dst.owner)" +
                    "  , coalesce(src.repo, dst.repo)" +
                    "  , coalesce(src.login, dst.login)" +
                    "  , coalesce(src.id, dst.id)" +
                    "  , coalesce(src.avatar_url, dst.avatar_url)" +
                    "  , coalesce(src.gravatar_id, dst.gravatar_id)" +
                    "  , coalesce(src.type, dst.type)" +
                    "  , coalesce(src.site_admin, dst.site_admin)" +
                    "  , coalesce(src.permission_pull, dst.permission_pull)" +
                    "  , coalesce(src.permission_triage, dst.permission_triage)" +
                    "  , coalesce(src.permission_push, dst.permission_push)" +
                    "  , coalesce(src.permission_maintain, dst.permission_maintain)" +
                    "  , coalesce(src.permission_admin, dst.permission_admin)" +
                    "  , coalesce(src.role_name, dst.role_name)" +
                    "  , coalesce(dst.joined_at, cast(current_timestamp as timestamp(3))) AS joined_at" +
                    "  , if(src.id IS NULL, cast(current_timestamp as timestamp(3))) AS removed_at" +
                    "  , 'sync' AS source" +
                    " FROM (" +
                    "  SELECT owner, repo, login, id, avatar_url, gravatar_id, type, site_admin, permission_pull, permission_triage, permission_push, permission_maintain, permission_admin, role_name, max(joined_at) AS joined_at, max(coalesce(removed_at, timestamp '9999-12-31')) AS removed_at " +
                    "  FROM " + destSchema + ".timestamped_collaborators WHERE owner = ? AND repo = ? " +
                    "  GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 " +
                    "  HAVING max(coalesce(removed_at, timestamp '9999-12-31')) = timestamp '9999-12-31' OR max(coalesce(removed_at, timestamp '9999-12-31')) < max(joined_at)" +
                    ") dst" +
                    " FULL OUTER JOIN (SELECT * FROM " + srcSchema + " .collaborators WHERE owner = ? AND repo = ?) src ON (dst.owner, dst.repo, dst.login) = (src.owner, src.repo, src.login)" +
                    " WHERE dst.login IS NULL OR (src.login IS NULL AND dst.removed_at != timestamp '9999-12-31')";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);
            insertStatement.setString(3, options.owner);
            insertStatement.setString(4, options.repo);

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

    private static int getEmptyLimit()
    {
        String limit = System.getenv("EMPTY_INSERT_LIMIT");
        if (limit == null) {
            return 1;
        }
        return Integer.parseInt(limit);
    }

    private static long getQueryDelayMillis()
    {
        String queryDelay = System.getenv("QUERY_DELAY_MS");
        if (queryDelay == null) {
            return 0L;
        }
        return Long.parseLong(queryDelay);
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

    private static List<String> getStringBatch(ResultSet resultSet, int desiredSize)
            throws SQLException
    {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        int size = 0;
        String lastValue = null;
        while (size < desiredSize && resultSet.next()) {
            lastValue = resultSet.getString(1);
            result.add(lastValue);
            size++;
        }
        if (size == 0) {
            return result.build();
        }
        // pad with last value if read fewer than desiredSize items
        for (; size < desiredSize; size++) {
            result.add(lastValue);
        }
        return result.build();
    }

    private static List<Long> getLongBatch(ResultSet resultSet, int desiredSize)
            throws SQLException
    {
        ImmutableList.Builder<Long> result = ImmutableList.builder();
        int size = 0;
        long lastValue = 0;
        while (size < desiredSize && resultSet.next()) {
            lastValue = resultSet.getLong(1);
            result.add(lastValue);
            size++;
        }
        if (size == 0) {
            return result.build();
        }
        // pad with last value if read fewer than desiredSize items
        for (; size < desiredSize; size++) {
            result.add(lastValue);
        }
        return result.build();
    }
}

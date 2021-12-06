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
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

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
                "CHECK_ARTIFACTS_DUPLICATES");
        Map<String, String> defaults = Map.of(
                "TRINO_URL", url,
                "TRINO_USERNAME", username,
                "TRINO_PASSWORD", password,
                // notice that jog_logs and artifacts are not enabled by default
                "SYNC_TABLES", "issues,issue_comments,pulls,reviews,review_comments,runs,jobs,steps,check_suites,check_runs,check_run_annotations",
                "LOG_LEVEL", "INFO",
                "EMPTY_INSERT_LIMIT", "1",
                "CHECK_STEPS_DUPLICATES", "false",
                "CHECK_ARTIFACTS_DUPLICATES", "false");
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

        // TODO missing pull commits
        // Note that the order in which these functions are called is determined by enabledTables, not availableTables
        Map<String, Consumer<Options>> availableTables = new LinkedHashMap<>();
        availableTables.put("issues", Sync::syncIssues);
        availableTables.put("issue_comments", Sync::syncIssueComments);
        availableTables.put("pulls", Sync::syncPulls);
        availableTables.put("reviews", Sync::syncReviews);
        availableTables.put("review_comments", Sync::syncReviewComments);
        availableTables.put("runs", Sync::syncRuns);
        availableTables.put("jobs", Sync::syncJobs);
        availableTables.put("job_logs", Sync::syncJobLogs);
        availableTables.put("steps", Sync::syncSteps);
        availableTables.put("artifacts", Sync::syncArtifacts);
        availableTables.put("check_suites", Sync::syncCheckSuites);
        availableTables.put("check_runs", Sync::syncCheckRuns);
        availableTables.put("check_run_annotations", Sync::syncCheckRunAnnotations);

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            options.conn = conn;
            for (String table : enabledTables) {
                Consumer<Options> consumer = availableTables.get(table);
                if (consumer == null) {
                    throw new IllegalArgumentException(format(
                            "Unknown table %s, must be one of: %s",
                            table,
                            String.join(", ", availableTables.keySet())));
                }
                consumer.accept(options);
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
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

    private static void syncIssues(Options options)
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
        }
    }

    private static void syncIssueComments(Options options)
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

            syncSince(options, "issue_comments");
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncReviewComments(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".review_comments AS SELECT * FROM " + srcSchema + ".review_comments WITH NO DATA");
            // consider adding some indexes:
            // CREATE INDEX ON review_comments(owner, repo);
            // CREATE INDEX ON review_comments(id);
            // CREATE INDEX ON review_comments(user_id);
            // CREATE INDEX ON review_comments(pull_request_review_id);
            // CREATE INDEX ON review_comments(in_reply_to_id);
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using a procedure
            // DELETE FROM review_comments a USING review_comments b WHERE a.updated_at < b.updated_at AND a.id = b.id;

            syncSince(options, "review_comments");
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncSince(Options options, String name)
            throws SQLException
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        PreparedStatement lastUpdatedStatement = conn.prepareStatement(
                "SELECT COALESCE(MAX(updated_at), TIMESTAMP '1970-01-01') AS latest FROM " + destSchema + "." + name + " WHERE owner = ? AND repo = ?");
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
            log.info(format("Fetching page number %d", page));
            long startTime = System.currentTimeMillis();
            statement.setInt(3, page++);
            int rows = retryExecute(statement);
            log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            if (rows == 0) {
                break;
            }
        }
    }

    private static void syncPulls(Options options)
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
            // note that the first one is NOT a primary key, so updated records can be inserted and then removed as duplicates using:
            // DELETE FROM pulls a USING pulls b WHERE a.updated_at < b.updated_at AND a.id = b.id;

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
                log.info(format("Fetching page number %d", page));
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
        }
    }

    private static void syncReviews(Options options)
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

            // only fetch reviews from jobs from up to 2 completed runs not older than 2 months, without any job reviews
            String runsQuery = format(
                    "SELECT p.number " +
                            "FROM " + destSchema + ".pulls p " +
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
        }
    }

    private static void syncRuns(Options options)
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
                            "LEFT JOIN " + destSchema + ".runs dst ON dst.id = src.id " +
                            "WHERE dst.id IS NULL");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);

            int page = 1;
            int breaker = getEmptyLimit();
            while (true) {
                log.info(format("Fetching page number %d", page));
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
        }
    }

    private static void syncJobs(Options options)
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

            // only fetch jobs for up to 20 completed runs not older than 2 months, without any jobs
            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO " + destSchema + ".jobs " +
                            "SELECT src.* " +
                            "FROM (" +
                            "SELECT r.id " +
                            "FROM " + destSchema + ".runs r " +
                            "LEFT JOIN " + destSchema + ".jobs j ON j.run_id = r.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                            "GROUP BY r.id " +
                            "HAVING COUNT(j.id) = 0 " +
                            "ORDER BY r.id DESC LIMIT 20" +
                            ") r " +
                            "CROSS JOIN unnest(jobs(?, ?, r.id)) src " +
                            "LEFT JOIN " + destSchema + ".jobs dst ON (dst.run_id, dst.id) = (src.run_id, src.id) " +
                            "WHERE dst.id IS NULL");
            statement.setString(1, options.owner);
            statement.setString(2, options.repo);
            statement.setString(3, options.owner);
            statement.setString(4, options.repo);

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
        }
    }

    private static void syncSteps(Options options)
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
                syncStepsBatches(options, batchSize);
                return;
            }
            // if the batchSize is small, it's completely ignored and runs will be processed one by one

            // only fetch steps from jobs from completed runs not older than 2 months, without any job steps
            String runsQuery =
                    "SELECT r.id " +
                            "FROM " + destSchema + ".runs r " +
                            "JOIN " + destSchema + ".jobs j ON j.run_id = r.id " +
                            "LEFT JOIN " + destSchema + ".steps s ON s.job_id = j.id " +
                            "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
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
                return;
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
        }
    }

    private static void syncStepsBatches(Options options, int batchSize)
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
        }
    }

    private static void syncArtifacts(Options options)
    {
        Connection conn = options.conn;
        String destSchema = options.destSchema;
        String srcSchema = options.srcSchema;
        try {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + destSchema + ".artifacts AS SELECT * FROM " + srcSchema + ".artifacts WITH NO DATA");
            // consider adding some indexes:
            // ALTER TABLE artifacts ADD PRIMARY KEY (id, path, part_number);
            // CREATE INDEX ON artifacts(owner, repo);
            // CREATE INDEX ON artifacts(run_id);

            // assuming that all runs should finish in at least 2 hours
            // find the last run id at least 2 hours old with a check_run present
            // and move up
            // TODO because dynamic filtering is not supported yet, CROSS JOIN LATERAL between runs and artifacts would not push down filter on run_id
            String runsQuery = "SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".artifacts a ON a.run_id = r.id " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "GROUP BY r.id " +
                    "HAVING COUNT(a.id) != 0 " +
                    "ORDER BY r.id DESC LIMIT 1";
            PreparedStatement idStatement = conn.prepareStatement("SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.id > COALESCE((" + runsQuery + "), 0) AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                    "ORDER BY r.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);
            idStatement.setString(3, options.owner);
            idStatement.setString(4, options.repo);

            String query = "INSERT INTO " + destSchema + ".artifacts " +
                    "SELECT src.* " +
                    "FROM artifacts src " +
                    "LEFT JOIN " + destSchema + ".artifacts dst ON dst.run_id = ? AND (dst.id, dst.path, dst.part_number) = (src.id, src.path, src.part_number) " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.run_id = ? AND dst.id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(2, options.owner);
            insertStatement.setString(3, options.repo);

            log.info("Fetching run ids to get artifacts for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (resultSet.next()) {
                long runId = resultSet.getLong(1);
                insertStatement.setLong(1, runId);
                insertStatement.setLong(4, runId);

                log.info(format("Fetching artifacts for jobs of run %d", runId));
                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncJobLogs(Options options)
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
                    "FROM job_logs src " +
                    "LEFT JOIN " + destSchema + ".job_logs dst ON dst.job_id = ? AND dst.part_number = src.part_number " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.job_id = ? AND dst.job_id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(2, options.owner);
            insertStatement.setString(3, options.repo);

            log.info("Fetching job ids to get logs for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return;
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
        }
    }

    private static void syncCheckSuites(Options options)
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

            // assuming that all runs should finish in at least 2 hours
            // find the last run id at least 2 hours old with a check_run present
            // and move up
            // TODO because dynamic filtering is not supported yet, CROSS JOIN LATERAL between runs and checks would not push down filter on ref
            String runsQuery = "SELECT min(id) FROM (SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".check_suites c ON c.ref = r.head_sha " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "GROUP BY r.id " +
                    "HAVING COUNT(c.id) != 0 " +
                    "ORDER BY r.id DESC LIMIT " + batchSize + ") a";
            PreparedStatement idStatement = conn.prepareStatement("SELECT r.head_sha " +
                    "FROM " + destSchema + ".runs r " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.id > COALESCE((" + runsQuery + "), 0) AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                    "ORDER BY r.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);
            idStatement.setString(3, options.owner);
            idStatement.setString(4, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_suites " +
                    "SELECT src.* " +
                    "FROM check_suites src " +
                    "LEFT JOIN " + destSchema + ".check_suites dst ON dst.ref = src.ref AND dst.id = src.id " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.ref IN (" + batchPlaceholders + ") AND dst.id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching run refs to get check suites for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (true) {
                List<String> runRefs = getStringBatch(resultSet, batchSize);
                log.info(format("Fetching check suites for refs: %s", runRefs));
                if (runRefs.isEmpty()) {
                    break;
                }
                for (int i = 0; i < runRefs.size(); i++) {
                    insertStatement.setString(3 + i, runRefs.get(i));
                }

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncCheckRuns(Options options)
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

            // assuming that all runs should finish in at least 2 hours
            // find the last run id at least 2 hours old with a check_run present
            // and move up
            // TODO because dynamic filtering is not supported yet, CROSS JOIN LATERAL between runs and checks would not push down filter on ref
            String runsQuery = "SELECT min(id) FROM (SELECT r.id " +
                    "FROM " + destSchema + ".runs r " +
                    "LEFT JOIN " + destSchema + ".check_runs c ON c.ref = r.head_sha " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH AND r.created_at < NOW() - INTERVAL '2' HOUR " +
                    "GROUP BY r.id " +
                    "HAVING COUNT(c.id) != 0 " +
                    "ORDER BY r.id DESC LIMIT " + batchSize + ") a";
            PreparedStatement idStatement = conn.prepareStatement("SELECT r.head_sha " +
                    "FROM " + destSchema + ".runs r " +
                    "WHERE r.owner = ? AND r.repo = ? AND r.id > COALESCE((" + runsQuery + "), 0) AND r.status = 'completed' AND r.created_at > NOW() - INTERVAL '2' MONTH " +
                    "ORDER BY r.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);
            idStatement.setString(3, options.owner);
            idStatement.setString(4, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_runs " +
                    "SELECT src.* " +
                    "FROM check_runs src " +
                    "LEFT JOIN " + destSchema + ".check_runs dst ON dst.ref = src.ref AND dst.id = src.id " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.ref IN (" + batchPlaceholders + ") AND dst.id IS NULL";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching run refs to get check runs for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return;
            }
            ResultSet resultSet = idStatement.getResultSet();
            while (true) {
                List<String> runRefs = getStringBatch(resultSet, batchSize);
                log.info(format("Fetching check runs for refs: %s", runRefs));
                if (runRefs.isEmpty()) {
                    break;
                }
                for (int i = 0; i < runRefs.size(); i++) {
                    insertStatement.setString(3 + i, runRefs.get(i));
                }

                long startTime = System.currentTimeMillis();
                int rows = retryExecute(insertStatement);
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncCheckRunAnnotations(Options options)
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

            // get smallest check run id of those with missing annotations and move up
            // TODO because dynamic filtering is not supported yet, CROSS JOIN LATERAL between checks and annotations would not push down filter on check_run_id
            PreparedStatement idStatement = conn.prepareStatement("SELECT c.id " +
                    "FROM " + destSchema + ".check_runs c " +
                    "LEFT JOIN " + destSchema + ".check_run_annotations a ON a.check_run_id = c.id " +
                    "WHERE c.owner = ? AND c.repo = ? AND c.status = 'completed' AND c.annotations_count != 0 AND c.started_at > NOW() - INTERVAL '2' MONTH " +
                    "GROUP BY c.id " +
                    "HAVING COUNT(a.check_run_id) = 0 " +
                    "ORDER BY c.id ASC");
            idStatement.setString(1, options.owner);
            idStatement.setString(2, options.repo);

            String batchPlaceholders = "?" + ", ?".repeat(batchSize - 1);
            String query = "INSERT INTO " + destSchema + ".check_run_annotations " +
                    "SELECT owner, repo, check_run_id, path, start_line, end_line, start_column, end_column, annotation_level, title, replace(message, U&'\\0000', ' '), replace(raw_details, U&'\\0000', ' '), blob_href " +
                    "FROM check_run_annotations src " +
                    "WHERE src.owner = ? AND src.repo = ? AND src.check_run_id IN (" + batchPlaceholders + ")";
            PreparedStatement insertStatement = conn.prepareStatement(query);
            insertStatement.setString(1, options.owner);
            insertStatement.setString(2, options.repo);

            log.info("Fetching check ids to get annotations for");
            if (!idStatement.execute()) {
                log.info("No results!");
                return;
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
                log.info(format("Inserted %d rows, took %s", rows, Duration.ofMillis(System.currentTimeMillis() - startTime)));
            }
        }
        catch (Exception e) {
            log.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getEmptyLimit()
    {
        String limit = System.getenv("EMPTY_INSERT_LIMIT");
        if (limit == null) {
            return 1;
        }
        return Integer.parseInt(limit);
    }

    private static int retryExecute(PreparedStatement statement)
            throws SQLException
    {
        int breaker = 3;
        while (true) {
            try {
                return statement.executeUpdate();
            }
            catch (SQLException e) {
                if (breaker-- == 1) {
                    throw e;
                }
                log.severe(e.getMessage());
                log.severe(format("Retrying %d more times", breaker));
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

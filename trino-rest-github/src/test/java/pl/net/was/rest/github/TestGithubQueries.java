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

import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import org.testng.annotations.Test;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class TestGithubQueries
        extends AbstractTestQueryFramework
{
    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        return GithubQueryRunner.createQueryRunner();
    }

    @Test
    public void showTables()
    {
        assertQuery("SHOW SCHEMAS FROM github", "VALUES 'default', 'information_schema'");
        assertQuery("SHOW TABLES FROM github.default", "VALUES 'orgs', 'users', 'repos', 'members', 'teams', 'commits', 'issues', 'issue_comments', 'pulls', 'pull_commits', 'pull_stats', 'reviews', 'review_comments', 'workflows', 'runs', 'jobs', 'job_logs', 'steps', 'artifacts', 'runners', 'check_suites', 'check_runs', 'check_run_annotations'");
    }

    @Test
    public void selectFromTable()
    {
        assertQuery("SELECT login FROM orgs WHERE login = 'trinodb'",
                "VALUES ('trinodb')");
        assertQuery("SELECT login FROM users WHERE login = 'nineinchnick'",
                "VALUES ('nineinchnick')");
        assertQuery("SELECT name FROM repos WHERE owner_login = 'nineinchnick' AND name = 'trino-rest'",
                "VALUES ('trino-rest')");
        assertQuery("SELECT login FROM members WHERE org = 'trinodb' AND login = 'martint'",
                "VALUES ('martint')");
        // TODO this doesn't work with the default token available in GHA
        //assertQuery("SELECT slug FROM teams WHERE org = 'trinodb' AND slug = 'maintainers'",
        //        "VALUES ('maintainers')");
        assertQuery("SELECT count(*) FROM commits WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND sha = 'e43f63027cae851f3a02c2816b2f234991b2d139'",
                "VALUES (18)");
        assertQuery("SELECT title FROM issues WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND number = 40",
                "VALUES ('Dynamic filtering')");
        assertQuery("SELECT user_login FROM issue_comments WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND id = 873167897",
                "VALUES ('nineinchnick')");
        assertQuery("SELECT title FROM pulls WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND number = 1",
                "VALUES ('GitHub runs')");
        assertQuery("SELECT commits, additions, deletions, changed_files FROM pull_stats WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND pull_number = 1",
                "VALUES (5, 1736, 75, 27)");
        assertQuery("SELECT commit_message FROM pull_commits WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND pull_number = 1 AND sha = 'e43f63027cae851f3a02c2816b2f234991b2d139'",
                "VALUES ('Add Github Action runs')");
        assertQuery("SELECT user_login FROM reviews WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND pull_number = 66",
                "VALUES ('nineinchnick')");
        assertQuery("SELECT user_login FROM review_comments WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND id = 660141310",
                "VALUES ('nineinchnick')");
    }

    @Test
    public void selectFromGithubActionsTable()
    {
        assertQuery("SELECT name FROM workflows WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND name = 'Release with Maven'",
                "VALUES ('Release with Maven')");
        assertQuery("SELECT name FROM runs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND name = 'Release with Maven' LIMIT 1",
                "VALUES ('Release with Maven')");

        QueryRunner runner = getQueryRunner();
        long runId = (long) runner.execute("SELECT id FROM runs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND name = 'Release with Maven' LIMIT 1").getOnlyValue();
        assertThat(runId).isGreaterThan(0);
        long jobId = (long) runner.execute(format("SELECT id FROM jobs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND run_id = %d LIMIT 1", runId)).getOnlyValue();
        assertThat(jobId).isGreaterThan(0);
        // TODO this is unreliable, as old job logs can get pruned by Github
        //long logLength = (long) runner.execute(format("SELECT length(contents) FROM job_logs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND job_id = %d", jobId)).getOnlyValue();
        //assertThat(logLength).isGreaterThan(0);

        assertQuery(format("SELECT owner FROM steps WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND run_id = %d LIMIT 1", runId),
                "VALUES ('nineinchnick')");
        // can't check results, since currently no jobs produce artifacts
        assertQuerySucceeds(format("SELECT owner FROM artifacts WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND run_id = %d", runId));
        // TODO this doesn't work with the default token available in GHA
        //assertQuery("SELECT * FROM runners WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        // TODO this require admin rights
        //assertQuery("SELECT * FROM runners WHERE org = 'trinodb'");
        assertQuerySucceeds("SELECT * FROM check_suites WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND ref = '5e53296c8f8124168d1a9e37fc310e9c517d3ec5'");
        assertQuerySucceeds("SELECT * FROM check_runs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND ref = '5e53296c8f8124168d1a9e37fc310e9c517d3ec5'");
        assertQuerySucceeds("SELECT * FROM check_run_annotations WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND check_run_id = 1");
    }

    @Test
    public void selectJoinDynamicFilter()
    {
        assertQuery("WITH " +
                        "r AS (SELECT * FROM repos WHERE owner_login = 'nineinchnick' AND name IN ('trino-git', 'trino-rest')) " +
                        "SELECT count(*) > 0 " +
                        "FROM r " +
                        "JOIN workflows w ON w.owner = r.owner_login AND w.repo = r.name",
                "VALUES (true)");
        assertQuery("SELECT count(*) > 0 " +
                        "FROM workflows w " +
                        "JOIN runs r ON r.workflow_id = w.id " +
                        "WHERE w.owner = 'nineinchnick' AND w.repo = 'trino-rest' " +
                        "AND r.owner = 'nineinchnick' AND r.repo = 'trino-rest'",
                "VALUES (true)");
        // TODO this is not reliable, because table stats don't include constraints
        /*
        assertQuery("WITH " +
                        "r AS (SELECT * FROM runs WHERE owner = 'nineinchnick' AND repo = 'trino-rest' LIMIT 5) " +
                        "SELECT count(*) > 0 " +
                        "FROM r " +
                        "JOIN jobs j ON j.run_id = r.id " +
                        "WHERE j.owner = 'nineinchnick' AND j.repo = 'trino-rest'",
                "VALUES (true)");
         */
    }

    @Test
    public void selectMissingRequired()
    {
        assertQueryFails("SELECT * FROM orgs", "Missing required constraint for orgs.login");
        assertQueryFails("SELECT * FROM users", "Missing required constraint for users.login");
        assertQueryFails("SELECT * FROM repos", "Missing required constraint for repos.owner_login");
        assertQueryFails("SELECT * FROM members", "Missing required constraint for members.org");
        assertQueryFails("SELECT * FROM teams", "Missing required constraint for teams.org");
        assertQueryFails("SELECT * FROM commits", "Missing required constraint for commits.owner");
        assertQueryFails("SELECT * FROM issues", "Missing required constraint for issues.owner");
        assertQueryFails("SELECT * FROM issue_comments", "Missing required constraint for issue_comments.owner");
        assertQueryFails("SELECT * FROM pulls", "Missing required constraint for pulls.owner");
        assertQueryFails("SELECT * FROM pull_commits", "Missing required constraint for pull_commits.owner");
        assertQueryFails("SELECT * FROM pull_stats", "Missing required constraint for pull_stats.owner");
        assertQueryFails("SELECT * FROM reviews", "Missing required constraint for reviews.owner");
        assertQueryFails("SELECT * FROM review_comments", "Missing required constraint for review_comments.owner");
        assertQueryFails("SELECT * FROM workflows", "Missing required constraint for workflows.owner");
        assertQueryFails("SELECT * FROM runs", "Missing required constraint for runs.owner");
        assertQueryFails("SELECT * FROM jobs", "Missing required constraint for jobs.owner");
        assertQueryFails("SELECT * FROM job_logs", "Missing required constraint for job_logs.owner");
        assertQueryFails("SELECT * FROM steps", "Missing required constraint for steps.owner");
        assertQueryFails("SELECT * FROM artifacts", "Missing required constraint for artifacts.owner");
        assertQueryFails("SELECT * FROM runners", "Missing required constraint for runners.org");
        assertQueryFails("SELECT * FROM check_suites", "Missing required constraint for check_suites.owner");
        assertQueryFails("SELECT * FROM check_runs", "Missing required constraint for check_runs.owner");
        assertQueryFails("SELECT * FROM check_run_annotations", "Missing required constraint for check_run_annotations.owner");
    }

    @Test
    public void selectFromFunction()
    {
        assertQuerySucceeds("SELECT org('trinodb')");
        assertQuerySucceeds("SELECT * FROM unnest(orgs(1))");
        assertQuerySucceeds("SELECT user('nineinchnick')");
        assertQuerySucceeds("SELECT * FROM unnest(users(1))");
        assertQuerySucceeds("SELECT * FROM unnest(user_repos('nineinchnick'))");
        assertQuerySucceeds("SELECT * FROM unnest(org_repos('trinodb'))");
        assertQuerySucceeds("SELECT * FROM unnest(org_members('trinodb'))");
        // TODO this doesn't work with the default token available in GHA
        //assertQuerySucceeds("SELECT * FROM unnest(teams('trinodb'))");
        //assertQuerySucceeds("SELECT * FROM unnest(team_members('trinodb', 'maintainers'))");
        assertQuerySucceeds("SELECT * FROM unnest(commits('nineinchnick', 'trino-rest', 1, timestamp '1970-01-01 00:00:00'))");
        assertQuerySucceeds("SELECT * FROM unnest(commits('nineinchnick', 'trino-rest', 1, timestamp '1970-01-01 00:00:00', 'master'))");
        assertQuerySucceeds("SELECT * FROM unnest(repos(1))");
        assertQuerySucceeds("SELECT * FROM unnest(issues('nineinchnick', 'trino-rest', 1, timestamp '1970-01-01 00:00:00'))");
        assertQuerySucceeds("SELECT * FROM unnest(issue_comments('nineinchnick', 'trino-rest', 1, timestamp '1970-01-01 00:00:00'))");
        assertQuerySucceeds("SELECT * FROM unnest(pulls('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(pull_commits('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT pull_stats('nineinchnick', 'trino-rest', 1)");
        assertQuerySucceeds("SELECT * FROM unnest(reviews('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(review_comments('nineinchnick', 'trino-rest', 1, timestamp '1970-01-01 00:00:00'))");
        assertQuerySucceeds("SELECT * FROM unnest(workflows('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(runs('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(jobs('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(steps('nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(artifacts('nineinchnick', 'trino-rest', 1))");
        // TODO this doesn't work with the default token available in GHA
        //assertQuerySucceeds("SELECT * FROM unnest(runners('nineinchnick', 'trino-rest', 1))");
        // TODO this requires admin rights
        //assertQuerySucceeds("SELECT * FROM unnest(org_runners('trinodb', 1))");
        // TODO figure out why this requires special permissions
        //assertQuerySucceeds("SELECT * FROM unnest(job_logs('nineinchnick', 'trino-rest', 1))");
    }
}

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
        assertQuery("SHOW TABLES FROM github.default", "VALUES 'orgs', 'users', 'repos', 'issues', 'issue_comments', 'pulls', 'pull_commits', 'reviews', 'review_comments', 'runs', 'jobs', 'steps'");
    }

    @Test
    public void selectFromTable()
    {
        assertQuerySucceeds("SELECT * FROM orgs WHERE login = 'trinodb'");
        assertQuerySucceeds("SELECT * FROM users WHERE login = 'nineinchnick'");
        assertQuerySucceeds("SELECT * FROM repos WHERE owner_login = 'nineinchnick'");
        assertQuerySucceeds("SELECT * FROM issues WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM issue_comments WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM pulls WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM pull_commits WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND pull_number = 1");
        assertQuerySucceeds("SELECT * FROM reviews WHERE owner = 'nineinchnick' AND repo = 'trino-rest' AND pull_number = 1");
        assertQuerySucceeds("SELECT * FROM review_comments WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM runs WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM jobs WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
        assertQuerySucceeds("SELECT * FROM steps WHERE owner = 'nineinchnick' AND repo = 'trino-rest'");
    }

    @Test(invocationCount = 100)
    public void selectMissingRequired()
    {
        assertQueryFails("SELECT * FROM orgs", "Missing required constraint for login");
        assertQueryFails("SELECT * FROM users", "Missing required constraint for login");
        assertQueryFails("SELECT * FROM repos", "Missing required constraint for owner_login");
        assertQueryFails("SELECT * FROM issues", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM issue_comments", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM pulls", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM pull_commits", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM reviews", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM review_comments", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM runs", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM jobs", "Missing required constraint for owner");
        assertQueryFails("SELECT * FROM steps", "Missing required constraint for owner");
    }

    @Test
    public void selectFromUser()
    {
        computeActual("SELECT user('invalid.token', 'nineinchnick')");
    }


    @Test(invocationCount = 100)
    public void selectFromFunction()
    {
        assertQuerySucceeds("SELECT * FROM unnest(org('invalid.token', 'trinodb'))");
        assertQuerySucceeds("SELECT * FROM unnest(orgs('invalid.token', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(user('invalid.token', 'nineinchnick'))");
        assertQuerySucceeds("SELECT * FROM unnest(users('invalid.token', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(user_repos('invalid.token', 'nineinchnick'))");
        assertQuerySucceeds("SELECT * FROM unnest(org_repos('invalid.token', 'trinodb'))");
        assertQuerySucceeds("SELECT * FROM unnest(repos('invalid.token', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(issues('invalid.token', 'nineinchnick', 'trino-rest', 1, '1970-01-01T00:00:00Z'))");
        assertQuerySucceeds("SELECT * FROM unnest(issue_comments('invalid.token', 'nineinchnick', 'trino-rest', 1, '1970-01-01T00:00:00Z'))");
        assertQuerySucceeds("SELECT * FROM unnest(pulls('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(pull_commits('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(reviews('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(review_comments('invalid.token', 'nineinchnick', 'trino-rest', 1, 1))");
        assertQuerySucceeds("SELECT * FROM unnest(runs('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(jobs('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(steps('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(job_logs('invalid.token', 'nineinchnick', 'trino-rest', 1))");
        assertQuerySucceeds("SELECT * FROM unnest(artifacts('invalid.token', 'nineinchnick', 'trino-rest', 1))");
    }
}

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

import io.trino.spi.connector.SchemaTableName;
import pl.net.was.rest.RestTableHandle;

public enum GithubTable
{
    ORGS("orgs"),
    USERS("users"),
    REPOS("repos"),
    MEMBERS("members"),
    TEAMS("teams"),
    COMMITS("commits"),
    PULLS("pulls"),
    PULL_COMMITS("pull_commits"),
    PULL_STATS("pull_stats"),
    REVIEWS("reviews"),
    REVIEW_COMMENTS("review_comments"),
    ISSUES("issues"),
    ISSUE_COMMENTS("issue_comments"),
    WORKFLOWS("workflows"),
    RUNS("runs"),
    JOBS("jobs"),
    JOB_LOGS("job_logs"),
    STEPS("steps"),
    ARTIFACTS("artifacts"),
    RUNNERS("runners"),
    CHECK_SUITES("check_suites"),
    CHECK_RUNS("check_runs"),
    CHECK_RUN_ANNOTATIONS("check_run_annotations");

    private final String name;

    GithubTable(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public static GithubTable valueOf(RestTableHandle table)
    {
        return valueOf(table.getSchemaTableName());
    }

    public static GithubTable valueOf(SchemaTableName schemaTable)
    {
        return valueOf(schemaTable.getTableName().toUpperCase());
    }
}

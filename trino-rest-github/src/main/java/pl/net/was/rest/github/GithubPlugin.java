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
import com.google.common.collect.ImmutableSet;
import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;
import pl.net.was.rest.RestConnectorFactory;
import pl.net.was.rest.github.function.Artifacts;
import pl.net.was.rest.github.function.IssueComments;
import pl.net.was.rest.github.function.Issues;
import pl.net.was.rest.github.function.JobLogs;
import pl.net.was.rest.github.function.Jobs;
import pl.net.was.rest.github.function.Org;
import pl.net.was.rest.github.function.OrgMembers;
import pl.net.was.rest.github.function.OrgRepos;
import pl.net.was.rest.github.function.OrgRunners;
import pl.net.was.rest.github.function.Orgs;
import pl.net.was.rest.github.function.PullCommits;
import pl.net.was.rest.github.function.PullStats;
import pl.net.was.rest.github.function.Pulls;
import pl.net.was.rest.github.function.RepoCommits;
import pl.net.was.rest.github.function.Repos;
import pl.net.was.rest.github.function.ReviewComments;
import pl.net.was.rest.github.function.Reviews;
import pl.net.was.rest.github.function.Runners;
import pl.net.was.rest.github.function.Runs;
import pl.net.was.rest.github.function.Steps;
import pl.net.was.rest.github.function.TeamMembers;
import pl.net.was.rest.github.function.Teams;
import pl.net.was.rest.github.function.UserGetter;
import pl.net.was.rest.github.function.UserRepos;
import pl.net.was.rest.github.function.Users;
import pl.net.was.rest.github.function.Workflows;

import java.util.Set;

public class GithubPlugin
        implements Plugin
{
    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new RestConnectorFactory(
                "github",
                GithubRest.class));
    }

    @Override
    public Set<Class<?>> getFunctions()
    {
        return ImmutableSet.<Class<?>>builder()
                .add(Org.class)
                .add(Orgs.class)
                .add(OrgRepos.class)
                .add(OrgMembers.class)
                .add(TeamMembers.class)
                .add(Teams.class)
                .add(Users.class)
                .add(UserGetter.class)
                .add(UserRepos.class)
                .add(Repos.class)
                .add(RepoCommits.class)
                .add(Pulls.class)
                .add(PullCommits.class)
                .add(PullStats.class)
                .add(Reviews.class)
                .add(ReviewComments.class)
                .add(IssueComments.class)
                .add(Issues.class)
                .add(JobLogs.class)
                .add(Jobs.class)
                .add(Workflows.class)
                .add(Runs.class)
                .add(Steps.class)
                .add(Artifacts.class)
                .add(Runners.class)
                .add(OrgRunners.class)
                .build();
    }
}

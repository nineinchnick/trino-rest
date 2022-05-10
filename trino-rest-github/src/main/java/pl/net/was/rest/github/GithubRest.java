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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.airlift.slice.Slice;
import io.trino.spi.HostAddress;
import io.trino.spi.Node;
import io.trino.spi.NodeManager;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;
import io.trino.spi.connector.LimitApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.connector.SortItem;
import io.trino.spi.connector.SortOrder;
import io.trino.spi.connector.TopNApplicationResult;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.statistics.ColumnStatistics;
import io.trino.spi.statistics.Estimate;
import io.trino.spi.statistics.TableStatistics;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FixedWidthType;
import io.trino.spi.type.MapType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.TimestampWithTimeZoneType;
import io.trino.spi.type.TypeOperators;
import okhttp3.ResponseBody;
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestConfig;
import pl.net.was.rest.RestConnectorSplit;
import pl.net.was.rest.RestModule;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.filter.FilterApplier;
import pl.net.was.rest.filter.FilterType;
import pl.net.was.rest.github.filter.ArtifactFilter;
import pl.net.was.rest.github.filter.CheckRunAnnotationFilter;
import pl.net.was.rest.github.filter.CheckRunFilter;
import pl.net.was.rest.github.filter.CheckSuiteFilter;
import pl.net.was.rest.github.filter.IssueCommentFilter;
import pl.net.was.rest.github.filter.IssueFilter;
import pl.net.was.rest.github.filter.JobFilter;
import pl.net.was.rest.github.filter.JobLogFilter;
import pl.net.was.rest.github.filter.MemberFilter;
import pl.net.was.rest.github.filter.OrgFilter;
import pl.net.was.rest.github.filter.PullCommitFilter;
import pl.net.was.rest.github.filter.PullFilter;
import pl.net.was.rest.github.filter.PullStatisticsFilter;
import pl.net.was.rest.github.filter.RepoCommitFilter;
import pl.net.was.rest.github.filter.RepoFilter;
import pl.net.was.rest.github.filter.ReviewCommentFilter;
import pl.net.was.rest.github.filter.ReviewFilter;
import pl.net.was.rest.github.filter.RunFilter;
import pl.net.was.rest.github.filter.RunnerFilter;
import pl.net.was.rest.github.filter.StepFilter;
import pl.net.was.rest.github.filter.TeamFilter;
import pl.net.was.rest.github.filter.UserFilter;
import pl.net.was.rest.github.filter.WorkflowFilter;
import pl.net.was.rest.github.function.JobLogs;
import pl.net.was.rest.github.model.Artifact;
import pl.net.was.rest.github.model.ArtifactsList;
import pl.net.was.rest.github.model.Envelope;
import pl.net.was.rest.github.model.IssueComment;
import pl.net.was.rest.github.model.Job;
import pl.net.was.rest.github.model.JobsList;
import pl.net.was.rest.github.model.Member;
import pl.net.was.rest.github.model.Organization;
import pl.net.was.rest.github.model.Repository;
import pl.net.was.rest.github.model.ReviewComment;
import pl.net.was.rest.github.model.RunnersList;
import pl.net.was.rest.github.model.Step;
import pl.net.was.rest.github.model.User;
import pl.net.was.rest.github.service.GithubService;
import retrofit2.Call;
import retrofit2.Response;

import javax.inject.Inject;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Verify.verify;
import static io.trino.spi.StandardErrorCode.INVALID_ORDER_BY;
import static io.trino.spi.StandardErrorCode.INVALID_ROW_FILTER;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static pl.net.was.rest.github.function.Artifacts.download;

public class GithubRest
        implements Rest
{
    // Estimate ratio of one to many relationships
    private static final double RELATIONSHIPS_RATIO = 0.01;
    // Estimate ratio of issue comments to issues
    private static final double ISSUE_COMMENTS_RATIO = 10;
    // Estimate number of steps per job
    private static final long STEPS_PER_JOB = 5;
    Logger log = Logger.getLogger(GithubRest.class.getName());
    public static final String SCHEMA_NAME = "default";

    private static final int PER_PAGE = 100;

    private static int minSplits;
    private static List<GithubTable> minSplitTables;
    private static String token;
    private static GithubService service;
    private static long maxBinaryDownloadSizeBytes;

    public static final Map<GithubTable, List<ColumnMetadata>> columns = new ImmutableMap.Builder<GithubTable, List<ColumnMetadata>>()
            .put(GithubTable.ORGS, ImmutableList.of(
                    new ColumnMetadata("login", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("description", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("company", VARCHAR),
                    new ColumnMetadata("blog", VARCHAR),
                    new ColumnMetadata("location", VARCHAR),
                    new ColumnMetadata("email", VARCHAR),
                    new ColumnMetadata("twitter_username", VARCHAR),
                    new ColumnMetadata("is_verified", BOOLEAN),
                    new ColumnMetadata("has_organization_projects", BOOLEAN),
                    new ColumnMetadata("has_repository_projects", BOOLEAN),
                    new ColumnMetadata("public_repos", BIGINT),
                    new ColumnMetadata("public_gists", BIGINT),
                    new ColumnMetadata("followers", BIGINT),
                    new ColumnMetadata("following", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("total_private_repos", BIGINT),
                    new ColumnMetadata("owned_private_repos", BIGINT),
                    new ColumnMetadata("private_gists", BIGINT),
                    new ColumnMetadata("disk_usage", BIGINT),
                    new ColumnMetadata("collaborators", BIGINT),
                    new ColumnMetadata("billing_email", VARCHAR),
                    new ColumnMetadata("default_repository_permission", VARCHAR),
                    new ColumnMetadata("members_can_create_repositories", BOOLEAN),
                    new ColumnMetadata("two_factor_requirement_enabled", BOOLEAN),
                    new ColumnMetadata("members_allowed_repository_creation_type", VARCHAR),
                    new ColumnMetadata("members_can_create_public_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_private_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_internal_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_pages", BOOLEAN),
                    new ColumnMetadata("members_can_create_public_pages", BOOLEAN),
                    new ColumnMetadata("members_can_create_private_pages", BOOLEAN),
                    new ColumnMetadata("members_can_fork_private_repositories", BOOLEAN)))
            .put(GithubTable.USERS, ImmutableList.of(
                    new ColumnMetadata("login", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("avatar_url", VARCHAR),
                    new ColumnMetadata("gravatar_id", VARCHAR),
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("site_admin", BOOLEAN),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("company", VARCHAR),
                    new ColumnMetadata("blog", VARCHAR),
                    new ColumnMetadata("location", VARCHAR),
                    new ColumnMetadata("email", VARCHAR),
                    new ColumnMetadata("hireable", BOOLEAN),
                    new ColumnMetadata("bio", VARCHAR),
                    new ColumnMetadata("twitter_username", VARCHAR),
                    new ColumnMetadata("public_repos", BIGINT),
                    new ColumnMetadata("public_gists", BIGINT),
                    new ColumnMetadata("followers", BIGINT),
                    new ColumnMetadata("following", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put(GithubTable.REPOS, ImmutableList.of(
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("full_name", VARCHAR),
                    new ColumnMetadata("owner_id", BIGINT),
                    new ColumnMetadata("owner_login", VARCHAR),
                    new ColumnMetadata("private", BOOLEAN),
                    new ColumnMetadata("description", VARCHAR),
                    new ColumnMetadata("fork", BOOLEAN),
                    new ColumnMetadata("homepage", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("forks_count", BIGINT),
                    new ColumnMetadata("stargazers_count", BIGINT),
                    new ColumnMetadata("watchers_count", BIGINT),
                    new ColumnMetadata("size", BIGINT),
                    new ColumnMetadata("default_branch", VARCHAR),
                    new ColumnMetadata("open_issues_count", BIGINT),
                    new ColumnMetadata("is_template", BOOLEAN),
                    new ColumnMetadata("topics", new ArrayType(VARCHAR)),
                    new ColumnMetadata("has_issues", BOOLEAN),
                    new ColumnMetadata("has_projects", BOOLEAN),
                    new ColumnMetadata("has_wiki", BOOLEAN),
                    new ColumnMetadata("has_pages", BOOLEAN),
                    new ColumnMetadata("has_downloads", BOOLEAN),
                    new ColumnMetadata("archived", BOOLEAN),
                    new ColumnMetadata("disabled", BOOLEAN),
                    new ColumnMetadata("visibility", VARCHAR),
                    new ColumnMetadata("pushed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("permissions", new MapType(VARCHAR, BOOLEAN, new TypeOperators()))))
            .put(GithubTable.MEMBERS, ImmutableList.of(
                    new ColumnMetadata("org", VARCHAR),
                    new ColumnMetadata("team_slug", VARCHAR),
                    new ColumnMetadata("login", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("avatar_url", VARCHAR),
                    new ColumnMetadata("gravatar_id", VARCHAR),
                    new ColumnMetadata("type", VARCHAR),
                    new ColumnMetadata("site_admin", BOOLEAN)))
            .put(GithubTable.TEAMS, ImmutableList.of(
                    new ColumnMetadata("org", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("slug", VARCHAR),
                    new ColumnMetadata("description", VARCHAR),
                    new ColumnMetadata("privacy", VARCHAR),
                    new ColumnMetadata("permission", VARCHAR),
                    new ColumnMetadata("members_url", VARCHAR),
                    new ColumnMetadata("repositories_url", VARCHAR),
                    new ColumnMetadata("parent_id", BIGINT),
                    new ColumnMetadata("parent_slug", VARCHAR)))
            .put(GithubTable.COMMITS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("sha", VARCHAR),
                    new ColumnMetadata("commit_message", VARCHAR),
                    new ColumnMetadata("commit_tree_sha", VARCHAR),
                    new ColumnMetadata("commit_comment_count", BIGINT),
                    new ColumnMetadata("commit_verified", BOOLEAN),
                    new ColumnMetadata("commit_verification_reason", VARCHAR),
                    new ColumnMetadata("author_name", VARCHAR),
                    new ColumnMetadata("author_email", VARCHAR),
                    new ColumnMetadata("author_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_id", BIGINT),
                    new ColumnMetadata("author_login", VARCHAR),
                    new ColumnMetadata("committer_name", VARCHAR),
                    new ColumnMetadata("committer_email", VARCHAR),
                    new ColumnMetadata("committer_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("committer_id", BIGINT),
                    new ColumnMetadata("committer_login", VARCHAR),
                    new ColumnMetadata("parent_shas", new ArrayType(VARCHAR))))
            .put(GithubTable.PULLS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("diff_url", VARCHAR),
                    new ColumnMetadata("patch_url", VARCHAR),
                    new ColumnMetadata("issue_url", VARCHAR),
                    new ColumnMetadata("commits_url", VARCHAR),
                    new ColumnMetadata("review_comments_url", VARCHAR),
                    new ColumnMetadata("review_comment_url", VARCHAR),
                    new ColumnMetadata("comments_url", VARCHAR),
                    new ColumnMetadata("statuses_url", VARCHAR),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("state", VARCHAR),
                    new ColumnMetadata("locked", BOOLEAN),
                    new ColumnMetadata("title", VARCHAR),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", VARCHAR),
                    new ColumnMetadata("body", VARCHAR),
                    new ColumnMetadata("label_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("label_names", new ArrayType(VARCHAR)),
                    new ColumnMetadata("milestone_id", BIGINT),
                    new ColumnMetadata("milestone_title", VARCHAR),
                    new ColumnMetadata("active_lock_reason", VARCHAR),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("closed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("merged_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("merge_commit_sha", VARCHAR),
                    new ColumnMetadata("assignee_id", BIGINT),
                    new ColumnMetadata("assignee_login", VARCHAR),
                    new ColumnMetadata("requested_reviewer_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("requested_reviewer_logins", new ArrayType(VARCHAR)),
                    new ColumnMetadata("head_ref", VARCHAR),
                    new ColumnMetadata("head_sha", VARCHAR),
                    new ColumnMetadata("base_ref", VARCHAR),
                    new ColumnMetadata("base_sha", VARCHAR),
                    new ColumnMetadata("author_association", VARCHAR),
                    new ColumnMetadata("draft", BOOLEAN),
                    new ColumnMetadata("auto_merge", BOOLEAN)))
            .put(GithubTable.PULL_COMMITS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("sha", VARCHAR),
                    // this column is filled in from request params, it is not returned by the api
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("commit_message", VARCHAR),
                    new ColumnMetadata("commit_tree_sha", VARCHAR),
                    new ColumnMetadata("commit_comment_count", BIGINT),
                    new ColumnMetadata("commit_verified", BOOLEAN),
                    new ColumnMetadata("commit_verification_reason", VARCHAR),
                    new ColumnMetadata("author_name", VARCHAR),
                    new ColumnMetadata("author_email", VARCHAR),
                    new ColumnMetadata("author_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_id", BIGINT),
                    new ColumnMetadata("author_login", VARCHAR),
                    new ColumnMetadata("committer_name", VARCHAR),
                    new ColumnMetadata("committer_email", VARCHAR),
                    new ColumnMetadata("committer_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("committer_id", BIGINT),
                    new ColumnMetadata("committer_login", VARCHAR),
                    new ColumnMetadata("parent_shas", new ArrayType(VARCHAR))))
            .put(GithubTable.PULL_STATS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("comments", BIGINT),
                    new ColumnMetadata("review_comments", BIGINT),
                    new ColumnMetadata("commits", BIGINT),
                    new ColumnMetadata("additions", BIGINT),
                    new ColumnMetadata("deletions", BIGINT),
                    new ColumnMetadata("changed_files", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("closed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("merged_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put(GithubTable.REVIEWS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    // this column is filled in from request params, it is not returned by the api
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", VARCHAR),
                    new ColumnMetadata("body", VARCHAR),
                    new ColumnMetadata("state", VARCHAR),
                    new ColumnMetadata("submitted_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("commit_id", VARCHAR),
                    new ColumnMetadata("author_association", VARCHAR)))
            .put(GithubTable.REVIEW_COMMENTS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    // this column is filled in from request params, it is not returned by the api
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("pull_request_url", VARCHAR),
                    new ColumnMetadata("pull_request_review_id", BIGINT),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("diff_hunk", VARCHAR),
                    new ColumnMetadata("path", VARCHAR),
                    new ColumnMetadata("position", BIGINT),
                    new ColumnMetadata("original_position", BIGINT),
                    new ColumnMetadata("commit_id", VARCHAR),
                    new ColumnMetadata("original_commit_id", VARCHAR),
                    new ColumnMetadata("in_reply_to_id", BIGINT),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", VARCHAR),
                    new ColumnMetadata("body", VARCHAR),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_association", VARCHAR),
                    new ColumnMetadata("start_line", BIGINT),
                    new ColumnMetadata("original_start_line", BIGINT),
                    new ColumnMetadata("start_side", VARCHAR),
                    new ColumnMetadata("line", BIGINT),
                    new ColumnMetadata("original_line", BIGINT),
                    new ColumnMetadata("side", VARCHAR)))
            .put(GithubTable.ISSUES, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("repository_url", VARCHAR),
                    new ColumnMetadata("labels_url", VARCHAR),
                    new ColumnMetadata("comments_url", VARCHAR),
                    new ColumnMetadata("events_url", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("timeline_url", VARCHAR),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("state", VARCHAR),
                    new ColumnMetadata("title", VARCHAR),
                    new ColumnMetadata("body", VARCHAR),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", VARCHAR),
                    new ColumnMetadata("label_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("label_names", new ArrayType(VARCHAR)),
                    new ColumnMetadata("assignee_id", BIGINT),
                    new ColumnMetadata("assignee_login", VARCHAR),
                    new ColumnMetadata("milestone_id", BIGINT),
                    new ColumnMetadata("milestone_title", VARCHAR),
                    new ColumnMetadata("locked", BOOLEAN),
                    new ColumnMetadata("active_lock_reason", VARCHAR),
                    new ColumnMetadata("comments", BIGINT),
                    new ColumnMetadata("closed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("closed_by_id", BIGINT),
                    new ColumnMetadata("closed_by_login", VARCHAR),
                    new ColumnMetadata("author_association", VARCHAR),
                    new ColumnMetadata("draft", BOOLEAN),
                    new ColumnMetadata("reactions_url", VARCHAR),
                    new ColumnMetadata("reactions_total_count", INTEGER),
                    new ColumnMetadata("app_id", BIGINT),
                    new ColumnMetadata("app_slug", VARCHAR),
                    new ColumnMetadata("app_name", VARCHAR)))
            .put(GithubTable.ISSUE_COMMENTS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("issue_number", BIGINT),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("body", VARCHAR),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", VARCHAR),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("issue_url", VARCHAR),
                    new ColumnMetadata("author_association", VARCHAR),
                    new ColumnMetadata("reactions_url", VARCHAR),
                    new ColumnMetadata("reactions_total_count", INTEGER),
                    new ColumnMetadata("app_id", BIGINT),
                    new ColumnMetadata("app_slug", VARCHAR),
                    new ColumnMetadata("app_name", VARCHAR)))
            .put(GithubTable.WORKFLOWS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("path", VARCHAR),
                    new ColumnMetadata("state", VARCHAR),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("badge_url", VARCHAR)))
            .put(GithubTable.RUNS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("check_suite_id", BIGINT),
                    new ColumnMetadata("check_suite_node_id", VARCHAR),
                    new ColumnMetadata("head_branch", VARCHAR),
                    new ColumnMetadata("head_sha", VARCHAR),
                    new ColumnMetadata("run_number", BIGINT),
                    new ColumnMetadata("run_attempt", INTEGER),
                    new ColumnMetadata("event", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("conclusion", VARCHAR),
                    new ColumnMetadata("workflow_id", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("run_started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put(GithubTable.JOBS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("run_id", BIGINT),
                    new ColumnMetadata("run_attempt", INTEGER),
                    new ColumnMetadata("node_id", VARCHAR),
                    new ColumnMetadata("head_sha", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("conclusion", VARCHAR),
                    new ColumnMetadata("started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("completed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("steps_count", INTEGER)))
            .put(GithubTable.JOB_LOGS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("job_id", BIGINT),
                    new ColumnMetadata("size_in_bytes", BIGINT),
                    new ColumnMetadata("part_number", INTEGER),
                    new ColumnMetadata("contents", VARBINARY)))
            .put(GithubTable.STEPS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("run_id", BIGINT),
                    new ColumnMetadata("run_attempt", INTEGER),
                    new ColumnMetadata("job_id", BIGINT),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("conclusion", VARCHAR),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("completed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put(GithubTable.ARTIFACTS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("run_id", BIGINT),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("size_in_bytes", BIGINT),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("archive_download_url", VARCHAR),
                    new ColumnMetadata("expired", BOOLEAN),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("expires_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("filename", VARCHAR),
                    new ColumnMetadata("path", VARCHAR),
                    new ColumnMetadata("mimetype", VARCHAR),
                    new ColumnMetadata("file_size_in_bytes", BIGINT),
                    new ColumnMetadata("part_number", INTEGER),
                    new ColumnMetadata("contents", VARBINARY)))
            .put(GithubTable.RUNNERS, ImmutableList.of(
                    new ColumnMetadata("org", VARCHAR),
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("os", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("busy", BOOLEAN),
                    new ColumnMetadata("label_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("label_names", new ArrayType(VARCHAR))))
            .put(GithubTable.CHECK_SUITES, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("ref", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("head_branch", VARCHAR),
                    new ColumnMetadata("head_sha", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("conclusion", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("before", VARCHAR),
                    new ColumnMetadata("after", VARCHAR),
                    new ColumnMetadata("pull_request_numbers", new ArrayType(BIGINT)),
                    new ColumnMetadata("app_id", BIGINT),
                    new ColumnMetadata("app_slug", VARCHAR),
                    new ColumnMetadata("app_name", VARCHAR),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("latest_check_runs_count", BIGINT),
                    new ColumnMetadata("check_runs_url", VARCHAR)))
            .put(GithubTable.CHECK_RUNS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("ref", VARCHAR),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("head_sha", VARCHAR),
                    new ColumnMetadata("external_id", VARCHAR),
                    new ColumnMetadata("url", VARCHAR),
                    new ColumnMetadata("html_url", VARCHAR),
                    new ColumnMetadata("details_url", VARCHAR),
                    new ColumnMetadata("status", VARCHAR),
                    new ColumnMetadata("conclusion", VARCHAR),
                    new ColumnMetadata("started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("completed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("output_title", VARCHAR),
                    new ColumnMetadata("output_summary", VARCHAR),
                    new ColumnMetadata("output_text", VARCHAR),
                    new ColumnMetadata("annotations_count", BIGINT),
                    new ColumnMetadata("annotations_url", VARCHAR),
                    new ColumnMetadata("name", VARCHAR),
                    new ColumnMetadata("check_suite_id", BIGINT),
                    new ColumnMetadata("app_id", BIGINT),
                    new ColumnMetadata("app_slug", VARCHAR),
                    new ColumnMetadata("app_name", VARCHAR)))
            .put(GithubTable.CHECK_RUN_ANNOTATIONS, ImmutableList.of(
                    new ColumnMetadata("owner", VARCHAR),
                    new ColumnMetadata("repo", VARCHAR),
                    new ColumnMetadata("check_run_id", BIGINT),
                    new ColumnMetadata("path", VARCHAR),
                    new ColumnMetadata("start_line", INTEGER),
                    new ColumnMetadata("end_line", INTEGER),
                    new ColumnMetadata("start_column", INTEGER),
                    new ColumnMetadata("end_column", INTEGER),
                    new ColumnMetadata("annotation_level", VARCHAR),
                    new ColumnMetadata("title", VARCHAR),
                    new ColumnMetadata("message", VARCHAR),
                    new ColumnMetadata("raw_details", VARCHAR),
                    new ColumnMetadata("blob_href", VARCHAR)))
            .build();

    // These are only used by tableStatistics to estimate row counts, so they don't have to be complete
    public static final Map<GithubTable, Map<String, KeyType>> keyColumns = new ImmutableMap.Builder<GithubTable, Map<String, KeyType>>()
            .put(GithubTable.ISSUES, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "url", KeyType.PRIMARY_KEY,
                    "user_id", KeyType.FOREIGN_KEY))
            .put(GithubTable.ISSUE_COMMENTS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "issue_number", KeyType.FOREIGN_KEY,
                    "issue_url", KeyType.FOREIGN_KEY,
                    "user_id", KeyType.FOREIGN_KEY))
            .put(GithubTable.WORKFLOWS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "repo", KeyType.FOREIGN_KEY))
            .put(GithubTable.RUNS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "run_number", KeyType.FOREIGN_KEY,
                    "run_attempt", KeyType.FOREIGN_KEY))
            .put(GithubTable.JOBS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "run_id", KeyType.FOREIGN_KEY,
                    "run_attempt", KeyType.FOREIGN_KEY))
            .put(GithubTable.STEPS, ImmutableMap.of(
                    "job_id", KeyType.FOREIGN_KEY,
                    "run_id", KeyType.FOREIGN_KEY,
                    "run_attempt", KeyType.FOREIGN_KEY))
            .put(GithubTable.ARTIFACTS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "run_id", KeyType.FOREIGN_KEY))
            .put(GithubTable.RUNNERS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY))
            .put(GithubTable.CHECK_SUITES, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "ref", KeyType.FOREIGN_KEY))
            .put(GithubTable.CHECK_RUNS, ImmutableMap.of(
                    "id", KeyType.PRIMARY_KEY,
                    "ref", KeyType.FOREIGN_KEY,
                    "check_suite_id", KeyType.FOREIGN_KEY))
            .put(GithubTable.CHECK_RUN_ANNOTATIONS, ImmutableMap.of(
                    "check_run_id", KeyType.FOREIGN_KEY))
            .build();

    private enum KeyType
    {
        PRIMARY_KEY,
        FOREIGN_KEY
    }

    private final Map<GithubTable, Function<RestTableHandle, Iterable<List<?>>>> rowGetters = new ImmutableMap.Builder<GithubTable, Function<RestTableHandle, Iterable<List<?>>>>()
            .put(GithubTable.ORGS, this::getOrgs)
            .put(GithubTable.USERS, this::getUsers)
            .put(GithubTable.REPOS, this::getRepos)
            .put(GithubTable.MEMBERS, this::getMembers)
            .put(GithubTable.TEAMS, this::getTeams)
            .put(GithubTable.COMMITS, this::getRepoCommits)
            .put(GithubTable.PULLS, this::getPulls)
            .put(GithubTable.PULL_COMMITS, this::getPullCommits)
            .put(GithubTable.PULL_STATS, this::getPullStats)
            .put(GithubTable.REVIEWS, this::getReviews)
            .put(GithubTable.REVIEW_COMMENTS, this::getReviewComments)
            .put(GithubTable.ISSUES, this::getIssues)
            .put(GithubTable.ISSUE_COMMENTS, this::getIssueComments)
            .put(GithubTable.WORKFLOWS, this::getWorkflows)
            .put(GithubTable.RUNS, this::getRuns)
            .put(GithubTable.JOBS, this::getJobs)
            .put(GithubTable.JOB_LOGS, this::getJobLogs)
            .put(GithubTable.STEPS, this::getSteps)
            .put(GithubTable.ARTIFACTS, this::getArtifacts)
            .put(GithubTable.RUNNERS, this::getRunners)
            .put(GithubTable.CHECK_SUITES, this::getCheckSuites)
            .put(GithubTable.CHECK_RUNS, this::getCheckRuns)
            .put(GithubTable.CHECK_RUN_ANNOTATIONS, this::getCheckRunAnnotations)
            .build();

    private final Map<GithubTable, Function<RestTableHandle, Long>> rowCountGetters = new ImmutableMap.Builder<GithubTable, Function<RestTableHandle, Long>>()
            .put(GithubTable.WORKFLOWS, this::getWorkflowsCount)
            .put(GithubTable.RUNS, this::getRunsCount)
            .put(GithubTable.JOBS, this::getJobsCount)
            .put(GithubTable.STEPS, this::getStepsCount)
            // artifacts are in an envelope, but since artifact contents are denormalized into separate rows so there's no way to know how many actual rows there are
            .put(GithubTable.RUNNERS, this::getRunnersCount)
            .put(GithubTable.CHECK_SUITES, this::getCheckSuitesCount)
            .put(GithubTable.CHECK_RUNS, this::getCheckRunsCount)
            .build();

    // The first sortItem is the default
    private final Map<GithubTable, List<SortItem>> supportedTableSort = new ImmutableMap.Builder<GithubTable, List<SortItem>>()
            .put(GithubTable.ORGS, ImmutableList.of(
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.REPOS, ImmutableList.of(
                    new SortItem("full_name", SortOrder.ASC_NULLS_LAST),
                    new SortItem("full_name", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("pushed_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("pushed_at", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.COMMITS, ImmutableList.of(
                    new SortItem("committer_date", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.PULLS, ImmutableList.of(
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.PULL_COMMITS, ImmutableList.of(
                    new SortItem("committer_date", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.REVIEWS, ImmutableList.of(
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.REVIEW_COMMENTS, ImmutableList.of(
                    new SortItem("id", SortOrder.ASC_NULLS_LAST),
                    new SortItem("id", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.ISSUES, ImmutableList.of(
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.DESC_NULLS_LAST),
                    new SortItem("updated_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("comments", SortOrder.DESC_NULLS_LAST),
                    new SortItem("comments", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.ISSUE_COMMENTS, ImmutableList.of(
                    new SortItem("id", SortOrder.ASC_NULLS_LAST),
                    new SortItem("id", SortOrder.DESC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.ASC_NULLS_LAST),
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.WORKFLOWS, ImmutableList.of(
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.RUNS, ImmutableList.of(
                    new SortItem("created_at", SortOrder.DESC_NULLS_LAST)))
            .put(GithubTable.JOBS, ImmutableList.of(
                    new SortItem("started_at", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.STEPS, ImmutableList.of(
                    new SortItem("job_id", SortOrder.ASC_NULLS_LAST)))
            .put(GithubTable.ARTIFACTS, ImmutableList.of(
                    new SortItem("id", SortOrder.ASC_NULLS_LAST)))
            .build();

    // TODO add tests that would verify this using getSqlType(), print the expected string so its easy to copy&paste
    // TODO consider moving to a separate class
    public static final String ORG_ROW_TYPE = "row(" +
            "login varchar, " +
            "id bigint, " +
            "description varchar, " +
            "name varchar, " +
            "company varchar, " +
            "blog varchar, " +
            "location varchar, " +
            "email varchar, " +
            "twitter_username varchar, " +
            "is_verified boolean, " +
            "has_organization_projects boolean, " +
            "has_repository_projects boolean, " +
            "public_repos bigint, " +
            "public_gists bigint, " +
            "followers bigint, " +
            "following bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "type varchar, " +
            "total_private_repos bigint, " +
            "owned_private_repos bigint, " +
            "private_gists bigint, " +
            "disk_usage bigint, " +
            "collaborators bigint, " +
            "billing_email varchar, " +
            "default_repository_permission varchar, " +
            "members_can_create_repositories boolean, " +
            "two_factor_requirement_enabled boolean, " +
            "members_allowed_repository_creation_type varchar, " +
            "members_can_create_public_repositories boolean, " +
            "members_can_create_private_repositories boolean, " +
            "members_can_create_internal_repositories boolean, " +
            "members_can_create_pages boolean, " +
            "members_can_create_public_pages boolean, " +
            "members_can_create_private_pages boolean, " +
            "members_can_fork_private_repositories boolean" +
            ")";

    public static final String ORGS_TABLE_TYPE = "array(" + ORG_ROW_TYPE + ")";

    public static final String USER_ROW_TYPE = "row(" +
            "login varchar, " +
            "id bigint, " +
            "avatar_url varchar, " +
            "gravatar_id varchar, " +
            "type varchar, " +
            "site_admin boolean, " +
            "name varchar, " +
            "company varchar, " +
            "blog varchar, " +
            "location varchar, " +
            "email varchar, " +
            "hireable boolean, " +
            "bio varchar, " +
            "twitter_username varchar, " +
            "public_repos bigint, " +
            "public_gists bigint, " +
            "followers bigint, " +
            "following bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone" +
            ")";

    public static final String USERS_TABLE_TYPE = "array(" + USER_ROW_TYPE + ")";

    public static final String REPOS_TABLE_TYPE = "array(row(" +
            "id bigint, " +
            "name varchar, " +
            "full_name varchar, " +
            "owner_id bigint, " +
            "owner_login varchar, " +
            "private boolean, " +
            "description varchar, " +
            "fork boolean, " +
            "homepage varchar, " +
            "url varchar, " +
            "forks_count bigint, " +
            "stargazers_count bigint, " +
            "watchers_count bigint, " +
            "size bigint, " +
            "default_branch varchar, " +
            "open_issues_count bigint, " +
            "is_template boolean, " +
            "topics array(varchar), " +
            "has_issues boolean, " +
            "has_projects boolean, " +
            "has_wiki boolean, " +
            "has_pages boolean, " +
            "has_downloads boolean, " +
            "archived boolean, " +
            "disabled boolean, " +
            "visibility varchar, " +
            "pushed_at timestamp(3) with time zone, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "permissions map(varchar, boolean)" +
            "))";

    public static final String MEMBER_ROW_TYPE = "row(" +
            "org varchar, " +
            "team_slug varchar, " +
            "login varchar, " +
            "id bigint, " +
            "avatar_url varchar, " +
            "gravatar_id varchar, " +
            "type varchar, " +
            "site_admin boolean" +
            ")";

    public static final String MEMBERS_TABLE_TYPE = "array(" + MEMBER_ROW_TYPE + ")";

    public static final String TEAM_ROW_TYPE = "row(" +
            "org varchar, " +
            "id bigint, " +
            "node_id varchar, " +
            "url varchar, " +
            "html_url varchar, " +
            "name varchar, " +
            "slug varchar, " +
            "description varchar, " +
            "privacy varchar, " +
            "permission varchar, " +
            "members_url varchar, " +
            "repositories_url varchar, " +
            "parent_id bigint, " +
            "parent_slug boolean" +
            ")";

    public static final String TEAMS_TABLE_TYPE = "array(" + TEAM_ROW_TYPE + ")";

    public static final String COMMITS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "sha varchar, " +
            "commit_message varchar, " +
            "commit_tree_sha varchar, " +
            "commit_comment_count bigint, " +
            "commit_verified boolean, " +
            "commit_verification_reason varchar, " +
            "author_name varchar, " +
            "author_email varchar, " +
            "author_date timestamp(3) with time zone, " +
            "author_id bigint, " +
            "author_login varchar, " +
            "committer_name varchar, " +
            "committer_email varchar, " +
            "committer_date timestamp(3) with time zone, " +
            "committer_id bigint, " +
            "committer_login varchar, " +
            "parent_shas array(varchar)" +
            "))";

    public static final String PULLS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "url varchar, " +
            "id bigint, " +
            "node_id varchar, " +
            "html_url varchar, " +
            "diff_url varchar, " +
            "patch_url varchar, " +
            "issue_url varchar, " +
            "commits_url varchar, " +
            "review_comments_url varchar, " +
            "review_comment_url varchar, " +
            "comments_url varchar, " +
            "statuses_url varchar, " +
            "number bigint, " +
            "state varchar, " +
            "locked boolean, " +
            "title varchar, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "body varchar, " +
            "label_ids array(bigint), " +
            "label_names array(varchar), " +
            "milestone_id bigint, " +
            "milestone_title varchar, " +
            "active_lock_reason varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "closed_at timestamp(3) with time zone, " +
            "merged_at timestamp(3) with time zone, " +
            "merge_commit_sha varchar, " +
            "assignee_id bigint, " +
            "assignee_login varchar, " +
            "requested_reviewer_ids array(bigint), " +
            "requested_reviewer_logins array(varchar), " +
            "head_ref varchar, " +
            "head_sha varchar, " +
            "base_ref varchar, " +
            "base_sha varchar, " +
            "author_association varchar, " +
            "draft boolean, " +
            "auto_merge boolean" +
            "))";

    public static final String PULL_COMMITS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "sha varchar, " +
            "pull_number bigint, " +
            "commit_message varchar, " +
            "commit_tree_sha varchar, " +
            "commit_comment_count bigint, " +
            "commit_verified boolean, " +
            "commit_verification_reason varchar, " +
            "author_name varchar, " +
            "author_email varchar, " +
            "author_date timestamp(3) with time zone, " +
            "author_id bigint, " +
            "author_login varchar, " +
            "committer_name varchar, " +
            "committer_email varchar, " +
            "committer_date timestamp(3) with time zone, " +
            "committer_id bigint, " +
            "committer_login varchar, " +
            "parent_shas array(varchar)" +
            "))";

    public static final String PULL_STATS_ROW_TYPE = "row(" +
            "owner varchar, " +
            "repo varchar, " +
            "pull_number bigint, " +
            "comments bigint, " +
            "review_comments bigint, " +
            "commits bigint, " +
            "additions bigint, " +
            "deletions bigint, " +
            "changed_files bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "closed_at timestamp(3) with time zone, " +
            "merged_at timestamp(3) with time zone" +
            ")";

    public static final String REVIEWS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "pull_number bigint, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "body varchar, " +
            "state varchar, " +
            "submitted_at timestamp(3) with time zone, " +
            "commit_id varchar, " +
            "author_association varchar" +
            "))";

    public static final String REVIEW_COMMENTS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "pull_number bigint, " +
            "pull_request_url varchar, " +
            "pull_request_review_id bigint, " +
            "id bigint, " +
            "diff_hunk varchar, " +
            "path varchar, " +
            "position bigint, " +
            "original_position bigint, " +
            "commit_id varchar, " +
            "original_commit_id varchar, " +
            "in_reply_to_id bigint, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "body varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "author_association varchar, " +
            "start_line bigint, " +
            "original_start_line bigint, " +
            "start_side varchar, " +
            "line bigint, " +
            "original_line bigint, " +
            "side varchar" +
            "))";

    public static final String ISSUES_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "node_id varchar, " +
            "url varchar, " +
            "repository_url varchar, " +
            "labels_url varchar, " +
            "comments_url varchar, " +
            "events_url varchar, " +
            "html_url varchar, " +
            "timeline_url varchar, " +
            "number bigint, " +
            "state varchar, " +
            "title varchar, " +
            "body varchar, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "label_ids array(bigint), " +
            "label_names array(varchar), " +
            "assignee_id bigint, " +
            "assignee_login varchar, " +
            "milestone_id bigint, " +
            "milestone_title varchar, " +
            "locked boolean, " +
            "active_lock_reason varchar, " +
            "comments bigint, " +
            "closed_at timestamp(3) with time zone, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "closed_by_id bigint, " +
            "closed_by_login varchar, " +
            "author_association varchar, " +
            "draft boolean, " +
            "reactions_url varchar, " +
            "reactions_total_count integer, " +
            "app_id bigint, " +
            "app_slug varchar, " +
            "app_name varchar" +
            "))";

    public static final String ISSUE_COMMENTS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "issue_number bigint, " +
            "id bigint, " +
            "node_id varchar, " +
            "url varchar, " +
            "html_url varchar, " +
            "body varchar, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "issue_url varchar, " +
            "author_association varchar, " +
            "reactions_url varchar, " +
            "reactions_total_count integer, " +
            "app_id bigint, " +
            "app_slug varchar, " +
            "app_name varchar" +
            "))";

    public static final String WORKFLOWS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "node_id varchar, " +
            "name varchar, " +
            "path varchar, " +
            "state varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "url varchar, " +
            "html_url varchar, " +
            "badge_url varchar" +
            "))";

    public static final String RUNS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "name varchar, " +
            "node_id varchar, " +
            "check_suite_id bigint, " +
            "check_suite_node_id varchar, " +
            "head_branch varchar, " +
            "head_sha varchar, " +
            "run_number bigint, " +
            "run_attempt integer, " +
            "event varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "workflow_id bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "run_started_at timestamp(3) with time zone" +
            "))";

    public static final String JOBS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "run_id bigint, " +
            "run_attempt integer, " +
            "node_id varchar, " +
            "head_sha varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "name varchar, " +
            "steps_count integer" +
            "))";

    public static final String JOB_LOGS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "job_id bigint, " +
            "size_in_bytes bigint, " +
            "part_number integer, " +
            "contents varbinary" +
            "))";

    public static final String STEPS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "run_id bigint, " +
            "run_attempt integer, " +
            "job_id bigint, " +
            "name varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "number bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone" +
            "))";

    public static final String ARTIFACTS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "run_id bigint, " +
            "id bigint, " +
            "size_in_bytes bigint, " +
            "name varchar, " +
            "url varchar, " +
            "archive_download_url varchar, " +
            "expired boolean, " +
            "created_at timestamp(3) with time zone, " +
            "expires_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "filename varchar, " +
            "path varchar, " +
            "mimetype varchar, " +
            "file_size_in_bytes bigint, " +
            "part_number integer, " +
            "contents varbinary" +
            "))";

    public static final String RUNNERS_TABLE_TYPE = "array(row(" +
            "org varchar, " +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "name varchar, " +
            "os varchar, " +
            "status varchar, " +
            "busy boolean, " +
            "label_ids array(bigint), " +
            "label_names array(varchar)" +
            "))";

    /* there are no functions that would use these
    public static final String CHECK_SUITES_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "ref varchar, " +
            "id bigint, " +
            "head_sha varchar, " +
            "head_branch varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "url varchar, " +
            "before varchar, " +
            "after varchar, " +
            "pull_request_numbers array(bigint), " +
            "app_id bigint, " +
            "app_slug varchar, " +
            "app_name varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "latest_check_runs_count bigint, " +
            "check_runs_url varchar" +
        "))";

    public static final String CHECK_RUNS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "ref varchar, " +
            "id bigint, " +
            "head_sha varchar, " +
            "external_id varchar, " +
            "url varchar, " +
            "html_url varchar, " +
            "details_url varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "started_at timestamp(3) with time zone, " +
            "completed_at timestamp(3) with time zone, " +
            "output_title varchar, " +
            "output_summary varchar, " +
            "output_text varchar, " +
            "annotations_count bigint, " +
            "annotations_url varchar, " +
            "check_suite_id bigint, " +
            "app_id bigint, " +
            "app_slug varchar, " +
            "app_name varchar" +
        "))";

    public static final String CHECK_RUN_ANNOTATIONS_TABLE_TYPE = "array(row(" +
        "owner varchar, " +
        "repo varchar, " +
        "check_run_id bigint, " +
        "path varchar, " +
        "start_line integer, " +
        "end_line integer, " +
        "start_column integer, " +
        "end_column integer, " +
        "annotation_level varchar, " +
        "title varchar, " +
        "message varchar, " +
        "raw_details varchar, " +
        "blob_href varchar" +
        "))";
     */

    private final Map<GithubTable, Map<String, ColumnHandle>> columnHandles;

    private final Map<GithubTable, ? extends FilterApplier> filterAppliers = new ImmutableMap.Builder<GithubTable, FilterApplier>()
            .put(GithubTable.COMMITS, new RepoCommitFilter())
            .put(GithubTable.PULLS, new PullFilter())
            .put(GithubTable.PULL_COMMITS, new PullCommitFilter())
            .put(GithubTable.PULL_STATS, new PullStatisticsFilter())
            .put(GithubTable.REVIEWS, new ReviewFilter())
            .put(GithubTable.REVIEW_COMMENTS, new ReviewCommentFilter())
            .put(GithubTable.ISSUES, new IssueFilter())
            .put(GithubTable.ISSUE_COMMENTS, new IssueCommentFilter())
            .put(GithubTable.WORKFLOWS, new WorkflowFilter())
            .put(GithubTable.RUNS, new RunFilter())
            .put(GithubTable.JOBS, new JobFilter())
            .put(GithubTable.JOB_LOGS, new JobLogFilter())
            .put(GithubTable.STEPS, new StepFilter())
            .put(GithubTable.ARTIFACTS, new ArtifactFilter())
            .put(GithubTable.RUNNERS, new RunnerFilter())
            .put(GithubTable.ORGS, new OrgFilter())
            .put(GithubTable.USERS, new UserFilter())
            .put(GithubTable.REPOS, new RepoFilter())
            .put(GithubTable.MEMBERS, new MemberFilter())
            .put(GithubTable.TEAMS, new TeamFilter())
            .put(GithubTable.CHECK_SUITES, new CheckSuiteFilter())
            .put(GithubTable.CHECK_RUNS, new CheckRunFilter())
            .put(GithubTable.CHECK_RUN_ANNOTATIONS, new CheckRunAnnotationFilter())
            .build();

    @Inject
    @SuppressWarnings("StaticAssignmentInConstructor")
    public GithubRest(RestConfig config)
    {
        requireNonNull(config, "config is null");
        GithubRest.token = config.getToken();
        GithubRest.service = RestModule.getService(GithubService.class, "https://api.github.com/", config.getClientBuilder());
        GithubRest.maxBinaryDownloadSizeBytes = config.getClientMaxBinaryDownloadSizeBytes();
        GithubRest.minSplits = config.getMinSplits();
        GithubRest.minSplitTables = config.getMinSplitTables().stream()
                .map(String::toUpperCase)
                .map(GithubTable::valueOf)
                .collect(Collectors.toList());
        if (minSplitTables.size() == 0) {
            minSplitTables = List.of(
                    GithubTable.PULLS,
                    GithubTable.ISSUES,
                    GithubTable.RUNS,
                    GithubTable.CHECK_SUITES);
        }
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

    public static String getToken()
    {
        return token;
    }

    public static GithubService getService()
    {
        return service;
    }

    public static long getMaxBinaryDownloadSizeBytes()
    {
        return maxBinaryDownloadSizeBytes;
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(SchemaTableName schemaTableName)
    {
        GithubTable tableName = GithubTable.valueOf(schemaTableName);
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
        GithubTable tableName = GithubTable.valueOf(table);
        return rowGetters.get(tableName).apply(table);
    }

    public OptionalInt getMaxPage(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        Function<RestTableHandle, Long> getter = rowCountGetters.get(tableName);
        if (getter == null) {
            return OptionalInt.empty();
        }
        long totalCount = getter.apply(table);
        return OptionalInt.of((int) (totalCount + PER_PAGE - 1) / PER_PAGE);
    }

    private Iterable<List<?>> getOrgs(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String login = (String) filter.getFilter((RestColumnHandle) columns.get("login"), table.getConstraint());
        requirePredicate(login, "orgs.login");
        return getRow(() -> service.getOrg("Bearer " + token, login), Organization::toRow);
    }

    private Iterable<List<?>> getUsers(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String login = (String) filter.getFilter((RestColumnHandle) columns.get("login"), table.getConstraint());
        requirePredicate(login, "users.login");
        return getRow(() -> service.getUser("Bearer " + token, login), User::toRow);
    }

    private Iterable<List<?>> getRepos(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner_login"), table.getConstraint());
        requirePredicate(owner, "repos.owner_login");
        SortItem sortOrder = getSortItem(table);
        Iterable<List<?>> userRepos = getRowsFromPages(
                page -> service.listUserRepos(
                        "Bearer " + token,
                        owner,
                        PER_PAGE,
                        page,
                        sortOrder.getName(),
                        sortOrder.getSortOrder().isAscending() ? "asc" : "desc"),
                Repository::toRow,
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
        Iterable<List<?>> orgRepos = getRowsFromPages(
                page -> service.listOrgRepos(
                        "Bearer " + token,
                        owner,
                        PER_PAGE,
                        page,
                        sortOrder.getName(),
                        sortOrder.getSortOrder().isAscending() ? "asc" : "desc"),
                Repository::toRow,
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
        return Iterables.concat(userRepos, orgRepos);
    }

    private Iterable<List<?>> getMembers(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String org = (String) filter.getFilter((RestColumnHandle) columns.get("org"), table.getConstraint());
        requirePredicate(org, "members.org");
        String teamSlug = (String) filter.getFilter((RestColumnHandle) columns.get("team_slug"), table.getConstraint());
        IntFunction<Call<List<Member>>> fetcher;
        if (teamSlug != null) {
            fetcher = page -> service.listOrgTeamMembers(
                    "Bearer " + token,
                    org,
                    teamSlug,
                    PER_PAGE,
                    page);
        }
        else {
            fetcher = page -> service.listOrgMembers(
                    "Bearer " + token,
                    org,
                    PER_PAGE,
                    page);
        }
        return getRowsFromPages(
                fetcher,
                item -> {
                    item.setOrg(org);
                    item.setTeamSlug(teamSlug);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getTeams(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String org = (String) filter.getFilter((RestColumnHandle) columns.get("org"), table.getConstraint());
        requirePredicate(org, "teams.org");
        return getRowsFromPages(
                page -> service.listOrgTeams("Bearer " + token, org, PER_PAGE, page),
                item -> {
                    item.setOrg(org);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getRepoCommits(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "commits.owner");
        requirePredicate(repo, "commits.repo");
        String sha = (String) filter.getFilter((RestColumnHandle) columns.get("sha"), constraint, "master");
        String since = (String) filter.getFilter((RestColumnHandle) columns.get("committer_date"), constraint, "1970-01-01T00:00:00Z");
        // TODO allow filtering by state (many, comma separated, or all), requires https://github.com/nineinchnick/trino-rest/issues/30
        return getRowsFromPages(
                page -> service.listShaCommits("Bearer " + token, owner, repo, PER_PAGE, page, sha, since),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getPulls(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "pulls.owner");
        requirePredicate(repo, "pulls.repo");
        String state = (String) filter.getFilter((RestColumnHandle) columns.get("state"), constraint, "all");
        SortItem sortOrder = getSortItem(table);
        return getRowsFromPages(
                page -> service.listPulls(
                        "Bearer " + token,
                        owner,
                        repo,
                        PER_PAGE,
                        page,
                        sortOrder.getName(),
                        sortOrder.getSortOrder().isAscending() ? "asc" : "desc",
                        state),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getPullStats(RestTableHandle table)
    {
        if (table.getLimit() == 0) {
            return List.of();
        }
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "pull_stats.owner");
        requirePredicate(repo, "pull_stats.repo");
        long pullNumber = (long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        requirePredicate(pullNumber, "pull_stats.pull_number");

        return getRow(() -> service.getPull("Bearer " + token, owner, repo, pullNumber), item -> {
            item.setOwner(owner);
            item.setRepo(repo);
            item.setPullNumber(pullNumber);
            return item.toRow();
        });
    }

    private void requirePredicate(Object value, String name)
    {
        if (value == null) {
            throw new TrinoException(INVALID_ROW_FILTER, "Missing required constraint for " + name);
        }
    }

    private SortItem getSortItem(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        List<SortItem> sortOrder = table.getSortOrder().orElseGet(() -> supportedTableSort.get(tableName));
        String sortName = sortOrder.get(0).getName();
        switch (sortOrder.get(0).getName()) {
            case "created_at":
                sortName = "created";
                break;
            case "updated_at":
                sortName = "updated";
                break;
        }
        return new SortItem(sortName, sortOrder.get(0).getSortOrder());
    }

    private Iterable<List<?>> getPullCommits(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "pull_commits.owner");
        requirePredicate(repo, "pull_commits.repo");
        long pullNumber = (long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        requirePredicate(pullNumber, "pull_commits.pull_number");
        // TODO allow filtering by state (many, comma separated, or all), requires https://github.com/nineinchnick/trino-rest/issues/30
        return getRowsFromPages(
                page -> service.listPullCommits("Bearer " + token, owner, repo, pullNumber, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setPullNumber(pullNumber);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getReviews(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "reviews.owner");
        requirePredicate(repo, "reviews.repo");
        long pullNumber = (long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        requirePredicate(pullNumber, "reviews.pull_number");
        return getRowsFromPages(
                page -> service.listPullReviews("Bearer " + token, owner, repo, pullNumber, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setPullNumber(pullNumber);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getReviewComments(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "review_comments.owner");
        requirePredicate(repo, "review_comments.repo");
        Long pullNumber = (Long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        IntFunction<Call<List<ReviewComment>>> fetcher;
        if (pullNumber != null) {
            fetcher = page -> service.listSingleReviewComments("Bearer " + token, owner, repo, pullNumber, PER_PAGE, page);
        }
        else {
            String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint, "1970-01-01T00:00:00Z");
            SortItem sortOrder = getSortItem(table);
            fetcher = page -> service.listReviewComments("Bearer " + token, owner, repo, PER_PAGE, page, sortOrder.getName(), sortOrder.getSortOrder().isAscending() ? "asc" : "desc", since);
        }
        return getRowsFromPages(
                fetcher,
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    if (pullNumber != null) {
                        item.setPullNumber(pullNumber);
                    }
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getIssues(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "issues.owner");
        requirePredicate(repo, "issues.repo");
        String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint, "1970-01-01T00:00:00Z");
        String state = (String) filter.getFilter((RestColumnHandle) columns.get("state"), constraint, "all");
        SortItem sortOrder = getSortItem(table);
        return getRowsFromPages(
                page -> service.listIssues(
                        "Bearer " + token,
                        owner,
                        repo,
                        PER_PAGE,
                        page,
                        sortOrder.getName(),
                        sortOrder.getSortOrder().isAscending() ? "asc" : "desc",
                        state,
                        since),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getIssueComments(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "issue_comments.owner");
        requirePredicate(repo, "issue_comments.repo");
        Long issueNumber = (Long) filter.getFilter((RestColumnHandle) columns.get("issue_number"), constraint);
        IntFunction<Call<List<IssueComment>>> fetcher;
        if (issueNumber != null) {
            fetcher = page -> service.listSingleIssueComments("Bearer " + token, owner, repo, issueNumber, PER_PAGE, page);
        }
        else {
            String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint, "1970-01-01T00:00:00Z");
            SortItem sortOrder = getSortItem(table);
            fetcher = page -> service.listIssueComments("Bearer " + token, owner, repo, PER_PAGE, page, sortOrder.getName(), sortOrder.getSortOrder().isAscending() ? "asc" : "desc", since);
        }
        return getRowsFromPages(
                fetcher,
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    if (issueNumber != null) {
                        item.setIssueNumber(issueNumber);
                    }
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Iterable<List<?>> getWorkflows(RestTableHandle table)
    {
        Map<String, String> filters = getWorkflowsFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        return getRowsFromPagesEnvelope(
                page -> service.listWorkflows("Bearer " + token, owner, repo, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getWorkflowsCount(RestTableHandle table)
    {
        Map<String, String> filters = getWorkflowsFilters(table);
        return getTotalCountFromPagesEnvelope(
                () -> service.listWorkflows("Bearer " + token, filters.get("owner"), filters.get("repo"), 0, 1));
    }

    private Map<String, String> getWorkflowsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "workflows.owner");
        requirePredicate(repo, "workflows.repo");

        return ImmutableMap.of(
                "owner", owner,
                "repo", repo);
    }

    private Iterable<List<?>> getRuns(RestTableHandle table)
    {
        Map<String, String> filters = getRunsFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        return getRowsFromPagesEnvelope(
                page -> service.listRuns("Bearer " + token, owner, repo, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getRunsCount(RestTableHandle table)
    {
        Map<String, String> filters = getRunsFilters(table);
        return getTotalCountFromPagesEnvelope(
                () -> service.listRuns("Bearer " + token, filters.get("owner"), filters.get("repo"), 0, 1));
    }

    private Map<String, String> getRunsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "runs.owner");
        requirePredicate(repo, "runs.repo");

        return ImmutableMap.of(
                "owner", owner,
                "repo", repo);
    }

    private Iterable<List<?>> getJobs(RestTableHandle table)
    {
        Map<String, Object> filters = getJobsFilters(table);
        String owner = (String) filters.get("owner");
        String repo = (String) filters.get("repo");
        Long runId = (Long) filters.get("runId");
        return getRowsFromPagesEnvelope(
                page -> service.listRunJobs("Bearer " + token, owner, repo, runId, "all", PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getJobsCount(RestTableHandle table)
    {
        Map<String, Object> filters = getJobsFilters(table);
        return getTotalCountFromPagesEnvelope(
                () -> service.listRunJobs(
                        "Bearer " + token,
                        (String) filters.get("owner"),
                        (String) filters.get("repo"),
                        (Long) filters.get("runId"),
                        "all",
                        0,
                        1));
    }

    private Map<String, Object> getJobsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "jobs.owner");
        requirePredicate(repo, "jobs.repo");
        Long runId = (Long) filter.getFilter((RestColumnHandle) columns.get("run_id"), constraint);
        requirePredicate(runId, "jobs.run_id");
        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "runId", runId);
    }

    private Iterable<List<?>> getJobLogs(RestTableHandle table)
    {
        Map<String, Object> filters = getJobLogsFilters(table);
        String owner = (String) filters.get("owner");
        String repo = (String) filters.get("repo");
        Long jobId = (Long) filters.get("jobId");

        Response<ResponseBody> response;
        try {
            response = service.jobLogs(
                    "Bearer " + token,
                    owner,
                    repo,
                    jobId).execute();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (response.code() == HTTP_NOT_FOUND) {
            return List.of();
        }
        Rest.checkServiceResponse(response);
        ResponseBody body = requireNonNull(response.body(), "response body is null");
        String size = response.headers().get("Content-Length");
        long sizeBytes = size != null ? Long.parseLong(size) : 0;

        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();
        int i = 1;
        try {
            for (Slice slice : JobLogs.getParts(body.byteStream())) {
                result.add(List.of(
                        owner,
                        repo,
                        jobId,
                        sizeBytes,
                        i++,
                        slice));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.build();
    }

    private Map<String, Object> getJobLogsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "job_logs.owner");
        requirePredicate(repo, "job_logs.repo");
        Long jobId = (Long) filter.getFilter((RestColumnHandle) columns.get("job_id"), constraint);
        requirePredicate(jobId, "job_logs.job_id");
        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "jobId", jobId);
    }

    private Iterable<List<?>> getSteps(RestTableHandle table)
    {
        Map<String, Object> filters = getStepsFilters(table);
        String owner = (String) filters.get("owner");
        String repo = (String) filters.get("repo");
        Long runId = (Long) filters.get("runId");

        ImmutableList.Builder<Job> jobs = new ImmutableList.Builder<>();

        int page = 1;
        while (true) {
            Response<JobsList> response;
            try {
                response = service.listRunJobs("Bearer " + token, owner, repo, runId, "all", PER_PAGE, page++).execute();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            Rest.checkServiceResponse(response);
            List<Job> items = requireNonNull(response.body(), "response body is null").getItems();
            if (items.size() == 0) {
                break;
            }
            items.forEach(i -> i.setOwner(owner));
            items.forEach(i -> i.setRepo(repo));
            jobs.addAll(items);
            if (items.size() < PER_PAGE) {
                break;
            }
        }

        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();
        int resultSize = 0;
        for (Job job : jobs.build()) {
            List<List<?>> steps = job.getSteps().stream().map(Step::toRow).collect(toList());
            int stepsToUse = Math.max(0, table.getLimit() - resultSize);
            if (steps.size() > stepsToUse) {
                steps = steps.subList(0, stepsToUse);
            }
            else {
                stepsToUse = steps.size();
            }
            result.addAll(steps);
            resultSize += stepsToUse;
        }
        return result.build();
    }

    private long getStepsCount(RestTableHandle table)
    {
        Map<String, Object> filters = getStepsFilters(table);
        return STEPS_PER_JOB * getTotalCountFromPagesEnvelope(
                () -> service.listRunJobs(
                        "Bearer " + token,
                        (String) filters.get("owner"),
                        (String) filters.get("repo"),
                        (Long) filters.get("runId"),
                        "all",
                        0,
                        1));
    }

    private Map<String, Object> getStepsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "steps.owner");
        requirePredicate(repo, "steps.repo");
        Long runId = (Long) filter.getFilter((RestColumnHandle) columns.get("run_id"), constraint);
        requirePredicate(runId, "steps.run_id");
        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "runId", runId);
    }

    private Iterable<List<?>> getArtifacts(RestTableHandle table)
    {
        Map<String, Object> filters = getArtifactsFilters(table);
        String owner = (String) filters.get("owner");
        String repo = (String) filters.get("repo");
        Long runId = (Long) filters.get("runId");

        IntFunction<Call<ArtifactsList>> fetcher = page -> service.listArtifacts("Bearer " + token, owner, repo, PER_PAGE, page);
        if (runId != null) {
            fetcher = page -> service.listRunArtifacts("Bearer " + token, owner, repo, runId, PER_PAGE, page);
        }
        else {
            log.warning(format("Missing filter on run_id, will try to fetch all artifacts for %s/%s", owner, repo));
        }
        return getRowsFromPagesEnvelope(
                fetcher,
                item -> {
                    Stream.Builder<List<?>> result = Stream.builder();
                    item.setOwner(owner);
                    item.setRepo(repo);
                    if (runId != null) {
                        item.setRunId(runId);
                    }
                    if (item.getSizeInBytes() > getMaxBinaryDownloadSizeBytes()) {
                        log.warning(format("Skipping downloading artifact %s because its size %d is greater than max of %d",
                                item.getId(), item.getSizeInBytes(), getMaxBinaryDownloadSizeBytes()));
                        result.add(item.toRow());
                    }
                    else {
                        try {
                            for (Artifact artifact : download(service, token, item)) {
                                result.add(artifact.toRow());
                            }
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return result.build();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private Map<String, Object> getArtifactsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        requirePredicate(owner, "artifacts.owner");
        requirePredicate(repo, "artifacts.repo");
        Long runId = (Long) filter.getFilter((RestColumnHandle) columns.get("run_id"), constraint);

        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "runId", runId);
    }

    private Iterable<List<?>> getRunners(RestTableHandle table)
    {
        Map<String, Optional<String>> filters = getRunnersFilters(table);
        Optional<String> org = filters.get("org");
        Optional<String> owner = filters.get("owner");
        Optional<String> repo = filters.get("repo");

        IntFunction<Call<RunnersList>> fetcher;

        //noinspection OptionalIsPresent
        if (org.isPresent()) {
            fetcher = page -> service.listOrgRunners("Bearer " + token, org.get(), PER_PAGE, page);
        }
        else {
            fetcher = page -> service.listRunners("Bearer " + token, owner.get(), repo.get(), PER_PAGE, page);
        }
        return getRowsFromPagesEnvelope(
                fetcher,
                item -> {
                    item.setOrg(org.orElse(null));
                    item.setOwner(owner.orElse(null));
                    item.setRepo(repo.orElse(null));
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getRunnersCount(RestTableHandle table)
    {
        Map<String, Optional<String>> filters = getRunnersFilters(table);
        Optional<String> org = filters.get("org");
        Optional<String> owner = filters.get("owner");
        Optional<String> repo = filters.get("repo");
        Supplier<Call<RunnersList>> fetcher;
        //noinspection OptionalIsPresent
        if (org.isPresent()) {
            fetcher = () -> service.listOrgRunners("Bearer " + token, org.get(), 0, 1);
        }
        else {
            fetcher = () -> service.listRunners("Bearer " + token, owner.get(), repo.get(), 0, 1);
        }
        return getTotalCountFromPagesEnvelope(fetcher);
    }

    private Map<String, Optional<String>> getRunnersFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String org = (String) filter.getFilter((RestColumnHandle) columns.get("org"), constraint);
        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        if (owner == null && repo == null) {
            requirePredicate(org, "runners.org");
        }
        if (org == null) {
            requirePredicate(owner, "runners.owner");
            requirePredicate(repo, "runners.repo");
        }

        return ImmutableMap.of(
                "org", Optional.ofNullable(org),
                "owner", Optional.ofNullable(owner),
                "repo", Optional.ofNullable(repo));
    }

    private Iterable<List<?>> getCheckSuites(RestTableHandle table)
    {
        Map<String, String> filters = getCheckSuitesFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        String ref = filters.get("ref");

        return getRowsFromPagesEnvelope(
                page -> service.listCheckSuites("Bearer " + token, owner, repo, ref, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setRef(ref);
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getCheckSuitesCount(RestTableHandle table)
    {
        Map<String, String> filters = getCheckSuitesFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        String ref = filters.get("ref");
        return getTotalCountFromPagesEnvelope(
                () -> service.listCheckSuites("Bearer " + token, owner, repo, ref, 0, 1));
    }

    private Map<String, String> getCheckSuitesFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        String ref = (String) filter.getFilter((RestColumnHandle) columns.get("ref"), constraint);
        requirePredicate(owner, "check_suites.owner");
        requirePredicate(repo, "check_suites.repo");
        requirePredicate(ref, "check_suites.ref");

        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "ref", ref);
    }

    private Iterable<List<?>> getCheckRuns(RestTableHandle table)
    {
        Map<String, String> filters = getCheckRunsFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        String ref = filters.get("ref");

        return getRowsFromPagesEnvelope(
                page -> service.listCheckRuns("Bearer " + token, owner, repo, ref, PER_PAGE, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setRef(ref);
                    return Stream.of(item.toRow());
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private long getCheckRunsCount(RestTableHandle table)
    {
        Map<String, String> filters = getCheckRunsFilters(table);
        String owner = filters.get("owner");
        String repo = filters.get("repo");
        String ref = filters.get("ref");
        return getTotalCountFromPagesEnvelope(
                () -> service.listCheckRuns("Bearer " + token, owner, repo, ref, 0, 1));
    }

    private Map<String, String> getCheckRunsFilters(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        String ref = (String) filter.getFilter((RestColumnHandle) columns.get("ref"), constraint);
        requirePredicate(owner, "check_runs.owner");
        requirePredicate(repo, "check_runs.repo");
        requirePredicate(ref, "check_runs.ref");

        return ImmutableMap.of(
                "owner", owner,
                "repo", repo,
                "ref", ref);
    }

    private Iterable<List<?>> getCheckRunAnnotations(RestTableHandle table)
    {
        GithubTable tableName = GithubTable.valueOf(table);
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        Long checkRunId = (Long) filter.getFilter((RestColumnHandle) columns.get("check_run_id"), constraint);
        requirePredicate(owner, "check_run_annotations.owner");
        requirePredicate(repo, "check_run_annotations.repo");
        requirePredicate(checkRunId, "check_run_annotations.check_run_id");
        return getRowsFromPages(
                page -> service.listCheckRunAnnotations(
                        "Bearer " + token,
                        owner,
                        repo,
                        checkRunId,
                        PER_PAGE,
                        page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setCheckRunId(checkRunId);
                    return item.toRow();
                },
                table.getOffset(),
                table.getLimit(),
                table.getPageIncrement());
    }

    private <T> Iterable<List<?>> getRow(Supplier<Call<T>> fetcher, Function<T, List<?>> mapper)
    {
        T record = getRecord(fetcher);
        if (record == null) {
            return List.of();
        }
        return List.of(mapper.apply(record));
    }

    private <T> T getRecord(Supplier<Call<T>> fetcher)
    {
        Response<T> response;
        try {
            response = fetcher.get().execute();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        Rest.checkServiceResponse(response);
        return response.body();
    }

    // TODO this abomination should be in a base class implementing a cursor
    private <T> Iterable<List<?>> getRowsFromPages(
            IntFunction<Call<List<T>>> fetcher,
            Function<T, List<?>> mapper,
            int offset,
            final int limit,
            int pageIncrement)
    {
        return () -> new Iterator<>()
        {
            int resultSize;
            int page = offset + 1;
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
                // TODO only do this if no more records in batch
                Response<List<T>> response;
                try {
                    response = fetcher.apply(page).execute();
                    page += pageIncrement;
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (response.code() == HTTP_NOT_FOUND) {
                    return false;
                }
                Rest.checkServiceResponse(response);
                List<T> items = requireNonNull(response.body(), "response body is null");
                if (items.size() == 0) {
                    return false;
                }
                int itemsToUse = Math.min(
                        Math.max(0, limit - resultSize),
                        items.size());
                rows = items.subList(0, itemsToUse).stream().map(mapper).collect(toList()).iterator();

                resultSize += itemsToUse;
                if (items.size() < PER_PAGE) {
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

    // TODO this abomination is even worse
    private <T, E extends Envelope<T>> Iterable<List<?>> getRowsFromPagesEnvelope(
            IntFunction<Call<E>> fetcher,
            Function<T, Stream<List<?>>> mapper,
            int offset,
            int limit,
            int pageIncrement)
    {
        return () -> new Iterator<>()
        {
            int resultSize;
            int itemsSeen;
            int page = offset + 1;
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
                    response = fetcher.apply(page).execute();
                    page += pageIncrement;
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (response.code() == HTTP_NOT_FOUND) {
                    return false;
                }
                Rest.checkServiceResponse(response);
                E envelope = requireNonNull(response.body(), "response body is null");
                List<T> items = envelope.getItems();
                if (items.size() == 0) {
                    return false;
                }
                int itemsToUse = Math.min(
                        Math.max(0, limit - resultSize),
                        items.size());
                List<List<?>> rows = items.subList(0, itemsToUse).stream().flatMap(mapper).collect(toList());
                itemsSeen += items.size();

                // mapper can produce 1 or more rows per item, so subList them again
                if (rows.size() != items.size()) {
                    itemsToUse = Math.min(
                            Math.max(0, limit - resultSize),
                            rows.size());
                    rows = rows.subList(0, itemsToUse);
                }
                if (rows.size() == 0) {
                    return false;
                }
                this.rows = rows.iterator();
                resultSize += itemsToUse;
                // check against total count to avoid making a request that would return a 404
                if (itemsSeen >= envelope.getTotalCount()) {
                    resultSize = limit;
                }
                return this.rows.hasNext();
            }

            @Override
            public List<?> next()
            {
                return rows.next();
            }
        };
    }

    private <T, E extends Envelope<T>> long getTotalCountFromPagesEnvelope(Supplier<Call<E>> fetcher)
    {
        Response<E> response;
        try {
            response = fetcher.get().execute();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (response.code() == HTTP_NOT_FOUND) {
            return 0;
        }
        Rest.checkServiceResponse(response);
        E envelope = requireNonNull(response.body(), "response body is null");
        return envelope.getTotalCount();
    }

    @Override
    public Consumer<List> createRowSink(SchemaTableName schemaTableName)
    {
        throw new IllegalStateException("This connector does not support write");
    }

    public static String getSqlType(String tableName)
    {
        return columns.get(GithubTable.valueOf(tableName.toUpperCase()))
                .stream()
                .map(column -> column.getName() + " " + column.getType().getDisplayName())
                .collect(Collectors.joining(", ", "ARRAY(ROW(", "))"));
    }

    public static RowType getRowType(GithubTable tableName)
    {
        List<RowType.Field> fields = GithubRest.columns.get(tableName)
                .stream()
                .map(columnMetadata -> RowType.field(
                        columnMetadata.getName(),
                        columnMetadata.getType()))
                .collect(Collectors.toList());
        return RowType.from(fields);
    }

    @Override
    public Optional<LimitApplicationResult<ConnectorTableHandle>> applyLimit(
            ConnectorSession session,
            ConnectorTableHandle table,
            long limit)
    {
        RestTableHandle restTable = (RestTableHandle) table;
        if (restTable.getLimit() == limit) {
            return Optional.empty();
        }
        return Optional.of(new LimitApplicationResult<>(
                restTable.cloneWithLimit((int) Math.min(limit, Integer.MAX_VALUE)),
                false,
                true));
    }

    @Override
    public Optional<TopNApplicationResult<ConnectorTableHandle>> applyTopN(
            ConnectorSession session,
            ConnectorTableHandle table,
            long topNCount,
            List<SortItem> sortItems,
            Map<String, ColumnHandle> assignments)
    {
        verify(!sortItems.isEmpty(), "sortItems are empty");
        RestTableHandle restTable = (RestTableHandle) table;
        GithubTable tableName = GithubTable.valueOf(restTable);
        List<SortItem> supportedItems = supportedTableSort.get(tableName);
        if (supportedItems == null) {
            return Optional.empty();
        }

        if (!supportedItems.contains(sortItems.get(0))) {
            throw new TrinoException(INVALID_ORDER_BY,
                    format("When using LIMIT or FETCH, first expression in ORDER BY must be one of: %s",
                            supportedItems
                                    .stream()
                                    .map(s -> s.getName() + " " + s.getSortOrder().toString())
                                    .collect(Collectors.joining(", "))));
        }

        int limit = (int) Math.min(topNCount, Integer.MAX_VALUE);
        sortItems = sortItems.subList(0, 1);
        if (restTable.getSortOrder().isPresent()) {
            if (restTable.getLimit() == limit && restTable.getSortOrder().equals(Optional.of(sortItems))) {
                return Optional.empty();
            }
        }

        return Optional.of(new TopNApplicationResult<>(
                restTable
                        .cloneWithLimit(limit)
                        .cloneWithSortOrder(sortItems),
                false,
                true));
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(
            ConnectorSession session,
            ConnectorTableHandle table,
            Constraint constraint)
    {
        RestTableHandle restTable = (RestTableHandle) table;
        GithubTable tableName = GithubTable.valueOf(restTable);

        FilterApplier filterApplier = filterAppliers.get(tableName);
        if (filterApplier == null) {
            return Optional.empty();
        }
        return filterApplier.applyFilter(
                restTable,
                columnHandles.get(tableName),
                filterApplier.getSupportedFilters(),
                constraint.getSummary());
    }

    @Override
    public ConnectorSplitSource getSplitSource(
            NodeManager nodeManager,
            ConnectorTableHandle handle,
            ConnectorSplitManager.SplitSchedulingStrategy splitSchedulingStrategy,
            DynamicFilter dynamicFilter)
    {
        if (splitSchedulingStrategy != ConnectorSplitManager.SplitSchedulingStrategy.UNGROUPED_SCHEDULING) {
            throw new IllegalArgumentException("Unknown splitSchedulingStrategy: " + splitSchedulingStrategy);
        }

        RestTableHandle table = (RestTableHandle) handle;

        List<HostAddress> addresses = nodeManager.getRequiredWorkerNodes().stream()
                .map(Node::getHostAndPort)
                .collect(toList());

        GithubTable tableName = GithubTable.valueOf(table);
        FilterApplier filterApplier = filterAppliers.get(tableName);
        if (filterApplier == null) {
            List<RestConnectorSplit> splits = List.of(new RestConnectorSplit(table, addresses));
            return new FixedSplitSource(splits);
        }
        // merge in constraints from dynamicFilter, which may contain multivalued domains
        Optional<ConstraintApplicationResult<ConnectorTableHandle>> result = filterApplier.applyFilter(
                table,
                columnHandles.get(tableName),
                filterApplier.getSupportedFilters(),
                dynamicFilter.getCurrentPredicate());
        if (result.isPresent()) {
            table = (RestTableHandle) result.get().getHandle();
        }

        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        if (constraint.getDomains().isEmpty()) {
            List<RestConnectorSplit> splits = List.of(new RestConnectorSplit(table, addresses));
            return new FixedSplitSource(splits);
        }

        /*
        Generate splits based on the cartesian product of all multi-valued domains.
        Example, given predicates such as: `job_id IN (12, 34) AND conclusion IN ('canceled', 'failure')`
        the cartesian product will yield:
        * job_id:12, conclusion:canceled
        * job_id:34, conclusion:canceled
        * job_id:12, conclusion:failure
        * job_id:34, conclusion:failure
         */
        Map<ColumnHandle, Domain> originalDomains = constraint.getDomains().get();
        // first build a list of lists of tuples with the column and single-valued domain, for every value of a multi-valued domain
        ImmutableList.Builder<List<Map.Entry<ColumnHandle, Domain>>> singleDomains = new ImmutableList.Builder<>();
        for (Map.Entry<ColumnHandle, Domain> entry : originalDomains.entrySet()) {
            RestColumnHandle column = (RestColumnHandle) entry.getKey();
            Domain domain = entry.getValue();

            FilterType supportedFilter = filterApplier.getSupportedFilters().get(column.getName());
            if (domain.isSingleValue() || supportedFilter != FilterType.EQUAL) {
                continue;
            }
            List<Object> values;
            if (domain.getValues().isDiscreteSet()) {
                values = domain.getValues().getDiscreteSet();
            }
            else {
                values = domain.getValues().getRanges().getOrderedRanges()
                        .stream()
                        .map(Range::getSingleValue)
                        .collect(toList());
            }
            ImmutableList.Builder<Map.Entry<ColumnHandle, Domain>> splitDomains = new ImmutableList.Builder<>();
            for (Object value : values) {
                splitDomains.add(new AbstractMap.SimpleImmutableEntry<>(column, Domain.create(
                        ValueSet.of(domain.getType(), value),
                        domain.isNullAllowed())));
            }
            singleDomains.add(splitDomains.build());
        }
        // then create copies of the original constraints, with every multi-valued domain replaced with single-value sets
        ImmutableList.Builder<RestConnectorSplit> splits = new ImmutableList.Builder<>();
        for (List<Map.Entry<ColumnHandle, Domain>> splitDomains : Lists.cartesianProduct(singleDomains.build())) {
            Map<ColumnHandle, Domain> newDomains = new HashMap<>(originalDomains);
            for (Map.Entry<ColumnHandle, Domain> entry : splitDomains) {
                newDomains.put(entry.getKey(), entry.getValue());
            }
            splits.add(new RestConnectorSplit(
                    table.cloneWithConstraint(TupleDomain.withColumnDomains(newDomains)),
                    addresses));
        }
        if (table.getLimit() > PER_PAGE) {
            ImmutableList<RestConnectorSplit> oldSplits = splits.build();
            splits = new ImmutableList.Builder<>();
            for (RestConnectorSplit split : oldSplits) {
                OptionalInt maxPage = getMaxPage(split.getTableHandle());
                if (maxPage.isEmpty()) {
                    int minSplits = 1;
                    if (minSplitTables.contains(tableName)) {
                        minSplits = GithubRest.minSplits;
                    }
                    for (int i = 0; i < minSplits; i++) {
                        splits.add(new RestConnectorSplit(
                                split.getTableHandle().cloneWithOffset(i, minSplits),
                                List.of(addresses.get(i % addresses.size()))));
                    }
                }
                else {
                    for (int i = 0; i < maxPage.getAsInt(); i++) {
                        RestTableHandle tableHandle = split.getTableHandle();
                        splits.add(new RestConnectorSplit(
                                tableHandle
                                        .cloneWithOffset(i, 1)
                                        .cloneWithLimit(Math.min(tableHandle.getLimit(), PER_PAGE)),
                                List.of(addresses.get(i % addresses.size()))));
                    }
                }
            }
        }
        return new FixedSplitSource(splits.build());
    }

    @Override
    public TableStatistics getTableStatistics(
            ConnectorSession session,
            ConnectorTableHandle handle,
            Constraint constraint)
    {
        RestTableHandle table = (RestTableHandle) handle;
        GithubTable tableName = GithubTable.valueOf(table);
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);
        TableStatistics.Builder builder = TableStatistics.builder();

        Function<RestTableHandle, Long> getter = rowCountGetters.get(tableName);
        if (getter != null) {
            // constraint needs to be pushed down into the table handle
            if (filter != null) {
                Optional<ConstraintApplicationResult<ConnectorTableHandle>> result = filter.applyFilter(
                        table,
                        columns,
                        filter.getSupportedFilters(),
                        constraint.getSummary());
                if (result.isPresent()) {
                    table = (RestTableHandle) result.get().getHandle();
                }
            }
            long totalCount;
            try {
                totalCount = getter.apply(table);
            }
            catch (TrinoException e) {
                if (!e.getErrorCode().equals(INVALID_ROW_FILTER.toErrorCode())) {
                    throw e;
                }
                // TODO this is supposed to force this table to be the right side of the join  but should be relative to the left side, which is unknown here
                totalCount = 1_000_000L;
            }
            builder.setRowCount(Estimate.of(totalCount));
            // set stats at least for columns that might be joined by
            Map<String, KeyType> keyColumns = GithubRest.keyColumns.get(tableName);
            if (keyColumns == null) {
                return builder.build();
            }
            for (Map.Entry<String, KeyType> entry : keyColumns.entrySet()) {
                boolean isPrimary = entry.getValue() == KeyType.PRIMARY_KEY;
                RestColumnHandle column = (RestColumnHandle) columns.get(entry.getKey());
                ColumnStatistics.Builder columnStatistic = ColumnStatistics.builder()
                        .setNullsFraction(Estimate.zero())
                        .setDistinctValuesCount(Estimate.of((double) totalCount * (isPrimary ? 1 : RELATIONSHIPS_RATIO)));
                if (column.getType() instanceof FixedWidthType) {
                    columnStatistic.setDataSize(Estimate.of(((FixedWidthType) column.getType()).getFixedSize() * totalCount));
                }
                builder.setColumnStatistics(column, columnStatistic.build());
            }
            return builder.build();
        }

        switch (tableName) {
            // return same number of rows for issues and issue_comments,
            // assuming that some issues don't have any comments, so these numbers are close together
            case ISSUES:
            case ISSUE_COMMENTS:
                String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint.getSummary());
                String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint.getSummary());
                if (owner == null || repo == null) {
                    return TableStatistics.empty();
                }
                Repository repository = getRecord(() -> service.getRepo("Bearer " + token, owner, repo));
                if (repository == null) {
                    return TableStatistics.empty();
                }
                long totalCount = repository.getOpenIssuesCount();
                builder.setRowCount(Estimate.of(totalCount));
                // set stats at least for columns that might be joined by
                for (Map.Entry<String, KeyType> entry : keyColumns.get(tableName).entrySet()) {
                    boolean isPrimary = entry.getValue() == KeyType.PRIMARY_KEY;
                    RestColumnHandle column = (RestColumnHandle) columns.get(entry.getKey());
                    ColumnStatistics.Builder columnStatistic = ColumnStatistics.builder()
                            .setNullsFraction(Estimate.zero())
                            .setDistinctValuesCount(Estimate.of((double) totalCount * (isPrimary ? ISSUE_COMMENTS_RATIO : 1)));
                    if (column.getType() instanceof FixedWidthType) {
                        columnStatistic.setDataSize(Estimate.of(((FixedWidthType) column.getType()).getFixedSize() * totalCount));
                    }
                    builder.setColumnStatistics(column, columnStatistic.build());
                }
                break;
            default:
                return TableStatistics.empty();
        }
        return builder.build();
    }
}

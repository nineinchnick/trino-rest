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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Throwables;
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
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.TimestampWithTimeZoneType;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.github.filter.FilterApplier;
import pl.net.was.rest.github.filter.IssueCommentFilter;
import pl.net.was.rest.github.filter.IssueFilter;
import pl.net.was.rest.github.filter.JobFilter;
import pl.net.was.rest.github.filter.OrgFilter;
import pl.net.was.rest.github.filter.PullCommitFilter;
import pl.net.was.rest.github.filter.PullFilter;
import pl.net.was.rest.github.filter.RepoFilter;
import pl.net.was.rest.github.filter.ReviewCommentFilter;
import pl.net.was.rest.github.filter.ReviewFilter;
import pl.net.was.rest.github.filter.RunFilter;
import pl.net.was.rest.github.filter.StepFilter;
import pl.net.was.rest.github.filter.UserFilter;
import pl.net.was.rest.github.function.BaseFunction;
import pl.net.was.rest.github.model.Envelope;
import pl.net.was.rest.github.model.Job;
import pl.net.was.rest.github.model.JobsList;
import pl.net.was.rest.github.model.Organization;
import pl.net.was.rest.github.model.Repository;
import pl.net.was.rest.github.model.Step;
import pl.net.was.rest.github.model.User;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class GithubRest
        implements Rest
{
    public static final String SCHEMA_NAME = "default";

    private final String token;
    private final GithubService service = getService();

    public static final Map<String, List<ColumnMetadata>> columns = new ImmutableMap.Builder<String, List<ColumnMetadata>>()
            .put("orgs", ImmutableList.of(
                    new ColumnMetadata("login", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("description", createUnboundedVarcharType()),
                    new ColumnMetadata("name", createUnboundedVarcharType()),
                    new ColumnMetadata("company", createUnboundedVarcharType()),
                    new ColumnMetadata("blog", createUnboundedVarcharType()),
                    new ColumnMetadata("location", createUnboundedVarcharType()),
                    new ColumnMetadata("email", createUnboundedVarcharType()),
                    new ColumnMetadata("twitter_username", createUnboundedVarcharType()),
                    new ColumnMetadata("is_verified", BOOLEAN),
                    new ColumnMetadata("has_organization_projects", BOOLEAN),
                    new ColumnMetadata("has_repository_projects", BOOLEAN),
                    new ColumnMetadata("public_repos", BIGINT),
                    new ColumnMetadata("public_gists", BIGINT),
                    new ColumnMetadata("followers", BIGINT),
                    new ColumnMetadata("following", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("type", createUnboundedVarcharType()),
                    new ColumnMetadata("total_private_repos", BIGINT),
                    new ColumnMetadata("owned_private_repos", BIGINT),
                    new ColumnMetadata("private_gists", BIGINT),
                    new ColumnMetadata("disk_usage", BIGINT),
                    new ColumnMetadata("collaborators", BIGINT),
                    new ColumnMetadata("billing_email", createUnboundedVarcharType()),
                    new ColumnMetadata("default_repository_permission", createUnboundedVarcharType()),
                    new ColumnMetadata("members_can_create_repositories", BOOLEAN),
                    new ColumnMetadata("two_factor_requirement_enabled", BOOLEAN),
                    new ColumnMetadata("members_allowed_repository_creation_type", createUnboundedVarcharType()),
                    new ColumnMetadata("members_can_create_public_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_private_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_internal_repositories", BOOLEAN),
                    new ColumnMetadata("members_can_create_pages", BOOLEAN)))
            .put("users", ImmutableList.of(
                    new ColumnMetadata("login", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("avatar_url", createUnboundedVarcharType()),
                    new ColumnMetadata("gravatar_id", createUnboundedVarcharType()),
                    new ColumnMetadata("type", createUnboundedVarcharType()),
                    new ColumnMetadata("site_admin", BOOLEAN),
                    new ColumnMetadata("name", createUnboundedVarcharType()),
                    new ColumnMetadata("company", createUnboundedVarcharType()),
                    new ColumnMetadata("blog", createUnboundedVarcharType()),
                    new ColumnMetadata("location", createUnboundedVarcharType()),
                    new ColumnMetadata("email", createUnboundedVarcharType()),
                    new ColumnMetadata("hireable", BOOLEAN),
                    new ColumnMetadata("bio", createUnboundedVarcharType()),
                    new ColumnMetadata("twitter_username", createUnboundedVarcharType()),
                    new ColumnMetadata("public_repos", BIGINT),
                    new ColumnMetadata("public_gists", BIGINT),
                    new ColumnMetadata("followers", BIGINT),
                    new ColumnMetadata("following", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put("repos", ImmutableList.of(
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("name", createUnboundedVarcharType()),
                    new ColumnMetadata("full_name", createUnboundedVarcharType()),
                    new ColumnMetadata("owner_id", BIGINT),
                    new ColumnMetadata("owner_login", createUnboundedVarcharType()),
                    new ColumnMetadata("private", BOOLEAN),
                    new ColumnMetadata("description", createUnboundedVarcharType()),
                    new ColumnMetadata("fork", BOOLEAN),
                    new ColumnMetadata("url", createUnboundedVarcharType())))
            .put("pulls", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("state", createUnboundedVarcharType()),
                    new ColumnMetadata("locked", BOOLEAN),
                    new ColumnMetadata("title", createUnboundedVarcharType()),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("label_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("label_names", new ArrayType(createUnboundedVarcharType())),
                    new ColumnMetadata("milestone_id", BIGINT),
                    new ColumnMetadata("milestone_title", createUnboundedVarcharType()),
                    new ColumnMetadata("active_lock_reason", createUnboundedVarcharType()),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("closed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("merged_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("merge_commit_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("assignee_id", BIGINT),
                    new ColumnMetadata("assignee_login", createUnboundedVarcharType()),
                    new ColumnMetadata("requested_reviewer_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("requested_reviewer_logins", new ArrayType(createUnboundedVarcharType())),
                    new ColumnMetadata("head_ref", createUnboundedVarcharType()),
                    new ColumnMetadata("head_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("base_ref", createUnboundedVarcharType()),
                    new ColumnMetadata("base_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("author_association", createUnboundedVarcharType()),
                    new ColumnMetadata("draft", BOOLEAN)))
            .put("pull_commits", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("sha", createUnboundedVarcharType()),
                    // this column is filled in from request params, it is not returned by the api
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("commit_message", createUnboundedVarcharType()),
                    new ColumnMetadata("commit_tree_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("commit_comments_count", BIGINT),
                    new ColumnMetadata("commit_verified", BOOLEAN),
                    new ColumnMetadata("commit_verification_reason", createUnboundedVarcharType()),
                    new ColumnMetadata("author_name", createUnboundedVarcharType()),
                    new ColumnMetadata("author_email", createUnboundedVarcharType()),
                    new ColumnMetadata("author_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_id", BIGINT),
                    new ColumnMetadata("author_login", createUnboundedVarcharType()),
                    new ColumnMetadata("committer_name", createUnboundedVarcharType()),
                    new ColumnMetadata("committer_email", createUnboundedVarcharType()),
                    new ColumnMetadata("committer_date", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("committer_id", BIGINT),
                    new ColumnMetadata("committer_login", createUnboundedVarcharType()),
                    new ColumnMetadata("parent_shas", new ArrayType(createUnboundedVarcharType()))))
            .put("reviews", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    // this column is filled in from request params, it is not returned by the api
                    new ColumnMetadata("pull_number", BIGINT),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("state", createUnboundedVarcharType()),
                    new ColumnMetadata("submitted_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("commit_id", createUnboundedVarcharType()),
                    new ColumnMetadata("author_association", createUnboundedVarcharType())))
            .put("review_comments", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("pull_request_review_id", BIGINT),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("diff_hunk", createUnboundedVarcharType()),
                    new ColumnMetadata("path", createUnboundedVarcharType()),
                    new ColumnMetadata("position", BIGINT),
                    new ColumnMetadata("original_position", BIGINT),
                    new ColumnMetadata("commit_id", createUnboundedVarcharType()),
                    new ColumnMetadata("original_commit_id", createUnboundedVarcharType()),
                    new ColumnMetadata("in_reply_to_id", BIGINT),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_association", createUnboundedVarcharType()),
                    new ColumnMetadata("start_line", BIGINT),
                    new ColumnMetadata("original_start_line", BIGINT),
                    new ColumnMetadata("start_side", createUnboundedVarcharType()),
                    new ColumnMetadata("line", BIGINT),
                    new ColumnMetadata("original_line", BIGINT),
                    new ColumnMetadata("side", createUnboundedVarcharType())))
            .put("issues", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("state", createUnboundedVarcharType()),
                    new ColumnMetadata("title", createUnboundedVarcharType()),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("label_ids", new ArrayType(BIGINT)),
                    new ColumnMetadata("label_names", new ArrayType(createUnboundedVarcharType())),
                    new ColumnMetadata("assignee_id", BIGINT),
                    new ColumnMetadata("assignee_login", createUnboundedVarcharType()),
                    new ColumnMetadata("milestone_id", BIGINT),
                    new ColumnMetadata("milestone_title", createUnboundedVarcharType()),
                    new ColumnMetadata("locked", BOOLEAN),
                    new ColumnMetadata("active_lock_reason", createUnboundedVarcharType()),
                    new ColumnMetadata("comments", BIGINT),
                    new ColumnMetadata("closed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_association", createUnboundedVarcharType())))
            .put("issue_comments", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_association", createUnboundedVarcharType())))
            .put("runs", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("name", createUnboundedVarcharType()),
                    new ColumnMetadata("node_id", createUnboundedVarcharType()),
                    new ColumnMetadata("head_branch", createUnboundedVarcharType()),
                    new ColumnMetadata("head_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("run_number", BIGINT),
                    new ColumnMetadata("event", createUnboundedVarcharType()),
                    new ColumnMetadata("status", createUnboundedVarcharType()),
                    new ColumnMetadata("conclusion", createUnboundedVarcharType()),
                    new ColumnMetadata("workflow_id", BIGINT),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3))))
            .put("jobs", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("run_id", BIGINT),
                    new ColumnMetadata("node_id", createUnboundedVarcharType()),
                    new ColumnMetadata("head_sha", createUnboundedVarcharType()),
                    new ColumnMetadata("status", createUnboundedVarcharType()),
                    new ColumnMetadata("conclusion", createUnboundedVarcharType()),
                    new ColumnMetadata("started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("completed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("name", createUnboundedVarcharType())))
            .put("steps", ImmutableList.of(
                    new ColumnMetadata("owner", createUnboundedVarcharType()),
                    new ColumnMetadata("repo", createUnboundedVarcharType()),
                    new ColumnMetadata("job_id", BIGINT),
                    new ColumnMetadata("name", createUnboundedVarcharType()),
                    new ColumnMetadata("status", createUnboundedVarcharType()),
                    new ColumnMetadata("conclusion", createUnboundedVarcharType()),
                    new ColumnMetadata("number", BIGINT),
                    new ColumnMetadata("started_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("completed_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)))).build();

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
            "members_can_create_pages boolean" +
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
            "url varchar" +
            "))";

    public static final String PULLS_TABLE_TYPE = "array(row(" +
            "id bigint, " +
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
            "draft boolean" +
            "))";

    public static final String PULL_COMMITS_TABLE_TYPE = "array(row(" +
            "sha varchar, " +
            "pull_number bigint, " +
            "commit_message varchar, " +
            "commit_tree_sha varchar, " +
            "commit_comments_count bigint, " +
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

    public static final String REVIEWS_TABLE_TYPE = "array(row(" +
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
            "author_association varchar" +
            "))";

    public static final String ISSUE_COMMENTS_TABLE_TYPE = "array(row(" +
            "owner varchar, " +
            "repo varchar, " +
            "id bigint, " +
            "body varchar, " +
            "user_id bigint, " +
            "user_login varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "author_association varchar" +
            "))";

    public static final String RUNS_TABLE_TYPE = "array(row(" +
            "id bigint, " +
            "name varchar, " +
            "node_id varchar, " +
            "head_branch varchar, " +
            "head_sha varchar, " +
            "run_number bigint, " +
            "event varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "workflow_id bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone" +
            "))";

    public static final String JOBS_TABLE_TYPE = "array(row(" +
            "id bigint, " +
            "run_id bigint, " +
            "node_id varchar, " +
            "head_sha varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone, " +
            "name varchar" +
            "))";

    public static final String STEPS_TABLE_TYPE = "array(row(" +
            "job_id bigint, " +
            "name varchar, " +
            "status varchar, " +
            "conclusion varchar, " +
            "number bigint, " +
            "created_at timestamp(3) with time zone, " +
            "updated_at timestamp(3) with time zone" +
            "))";

    private final Map<String, Map<String, ColumnHandle>> columnHandles;

    private final Map<String, ? extends FilterApplier> filterAppliers = new ImmutableMap.Builder<String, FilterApplier>()
            .put("pulls", new PullFilter())
            .put("pull_commits", new PullCommitFilter())
            .put("reviews", new ReviewFilter())
            .put("review_comments", new ReviewCommentFilter())
            .put("issues", new IssueFilter())
            .put("issue_comments", new IssueCommentFilter())
            .put("runs", new RunFilter())
            .put("jobs", new JobFilter())
            .put("steps", new StepFilter())
            .put("orgs", new OrgFilter())
            .put("users", new UserFilter())
            .put("repos", new RepoFilter())
            .build();

    public GithubRest(String token)
    {
        this.token = token;

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

    public static GithubService getService()
    {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        // TODO make configurable?
        Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "trino-rest-cache");
        clientBuilder.cache(new Cache(cacheDir.toFile(), 10 * 1024 * 1024));

        if (getLogLevel().intValue() <= Level.FINE.intValue()) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            clientBuilder.addInterceptor(interceptor);
        }

        return new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(clientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(
                        new ObjectMapper()
                                .registerModule(new Jdk8Module())
                                .registerModule(new JavaTimeModule())))
                .build()
                .create(GithubService.class);
    }

    private static Level getLogLevel()
    {
        String loggerName = BaseFunction.class.getName();
        Logger logger = Logger.getLogger(loggerName);
        Level level = logger.getLevel();
        while (level == null) {
            Logger parent = logger.getParent();
            if (parent == null) {
                return Level.OFF;
            }
            level = parent.getLevel();
        }
        return level;
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(SchemaTableName schemaTableName)
    {
        return new ConnectorTableMetadata(
                schemaTableName,
                columns.get(schemaTableName.getTableName()));
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
                .map(name -> new SchemaTableName(SCHEMA_NAME, name))
                .collect(toList());
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(SchemaTablePrefix schemaTablePrefix)
    {
        return columns.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> new SchemaTableName(schemaTablePrefix.getSchema().orElse(""), e.getKey()),
                        Map.Entry::getValue));
    }

    @Override
    public Collection<? extends List<?>> getRows(RestTableHandle table)
    {
        switch (table.getSchemaTableName().getTableName()) {
            case "orgs":
                return getOrgs(table);
            case "users":
                return getUsers(table);
            case "repos":
                return getRepos(table);
            case "pulls":
                return getPulls(table);
            case "pull_commits":
                return getPullCommits(table);
            case "reviews":
                return getReviews(table);
            case "review_comments":
                return getReviewComments(table);
            case "issues":
                return getIssues(table);
            case "issue_comments":
                return getIssueComments(table);
            case "runs":
                return getRuns(table);
            case "jobs":
                return getJobs(table);
            case "steps":
                return getSteps(table);
        }
        return null;
    }

    private Collection<? extends List<?>> getOrgs(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String login = (String) filter.getFilter((RestColumnHandle) columns.get("login"), table.getConstraint());
        return getRow(() -> service.getOrg("Bearer " + token, login), Organization::toRow);
    }

    private Collection<? extends List<?>> getUsers(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String login = (String) filter.getFilter((RestColumnHandle) columns.get("login"), table.getConstraint());
        return getRow(() -> service.getUser("Bearer " + token, login), User::toRow);
    }


    private Collection<? extends List<?>> getRepos(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner_login"), table.getConstraint());
        return getRowsFromPages(
                page -> service.listUserRepos("Bearer " + token, owner, 100, page, "updated"),
                Repository::toRow);
    }

    private Collection<? extends List<?>> getPulls(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        // TODO allow filtering by state (many, comma separated, or all)
        return getRowsFromPages(
                page -> service.listPulls("Bearer " + token, owner, repo, 100, page, "updated", "all"),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getPullCommits(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        long pullNumber = (long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        // TODO allow filtering by state (many, comma separated, or all)
        return getRowsFromPages(
                page -> service.listPullCommits("Bearer " + token, owner, repo, pullNumber, 100, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    item.setPullNumber(pullNumber);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getReviews(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        long pullNumber = (long) filter.getFilter((RestColumnHandle) columns.get("pull_number"), constraint);
        return getRowsFromPages(
                page -> service.listPullReviews("Bearer " + token, owner, repo, pullNumber, 100, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getReviewComments(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint);
        // TODO allow filtering by pull number, this would require using a different endpoint
        return getRowsFromPages(
                page -> service.listReviewComments("Bearer " + token, owner, repo, 100, page, since),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getIssues(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint);
        return getRowsFromPages(
                page -> service.listIssues("Bearer " + token, owner, repo, 100, page, since),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getIssueComments(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        String since = (String) filter.getFilter((RestColumnHandle) columns.get("updated_at"), constraint);
        return getRowsFromPages(
                page -> service.listIssueComments("Bearer " + token, owner, repo, 100, page, since),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getRuns(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        return getRowsFromPagesEnvelope(
                page -> service.listRuns("Bearer " + token, owner, repo, 100, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getJobs(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);
        // TODO this needs to allow pushing down multiple run_id values and make a separate request for each
        return getRowsFromPagesEnvelope(
                page -> service.listJobs("Bearer " + token, owner, repo, "all", 100, page),
                item -> {
                    item.setOwner(owner);
                    item.setRepo(repo);
                    return item.toRow();
                });
    }

    private Collection<? extends List<?>> getSteps(RestTableHandle table)
    {
        String tableName = table.getSchemaTableName().getTableName();
        TupleDomain<ColumnHandle> constraint = table.getConstraint();
        Map<String, ColumnHandle> columns = columnHandles.get(tableName);
        FilterApplier filter = filterAppliers.get(tableName);

        String owner = (String) filter.getFilter((RestColumnHandle) columns.get("owner"), constraint);
        String repo = (String) filter.getFilter((RestColumnHandle) columns.get("repo"), constraint);

        ImmutableList.Builder<Job> jobs = new ImmutableList.Builder<>();

        int page = 1;
        while (true) {
            Response<JobsList> response;
            try {
                response = service.listJobs("Bearer " + token, owner, repo, "all", 100, page++).execute();
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new TrinoException(GENERIC_INTERNAL_ERROR, "Unable to read: " + response.message());
            }
            List<Job> items = Objects.requireNonNull(response.body()).getItems();
            if (items == null || items.size() == 0) {
                break;
            }
            items.forEach(i -> i.setOwner(owner));
            items.forEach(i -> i.setRepo(repo));
            jobs.addAll(items);
        }

        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();
        for (Job job : jobs.build()) {
            Collection<List<?>> steps = job.getSteps().stream().map(Step::toRow).collect(toList());
            result.addAll(steps);
        }
        return result.build();
    }

    private <T> Collection<? extends List<?>> getRow(Supplier<Call<T>> fetcher, Function<T, List<?>> mapper)
    {
        Response<T> response;
        try {
            response = fetcher.get().execute();
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        if (response.code() == HTTP_NOT_FOUND) {
            return ImmutableList.of();
        }
        if (!response.isSuccessful()) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "Unable to read: " + response.message());
        }
        return ImmutableList.of(mapper.apply(response.body()));
    }

    // TODO this abomination should be in a base class implementing a cursor
    private <T> Collection<? extends List<?>> getRowsFromPages(IntFunction<Call<List<T>>> fetcher, Function<T, List<?>> mapper)
    {
        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();

        int page = 1;
        while (true) {
            Response<List<T>> response;
            try {
                response = fetcher.apply(page++).execute();
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new TrinoException(GENERIC_INTERNAL_ERROR, "Unable to read: " + response.message());
            }
            List<T> items = response.body();
            if (items == null || items.size() == 0) {
                break;
            }
            result.addAll(items.stream().map(mapper).collect(toList()));
        }

        return result.build();
    }

    // TODO this abomination is even worse
    private <T, E extends Envelope<T>> Collection<? extends List<?>> getRowsFromPagesEnvelope(IntFunction<Call<E>> fetcher, Function<T, List<?>> mapper)
    {
        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();

        int page = 1;
        while (true) {
            Response<E> response;
            try {
                response = fetcher.apply(page++).execute();
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new TrinoException(GENERIC_INTERNAL_ERROR, "Unable to read: " + response.message());
            }
            List<T> items = Objects.requireNonNull(response.body()).getItems();
            if (items == null || items.size() == 0) {
                break;
            }
            result.addAll(items.stream().map(mapper).collect(toList()));
        }

        return result.build();
    }

    @Override
    public Consumer<List> createRowSink(SchemaTableName schemaTableName)
    {
        throw new IllegalStateException("This connector does not support write");
    }

    public static String getSqlType(String tableName)
    {
        return columns.get(tableName)
                .stream()
                .map(column -> column.getName() + " " + column.getType().getDisplayName())
                .collect(Collectors.joining(", ", "ARRAY(ROW(", "))"));
    }

    public static RowType getRowType(String tableName)
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
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(ConnectorSession session, ConnectorTableHandle table, Constraint constraint)
    {
        RestTableHandle restTable = (RestTableHandle) table;
        String tableName = restTable.getSchemaTableName().getTableName();

        FilterApplier filterApplier = filterAppliers.get(tableName);
        if (filterApplier == null) {
            return Optional.empty();
        }
        return filterApplier.applyFilter(restTable, columnHandles.get(tableName), filterApplier.getSupportedFilters(), constraint.getSummary());
    }
}

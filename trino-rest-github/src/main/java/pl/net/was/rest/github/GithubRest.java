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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import pl.net.was.rest.Rest;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestTableHandle;
import pl.net.was.rest.github.model.Issue;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private final String owner;
    private final String repo;
    private final GithubService service = new Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(GithubService.class);

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
                    new ColumnMetadata("id", BIGINT),
                    new ColumnMetadata("body", createUnboundedVarcharType()),
                    new ColumnMetadata("user_id", BIGINT),
                    new ColumnMetadata("user_login", createUnboundedVarcharType()),
                    new ColumnMetadata("created_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("updated_at", TimestampWithTimeZoneType.createTimestampWithTimeZoneType(3)),
                    new ColumnMetadata("author_association", createUnboundedVarcharType())))
            .put("runs", ImmutableList.of(
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
                    // this column is filled in from request params, it is not returned by the api
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

    public GithubRest(String token, String owner, String repo)
    {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
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
        SchemaTableName schemaTableName = table.getSchemaTableName();
        // don't implement any API that has pagination, just expose functions to fetch that data
        // and put it into tables in other persistent dbs
        // TODO support predicate pushdown and allow more endpoints when all required params are present
        // TODO split manager should generate one split per page, but it has to know how many results there are
        // TODO maybe call it with per_page=0 to get total? but also account for rate limits
        switch (schemaTableName.getTableName()) {
            case "orgs":
                throw new UnsupportedOperationException("Use orgs or org functions instead");
            case "repos":
                throw new UnsupportedOperationException("Use repos, org_repos or user_repos functions instead");
            case "pulls":
                throw new UnsupportedOperationException("Use pulls function instead");
            case "pull_commits":
                throw new UnsupportedOperationException("Use pull_commits function instead");
            case "reviews":
                throw new UnsupportedOperationException("Use reviews function instead");
            case "review_comments":
                throw new UnsupportedOperationException("Use review_comments function instead");
            case "issues":
                // this is just an example and should not be used, see comments above
                return getIssues(table.getConstraint());
            case "issue_comments":
                throw new UnsupportedOperationException("Use issue_comments function instead");
            case "runs":
                throw new UnsupportedOperationException("Use runs function instead");
            case "jobs":
                throw new UnsupportedOperationException("Use jobs function instead");
            case "steps":
                throw new UnsupportedOperationException("Use steps function instead");
        }
        return null;
    }

    private Collection<? extends List<?>> getIssues(TupleDomain<ColumnHandle> constraint)
    {
        ImmutableList.Builder<List<?>> result = new ImmutableList.Builder<>();

        int page = 1;
        while (true) {
            Response<List<Issue>> response;
            try {
                // TODO apply constraint
                response = service.listIssues("Bearer " + token, owner, repo, 100, page++, "0000-00-00T00:00:00Z").execute();
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Unable to read: " + response.message());
            }
            List<Issue> issues = response.body();
            if (issues == null || issues.size() == 0) {
                break;
            }
            result.addAll(issues.stream().map(Issue::toRow).collect(toList()));
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
        TupleDomain<ColumnHandle> currentConstraint = restTable.getConstraint();

        TupleDomain<ColumnHandle> newConstraint = TupleDomain.all();
        TupleDomain<ColumnHandle> unenforcedConstraint = constraint.getSummary();

        Map<String, ColumnHandle> columns = getTableMetadata(restTable.getSchemaTableName())
                .getColumns()
                .stream()
                .collect(toMap(
                        ColumnMetadata::getName,
                        column -> new RestColumnHandle(column.getName(), column.getType())));

        // TODO this should be a map to a function to return both values
        if (restTable.getSchemaTableName().getTableName().equals("issues")) {
            newConstraint = constraint.getSummary().filter(
                    (columnHandle, domain) -> columnHandle.equals(columns.get("commit_id")));
            unenforcedConstraint = constraint.getSummary().filter(
                    (columnHandle, domain) -> !columnHandle.equals(columns.get("commit_id")));
        }

        if (currentConstraint.isAll() && newConstraint.isAll()) {
            return Optional.empty();
        }
        if (currentConstraint.isAll()) {
            currentConstraint = newConstraint;
        }
        else if (!newConstraint.isAll()) {
            // TODO validate if there are multiple values for a filter that supports only one value
            // for example, when filtering by status of open and closed, ignore such constraint
            currentConstraint = TupleDomain.columnWiseUnion(currentConstraint, newConstraint);
        }

        return Optional.of(new ConstraintApplicationResult<>(
                new RestTableHandle(
                        restTable.getSchemaTableName(),
                        currentConstraint),
                unenforcedConstraint));
    }
}

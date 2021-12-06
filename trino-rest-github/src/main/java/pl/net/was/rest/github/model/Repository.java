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

package pl.net.was.rest.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.type.MapType;
import io.trino.spi.type.TypeOperators;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.VARCHAR;

@SuppressWarnings("unused")
public class Repository
        extends BaseBlockWriter
{
    private final long id;
    private final String nodeId;
    private final String name;
    private final String fullName;
    private final User owner;
    private final boolean isPrivate;
    private final String htmlUrl;
    private final String description;
    private final boolean fork;
    private final String url;
    private final String archiveUrl;
    private final String assigneesUrl;
    private final String blobsUrl;
    private final String branchesUrl;
    private final String collaboratorsUrl;
    private final String commentsUrl;
    private final String commitsUrl;
    private final String compareUrl;
    private final String contentsUrl;
    private final String contributorsUrl;
    private final String deploymentsUrl;
    private final String downloadsUrl;
    private final String eventsUrl;
    private final String forksUrl;
    private final String gitCommitsUrl;
    private final String gitRefsUrl;
    private final String gitTagsUrl;
    private final String gitUrl;
    private final String issueCommentUrl;
    private final String issueEventsUrl;
    private final String issuesUrl;
    private final String keysUrl;
    private final String labelsUrl;
    private final String languagesUrl;
    private final String mergesUrl;
    private final String milestonesUrl;
    private final String notificationsUrl;
    private final String pullsUrl;
    private final String releasesUrl;
    private final String sshUrl;
    private final String stargazersUrl;
    private final String statusesUrl;
    private final String subscribersUrl;
    private final String subscriptionUrl;
    private final String tagsUrl;
    private final String teamsUrl;
    private final String treesUrl;
    private final String cloneUrl;
    private final String mirrorUrl;
    private final String hooksUrl;
    private final String svnUrl;
    private final String homepage;
    private final String language;
    private final long forksCount;
    private final long forks;
    private final long stargazersCount;
    private final long watchersCount;
    private final long watchers;
    private final long size;
    private final String defaultBranch;
    private final long openIssuesCount;
    private final long openIssues;
    private final boolean isTemplate;
    private final String[] topics;
    private final boolean hasIssues;
    private final boolean hasProjects;
    private final boolean hasWiki;
    private final boolean hasPages;
    private final boolean hasDownloads;
    private final boolean archived;
    private final boolean disabled;
    private final String visibility;
    private final ZonedDateTime pushedAt;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final Map<String, Boolean> permissions;
    private final boolean allowRebaseMerge;
    private final Repository templateRepository;
    private final String tempCloneToken;
    private final boolean allowSquashMerge;
    private final boolean allowAutoMerge;
    private final boolean deleteBranchOnMerge;
    private final boolean allowMergeCommit;
    private final boolean allowForking;
    private final long subscribersCount;
    private final long networkCount;
    private final License license;
    private final Organization organization;
    private final Repository parent;
    private final Repository source;

    public Repository(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("owner") User owner,
            @JsonProperty("private") boolean isPrivate,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("description") String description,
            @JsonProperty("fork") boolean fork,
            @JsonProperty("url") String url,
            @JsonProperty("archive_url") String archiveUrl,
            @JsonProperty("assignees_url") String assigneesUrl,
            @JsonProperty("blobs_url") String blobsUrl,
            @JsonProperty("branches_url") String branchesUrl,
            @JsonProperty("collaborators_url") String collaboratorsUrl,
            @JsonProperty("comments_url") String commentsUrl,
            @JsonProperty("commits_url") String commitsUrl,
            @JsonProperty("compare_url") String compareUrl,
            @JsonProperty("contents_url") String contentsUrl,
            @JsonProperty("contributors_url") String contributorsUrl,
            @JsonProperty("deployments_url") String deploymentsUrl,
            @JsonProperty("downloads_url") String downloadsUrl,
            @JsonProperty("events_url") String eventsUrl,
            @JsonProperty("forks_url") String forksUrl,
            @JsonProperty("git_commits_url") String gitCommitsUrl,
            @JsonProperty("git_refs_url") String gitRefsUrl,
            @JsonProperty("git_tags_url") String gitTagsUrl,
            @JsonProperty("git_url") String gitUrl,
            @JsonProperty("issue_comment_url") String issueCommentUrl,
            @JsonProperty("issue_events_url") String issueEventsUrl,
            @JsonProperty("issues_url") String issuesUrl,
            @JsonProperty("keys_url") String keysUrl,
            @JsonProperty("labels_url") String labelsUrl,
            @JsonProperty("languages_url") String languagesUrl,
            @JsonProperty("merges_url") String mergesUrl,
            @JsonProperty("milestones_url") String milestonesUrl,
            @JsonProperty("notifications_url") String notificationsUrl,
            @JsonProperty("pulls_url") String pullsUrl,
            @JsonProperty("releases_url") String releasesUrl,
            @JsonProperty("ssh_url") String sshUrl,
            @JsonProperty("stargazers_url") String stargazersUrl,
            @JsonProperty("statuses_url") String statusesUrl,
            @JsonProperty("subscribers_url") String subscribersUrl,
            @JsonProperty("subscription_url") String subscriptionUrl,
            @JsonProperty("tags_url") String tagsUrl,
            @JsonProperty("teams_url") String teamsUrl,
            @JsonProperty("trees_url") String treesUrl,
            @JsonProperty("clone_url") String cloneUrl,
            @JsonProperty("mirror_url") String mirrorUrl,
            @JsonProperty("hooks_url") String hooksUrl,
            @JsonProperty("svn_url") String svnUrl,
            @JsonProperty("homepage") String homepage,
            @JsonProperty("language") String language,
            @JsonProperty("forks_count") long forksCount,
            @JsonProperty("forks") long forks,
            @JsonProperty("stargazers_count") long stargazersCount,
            @JsonProperty("watchers_count") long watchersCount,
            @JsonProperty("watchers") long watchers,
            @JsonProperty("size") long size,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("open_issues_count") long openIssuesCount,
            @JsonProperty("open_issues") long openIssues,
            @JsonProperty("is_template") boolean isTemplate,
            @JsonProperty("topics") String[] topics,
            @JsonProperty("has_issues") boolean hasIssues,
            @JsonProperty("has_projects") boolean hasProjects,
            @JsonProperty("has_wiki") boolean hasWiki,
            @JsonProperty("has_pages") boolean hasPages,
            @JsonProperty("has_downloads") boolean hasDownloads,
            @JsonProperty("archived") boolean archived,
            @JsonProperty("disabled") boolean disabled,
            @JsonProperty("visibility") String visibility,
            @JsonProperty("pushed_at") ZonedDateTime pushedAt,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("permissions") Map<String, Boolean> permissions,
            @JsonProperty("allow_rebase_merge") boolean allowRebaseMerge,
            @JsonProperty("template_repository") Repository templateRepository,
            @JsonProperty("temp_clone_token") String tempCloneToken,
            @JsonProperty("allow_squash_merge") boolean allowSquashMerge,
            @JsonProperty("allow_auto_merge") boolean allowAutoMerge,
            @JsonProperty("delete_branch_on_merge") boolean deleteBranchOnMerge,
            @JsonProperty("allow_merge_commit") boolean allowMergeCommit,
            @JsonProperty("allow_forking") boolean allowForking,
            @JsonProperty("subscribers_count") long subscribersCount,
            @JsonProperty("network_count") long networkCount,
            @JsonProperty("license") License license,
            @JsonProperty("organization") Organization organization,
            @JsonProperty("parent") Repository parent,
            @JsonProperty("source") Repository source)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.name = name;
        this.fullName = fullName;
        this.owner = owner;
        this.isPrivate = isPrivate;
        this.htmlUrl = htmlUrl;
        this.description = description;
        this.fork = fork;
        this.url = url;
        this.archiveUrl = archiveUrl;
        this.assigneesUrl = assigneesUrl;
        this.blobsUrl = blobsUrl;
        this.branchesUrl = branchesUrl;
        this.collaboratorsUrl = collaboratorsUrl;
        this.commentsUrl = commentsUrl;
        this.commitsUrl = commitsUrl;
        this.compareUrl = compareUrl;
        this.contentsUrl = contentsUrl;
        this.contributorsUrl = contributorsUrl;
        this.deploymentsUrl = deploymentsUrl;
        this.downloadsUrl = downloadsUrl;
        this.eventsUrl = eventsUrl;
        this.forksUrl = forksUrl;
        this.gitCommitsUrl = gitCommitsUrl;
        this.gitRefsUrl = gitRefsUrl;
        this.gitTagsUrl = gitTagsUrl;
        this.gitUrl = gitUrl;
        this.issueCommentUrl = issueCommentUrl;
        this.issueEventsUrl = issueEventsUrl;
        this.issuesUrl = issuesUrl;
        this.keysUrl = keysUrl;
        this.labelsUrl = labelsUrl;
        this.languagesUrl = languagesUrl;
        this.mergesUrl = mergesUrl;
        this.milestonesUrl = milestonesUrl;
        this.notificationsUrl = notificationsUrl;
        this.pullsUrl = pullsUrl;
        this.releasesUrl = releasesUrl;
        this.sshUrl = sshUrl;
        this.stargazersUrl = stargazersUrl;
        this.statusesUrl = statusesUrl;
        this.subscribersUrl = subscribersUrl;
        this.subscriptionUrl = subscriptionUrl;
        this.tagsUrl = tagsUrl;
        this.teamsUrl = teamsUrl;
        this.treesUrl = treesUrl;
        this.cloneUrl = cloneUrl;
        this.mirrorUrl = mirrorUrl;
        this.hooksUrl = hooksUrl;
        this.svnUrl = svnUrl;
        this.homepage = homepage;
        this.language = language;
        this.forksCount = forksCount;
        this.forks = forks;
        this.stargazersCount = stargazersCount;
        this.watchersCount = watchersCount;
        this.watchers = watchers;
        this.size = size;
        this.defaultBranch = defaultBranch;
        this.openIssuesCount = openIssuesCount;
        this.openIssues = openIssues;
        this.isTemplate = isTemplate;
        this.topics = topics;
        this.hasIssues = hasIssues;
        this.hasProjects = hasProjects;
        this.hasWiki = hasWiki;
        this.hasPages = hasPages;
        this.hasDownloads = hasDownloads;
        this.archived = archived;
        this.disabled = disabled;
        this.visibility = visibility;
        this.pushedAt = pushedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.permissions = permissions;
        this.allowRebaseMerge = allowRebaseMerge;
        this.templateRepository = templateRepository;
        this.tempCloneToken = tempCloneToken;
        this.allowSquashMerge = allowSquashMerge;
        this.allowAutoMerge = allowAutoMerge;
        this.deleteBranchOnMerge = deleteBranchOnMerge;
        this.allowMergeCommit = allowMergeCommit;
        this.allowForking = allowForking;
        this.subscribersCount = subscribersCount;
        this.networkCount = networkCount;
        this.license = license;
        this.organization = organization;
        this.parent = parent;
        this.source = source;
    }

    public long getOpenIssuesCount()
    {
        return openIssuesCount;
    }

    public List<?> toRow()
    {
        BlockBuilder topics = VARCHAR.createBlockBuilder(null, this.topics != null ? this.topics.length : 0);
        if (this.topics != null) {
            for (String topic : this.topics) {
                VARCHAR.writeString(topics, topic);
            }
        }
        MapType mapType = new MapType(VARCHAR, BOOLEAN, new TypeOperators());
        BlockBuilder permissions = mapType.createBlockBuilder(null, this.permissions != null ? this.permissions.size() : 0);
        if (this.permissions != null) {
            BlockBuilder builder = permissions.beginBlockEntry();
            for (Map.Entry<String, Boolean> permission : this.permissions.entrySet()) {
                VARCHAR.writeString(builder, permission.getKey());
                BOOLEAN.writeBoolean(builder, permission.getValue());
            }
            permissions.closeEntry();
        }
        // TODO allow nulls
        return ImmutableList.of(
                id,
                name != null ? name : "",
                fullName != null ? fullName : "",
                owner.getId(),
                owner.getLogin(),
                isPrivate,
                description != null ? description : "",
                fork,
                homepage != null ? homepage : "",
                url != null ? url : "",
                forksCount,
                stargazersCount,
                watchersCount,
                size,
                defaultBranch,
                openIssuesCount,
                isTemplate,
                topics.build(),
                hasIssues,
                hasProjects,
                hasWiki,
                hasPages,
                hasDownloads,
                archived,
                disabled,
                visibility != null ? visibility : "",
                packTimestamp(pushedAt),
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                mapType.getObject(permissions, 0));
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        BIGINT.writeLong(rowBuilder, id);
        writeString(rowBuilder, name);
        writeString(rowBuilder, fullName);
        BIGINT.writeLong(rowBuilder, owner.getId());
        writeString(rowBuilder, owner.getLogin());
        BOOLEAN.writeBoolean(rowBuilder, isPrivate);
        writeString(rowBuilder, description);
        BOOLEAN.writeBoolean(rowBuilder, fork);
        writeString(rowBuilder, homepage);
        writeString(rowBuilder, url);
        BIGINT.writeLong(rowBuilder, forksCount);
        BIGINT.writeLong(rowBuilder, stargazersCount);
        BIGINT.writeLong(rowBuilder, watchersCount);
        BIGINT.writeLong(rowBuilder, size);
        writeString(rowBuilder, defaultBranch);
        BIGINT.writeLong(rowBuilder, openIssuesCount);
        BOOLEAN.writeBoolean(rowBuilder, isTemplate);

        BlockBuilder topics = VARCHAR.createBlockBuilder(null, this.topics != null ? this.topics.length : 0);
        if (this.topics != null) {
            for (String topic : this.topics) {
                VARCHAR.writeString(topics, topic);
            }
        }
        rowBuilder.appendStructure(topics.build());
        BOOLEAN.writeBoolean(rowBuilder, hasIssues);
        BOOLEAN.writeBoolean(rowBuilder, hasProjects);
        BOOLEAN.writeBoolean(rowBuilder, hasWiki);
        BOOLEAN.writeBoolean(rowBuilder, hasPages);
        BOOLEAN.writeBoolean(rowBuilder, hasDownloads);
        BOOLEAN.writeBoolean(rowBuilder, archived);
        BOOLEAN.writeBoolean(rowBuilder, disabled);
        writeString(rowBuilder, visibility);
        writeTimestamp(rowBuilder, pushedAt);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);

        MapType mapType = new MapType(VARCHAR, BOOLEAN, new TypeOperators());
        BlockBuilder permissions = mapType.createBlockBuilder(null, this.permissions != null ? this.permissions.size() : 0);
        if (this.permissions != null) {
            BlockBuilder builder = permissions.beginBlockEntry();
            for (Map.Entry<String, Boolean> permission : this.permissions.entrySet()) {
                VARCHAR.writeString(builder, permission.getKey());
                BOOLEAN.writeBoolean(builder, permission.getValue());
            }
            permissions.closeEntry();
            rowBuilder.appendStructure(mapType.getObject(permissions, 0));
        }
        else {
            rowBuilder.appendNull();
        }
    }
}

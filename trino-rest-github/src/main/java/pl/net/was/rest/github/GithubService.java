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

import okhttp3.ResponseBody;
import pl.net.was.rest.github.model.Issue;
import pl.net.was.rest.github.model.IssueComment;
import pl.net.was.rest.github.model.JobsList;
import pl.net.was.rest.github.model.Organization;
import pl.net.was.rest.github.model.Pull;
import pl.net.was.rest.github.model.PullCommit;
import pl.net.was.rest.github.model.Repository;
import pl.net.was.rest.github.model.Review;
import pl.net.was.rest.github.model.ReviewComment;
import pl.net.was.rest.github.model.RunsList;
import pl.net.was.rest.github.model.User;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface GithubService
{
    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/pulls")
    Call<List<Pull>> listPulls(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("sort") String sort,
            @Query("state") String state);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/pulls/{pull_number}/commits")
    Call<List<PullCommit>> listPullCommits(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("pull_number") long pullNumber,
            @Query("per_page") int perPage,
            @Query("page") int page);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    Call<List<Review>> listPullReviews(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("pull_number") long pullNumber,
            @Query("per_page") int perPage,
            @Query("page") int page);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/pulls/comments")
    Call<List<ReviewComment>> listReviewComments(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("since") String since);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/issues")
    Call<List<Issue>> listIssues(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("since") String since);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/issues/comments")
    Call<List<IssueComment>> listIssueComments(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("since") String since);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/actions/runs")
    Call<RunsList> listRuns(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    Call<JobsList> listRunJobs(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("run_id") long runId,
            @Query("filter") String filter,
            @Query("per_page") int perPage,
            @Query("page") int page);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    Call<JobsList> listJobs(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("filter") String filter,
            @Query("per_page") int perPage,
            @Query("page") int page);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    Call<ResponseBody> jobLogs(
            @Header("Authorization") String auth,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("job_id") long jobId);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/organizations")
    Call<List<Organization>> listOrgs(
            @Header("Authorization") String auth,
            @Query("per_page") int perPage,
            @Query("since") String since);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/orgs/{org}")
    Call<Organization> getOrg(
            @Header("Authorization") String auth,
            @Path("org") String org);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/repositories")
    Call<List<Repository>> listRepos(
            @Header("Authorization") String auth,
            @Query("since") long sinceId);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/orgs/{org}/repos")
    Call<List<Repository>> listOrgRepos(
            @Header("Authorization") String auth,
            @Path("org") String org,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("sort") String sort);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/users/{username}/repos")
    Call<List<Repository>> listUserRepos(
            @Header("Authorization") String auth,
            @Path("username") String username,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("sort") String sort);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/users")
    Call<List<User>> listUsers(
            @Header("Authorization") String auth,
            @Query("per_page") int perPage,
            @Query("since") String since);

    @Headers("accept: application/vnd.github.v3+json")
    @GET("/users/{username}")
    Call<User> getUser(
            @Header("Authorization") String auth,
            @Path("username") String username);
}

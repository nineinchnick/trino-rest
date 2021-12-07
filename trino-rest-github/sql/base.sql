/*
This file has been generated by running the following command in the [usql](https://github.com/xo/usql/) program
SELECT
  'SHOW CREATE TABLE ' || table_name
FROM hive.information_schema.tables
WHERE table_schema = 'default'
\gexec
*/

CREATE TABLE artifacts (
   owner varchar,
   repo varchar,
   run_id bigint,
   id bigint,
   size_in_bytes bigint,
   name varchar,
   url varchar,
   archive_download_url varchar,
   expired boolean,
   created_at timestamp(3),
   expires_at timestamp(3),
   updated_at timestamp(3),
   filename varchar,
   path varchar,
   mimetype varchar,
   file_size_in_bytes bigint,
   part_number integer,
   contents varbinary
)
WITH (
   format = 'ORC'
);
CREATE TABLE check_run_annotations (
   owner varchar,
   repo varchar,
   check_run_id bigint,
   path varchar,
   start_line integer,
   end_line integer,
   start_column integer,
   end_column integer,
   annotation_level varchar,
   title varchar,
   message varchar,
   raw_details varchar,
   blob_href varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE check_runs (
   owner varchar,
   repo varchar,
   ref varchar,
   id bigint,
   head_sha varchar,
   external_id varchar,
   url varchar,
   html_url varchar,
   details_url varchar,
   status varchar,
   conclusion varchar,
   started_at timestamp(3),
   completed_at timestamp(3),
   output_title varchar,
   output_summary varchar,
   output_text varchar,
   annotations_count bigint,
   annotations_url varchar,
   name varchar,
   check_suite_id bigint,
   app_id bigint,
   app_slug varchar,
   app_name varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE check_suites (
   owner varchar,
   repo varchar,
   ref varchar,
   id bigint,
   head_branch varchar,
   head_sha varchar,
   status varchar,
   conclusion varchar,
   url varchar,
   before varchar,
   after varchar,
   pull_request_numbers array(bigint),
   app_id bigint,
   app_slug varchar,
   app_name varchar,
   created_at timestamp(3),
   updated_at timestamp(3),
   latest_check_runs_count bigint,
   check_runs_url varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE issue_comments (
   owner varchar,
   repo varchar,
   id bigint,
   body varchar,
   user_id bigint,
   user_login varchar,
   created_at timestamp(3),
   updated_at timestamp(3),
   author_association varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE issues (
   owner varchar,
   repo varchar,
   id bigint,
   number bigint,
   state varchar,
   title varchar,
   body varchar,
   user_id bigint,
   user_login varchar,
   label_ids array(bigint),
   label_names array(varchar),
   assignee_id bigint,
   assignee_login varchar,
   milestone_id bigint,
   milestone_title varchar,
   locked boolean,
   active_lock_reason varchar,
   comments bigint,
   closed_at timestamp(3),
   created_at timestamp(3),
   updated_at timestamp(3),
   author_association varchar,
   draft boolean
)
WITH (
   format = 'ORC'
);
CREATE TABLE jobs (
   owner varchar,
   repo varchar,
   id bigint,
   run_id bigint,
   run_attempt integer,
   node_id varchar,
   head_sha varchar,
   status varchar,
   conclusion varchar,
   started_at timestamp(3),
   completed_at timestamp(3),
   name varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE orgs (
   login varchar,
   id bigint,
   description varchar,
   name varchar,
   company varchar,
   blog varchar,
   location varchar,
   email varchar,
   twitter_username varchar,
   is_verified boolean,
   has_organization_projects boolean,
   has_repository_projects boolean,
   public_repos bigint,
   public_gists bigint,
   followers bigint,
   following bigint,
   created_at timestamp(3),
   updated_at timestamp(3),
   type varchar,
   total_private_repos bigint,
   owned_private_repos bigint,
   private_gists bigint,
   disk_usage bigint,
   collaborators bigint,
   billing_email varchar,
   default_repository_permission varchar,
   members_can_create_repositories boolean,
   two_factor_requirement_enabled boolean,
   members_allowed_repository_creation_type varchar,
   members_can_create_public_repositories boolean,
   members_can_create_private_repositories boolean,
   members_can_create_internal_repositories boolean,
   members_can_create_pages boolean,
   members_can_create_public_pages boolean,
   members_can_create_private_pages boolean
)
WITH (
   format = 'ORC'
);
CREATE TABLE pull_commits (
   owner varchar,
   repo varchar,
   sha varchar,
   pull_number bigint,
   commit_message varchar,
   commit_tree_sha varchar,
   commit_comment_count bigint,
   commit_verified boolean,
   commit_verification_reason varchar,
   author_name varchar,
   author_email varchar,
   author_date timestamp(3),
   author_id bigint,
   author_login varchar,
   committer_name varchar,
   committer_email varchar,
   committer_date timestamp(3),
   committer_id bigint,
   committer_login varchar,
   parent_shas array(varchar)
)
WITH (
   format = 'ORC'
);
CREATE TABLE pulls (
   owner varchar,
   repo varchar,
   id bigint,
   number bigint,
   state varchar,
   locked boolean,
   title varchar,
   user_id bigint,
   user_login varchar,
   body varchar,
   label_ids array(bigint),
   label_names array(varchar),
   milestone_id bigint,
   milestone_title varchar,
   active_lock_reason varchar,
   created_at timestamp(3),
   updated_at timestamp(3),
   closed_at timestamp(3),
   merged_at timestamp(3),
   merge_commit_sha varchar,
   assignee_id bigint,
   assignee_login varchar,
   requested_reviewer_ids array(bigint),
   requested_reviewer_logins array(varchar),
   head_ref varchar,
   head_sha varchar,
   base_ref varchar,
   base_sha varchar,
   author_association varchar,
   draft boolean
)
WITH (
   format = 'ORC'
);
CREATE TABLE repos (
   id bigint,
   name varchar,
   full_name varchar,
   owner_id bigint,
   owner_login varchar,
   private boolean,
   description varchar,
   fork boolean,
   homepage varchar,
   url varchar,
   forks_count bigint,
   stargazers_count bigint,
   watchers_count bigint,
   size bigint,
   default_branch varchar,
   open_issues_count bigint,
   is_template boolean,
   topics array(varchar),
   has_issues boolean,
   has_projects boolean,
   has_wiki boolean,
   has_pages boolean,
   has_downloads boolean,
   archived boolean,
   disabled boolean,
   visibility varchar,
   pushed_at timestamp(3),
   created_at timestamp(3),
   updated_at timestamp(3),
   permissions map(varchar, boolean)
)
WITH (
   format = 'ORC'
);
CREATE TABLE review_comments (
   owner varchar,
   repo varchar,
   pull_request_review_id bigint,
   id bigint,
   diff_hunk varchar,
   path varchar,
   position bigint,
   original_position bigint,
   commit_id varchar,
   original_commit_id varchar,
   in_reply_to_id bigint,
   user_id bigint,
   user_login varchar,
   body varchar,
   created_at timestamp(3),
   updated_at timestamp(3),
   author_association varchar,
   start_line bigint,
   original_start_line bigint,
   start_side varchar,
   line bigint,
   original_line bigint,
   side varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE reviews (
   owner varchar,
   repo varchar,
   id bigint,
   pull_number bigint,
   user_id bigint,
   user_login varchar,
   body varchar,
   state varchar,
   submitted_at timestamp(3),
   commit_id varchar,
   author_association varchar
)
WITH (
   format = 'ORC'
);
CREATE TABLE runners (
   org varchar,
   owner varchar,
   repo varchar,
   id bigint,
   name varchar,
   os varchar,
   status varchar,
   busy boolean,
   label_ids array(bigint),
   label_names array(varchar)
)
WITH (
   format = 'ORC'
);
CREATE TABLE runs (
   owner varchar,
   repo varchar,
   id bigint,
   name varchar,
   node_id varchar,
   check_suite_id bigint,
   check_suite_node_id varchar,
   head_branch varchar,
   head_sha varchar,
   run_number bigint,
   run_attempt integer,
   event varchar,
   status varchar,
   conclusion varchar,
   workflow_id bigint,
   created_at timestamp(3),
   updated_at timestamp(3),
   run_started_at timestamp(3)
)
WITH (
   format = 'ORC'
);
CREATE TABLE steps (
   owner varchar,
   repo varchar,
   run_id bigint,
   run_attempt integer,
   job_id bigint,
   name varchar,
   status varchar,
   conclusion varchar,
   number bigint,
   started_at timestamp(3),
   completed_at timestamp(3)
)
WITH (
   format = 'ORC'
);
CREATE TABLE users (
   login varchar,
   id bigint,
   avatar_url varchar,
   gravatar_id varchar,
   type varchar,
   site_admin boolean,
   name varchar,
   company varchar,
   blog varchar,
   location varchar,
   email varchar,
   hireable boolean,
   bio varchar,
   twitter_username varchar,
   public_repos bigint,
   public_gists bigint,
   followers bigint,
   following bigint,
   created_at timestamp(3),
   updated_at timestamp(3)
)
WITH (
   format = 'ORC'
);
CREATE TABLE workflows (
   owner varchar,
   repo varchar,
   id bigint,
   node_id varchar,
   name varchar,
   path varchar,
   state varchar,
   created_at timestamp(3),
   updated_at timestamp(3),
   url varchar,
   html_url varchar,
   badge_url varchar
)
WITH (
   format = 'ORC'
)

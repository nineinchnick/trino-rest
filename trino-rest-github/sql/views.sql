CREATE OR REPLACE VIEW unique_pulls SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, id, max(updated_at) AS updated_at
  FROM pulls
  GROUP BY owner, repo, id
)
SELECT p.*
FROM pulls p
JOIN latest ON (latest.owner, latest.repo, latest.id, latest.updated_at) = (p.owner, p.repo, p.id, p.updated_at);

CREATE OR REPLACE VIEW unique_pull_commits SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, pull_number, parent_shas, max(committer_date) AS committer_date
  FROM pull_commits
  GROUP BY owner, repo, pull_number, parent_shas
)
SELECT c.*
FROM pull_commits c
JOIN latest ON (latest.owner, latest.repo, latest.pull_number, latest.parent_shas, latest.committer_date) = (c.owner, c.repo, c.pull_number, c.parent_shas, c.committer_date);

CREATE OR REPLACE VIEW unique_pull_stats SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, pull_number, max(updated_at) AS updated_at
  FROM pull_stats
  GROUP BY owner, repo, pull_number
)
SELECT s.*
FROM pull_stats s
JOIN latest ON (latest.owner, latest.repo, latest.pull_number, latest.updated_at) = (s.owner, s.repo, s.pull_number, s.updated_at);

CREATE OR REPLACE VIEW unique_review_comments SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, id, max(updated_at) AS updated_at
  FROM review_comments
  GROUP BY owner, repo, id
)
SELECT r.*
FROM review_comments r
JOIN latest ON (latest.owner, latest.repo, latest.id, latest.updated_at) = (r.owner, r.repo, r.id, r.updated_at);

CREATE OR REPLACE VIEW unique_issues SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, id, max(updated_at) AS updated_at
  FROM issues
  GROUP BY owner, repo, id
)
SELECT i.*
FROM issues i
JOIN latest ON (latest.owner, latest.repo, latest.id, latest.updated_at) = (i.owner, i.repo, i.id, i.updated_at);

CREATE OR REPLACE VIEW unique_issue_comments SECURITY INVOKER AS
WITH latest AS (
  SELECT owner, repo, id, max(updated_at) AS updated_at
  FROM issue_comments
  GROUP BY owner, repo, id
)
SELECT i.*
FROM issue_comments i
JOIN latest ON (latest.owner, latest.repo, latest.id, latest.updated_at) = (i.owner, i.repo, i.id, i.updated_at);

CREATE OR REPLACE VIEW latest_teams SECURITY INVOKER AS
SELECT org, id, node_id, url, html_url, name, slug, description, privacy, permission, members_url, repositories_url, parent_id, parent_slug
FROM timestamped_teams
GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
HAVING max(removed_at) IS NULL OR max(removed_at) < max(created_at);

CREATE OR REPLACE VIEW latest_members SECURITY INVOKER AS
SELECT org, team_slug, login, id, avatar_url, gravatar_id, type, site_admin
FROM timestamped_members
GROUP BY 1, 2, 3, 4, 5, 6, 7, 8
HAVING max(removed_at) IS NULL OR max(removed_at) < max(joined_at);

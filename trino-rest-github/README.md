trino-rest-github
=================

This is a Trino connector to access the GitHub API using SQL.

Because most endpoints have some parameters that are required, there are two ways of getting data:
* reading from functions - required parameters are passed explicitly as function arguments, including page number;
  functions return an array of rows, which must be unnested: `"SELECT * FROM unnest(pulls(:token, :owner, :repo, :page))`
* reading from tables - required parameters are inferred from query conditions (`WHERE` clause); if there are multiple pages of results,
  multiple requests will be made to fetch all of them before returning the data
  
Not all API endpoints are mapped yet, here's a list of the available tables:
* `orgs` - [Organizations](https://docs.github.com/en/rest/reference/orgs)
* `users` - [Users](https://docs.github.com/en/rest/reference/users)
* `repos` - [Repositories](https://docs.github.com/en/rest/reference/repos)
* `issues` and `issue_comments` - [Issues](https://docs.github.com/en/rest/reference/issues)
* `pulls`, `pull_commits`, `reviews`, `review_comments` - [Pull requests](https://docs.github.com/en/rest/reference/pulls)
* `workflows`, `runs`, `jobs`, `steps`, `artifacts`, `runners` - [Actions](https://docs.github.com/en/rest/reference/actions)

# Configuration

The following configuration options are recognied by this connector:

* `token` - required, the personal access token to authenticate with; it is required, but doesn't have to be valid;
  see the [Authentication and rate limits](#authentication-and-rate-limits) section below
* `min_splits` and `min_split_tables` - for tables matching endpoints that don't return total number of elements,
  a minimum number of splits can be used, where every split will fetch next page until no more results are available;
  defaults to 1 for `issues`, `pulls`, `runs` and `check_runs` tables

# Build

Build, test and create a deployable zip file:
```
mvn clean package
```

# Deploy

An example command to run the Trino server with the git plugin and catalog enabled:

```bash
src=$(git rev-parse --show-toplevel)
docker run \
  -v $src/trino-rest-github/target/trino-rest-github-0.40-SNAPSHOT:/usr/lib/trino/plugin/github \
  -v $src/catalog:/etc/trino/catalog \
  -p 8080:8080 \
  --name trino \
  -d \
  trinodb/trino:365
```

Connect to that server using:
```bash
docker run -it --rm --link trino trinodb/trino:365 trino --server trino:8080 --catalog github --schema default
```

# Authentication and rate limits

GitHub API doesn't require authentication, but unauthenticated requests have very low rate-limits.
Generate a personal auth token in `Settings -> Developer settings -> Personal access tokens`.
For more details, see [the Authentication section](https://docs.github.com/en/rest/guides/getting-started-with-the-rest-api#authentication) in GitHub's API docs.

# Concurrency

Some tables fetch data from endpoints that return total number of items. In such case,
this connector will generate one split for every page of data (with 100 items per page).
Such splits can be distributed evenly among all available nodes and/or threads, resulting in making concurrent HTTP requests.

For other tables, a configurable minimum number of splits will be created (defaults to 1), where each fetches pages until no more are available.

> Note: no rate limits are being accounted for when generating splits.

# Cache

Caching is enabled in the HTTP client used. API responses are cached in a temporary directory, and max cache size is 50Mb.

To better utilize the cache, avoid using query conditions that changes often, like timestamps with seconds. So when using relative time
to filter recent records, like `now() - interval '14' days`, use a date instead: `current_date - interval '14' days`.

Caching can prevent from fetching latest data. This could be mitigated by adding a condition to get records older than current timestamp,
but mos endpoints only support a `since` filter, which is the opposite. Another solution is to disable caching,
and copy the data into another, persistent catalog. There is a [Sync](src/main/java/pl/net/was/rest/github/Sync.java) program,
that does this in an incremental fashion, that is it can be run in regular intervals, for example from a cron job.

To run the `Sync` utility in `trino-rest-github`:
```bash
java -cp "trino-rest-github/target/trino-rest-github-0.40-SNAPSHOT/*" pl.net.was.rest.github.Sync
```

Check how much data the `Sync` collected by running a query like:
```sql
SELECT COUNT(DISTINCT id), COUNT(*), MIN(created_at), MAX(created_at) FROM runs;
```

# Adding new tables

To add a new table:

1. Register one or more endpoints in [GithubService](src/main/java/pl/net/was/rest/github/GithubService.java).
   This will be used to build HTTP requests sent to the API.
1. Create a new model in the [model](src/main/java/pl/net/was/rest/github/model) directory.
   This class is the data model for the API response. Annotations in the constructor arguments map JSON fields into properties.
   The `writeRow()` method is used to serialize the object into a Trino row, so this is where the mapping of JSON objects to SQL rows happens.
   Note that some properties might not be set from the API response, but from the request.
1. If the endpoint has some required filters, like `owner` or `repo` (most do),
   create a new filter in the [filter](src/main/java/pl/net/was/rest/github/filter) directory.
   It defines which columns' constraints will be pushed down and sent in HTTP requests.
1. In [GithubRest](src/main/java/pl/net/was/rest/github/GithubRest.java):
  * add table definition in the `columns` property; make sure they match exactly the write methods in the model;
  * add the filter created in previous step to the `filterAppliers` property;
  * create a function to get table rows and call it in the `getRows()` method.

To add a new function, assuming there's a model and columns mapping from previous points:
1. Add a row type string constant in the [GithubRest](src/main/java/pl/net/was/rest/github/GithubRest.java) class.
1. Create a new function class in [function](src/main/java/pl/net/was/rest/github/function).

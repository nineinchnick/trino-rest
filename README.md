trino-rest
==========

This is a [Trino](http://trino.io/) connector to access RESTful APIs. Please keep in mind that this is not production ready and it was created for tests.

# Quick Start

To run a Docker container with one of the connectors, set the `GITHUB_TOKEN` environmental variable, and run the following:
```bash
docker run \
  --tmpfs /etc/trino/catalog \
  -v $(pwd)/catalog/github.properties:/etc/trino/catalog/github.properties \
  -e GITHUB_TOKEN \
  -p 8080:8080 \
  --name trino-rest-github \
  nineinchnick/trino-rest:0.3
```

Then use your favourite SQL client to connect to Trino running at http://localhost:8080

# Usage

Download one of the ZIP packages, unzip it and copy the `trino-rest-github-0.3` directory to the plugin directory on every node in your Trino cluster.
Create a `github.properties` file in your Trino catalog directory and point to a remote repo.
You can also use a path to a local repo if it's available on every worker node.

```
connector.name=github
token=${ENV:GITHUB_TOKEN}
```

After reloading Trino, you should be able to connect to the `github` catalog and see the following tables in the `default` schema:
* `orgs`
* `users`
* `repos`
* `issues`
* `issue_comments`
* `pulls`
* `pull_commits`
* `reviews`
* `review_comments`
* `workflows`
* `runs`
* `jobs`
* `steps`
* `artifacts`
* `runners`

Most tables always require conditions on specific columns, like 'owner' and 'repo'.

Join conditions can be used for that in some situations. For example:
```sql
SELECT *
  FROM runs r
  JOIN jobs j ON j.run_id = r.id
 WHERE r.owner = 'nineinchnick' AND r.repo= 'trino-rest'
   AND j.owner = 'nineinchnick' AND j.repo = 'trino-rest'
```

Note that such query would perform :
* one HTTP request to count all runs, 
* one HTTP request to get the runs
* one HTTP request **for each run** to get its jobs

If you'll get an error message like `Missing required condition on run_id`, it means there might be too many runs (over 20).
Try to enable the `enable-large-dynamic-filters` config option,
or enable it in the current session by executing `SET SESSION enable_large_dynamic_filters = true`.
See the [dynamic filtering](https://trino.io/docs/current/admin/dynamic-filtering.html) for more configuration options.

# Development

For more information, see the README files in connector directories:
* [trino-rest-github](trino-rest-github/README.md)

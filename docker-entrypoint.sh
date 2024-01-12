#!/bin/bash

set -eo pipefail

catalog_dir=/etc/trino/catalog

if [ -n "$GITHUB_TOKEN" ]; then
    cp $catalog_dir/disabled/github.properties $catalog_dir/github.properties
fi

if [ -n "$SLACK_TOKEN" ]; then
    cp $catalog_dir/disabled/slack.properties $catalog_dir/slack.properties
fi

exec "$@"

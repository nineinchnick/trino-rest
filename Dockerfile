ARG TRINO_VERSION
FROM nineinchnick/trino-core:$TRINO_VERSION

USER root
RUN set -xeu && \
    apt-get update && \
    apt-get install --yes jq && \
    rm -rf /var/lib/apt/lists/*
USER trino:trino

ARG VERSION

ADD trino-rest-github/target/trino-rest-github-$VERSION/ /usr/lib/trino/plugin/github/
ADD trino-rest-twitter/target/trino-rest-twitter-$VERSION/ /usr/lib/trino/plugin/twitter/
ADD trino-rest-slack/target/trino-rest-slack-$VERSION/ /usr/lib/trino/plugin/slack/
ADD catalog/ /etc/trino/catalog/disabled/
ADD docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/lib/trino/bin/run-trino"]

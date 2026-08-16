ARG TRINO_VERSION=483
FROM trinodb/trino-core:$TRINO_VERSION AS plugin

ARG VERSION

COPY trino-rest-github/target/trino-rest-github-$VERSION.zip /tmp/trino-rest-github.zip
COPY trino-rest-slack/target/trino-rest-slack-$VERSION.zip /tmp/trino-rest-slack.zip
RUN mkdir /tmp/trino-rest-github && \
    cd /tmp/trino-rest-github && \
    jar --extract --file /tmp/trino-rest-github.zip
RUN mkdir /tmp/trino-rest-slack && \
    cd /tmp/trino-rest-slack && \
    jar --extract --file /tmp/trino-rest-slack.zip

FROM trinodb/trino-core:$TRINO_VERSION

ARG VERSION

COPY --chown=trino:trino --from=plugin /tmp/trino-rest-github/trino-rest-github-$VERSION/ /usr/lib/trino/plugin/github/
COPY --chown=trino:trino --from=plugin /tmp/trino-rest-slack/trino-rest-slack-$VERSION/ /usr/lib/trino/plugin/slack/
ADD catalog/ /etc/trino/catalog/disabled/
ADD docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/lib/trino/bin/run-trino"]

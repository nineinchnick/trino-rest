ARG TRINO_VERSION
FROM nineinchnick/trino-core:$TRINO_VERSION

USER root
RUN microdnf install -y jq
USER trino:trino

ARG VERSION

ADD trino-rest-github/target/trino-rest-github-$VERSION/ /usr/lib/trino/plugin/github/
ADD trino-rest-slack/target/trino-rest-slack-$VERSION/ /usr/lib/trino/plugin/slack/
ADD catalog/ /etc/trino/catalog/disabled/
ADD docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/lib/trino/bin/run-trino"]

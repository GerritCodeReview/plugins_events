FROM gerritcodereview/gerrit:3.7.6-ubuntu20

ENV GERRIT_SITE /var/gerrit
COPY artifacts/plugins/ $GERRIT_SITE/plugins/

USER root
COPY artifacts/bin/ /tmp/
RUN { [ -e /tmp/gerrit.war ] && cp /tmp/gerrit.war "$GERRIT_SITE/bin/gerrit.war" ; } || true
COPY start.sh /

USER gerrit
ENTRYPOINT /start.sh

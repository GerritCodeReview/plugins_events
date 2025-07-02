FROM gerritcodereview/gerrit:3.11.0-ubuntu24

ENV GERRIT_SITE /var/gerrit
COPY artifacts/plugins/ $GERRIT_SITE/plugins/

USER root
COPY artifacts/bin/ /tmp/
RUN { [ -e /tmp/gerrit.war ] && cp /tmp/gerrit.war "$GERRIT_SITE/bin/gerrit.war" ; } || true
COPY start.sh /
RUN touch "$GERRIT_SITE"/.firstTimeRedirect

USER gerrit
ENTRYPOINT /start.sh

FROM gerritcodereview/gerrit:3.5.0.1-ubuntu20

ENV GERRIT_SITE /var/gerrit
COPY artifacts/plugins/ $GERRIT_SITE/plugins/

USER root

RUN git config -f "$GERRIT_SITE/etc/gerrit.config" auth.type \
    DEVELOPMENT_BECOME_ANY_ACCOUNT

COPY artifacts/bin/ /tmp/
RUN { [ -e /tmp/gerrit.war ] && cp /tmp/gerrit.war "$GERRIT_SITE/bin/gerrit.war" ; } || true

USER gerrit

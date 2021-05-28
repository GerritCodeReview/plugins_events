FROM gerritcodereview/gerrit:3.0.16-ubuntu18

USER root

ENV GERRIT_SITE /var/gerrit
RUN git config -f "$GERRIT_SITE/etc/gerrit.config" auth.type \
    DEVELOPMENT_BECOME_ANY_ACCOUNT

COPY artifacts /tmp/
RUN cp /tmp/events.jar "$GERRIT_SITE/plugins/events.jar"
RUN { [ -e /tmp/gerrit.war ] && cp /tmp/gerrit.war "$GERRIT_SITE/bin/gerrit.war" ; } || true

USER gerrit

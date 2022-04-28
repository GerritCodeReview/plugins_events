#!/usr/bin/env bash

echo "Initializing Gerrit site ..."
java -jar "$GERRIT_SITE/bin/gerrit.war" init -d "$GERRIT_SITE" \
    --batch --dev

echo "Running Gerrit ..."
exec "$GERRIT_SITE"/bin/gerrit.sh run

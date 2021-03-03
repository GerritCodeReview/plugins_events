#!/usr/bin/env bash

PORT=29418
TEST_PROJECT=test-project

setup_test_project() {
    echo "Creating a test project ..."
    ssh -p "$PORT" -x "$GERRIT_HOST" gerrit create-project "${TEST_PROJECT}".git \
        --owner "Administrators" --submit-type "MERGE_IF_NECESSARY"
    git clone ssh://"$GERRIT_HOST":"$PORT"/"$TEST_PROJECT" "$WORKSPACE"
    pushd "$WORKSPACE" > /dev/null
    git commit -m "Initial commit" --allow-empty
    git push ssh://"$GERRIT_HOST":"$PORT"/"$TEST_PROJECT" HEAD:refs/heads/master
    popd > /dev/null
}

cp -r /events "$USER_HOME"/
ssh-keygen -P '' -f "$USER_HOME"/.ssh/id_rsa
chmod 400 "$USER_HOME"/.ssh/id_rsa

cd "$USER_HOME"/events/test
./docker/run_tests/wait-for-it.sh "$GERRIT_HOST":"$PORT" \
    -t 60 -- echo "Gerrit is up"

git config --global user.name "Gerrit Admin"
git config --global user.email "gerrit_admin@qualcomm.com"

echo "Creating a default user account ..."

cat "$USER_HOME"/.ssh/id_rsa.pub | ssh -p 29418 -i /server-ssh-key/ssh_host_rsa_key \
  "Gerrit Code Review@$GERRIT_HOST" suexec --as "admin@example.com" -- gerrit create-account \
     --ssh-key - --email "gerrit_admin@localdomain"  --group "Administrators" "gerrit_admin"

setup_test_project
./test_events_plugin.sh --server "$GERRIT_HOST" --project "$TEST_PROJECT"

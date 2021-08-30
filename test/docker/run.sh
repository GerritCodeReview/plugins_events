#!/usr/bin/env bash

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
ARTIFACTS=$MYDIR/gerrit/artifacts

die() { echo -e "\nERROR: $@" ; kill $$ ; exit 1 ; } # error_message

progress() { # message cmd [args]...
    local message=$1 ; shift
    echo -n "$message"
    "$@" &
    local pid=$!
    while kill -0 $pid 2> /dev/null ; do
        echo -n "."
        sleep 2
    done
    echo
    wait "$pid"
}

usage() { # [error_message]
    local prog=$(basename "$0")
    cat <<EOF
Usage:
    $prog [--events-plugin-jar|-t <FILE_PATH>] [--gerrit-war|-g <FILE_PATH>]

    This tool runs the plugin functional tests in a Docker environment built
    from the gerritcodereview/gerrit base Docker image.

    The events plugin JAR and optionally a Gerrit WAR are expected to be in the
    $ARTIFACTS dir;
    however, the --events-plugin-jar and --gerrit-war switches may be used as
    helpers to specify which files to copy there.

    Options:
    --help|-h
    --gerrit-war|-g             path to Gerrit WAR file
    --events-plugin-jar|-e      path to events plugin JAR file

EOF

    [ -n "$1" ] && echo -e "\nERROR: $1" && exit 1
    exit 0
}

check_prerequisite() {
    docker --version > /dev/null || die "docker is not installed"
    docker-compose --version > /dev/null || die "docker-compose is not installed"
}

build_images() {
    docker-compose "${COMPOSE_ARGS[@]}" build --quiet
}

run_events_plugin_tests() {
    docker-compose "${COMPOSE_ARGS[@]}" up --detach
    docker-compose "${COMPOSE_ARGS[@]}" exec -T --user=gerrit_admin run_tests \
        '/events/test/docker/run_tests/start.sh'
}

cleanup() {
    docker-compose "${COMPOSE_ARGS[@]}" down -v --rmi local 2>/dev/null
}

while (( "$#" )); do
    case "$1" in
        --help|-h)                    usage ;;
        --gerrit-war|-g)              shift ; GERRIT_WAR=$1 ;;
        --events-plugin-jar|-e)       shift ; EVENTS_PLUGIN_JAR=$1 ;;
        *)                            usage "invalid argument $1" ;;
    esac
    shift
done

PROJECT_NAME="events_$$"
COMPOSE_YAML="$MYDIR/docker-compose.yaml"
COMPOSE_ARGS=(--project-name "$PROJECT_NAME" -f "$COMPOSE_YAML")
check_prerequisite
mkdir -p -- "$ARTIFACTS"
[ -n "$EVENTS_PLUGIN_JAR" ] && cp -f -- "$EVENTS_PLUGIN_JAR" "$ARTIFACTS/events.jar"
if [ ! -e "$ARTIFACTS/events.jar" ] ; then
    MISSING="Missing $ARTIFACTS/events.jar"
    [ -n "$EVENTS_PLUGIN_JAR" ] && die "$MISSING, check for copy failure?"
    usage "$MISSING, did you forget --events-plugin-jar?"
fi
[ -n "$GERRIT_WAR" ] && cp -f -- "$GERRIT_WAR" "$ARTIFACTS/gerrit.war"
progress "Building docker images" build_images
run_events_plugin_tests ; RESULT=$?
cleanup

exit "$RESULT"

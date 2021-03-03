#!/usr/bin/env bash

readlink --canonicalize / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
ARTIFACTS="$MYDIR/gerrit/artifacts"

die() { echo -e "\nERROR: $@" ; exit 1 ; } # error_message

progress() { # message cmd[args]...
    message=$1 ; shift
    echo -n "$message"
    "$@" &
    pid=$!
    while kill -0 $pid 2>"/dev/null" ; do
        echo -n "."
        sleep 2
    done
    echo
}

usage() { # [error_message]
    local prog=$(basename "$0")
    cat <<EOF
Usage:
    "$prog" --gerrit-war|-g <WAR URL or file path>
            --events-plugin-jar|-e <events plugin JAR URL or file path>

    --help|-h
    --gerrit-war|-g             gerrit WAR URL or the file path in local workspace
                                eg: file:///path/to/gerrit.war
    --events-plugin-jar|-e      events plugin JAR URL or the file path in local workspace
                                eg: file:///path/to/events.jar

EOF

    [ -n "$1" ] && echo -e "\nERROR: $1" && exit 1
    exit 0
}

check_prerequisite() {
    docker --version > /dev/null || die "docker is not installed"
    docker-compose --version > /dev/null || die "docker-compose is not installed"
}

gcurl_or_die() { # source_location output_path
    curl --silent --fail --netrc "$1" --output "$2" --create-dirs || die "unable to fetch artifacts"
}

fetch_artifacts() {
    gcurl_or_die "$GERRIT_WAR" "$ARTIFACTS/gerrit.war"
    gcurl_or_die "$EVENTS_PLUGIN_JAR" "$ARTIFACTS/events.jar"
}

build_images() {
    local build_args=()
    build_args+=(--build-arg GERRIT_WAR="/artifacts/gerrit.war" \
        --build-arg EVENTS_PLUGIN_JAR="/artifacts/events.jar" \
        --build-arg UID="$(id -u)" --build-arg GID="$(id -g)")

    docker-compose "${COMPOSE_ARGS[@]}" build "${build_args[@]}" --quiet
    rm -r "$ARTIFACTS"
}

run_events_plugin_tests() {
   docker-compose "${COMPOSE_ARGS[@]}" up --abort-on-container-exit \
       --exit-code-from run_tests 2>/dev/null
}

cleanup() {
    docker-compose "${COMPOSE_ARGS[@]}" down -v --rmi local 2>/dev/null
}

while (( "$#" )); do
    case "$1" in
        --help|-h)                    usage ;;
        --gerrit-war|-g)              shift ; GERRIT_WAR=$1 ;;
        --events-plugin-jar|-e)       shift ; EVENTS_PLUGIN_JAR=$1 ;;
        *)                            usage "invalid argument '$1'" ;;
    esac
    shift
done

[ -n "$GERRIT_WAR" ] || usage "'--gerrit-war' not set"
[ -n "$EVENTS_PLUGIN_JAR" ] || usage "'--events-plugin-jar' not set "

PROJECT_NAME="events_$$"
COMPOSE_YAML="$MYDIR/docker-compose.yaml"
COMPOSE_ARGS=(--project-name "$PROJECT_NAME" -f "$COMPOSE_YAML")
check_prerequisite
progress "Fetching artifacts" fetch_artifacts
progress "Building docker images" build_images
run_events_plugin_tests ; RESULT=$?
cleanup

exit "$RESULT"

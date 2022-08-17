#!/usr/bin/env bash

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
ARTIFACTS=$MYDIR/gerrit/artifacts
PLUGIN=events
BAZEL_BUILT_JAR=$MYDIR/../../bazel-bin/$PLUGIN.jar

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
    $prog [--plugin <plugin name> <JAR path>] [--gerrit-war|-g <FILE_PATH>]

    This tool runs the plugin functional tests in a Docker environment built
    from the gerritcodereview/gerrit base Docker image.

    Options:
    --help|-h
    --gerrit-war|-g             optional path to Gerrit WAR file. Will likely
                                not function correctly if it's a different
                                MAJOR.MINOR version than the image version
                                in test/docker/gerrit/Dockerfile.
    --plugin                    optional plugin name and path to JAR file
                                Defaults to '$PLUGIN' '$BAZEL_BUILT_JAR'
                                Can be repeated to install additional
                                plugins.

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

fetch_artifact() { # source_location output_path
    if [[ "$1" =~ ^file://|^http://|^https:// ]] ; then
        curl --silent --fail --netrc "$1" --output "$2" --create-dirs || die "unable to fetch $1"
    else
        cp -f "$1" "$2" || die "unable to copy $1"
    fi
}

fetch_artifacts() {
    local plugin_name
    [ -n "$GERRIT_WAR" ] && fetch_artifact "$GERRIT_WAR" "$ARTIFACTS/bin/gerrit.war"
    for plugin_name in "${!PLUGIN_JAR_BY_NAME[@]}" ; do
        if [ -n "${PLUGIN_JAR_BY_NAME["$plugin_name"]}" ] ; then
            fetch_artifact "${PLUGIN_JAR_BY_NAME[$plugin_name]}" \
                "$ARTIFACTS/plugins/$plugin_name.jar"
        fi
    done
}

cleanup() {
    docker-compose "${COMPOSE_ARGS[@]}" down -v --rmi local 2>/dev/null
    rm -rf "$ARTIFACTS"
}

declare -A PLUGIN_JAR_BY_NAME
while (( "$#" )); do
    case "$1" in
        --help|-h)                    usage ;;
        --gerrit-war|-g)              shift ; GERRIT_WAR=$1 ;;
        --plugin)                     shift ; PLUGIN_JAR_BY_NAME["$1"]=$2 ; shift ;;
        *)                            usage "invalid argument $1" ;;
    esac
    shift
done

PROJECT_NAME="events_$$"
COMPOSE_YAML="$MYDIR/docker-compose.yaml"
COMPOSE_ARGS=(--project-name "$PROJECT_NAME" -f "$COMPOSE_YAML")
check_prerequisite
mkdir -p -- "$ARTIFACTS/bin" "$ARTIFACTS/plugins"
if [ -z "${PLUGIN_JAR_BY_NAME["$PLUGIN"]}" ] ; then
    PLUGIN_JAR_BY_NAME["$PLUGIN"]=$BAZEL_BUILT_JAR
fi
if [ ! -e "${PLUGIN_JAR_BY_NAME["$PLUGIN"]}" ] ; then
    usage "Cannot find plugin jar, did you forget --plugin?"
fi
progress "Fetching artifacts" fetch_artifacts
( trap cleanup EXIT SIGTERM
    progress "Building docker images" build_images
    run_events_plugin_tests
)

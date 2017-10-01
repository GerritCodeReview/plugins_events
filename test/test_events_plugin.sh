#!/bin/bash

q() { "$@" > /dev/null 2>&1 ; } # cmd [args...]  # quiet a command
gssh() { ssh -p 29418 -x "$SERVER" "$@" 2>&1 ; } # run a gerrit ssh command
mygit() { git --work-tree="$REPO_DIR" --git-dir="$GIT_DIR" "$@" ; } # [args...]

cleanup() {
    wait_event
    (kill_captures ; sleep 1 ; kill_captures -9 ) &
}

# > uuid
gen_uuid() { uuidgen | openssl dgst -sha1 -binary | xxd -p; }

gen_commit_msg() { # msg > commit_msg
    local msg=$1
    echo "$msg

Change-Id: I$(gen_uuid)"
}

get_change_num() { # < gerrit_push_response > changenum
    local url=$(awk '/New Changes:/ { getline; print $2 }')
    echo "${url##*\/}" | tr -d -c '[:digit:]'
}

create_change() { # [--dependent] [--draft] branch file [commit_message] > changenum
    local opt_d opt_c opt_draft=false
    [ "$1" = "--dependent" ] && { opt_d=$1 ; shift ; }
    [ "$1" = "--draft" ] && { opt_draft=true ; shift ; }
    local branch=$1 tmpfile=$2 msg=$3 out rtn
    local content=$RANDOM dest=refs/for/$branch
    "$opt_draft" && dest=refs/drafts/$branch

    if [ -z "$opt_d" ] ; then
        out=$(mygit fetch "$GITURL" "$branch" 2>&1) ||\
            cleanup "Failed to fetch $branch: $out"
        out=$(mygit checkout FETCH_HEAD 2>&1) ||\
            cleanup "Failed to checkout $branch: $out"
    fi

    echo -e "$content" > "$tmpfile"

    out=$(mygit add "$tmpfile" 2>&1) || cleanup "Failed to git add: $out"

    [ -n "$msg" ] || msg=$(gen_commit_msg "Add $tmpfile")

    out=$(mygit commit -m "$msg" 2>&1) ||\
        cleanup "Failed to commit change: $out"
    [ -n "$VERBOSE" ] && echo "  commit:$out" >&2

    out=$(mygit push "$GITURL" "HEAD:$dest" 2>&1) ||\
        cleanup "Failed to push change: $out"
    out=$(echo "$out" | get_change_num) ; rtn=$? ; echo "$out"
    [ -n "$VERBOSE" ] && echo "  change:$out" >&2
    return $rtn
}

review() { gssh gerrit review "$@" ; }

submit() { # change,ps
   local out=$(review "$1" --submit)
   local acl_err="one or more approvals failed; review output above"
   local conflict_err="The change could not be merged due to a path conflict."

   if echo "$out" | grep -q "$acl_err" ; then
      if ! echo "$out" | grep -q "$conflict_err" ; then
            echo "$out"
            echo "User needs ACLs to approve and submit changes to $REF_BRANCH"
            exit 1
       fi
   fi
}

# ------------------------- Event Capturing ---------------------------

kill_captures() { # sig
    local pid
    for pid in "${CAPTURE_PIDS[@]}" ; do
        q kill $1 $pid
    done
}

setup_captures() {
    ssh -p 29418 -x "$SERVER" "${CORE_CMD[@]}" > "$EVENTS_CORE" &
    CAPTURE_PIDS=("${CAPTURE_PIDS[@]}" $!)
    ssh -p 29418 -x "$SERVER" "${PLUGIN_CMD[@]}" > "$EVENTS_PLUGIN" &
    CAPTURE_PIDS=("${CAPTURE_PIDS[@]}" $!)
}

capture_events() { # count
    local count=$1
    [ -n "$count" ] || count=1
    ssh -p 29418 -x "$SERVER" "${PLUGIN_CMD[@]}" > "$EVENT_FIFO" &
    CAPTURE_PID_SSH=$!
    head -n $count < "$EVENT_FIFO" > "$EVENTS" &
    CAPTURE_PID_HEAD=$!
    sleep 1
}

wait_event() {
   (sleep 1 ; q kill -9 $CAPTURE_PID_SSH ; q kill -9 $CAPTURE_PID_HEAD ) &
    q wait $CAPTURE_PID_SSH $CAPTURE_PID_HEAD
}

get_event() { # number
    local number=$1
    [ -n "$number" ] || number=1

    awk "NR==$number" "$EVENTS"
}

result_type() { # test type [n]
    local test=$1 type=$2 number=$3
    [ -n "$number" ] || number=1
    wait_event
    local event=$(get_event "$number")
    echo "$event" | grep -q "\"type\":\"$type\""
    result "$test $type" "$event"
}

# ------------------------- Usage ---------------------------

usage() { # [error_message]
    cat <<-EOF
Usage: $MYPROG [-s|--server <server>] [-p|--project <project>]
             [-r|--ref <ref branch>] [-g|--plugin <plugin>] [-h|--help]

       -h|--help                usage/help
       -s|--server <server>     server to use for the test (default: localhost)
       -p|--project <project>   git project to use (default: project0)
       -r|--ref <ref branch>    reference branch used to create branches (default: master)
       --approvals <approvals>  approvals needed for submit (default: --code-review 2)
       --plugin-cmd <cmd>       event streaming command for plugin (default: <plugin> stream)
       --core-cmd <cmd>         event streaming command for core (default: gerrit stream-events)
EOF

    [ -n "$1" ] && echo -e '\n'"ERROR: $1"
    exit 1
}

parseArgs() {
    SERVER="localhost"
    PROJECT="tools/test/project0"
    REF_BRANCH="master"
    APPROVALS="--code-review 2"
    CORE_CMD=(gerrit stream-events)
    PLUGIN_CMD=(events stream)
    while (( "$#" )) ; do
        case "$1" in
            --server|-s)  shift; SERVER=$1 ;;
            --project|-p) shift; PROJECT=$1 ;;
            --ref|-r)     shift; REF_BRANCH=$1 ;;
            --approvals)  shift; APPROVALS=$1 ;;
            --plugin-cmd) shift; PLUGIN_CMD=($1) ;;
            --core-cmd)   shift; CORE_CMD=($1) ;;
            --help|-h)    usage ;;
            --verbose|-v) VERBOSE=$1 ;;
            *)            usage "invalid argument '$1'" ;;
        esac
        shift
    done

    [ -n "$SERVER" ]     || usage "server not set"
    [ -n "$PROJECT" ]    || usage "project not set"
    [ -n "$REF_BRANCH" ] || usage "ref branch not set"
}

MYPROG=$(basename "$0")
MYDIR=$(dirname "$0")

source "$MYDIR/lib_result.sh"

parseArgs "$@"

TEST_DIR=$MYDIR/../target/test
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR"
TEST_DIR=$(readlink -f "$TEST_DIR")

GITURL=ssh://$SERVER:29418/$PROJECT
DEST_REF=$REF_BRANCH
echo "$REF_BRANCH" | grep -q '^refs/' || DEST_REF=refs/heads/$REF_BRANCH
git ls-remote "$GITURL" | grep -q "$DEST_REF" || usage "invalid project/server/ref"

REPO_DIR=$TEST_DIR/repo
q git init "$REPO_DIR"
GIT_DIR="$REPO_DIR/.git"
FILE_A="$REPO_DIR/fileA"

EVENTS_CORE=$TEST_DIR/events-core
EVENTS_PLUGIN=$TEST_DIR/events-plugin
EVENT_FIFO=$TEST_DIR/event-fifo
EVENTS=$TEST_DIR/events
mkfifo "$EVENT_FIFO"

trap cleanup EXIT

setup_captures

# ------------------------- Individual Event Tests ---------------------------
GROUP=visible-events
type=patchset-created
capture_events
ch1=$(create_change --draft "$REF_BRANCH" "$FILE_A") || exit
result_type "$GROUP" "$type"

type=draft-published
capture_events
review "$ch1,1" --publish
result_type "$GROUP" "$type"

type=change-abandoned
capture_events
review "$ch1,1" --abandon
result_type "$GROUP" "$type"

type=change-restored
capture_events
review "$ch1,1" --restore
result_type "$GROUP" "$type"

type=comment-added
capture_events
review "$ch1,1" --message "my_comment" $APPROVALS
result_type "$GROUP" "$type"

type=change-merged
capture_events 2
submit "$ch1,1"
result_type "$GROUP" "ref-updated"
result_type "$GROUP" "$type" 2

# reviewer-added needs to be tested via Rest-API

# ------------------------- Compare them all to Core -------------------------

out=$(diff "$EVENTS_CORE" "$EVENTS_PLUGIN")
result "core/plugin diff" "$out"


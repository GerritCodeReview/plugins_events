#!/bin/bash

q() { "$@" > /dev/null 2>&1 ; } # cmd [args...]  # quiet a command
gssh() { ssh -p 29418 -x "$SERVER" "$@" 2>&1 ; } # run a gerrit ssh command
mygit() { git --work-tree="$REPO_DIR" --git-dir="$GIT_DIR" "$@" ; } # [args...]

# plugin_name
is_plugin_installed() { gssh gerrit plugin ls | awk '{print $1}' | grep -q "^$1$"; }

set_filter_rules() { # [rule]...
    git config -f "$GERRIT_CFG" --unset-all plugin.events.filter
    local rule
    for rule in "$@" ; do
        git config -f "$GERRIT_CFG" plugin.events.filter "$rule"
    done
    gssh gerrit plugin reload events
}

cleanup() {
    wait_event_for plugin
    wait_event_for core
    (kill_diff_captures ; sleep 1 ; kill_diff_captures -9 ) &
}

# > uuid
gen_uuid() { uuidgen | openssl dgst -sha1 -binary | xxd -p; }

gen_commit_msg() { # msg > commit_msg
    local msg=$1
    echo "$msg

Change-Id: I$(gen_uuid)"
}

get_change_num() { # < gerrit_push_response > changenum
    local url=$(awk '$NF ~ /\[NEW\]/ { print $2 }')
    echo "${url##*\/}" | tr -d -c '[:digit:]'
}

create_change() { # [--dependent] branch file [commit_message] > changenum
    local opt_d opt_c
    [ "$1" = "--dependent" ] && { opt_d=$1 ; shift ; }
    local branch=$1 tmpfile=$2 msg=$3 out rtn
    local content=$RANDOM dest=refs/for/$branch

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

get_open_changes() {
    curl --netrc --silent "$REST_API_CHANGES_URL/?q=status:open"
}

mark_change_wip() { # change
    curl --netrc --silent \
        --data "message=wip" "$REST_API_CHANGES_URL/$1/wip"
}

mark_change_ready() { # change
    curl --netrc --silent \
        --data "message=ready" "$REST_API_CHANGES_URL/$1/ready"
}

mark_change_private() { # change
    curl --netrc --silent \
        --data "message=private" "$REST_API_CHANGES_URL/$1/private"
}

unmark_change_private() { # change
    curl -X DELETE --netrc --silent --header 'Content-Type: application/json' \
        --data '{"message":"unmark_private"}' "$REST_API_CHANGES_URL/$1/private"
}


add_meta_ref_updates() { # num_events num_meta_ref_upates > total_num_events
    local n=$1 m=$2
    [ "$FILTERED" = "META_REF_UPDATES" ] && { echo "$n" ; return ; }
    echo $(($n + $m))
}

# ------------------------- Event Capturing ---------------------------

kill_diff_captures() { # sig
    local pid
    for pid in "${CAPTURE_PIDS[@]}" ; do
        q kill $1 $pid
    done
}

setup_diff_captures() {
    ssh -p 29418 -x "$SERVER" "${CORE_CMD[@]}" > "$EVENTS_CORE" &
    CAPTURE_PIDS=("${CAPTURE_PIDS[@]}" $!)
    ssh -p 29418 -x "$SERVER" "${PLUGIN_CMD[@]}" > "$EVENTS_PLUGIN" &
    CAPTURE_PIDS=("${CAPTURE_PIDS[@]}" $!)
}

capture_events_for() { # 'plugin'|'core' count
    local for=$1 count=$2 cmd
    [ -n "$count" ] || count=1

    # Re-create the fifo to ensure that is is empty
    rm -f -- "$EVENT_FIFO.$for"
    mkfifo -- "$EVENT_FIFO.$for"

    head -n $count < "$EVENT_FIFO.$for" > "$EVENTS.$for" &
    CAPTURE_PID_HEAD[$for]=$!
    sleep 1
    case "$1" in
        plugin) cmd=("${PLUGIN_CMD[@]}") ;;
        core)   cmd=("${CORE_CMD[@]}") ;;
        *) echo "Unkown type: $for (should be plugin|core)" >&2 ; exit 1 ;;
    esac
    ssh -p 29418 -x "$SERVER" "${cmd[@]}" > "$EVENT_FIFO.$for" &
    CAPTURE_PID_SSH[$for]=$!
    sleep 1
}

capture_events() { # count
    capture_events_for plugin "$@"
    capture_events_for core "$@"
}

wait_event_for() { # 'plugin'|'core'
   # Below kill of CAPTURE_PID_HEAD is a safety net and ideally we wouldn't
   # want this kill to stop CAPTURE_PID_HEAD, rather we want it die on its
   # own when the 'head' in capture_events() captures the desired events. The
   # delay here must ideally be greater than the run time of the entire suite.
   (sleep 120 ; q kill -9 ${CAPTURE_PID_HEAD[$1]} ; ) &
   q wait ${CAPTURE_PID_HEAD[$1]}
   q kill -9 ${CAPTURE_PID_SSH[$1]}
   q wait ${CAPTURE_PID_SSH[$1]}
}

result_event_for() { # 'plugin'|'core' test type [expected_count]
    local for=$1 test=$2 type=$3 expected_count=$4
    [ -n "$expected_count" ] || expected_count=1
    wait_event_for "$for"
    local actual_count=$(grep -c "\"type\":\"$type\"" "$EVENTS.$for")
    result_out "$test $for" "$expected_count $type event(s)" "$actual_count $type event(s)"
    [ "$expected_count" = "$actual_count" ] || cat "$EVENTS.$for"
}

# 'plugin'|'core' test type [expected_count]
result_type_for() { result_event_for "$1" "$2 $3" "$3" "$4" ; }

# test type [expected_count]
result_type() {
    result_type_for plugin "$@"
    result_type_for core "$@"
}

# ------------------------- Tests ---------------------------

main_suite() { # group_name
    GROUP=$1
    setup_diff_captures

    type=patchset-created
    capture_events $(add_meta_ref_updates 2 1)
    ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
    result_type "$GROUP" "$type" 1
    # The change ref and its meta ref are expected to be updated
    # For example: 'refs/changes/01/1001/1' and 'refs/changes/01/1001/meta'
    result_type "$GROUP $type" "ref-updated" $(add_meta_ref_updates 1 1)

    type=change-abandoned
    capture_events $(add_meta_ref_updates 1 1)
    review "$ch1,1" --abandon
    result_type "$GROUP" "$type"

    type=change-restored
    capture_events $(add_meta_ref_updates 1 1)
    review "$ch1,1" --restore
    result_type "$GROUP" "$type"

    type=comment-added
    capture_events $(add_meta_ref_updates 1 1)
    review "$ch1,1" --message "my_comment" $APPROVALS
    result_type "$GROUP" "$type"

    type=wip-state-changed
    capture_events $(add_meta_ref_updates 1 3)
    q mark_change_wip "$ch1"
    q mark_change_ready "$ch1"
    result_type "$GROUP" "$type" $(add_meta_ref_updates 1 1)

    type=private-state-changed
    capture_events $(add_meta_ref_updates 1 3)
    q mark_change_private "$ch1"
    q unmark_change_private "$ch1"
    result_type "$GROUP" "$type" $(add_meta_ref_updates 1 1)

    type=change-merged
    events_count=$(add_meta_ref_updates 2 2)
    # If reviewnotes plugin is installed, an extra event of type 'ref-updated'
    # on 'refs/notes/review' is fired when a change is merged.
    is_plugin_installed reviewnotes && events_count="$((events_count+1))"
    capture_events "$events_count"
    submit "$ch1,1"
    result_type "$GROUP" "$type"
    # The destination ref of the change, its meta ref and notes ref(if reviewnotes
    # plugin is installed) are expected to be updated.
    # For example: 'refs/heads/master', 'refs/changes/01/1001/meta' and 'refs/notes/review'
    result_type "$GROUP $type" "ref-updated" "$((events_count-1))"

    # reviewer-added needs to be tested via Rest-API

    out=$(diff -- "$EVENTS_CORE" "$EVENTS_PLUGIN")
    result "$GROUP core/plugin diff" "$out"

    kill_diff_captures
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

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
MYPROG=$(basename -- "$0")

source "$MYDIR/lib_result.sh"

parseArgs "$@"

TEST_DIR=$MYDIR/../target/test
rm -rf -- "$TEST_DIR"
mkdir -p -- "$TEST_DIR"
TEST_DIR=$(readlink -f -- "$TEST_DIR")

REST_API_CHANGES_URL="http://$SERVER:8080/a/changes"
GITURL=ssh://$SERVER:29418/$PROJECT
DEST_REF=$REF_BRANCH
echo "$REF_BRANCH" | grep -q '^refs/' || DEST_REF=refs/heads/$REF_BRANCH
git ls-remote "$GITURL" | grep -q "$DEST_REF" || usage "invalid project/server/ref"

REPO_DIR=$TEST_DIR/repo
q git init -- "$REPO_DIR"
GIT_DIR="$REPO_DIR/.git"
FILE_A="$REPO_DIR/fileA"

EVENTS_CORE=$TEST_DIR/events-core
EVENTS_PLUGIN=$TEST_DIR/events-plugin
EVENT_FIFO=$TEST_DIR/event-fifo
EVENTS=$TEST_DIR/events
GERRIT_CFG="/gerrit.config/gerrit.config"
declare -A CAPTURE_PID_HEAD
declare -A CAPTURE_PID_SSH

trap cleanup EXIT

# We need to do an initial REST call, as the first REST call after a server is
# brought up results in being anonymous despite providing proper authentication.
get_open_changes

RESULT=0

# ------------------------- Individual Event Tests ---------------------------
FILTERED=""
set_filter_rules # No rules
main_suite visible-events

# ------------------------- Filtering -------------------------

FILTERED="META_REF_UPDATES"
set_filter_rules 'DROP RefUpdatedEvent isNoteDbMetaRef'
main_suite meta-refUpdated-filtered


FILTERED=""
GROUP=restored-filtered
set_filter_rules 'DROP classname com.google.gerrit.server.events.ChangeRestoredEvent'

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
type=change-abandoned
capture_events $(add_meta_ref_updates 1 1)
review "$ch1,1" --abandon
result_type "$GROUP" "$type"

type=change-restored
capture_events $(add_meta_ref_updates 3 1)
review "$ch1,1" --restore
# Instead of timing out waiting for the filtered change-restored event,
# create follow-on events and capture them to trigger completion.
review "$ch1,1" --message "'\"trigger filtered completion\"'"
result_type "$GROUP" "$type" 0

exit $RESULT

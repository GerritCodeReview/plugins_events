# ---- TEST RESULTS ----
result() { # test [error_message]
    local result=$?
    if [ $result -eq 0 ] ; then
        echo "PASSED - $1 test"
    else
        echo "*** FAILED *** - $1 test"
        RESULT=$result
        [ $# -gt 1 ] && echo "$2"
    fi
}

# output must match expected to pass
result_out() { # test expected output
    local disp=$(echo "Expected Output:" ;\
                 echo "    $2" ;\
                 echo "Actual Output:" ;\
                 echo "    $3")

    [ "$2" = "$3" ]
    result "$1" "$disp"
}

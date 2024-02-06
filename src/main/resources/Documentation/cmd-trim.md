@PLUGIN@ trim
=============

NAME
----
@PLUGIN@ trim - Trim old events up from EventStore

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ trim
   [--trim-id] <TRIM_ID>
   [--size] <SIZE>
```

DESCRIPTION
-----------

Provides a mechanism to trim older events from the event store.

OPTIONS
-----------
**--trim-id**

: event id to trim up to and including

**--size**

: trim and keep up to size events

Note: if both the --trim-id and --size options are specified, both trim
operations will be performed.

ACCESS
------
Any user who has administrate server capabilities and has configured an SSH key.

SCRIPTING
---------
This command is intended to be used in scripts, perhaps to be called by a
cron job.

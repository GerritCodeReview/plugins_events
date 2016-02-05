@PLUGIN@ stream
===============

NAME
----
@PLUGIN@ stream - Monitor events occurring in real time

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ stream
   [--ids]
   [--resume-after <RESUME_AFTER>]
```

DESCRIPTION
-----------

Provides a portal into the major events occurring on the server,
outputing activity data in real-time to the client.  Events are
filtered by the caller's access permissions, ensuring the caller
only receives events for changes they can view on the web, or in
the project repository.

It is possible to make the events numbered so that clients may
request the server to send all the known (and visible) events
starting after a specific event using its id.  This makes it
possible to retrieve the events which occured when the client
was disconnected.

Event output is in JSON, one event per line.

OPTIONS
-----------
**--ids**

: add ids to events, useful for resuming after a disconnect
(see --resume-after).

**--resume-after**

: event id after which to resume playing events on connection.


ACCESS
------
Any user who has configured an SSH key.

SCRIPTING
---------
This command is intended to be used in scripts.

EXAMPLES
--------

```
    $ ssh -p 29418 review.example.com @PLUGIN@ stream-events --ids
    {"type":"comment-added", ..., "id":"bdedff7d-34fd-4459-a6a7-3f738f32c01d:1"}
    {"type":"change-merged", ..., "id":"bdedff7d-34fd-4459-a6a7-3f738f32c01d:2"}

```

```
    $ ssh -p 29418 review.example.com @PLUGIN@ stream-events --ids \
        --resume-after bdedff7d-34fd-4459-a6a7-3f738f32c01d:1
    {"type":"change-merged", ..., "id":"bdedff7d-34fd-4459-a6a7-3f738f32c01d:2"}
    {"type":"change-abandoned", ..., "id":"bdedff7d-34fd-4459-a6a7-3f738f32c01d:3"}

```

SCHEMA
------
The JSON messages consist of nested objects referencing the *change*,
*patchSet*, *account* involved, and other attributes as appropriate.
The currently supported message types are *patchset-created*,
*draft-published*, *change-abandoned*, *change-restored*,
*change-merged*, *merge-failed*, *comment-added*, *ref-updated* and
*reviewer-added*.

Note that any field may be missing in the JSON messages, so consumers of
this JSON stream should deal with that appropriately.

### Events

#### Patchset Created

**type**

: "patchset-created"

**change**

: [change attribute](../../../Documentation/json.html#change)

**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)

**uploader**

: [account attribute](../../../Documentation/json.html#account)

#### Draft Published

**type**

:  "draft-published"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchset**

: [patchSet attribute](../../../Documentation/json.html#patchSet)

**uploader**

: [account attribute](../../../Documentation/json.html#account)

#### Change Abandoned

**type**

:  "change-abandoned"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)


**abandoner**

: [account attribute](../../../Documentation/json.html#account)

**reason**

:  Reason for abandoning the change.

#### Change Restored

**type**

:  "change-restored"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)


**restorer**

: [account attribute](../../../Documentation/json.html#account)

**reason**

:  Reason for restoring the change.

#### Change Merged

**type**

:  "change-merged"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)


**submitter**

: [account attribute](../../../Documentation/json.html#account)

#### Merge Failed

**type**

:  "merge-failed"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)


**submitter**

: [account attribute](../../../Documentation/json.html#account)

**reason**

:  Reason that the merge failed.

#### Comment Added

**type**

:  "comment-added"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchSet**

: [patchSet attribute](../../../Documentation/json.html#patchSet)


**author**

: [account attribute](../../../Documentation/json.html#account)

**approvals**

: All [approval attributes](../../../Documentation/json.html#approval)

**comment**

:  Comment text author had written

#### Ref Updated

**type**

:  "ref-updated"

**submitter**

: [account attribute](../../../Documentation/json.html#account)

**refUpdate**

: [refUpdate attribute](../../../Documentation/json.html#refUpdate)

#### Reviewer Added

**type**

:  "reviewer-added"

**change**

: [change attribute](../../../Documentation/json.html#change)


**patchset**

: [patchSet attribute](../../../Documentation/json.html#patchSet)

**reviewer**

: [account attribute](../../../Documentation/json.html#account)


SEE ALSO
--------

* [JSON Data Formats](../../../Documentation/json.html)
* [Access Controls](../../../Documentation/access-control.html)

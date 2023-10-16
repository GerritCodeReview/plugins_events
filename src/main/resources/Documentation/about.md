@PLUGIN@
========

The @PLUGIN@ plugin provides a mechanism to store events.

<a id="storage"/>
@PLUGIN@ Storage
----------------

The events plugin stores events using a standard filesystem.  Events
are stored under "<site_dir>/data/plugin/events".  Events do not use
significant disk space, however it might still make sense to regularly
trim them with a cron job.

<a id="filtering"/>
@PLUGIN@ Filtering
------------------

The @PLUGIN@ plugin is able to drop events which site admins do not
want stored or sent out to users. Event filtering can be configured
in the `gerrit.config`, using the following "git-config" style
parameter:

*`plugin.@PLUGIN@.filter`

: rule to filter events with. Supported rules look like:

 DROP classname fully.qualified.java.ClassName

or:

 DROP RefUpdatedEvent isNoteDbMetaRef

If the `plugin.@PLUGIN@.filter` key is specified more than once it
will cause events matching any of the rules to be dropped.

The example config below drops all known replication plugin events:

```
[plugin "events"]
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationDoneEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationFailedEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationScheduledEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationSucceededEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent
```


<a id="future"/>
@PLUGIN@ Future
---------------

where the last section may be a star '*' to represent all classes
below that level. If this key is specified more than once it will
cause events to be dropped if they match either of the rules.


  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.*

In the future it might be desirable to be able to filter based on
mixed criteria, and also to be able to add exceptions to previous
filtering rules.

The most obvious idea would be to support 'KEEP' rules by
specifying a rule without the 'DROP' keyword. Rules would then
be interpretted in a top down fashion. Within a [sub]section, any
match in a contiguous KEEP block will cause events to be kept if
it is the first block in the [sub]section or if the previous
contiguous DROP block would cause them to be dropped.
Conversely, any match in a contiguous DROP block will cause
events to be dropped if it is the first block in the
[sub]section or if the previous contiguous KEEP block would
cause them to be kept.

With this idea, the example below would drop all replication
events except for the RemoteRefReplicationEvent:

```
[plugin "events"]
  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.*
  filter = classname com.googlesource.gerrit.plugins.replication.events.RemoteRefReplicationEvent
```

The example below would drop all non-core events (all plugin
events), and additionally it would drop the CommentAddedEvent.

```
[plugin "events"]
  filter = classname com.google.gerrit.server.events.*
  filter = DROP classname com.google.gerrit.server.events.CommentAddedEvent
```

The next most obvious enhancement would be to keep something
other than the 'classsname' keyword, the 'field' keyword comes
to mind first. For more complicated situations, a 'method' keyword
might be needed. The previous example might become:

```
[plugin "events"]
  filter = classname com.google.gerrit.server.events.*
  filter = DROP field TYPE comment-added
```

A 'method' example:

```
[plugin "events"]
  filter = classname com.google.gerrit.server.events.*
  filter = DROP method getRefName refs/stars/*
```

We might eventually need to chain stuff together

```
[plugin "events"] # drop ps-created and comment-added types
  filter = DROP field TYPE patchset-created
  filter = DROP field TYPE comment-added

[plugin "events"] # drop ps-created which are refs/meta/config
  filter = DROP field patchset-created
  filter = getRefName ! refs/meta/config

[plugin "events"] # drop ps-created which are stars and All-Users
              # and drop ps-created which are refs/meta/config
  filter = DROP field TYPE patchset-created
  filter = java com.google.gerrit.server.events.PatchsetCreatedEvent.TYPE patchset-created
  filter = java getProjectNameKey().get() ! All-Users
  filter = method getRefName ! refs/stars/*
  filter = DROP getRefName refs/meta/config

[plugin "events"] # draft-comments and refs/meta/config
  filter = classname com.google.gerrit.server.events.*
  filter = DROP field patchset-created
  filter = java getProjectNameKey().get() ! All-Users
  filter = method getRefName ! refs/stars/*
  filter = DROP getRefName refs/meta/config

[plugin "events"] # drop ps-created events only on a refs/stars
  filter = DROP field ref-updated
  filter = method getRefName !~ refs/drafts-comments/.*|refs/starred-changes/.*
```
If we don't really want to support the whole 'java' thing, we
might be able to set properties instead:

```
  set-projectNameKey = method getProjectNameKey UPPERCASE_CONSTANT
  set-project = method get ${projectNameKey}
```


  filter = DROP classname com.googlesource.gerrit.plugins.replication.events.RemoteRefReplicationEvent # super class only

  filter-TYPE = DROP ref-replicated
  filter-classname = com.google.gerrit.server.events.*
  filter-TYPE = ref-replication-done
  filter-TYPE = ref-replication-scheduled

  getStrings("filter-TYPE") -> "DROP ref-replicated", "ref-replication-done", "ref-replication-scheduled"

  getStrings("filter-classname") -> "com.google.gerrit.server.events.*"


project-deletion-replication-done
project-deletion-replication-failed
project-deletion-replication-scheduled
project-deletion-replication-succeeded

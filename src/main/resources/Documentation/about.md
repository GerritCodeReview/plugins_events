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

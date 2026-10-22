@PLUGIN@ Configuration
======================

In a multi-primary environment events may be created and written
to the filestore from any primary.  Without a notification system,
events written by other servers might only be seen by the current
server when the current server creates a new event.  If the traffic
on the current server is low, this delay may be significant and
unacceptable.

In order to get events from other primaries output in a timely manner,
the @PLUGIN@ plugin can be configured to poll and recongnize when
new events may have been written to the filestore and output them.

Reload the plugin on each primary for the changes to take effect.

Config
------

`pollingInterval`

: The polling frequency can be specified in the configuration.
For example:

```
  [plugin "@PLUGIN@"]
    pollingInterval = 3s
```

causes polling to be done every 3 seconds.

Values should use common time unit suffixes to express their setting:

* s, sec, second, seconds
* m, min, minute, minutes
* h, hr, hour, hours
* d, day, days
* w, week, weeks (`1 week` is treated as `7 days`)
* mon, month, months (`1 month` is treated as `30 days`)
* y, year, years (`1 year` is treated as `365 days`)

If a time unit suffix is not specified, `seconds` is assumed.

If 'pollingInterval' is not present in the configuration, polling
will not be enabled.

`trimInterval`

: How often old events are trimmed from the event store. Trimming
keeps the `trimSize` most recent events, exactly like the
[trim](cmd-trim.md) command with its `--size` option. For example:

```
  [plugin "@PLUGIN@"]
    trimInterval = 1h
    trimSize = 100000
```

causes all but the 100000 most recent events to be trimmed every hour.

The interval uses the same time unit suffixes as `pollingInterval`.

Both options are required to enable periodic trimming; if either one
is missing, or is set to 0, no trimming is done. In a multi-primary
environment, each primary trims the shared store on its own schedule,
which is safe since trimming is coordinated through the store itself.

`trimSize`

: How many of the most recent events periodic trimming keeps in the
event store, see `trimInterval`.

`queue`

: The work queue on which the polling and the trimming are scheduled
can be provided using the queue option. Example

```
  [plugin "@PLUGIN@"]
    queue = myQueue
```

causes polling to be scheduled on myQueue executor. If the specified
queue is not found or the queue is not specified, default queue
(WorkQueue) is used.

`directory`

: The directory in which events are stored can be specified using
the directory option. Below example causes events to be stored under
`/shared/gerrit/events`.

```
  [plugin "@PLUGIN@"]
    directory = /shared/gerrit/events
```

Relative paths are resolved against the site directory, so below is
equivalent to the default, `<site_dir>/data/plugin/events`.

```
  [plugin "@PLUGIN@"]
    directory = data/plugin/events
```

`maxConcurrentWrites`

: The maximum number of threads in this Gerrit process that may write
events to the store at the same time. Other threads wait for their
turn in arrival order. On a shared filesystem such as NFS, a couple
of writers can overlap their work, but many writers at once slow each
other down. The default is 2. Set it to 0 to remove the limit.

```
  [plugin "@PLUGIN@"]
    maxConcurrentWrites = 2
```

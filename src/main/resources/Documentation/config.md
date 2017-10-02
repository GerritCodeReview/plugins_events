@PLUGIN@ Configuration
======================

In a multi-master environment events may be created and written
to the filestore from any master.  Without a notification system,
events written by other servers might only be seen by the current
server when the current server creates a new event.  If the traffic
on the current server is low, this delay may be significant and
unacceptable.

In order to get events from other masters output in a timely manner,
the @PLUGIN@ plugin can be configured to poll and recongnize when
new events may have been written to the filestore and output them.

Reload the plugin on each master for the changes to take effect.

The polling frequency can be specified in the configuration.
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

@PLUGIN@
========

The @PLUGIN@ plugin provides a mechanism to store events.

@PLUGIN@ Commands
-----------------

[stream](cmd-stream.html)

: Monitor events occurring in real time.


[trim](cmd-trim.html)

: Trim old events up from EventStore.


<a id="storage"/>
@PLUGIN@ Storage
----------------

The events plugin stores events using a standard filesystem.  Events
are stored under "<site_dir>/data/plugin/events".  Events do not use
significant disk space, however it might still make sense to regularly
trim them with a cron job.

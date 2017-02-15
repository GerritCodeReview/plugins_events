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

// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.events;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Periodically trims the EventStore down to the configured amount of events. */
@Singleton
public class Trimmer implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(Trimmer.class);

  public static class TrimLifecycleListener implements LifecycleListener {
    protected final String workQueue;
    protected final WorkQueue queue;
    protected final Optional<Schedule> schedule;
    protected final long trimSize;
    protected final Trimmer trimmer;
    protected ScheduledFuture<?> future;

    @Inject
    protected TrimLifecycleListener(
        WorkQueue queue,
        @PollingQueue String workQueue,
        @TrimSchedule Optional<Schedule> schedule,
        @TrimSize long trimSize,
        Trimmer trimmer) {
      this.queue = queue;
      this.workQueue = workQueue;
      this.schedule = schedule;
      this.trimSize = trimSize;
      this.trimmer = trimmer;
    }

    @Override
    public void start() {
      if (!schedule.isPresent()) {
        return;
      }
      if (trimSize <= 0) {
        log.error("trimSize is required to trim periodically, periodic trimming is disabled");
        return;
      }
      ScheduledExecutorService executor = queue.getExecutor(workQueue);
      if (executor == null) {
        executor = queue.getDefaultQueue();
      }
      future =
          executor.scheduleAtFixedRate(
              trimmer, schedule.get().initialDelay(), schedule.get().interval(), MILLISECONDS);
    }

    @Override
    public void stop() {
      if (future != null) {
        future.cancel(true);
      }
    }
  }

  protected final EventStore store;
  protected final long trimSize;

  @Inject
  protected Trimmer(EventStore store, @TrimSize long trimSize) {
    this.store = store;
    this.trimSize = trimSize;
  }

  @Override
  public void run() {
    try {
      store.trim(store.getHead() - trimSize);
    } catch (IOException | RuntimeException e) {
      /* Exceptions thrown here would stop the task from being run again. */
      log.error("Cannot trim event store", e);
    }
  }

  @Override
  public String toString() {
    return "Events Trimmer";
  }
}

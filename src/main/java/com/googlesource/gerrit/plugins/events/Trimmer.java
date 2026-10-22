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
  /** Trimming to this size keeps every event, making it a safe default. */
  public static final long DEFAULT_TRIM_TO_SIZE = Long.MAX_VALUE;

  private static final Logger log = LoggerFactory.getLogger(Trimmer.class);

  public static class TrimLifecycleListener implements LifecycleListener {
    protected final String workQueue;
    protected final WorkQueue queue;
    protected final Optional<Schedule> schedule;
    protected final long trimToSize;
    protected final Trimmer trimmer;
    protected ScheduledFuture<?> future;

    @Inject
    protected TrimLifecycleListener(
        WorkQueue queue,
        @PollingQueue String workQueue,
        @TrimSchedule Optional<Schedule> schedule,
        @TrimToSize long trimToSize,
        Trimmer trimmer) {
      this.queue = queue;
      this.workQueue = workQueue;
      this.schedule = schedule;
      this.trimToSize = trimToSize;
      this.trimmer = trimmer;
    }

    @Override
    public void start() {
      if (!schedule.isPresent()) {
        return;
      }
      if (trimToSize < 0 || trimToSize == DEFAULT_TRIM_TO_SIZE) {
        log.error("trimToSize must be set to a non negative value, periodic trimming is disabled");
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
  protected final long trimToSize;

  @Inject
  protected Trimmer(EventStore store, @TrimToSize long trimToSize) {
    this.store = store;
    this.trimToSize = trimToSize;
  }

  @Override
  public void run() {
    try {
      store.trim(store.getHead() - trimToSize);
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

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
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
    protected final long trimInterval;
    protected final long trimSize;
    protected final Trimmer trimmer;
    protected ScheduledFuture<?> future;

    @Inject
    protected TrimLifecycleListener(
        WorkQueue queue,
        @PollingQueue String workQueue,
        @TrimInterval long trimInterval,
        @TrimSize long trimSize,
        Trimmer trimmer) {
      this.queue = queue;
      this.workQueue = workQueue;
      this.trimInterval = trimInterval;
      this.trimSize = trimSize;
      this.trimmer = trimmer;
    }

    @Override
    public void start() {
      if (trimInterval > 0 && trimSize > 0) {
        ScheduledExecutorService executor = queue.getExecutor(workQueue);
        if (executor == null) {
          executor = queue.getDefaultQueue();
        }
        /* Fixed delay, not fixed rate, so that a trim which takes longer than
         * trimInterval does not cause further trims to be run back to back. */
        future = executor.scheduleWithFixedDelay(trimmer, trimInterval, trimInterval, MILLISECONDS);
      } else if (trimInterval > 0 || trimSize > 0) {
        log.error("Both trimInterval and trimSize are required, periodic trimming is disabled");
      }
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
    } catch (IOException e) {
      log.error("Cannot trim event store", e);
    }
  }

  @Override
  public String toString() {
    return "Events Trimmer";
  }
}

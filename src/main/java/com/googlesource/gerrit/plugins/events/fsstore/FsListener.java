// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.events.fsstore;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.events.FileSystemEventBroker;
import com.googlesource.gerrit.plugins.events.PollingInterval;
import com.googlesource.gerrit.plugins.events.PollingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

@Singleton
public class FsListener implements Runnable {
  public static class FsLifecycleListener implements LifecycleListener {
    protected final String workQueue;
    protected final WorkQueue queue;
    protected final long pollingInterval;
    protected final FileSystemEventBroker broker;
    protected ScheduledFuture<?> future;

    @Inject
    protected FsLifecycleListener(
        WorkQueue queue,
        @PollingInterval long pollingInterval,
        EventDispatcher dispatcher,
        @PollingQueue String workQueue) {
      this.queue = queue;
      this.pollingInterval = pollingInterval;
      this.broker = (FileSystemEventBroker) dispatcher;
      this.workQueue = workQueue;
    }

    @Override
    public void start() {
      if (pollingInterval > 0) {
        ScheduledExecutorService executor = queue.getExecutor(workQueue);
        if (executor == null) {
          executor = queue.getDefaultQueue();
        }
        future =
            executor.scheduleAtFixedRate(
                new FsListener(broker), pollingInterval, pollingInterval, MILLISECONDS);
      }
    }

    @Override
    public void stop() {
      if (future != null) {
        future.cancel(true);
      }
    }
  }

  protected final FileSystemEventBroker broker;

  @Inject
  protected FsListener(FileSystemEventBroker broker) {
    this.broker = broker;
  }

  @Override
  public void run() {
    try {
      broker.fireEventForStreamListeners();
    } catch (PermissionBackendException e) {
      // Ignore
    }
  }

  @Override
  public String toString() {
    return "Events FS Polling Listener";
  }
}

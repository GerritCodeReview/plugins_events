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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.events.StreamEventListener;
import com.googlesource.gerrit.plugins.events.PollingInterval;

@Singleton
public class FsListener implements Runnable {
  public static class FsLifecycleListener implements LifecycleListener {
    protected final WorkQueue queue;
    protected final long pollingInterval;
    protected final DynamicSet<StreamEventListener> listeners;

    @Inject
    protected FsLifecycleListener(
        WorkQueue queue,
        DynamicSet<StreamEventListener> listeners,
        @PollingInterval long pollingInterval) {
      this.queue = queue;
      this.listeners = listeners;
      this.pollingInterval = pollingInterval;
    }

    @Override
    public void start() {
      if (pollingInterval > 0) {
        queue
            .getDefaultQueue()
            .scheduleAtFixedRate(
                new FsListener(listeners), pollingInterval, pollingInterval, MILLISECONDS);
      }
    }

    @Override
    public void stop() {}
  }

  protected final DynamicSet<StreamEventListener> listeners;

  @Inject
  protected FsListener(DynamicSet<StreamEventListener> listeners) {
    this.listeners = listeners;
  }

  @Override
  public void run() {
    for (StreamEventListener l : listeners) {
      l.onStreamEventUpdate();
    }
  }

  @Override
  public String toString() {
    return "Events FS Polling Listener";
  }
}

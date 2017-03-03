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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Use a file to store a sequence in a multi node/process (Multi-Master) safe way, and allow an
 * event to be delivered for each update to the sequence.
 *
 * <p>The event submitter must perform the first phase of the UpdatableFileValue transaction by
 * initiating the ownerless transaction with an event file in it. Any actor may perform the
 * remaining 5 UpdatableFileValue transaction phases.
 */
public class EventSequence extends UpdatableFileValue<Long> {
  public static final Path EVENT = Paths.get("event");

  protected static class EventBuilder extends UpdatableFileValue.UpdateBuilder {
    public EventBuilder(BasePaths paths, String event) throws IOException {
      super(paths);
      FileValue.prepare(udir.resolve(EVENT), event);
      // build/<tmp>/<uuid>/event
    }
  }

  protected class UniqueUpdate extends UpdatableFileValue.UniqueUpdate<Long> {
    final Path event;
    Path destination;

    UniqueUpdate(String uuid, boolean ours, long maxTries) throws IOException {
      super(EventSequence.this, uuid, ours, maxTries);
      event = upaths.udir.resolve(EVENT);
      spinFinish();
    }

    @Override
    protected void finish() throws IOException {
      storeEvent();
      super.finish();
    }

    protected void storeEvent() throws IOException {
      Path destination = getEventDestination(next);
      if (Fs.tryAtomicMove(event, destination)) {
        // update/<uuid>/event -> destination
        this.destination = destination;
      }
    }
  }

  public long totalSpins;
  public long totalUpdates;

  public EventSequence(Path base) {
    super(base);
  }

  public void initFs() throws IOException {
    initFs((long) 0);
  }

  protected UniqueUpdate spinSubmit(String event, long maxTries) throws IOException {
    try (EventBuilder b = new EventBuilder(paths, event)) {
      for (long tries = 0; tries < maxTries; tries++) {
        if (Fs.tryAtomicMove(b.dir, paths.update)) { // build/<tmp>/ -> update/
          // update/<uuid>/event
          synchronized (this) {
            totalUpdates++;
            totalSpins += tries - 1;
          }
          return createUniqueUpdate(b.uuid, true, maxTries - tries);
        }

        UniqueUpdate update = (UniqueUpdate) completeOngoing(maxTries - tries);
        if (update != null) {
          tries += update.tries - 1;
        }
      }
      throw new IOException("Cannot submit event " + paths.base + " after " + maxTries + " tries.");
    }
  }

  protected Long getToValue(Long currentValue) {
    return currentValue + 1;
  }

  protected UniqueUpdate createUniqueUpdate(String uuid, boolean ours, long maxTries)
      throws IOException {
    return new UniqueUpdate(uuid, ours, maxTries);
  }

  protected Path getEventDestination(Long n) {
    return paths.base.resolve(EVENT);
  }
}

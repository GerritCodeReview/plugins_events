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
 * <p>Adds phase 1+, add an event file in the <uuid> dir.
 *
 * <p>Adds phase 2+, move the event to the event store.
 *
 * <p>The event submitter must perform the first phase of the UpdatableFileValue transaction by
 * initiating the ownerless transaction with an event file in it. Any actor may perform the
 * remaining 5 UpdatableFileValue.
 */
public class EventSequence extends UpdatableFileValue<Long> {
  public static final Path EVENT = Paths.get("event");

  /** For phase 1 - 1+ */
  protected static class EventBuilder extends UpdatableFileValue.UpdateBuilder {
    public EventBuilder(BasePaths paths, String event) throws IOException {
      super(paths);
      FileValue.prepare(udir.resolve(EVENT), event); // Phase 1+
      // build/<tmp>/<uuid>/event
    }
  }

  protected class UniqueUpdate extends UpdatableFileValue.UniqueUpdate<Long> {
    protected final Path event;
    protected Path destination;

    /** Advance through phases 2 - 6 */
    protected UniqueUpdate(String uuid, boolean ours, long maxTries) throws IOException {
      super(EventSequence.this, uuid, ours, maxTries);
      event = upaths.udir.resolve(EVENT);
      spinFinish();
    }

    @Override
    protected void finish() throws IOException {
      storeEvent();
      super.finish();
    }

    /** Contains phase 2+ */
    protected void storeEvent() throws IOException {
      Path destination = getEventDestination(next);
      // Phase 2+
      if (Fs.tryAtomicMove(event, destination)) { // rename update/<uuid>/event -> destination
        // now there should be: update/<uuid>/ and destination (file)
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

  // Advance through phases 1 - 6
  protected UniqueUpdate spinSubmit(String event, long maxTries) throws IOException {
    try (EventBuilder b = new EventBuilder(paths, event)) {
      for (long tries = 0; tries < maxTries; tries++) {
        // Phase 1
        if (Fs.tryAtomicMove(b.dir, paths.update)) { // rename build/<tmp>/ -> update/
          // now there should be: update/<uuid>/event
          synchronized (this) {
            totalUpdates++;
            totalSpins += tries - 1;
          }
          return createUniqueUpdate(b.uuid, true, maxTries - tries); // Advance phases 2 - 6
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

  @Override
  protected UniqueUpdate createUniqueUpdate(String uuid, boolean ours, long maxTries)
      throws IOException {
    return new UniqueUpdate(uuid, ours, maxTries);
  }

  /** Override to shard */
  protected Path getEventDestination(Long n) {
    return paths.base.resolve(EVENT).resolve(n.toString());
  }
}

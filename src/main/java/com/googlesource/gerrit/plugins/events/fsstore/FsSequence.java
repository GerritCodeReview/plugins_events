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

/**
 * Use a file to store a sequence in a multi node/process (Multi-Master) safe way.
 *
 * <p>Any actor may perform any/all of the 6 UpdatableFileValue transaction phases. The actor
 * performing the commit will be considered to have performed the increment to the new sequence
 * value.
 */
public class FsSequence extends UpdatableFileValue<Long> {
  protected class UniqueUpdate extends UpdatableFileValue.UniqueUpdate<Long> {
    UniqueUpdate(String uuid, boolean ours, long maxTries) throws IOException {
      super(FsSequence.this, uuid, ours, maxTries);
      spinFinish();
    }
  }

  public long totalSpins;
  public long totalUpdates;

  public FsSequence(Path base) {
    super(base);
  }

  public void initFs() throws IOException {
    initFs((long) 0);
  }

  /**
   * Attempt up to maxTries to increment the sequence
   *
   * @param maxTries How many times to attempt to increment the sequence
   * @return the new sequence value after this increment.
   */
  public long spinIncrement(long maxTries) throws IOException {
    long tries = 0;
    for (; tries < maxTries; tries++) {
      try (UpdateBuilder b = new UpdateBuilder(paths)) {
        for (; tries < maxTries; tries++) {
          UniqueUpdate update = null;
          if (Fs.tryAtomicMove(b.dir, paths.update)) { // build/<tmp>/ -> update/
            update = new UniqueUpdate(b.uuid, true, maxTries);
            // update/<uuid>/
          } else {
            update = (UniqueUpdate) completeOngoing(maxTries);
          }
          if (update != null) {
            tries += update.tries - 1;
            if (update.myCommit) {
              spun(tries);
              return update.next;
            }
            if (update.ours && update.finished) {
              break; // usurped -> outer loop can make a new transaction
            }
          }
        }
      }
    }
    spun(tries - 1);
    throw new IOException(
        "Cannot increment sequence file " + path + " after " + maxTries + " tries.");
  }

  /** Do NOT spin on this, it creates a new transaction every time. */
  protected Long increment() throws IOException {
    try (UpdateBuilder b = new UpdateBuilder(paths)) {
      if (Fs.tryAtomicMove(b.dir, paths.update)) { // build/<tmp>/ -> update/
        // update/<uuid>/
        UniqueUpdate update = new UniqueUpdate(b.uuid, true, 1);
        if (update.myCommit) {
          return update.next;
        }
      }
    }
    return null;
  }

  protected synchronized void spun(long spins) {
    totalUpdates++;
    totalSpins += spins;
  }

  protected Long getToValue(Long currentValue) {
    return currentValue + 1;
  }

  protected UniqueUpdate createUniqueUpdate(String uuid, boolean ours, long maxTries)
      throws IOException {
    return new UniqueUpdate(uuid, ours, maxTries);
  }
}

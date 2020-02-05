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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * An infrastructure to create updates to a FileValue using a 6 phase transaction which is multi
 * node/process (Multi-Master) safe. The 6 phases of the transaction are:
 *
 * <p>1) Intiate a unique ownerless transaction, locking and preventing the value from changing
 * while the transaction is still open.
 *
 * <p>2) Read the current locked file value
 *
 * <p>3) Create a proposed update file containing the new proposed value based on the value read in
 * phase 2.
 *
 * <p>4) Close the transaction with the new proposed value, locking in the new value in the
 * transaction.
 *
 * <p>5) Atomically commit the new value to the locked file from the transaction.
 *
 * <p>6) Clean up the transaction
 *
 * <p>Any actor may perform any/all of the above phases.
 */
public abstract class UpdatableFileValue<T> extends NfsFileValue<T> {
  public static final Path CLOSED = Paths.get("closed");
  public static final Path INIT = Paths.get("init");
  public static final Path NEXT = Paths.get("next");
  public static final Path PRESERVED = Paths.get("preserved");
  public static final Path UPDATE = Paths.get("update");
  public static final Path VALUE = Paths.get("value");

  public static class BasePaths extends FsTransaction.BasePaths {
    public final Path update;
    public final Path preserved;

    public BasePaths(Path base) {
      super(base);
      update = base.resolve(UPDATE);
      preserved = base.resolve(PRESERVED);
    }
  }

  /** For Phase 1 */
  protected static class UpdateBuilder extends FsTransaction.Builder {
    public final String uuid = UUID.randomUUID().toString();
    public final Path udir = dir.resolve(uuid);
    public final Path next;

    public UpdateBuilder(BasePaths paths) throws IOException {
      super(paths);
      next = udir.resolve(NEXT);
      Files.createDirectories(next); // build/<tmp>/<uuid>/next
    }
  }

  /** For Phase 3 */
  protected static class NextBuilder extends FsTransaction.Builder {
    public NextBuilder(BasePaths paths, String next) throws IOException {
      super(paths);
      Path closed = dir.resolve(CLOSED);
      Files.createDirectory(closed);
      FileValue.prepare(closed.resolve(VALUE), next); // build/<tmp>/closed/value(next)
    }
  }

  /** Used to create the first value in the FS. Can handle contention, and existing values. */
  protected static class InitBuilder extends FsTransaction.Builder {
    public InitBuilder(BasePaths paths, String init) throws IOException {
      super(paths);
      FileValue.prepare(dir.resolve(INIT), init); // build/<tmp>/init(init)
    }
  }

  protected static class UpdatePaths {
    public final Path udir;
    public final Path next;
    public final Path closed;
    public final Path value;

    protected UpdatePaths(Path base, String uuid) {
      udir = base.resolve(uuid);
      next = udir.resolve(NEXT);
      closed = next.resolve(CLOSED);
      value = closed.resolve(VALUE);
    }
  }

  /** Phase 2 -6 helper. */
  protected static class UniqueUpdate<T> {
    protected final UpdatableFileValue<T> updatable;
    protected final String uuid;
    protected final UpdatePaths upaths;
    protected final boolean ours;
    protected final T currentValue;
    protected final T next;

    protected long maxTries;

    protected long tries;
    protected boolean closed;
    protected boolean preserved;
    protected boolean committed;
    protected boolean finished;

    protected boolean myCommit;

    /** Advance through phase 2 */
    protected UniqueUpdate(
        UpdatableFileValue<T> updatable, String uuid, boolean ours, long maxTries)
        throws IOException {
      this.updatable = updatable;
      this.uuid = uuid;
      this.ours = ours;
      this.maxTries = maxTries;

      upaths = new UpdatePaths(updatable.paths.update, uuid);

      currentValue = spinGet();
      next = currentValue == null ? null : updatable.getToValue(currentValue);
    }

    /** Spin attempting phases 3 - 6 */
    protected void spinFinish() throws IOException {
      for (; tries < maxTries && !finished; tries++) {
        finish();
      }
    }

    /** Attempt advance through phases 3 - 6 */
    protected void finish() throws IOException {
      createAndProposeNext();
      commit();
      clean();
    }

    /** Contains phase 2 */
    protected T spinGet() throws IOException {
      IOException ioe = new IOException("No chance to read " + updatable.path);
      for (; tries < maxTries; tries++) {
        try {
          return updatable.get(); // Phase 2
        } catch (IOException e) {
          Nfs.throwIfNotStaleFileHandle(e);
          finished = !Files.exists(upaths.udir);
          if (finished) {
            // stale handle must have been caused by another actor completing instead
            return null;
          }
          ioe = e;
        }
      }
      throw ioe;
    }

    /** Contains phases 3 & 4 */
    protected void createAndProposeNext() throws IOException {
      if (!closed && !ours) {
        // In the default fast path (!closed && ours), we would not expect
        // it to be closed, so skip this check to get to the building faster.
        // Conversely, if not ours, a quick check here might allow us
        // to skip the slow building phase
        closed = Files.exists(upaths.closed);
      }
      if (!closed) {
        try (NextBuilder b =
            new NextBuilder(updatable.paths, updatable.serializer.fromGeneric(next))) { // Phase 3

          // Phase 4. Rename can only succeed if update/<uuid>/next/ is empty (desired) or
          // non-existent (not desired). The later case is detected after the move.
          Fs.tryAtomicMove(b.dir, upaths.next); // rename build/<tmp>/ -> update/<uuid>/next/
          // now there should be: update/<uuid>/next/closed/value(next)
        }

        // Do not use the result of the move to determine if it is closed.
        // The move result could provide false positives due to some filesystem
        // implementions allowing a second move to succeed after the transaction
        // has been finished and the first "closed" has been deleted under the
        // "delete" dir. Additionally, this check allows us to be able to detect
        // closes by other actors, not just ourselves.
        closed = Files.exists(upaths.closed);
      }
    }

    /** Contains phase 5 */
    protected void commit() throws IOException {
      if (!committed) {
        // Safe to perform this block (for performance reasons) even if we
        // have not detected "closed yet", since it can only actually succeed
        // when closed (operations depend on "closed" in paths).
        preserve();

        // rename update/<uuid>/next/closed/value(next) -> value
        committed = myCommit = updatable.update(upaths.value); // Phase 5
        // now there should be: update/<uuid>/next/closed/ and: value (file)
      }
      if (!committed && closed) {
        committed = !Files.exists(upaths.value);
      }
    }

    /** Contains phase 6 */
    protected void clean() {
      if (committed) {
        FsTransaction.renameAndDeleteUnique(upaths.udir, updatable.paths.delete); // Phase 6
        updatable.cleanPreserved();
      }
      finished = !Files.exists(upaths.udir);
    }

    /*
     * Creating an extra hard link to future "value" files keeps a filesystem reference to them
     * after the "value" file is replaced with a new "value" file. Keeping the reference around
     * allows readers on other nodes to still read the contents of the file without experiencing a
     * stale file handle exception over NFS. This can reduce the amount of spinning required for
     * readers.
     */
    protected void preserve() {
      if (!preserved) {
        preserved = Fs.tryCreateLink(updatable.paths.preserved.resolve(uuid), upaths.value);
      }
    }
  }

  protected final BasePaths paths;

  public UpdatableFileValue(Path base) {
    super(base.resolve(VALUE)); // value(val)
    this.paths = new BasePaths(base);
  }

  public void initFs(T init) throws IOException {
    super.init(init);
    Files.createDirectories(paths.preserved);
    while (!Files.exists(path)) {
      try (InitBuilder b = new InitBuilder(paths, serializer.fromGeneric(init))) {
        Fs.tryAtomicMove(b.dir, paths.update); // rename build/<tmp>/ -> update/
        // now there should be: update/init(init_value)
        if (!Files.exists(path)) {
          // using a non unique name, "init", to allow recovery below
          Fs.tryAtomicMove(paths.update.resolve(INIT), path); // rename update/init(init) -> value
          // now there should be: update/ and: value (file)
        }
      }
    }
    Fs.tryDelete(paths.update.resolve(INIT)); // cleanup
  }

  protected abstract T getToValue(T currentValue);

  protected UniqueUpdate<T> completeOngoing(long maxTries) throws IOException {
    if (shouldCompleteOngoing()) {
      Path ongoing = Nfs.getFirstDirEntry(paths.update);
      if (ongoing != null) {
        // Attempt to complete previous updates
        return createUniqueUpdate(Fs.basename(ongoing).toString(), false, maxTries);
      }
    }
    return null;
  }

  protected boolean shouldCompleteOngoing() throws IOException {
    // Collisions are expected, and we don't actually want to
    // complete them too often since it affects fairness
    // by potentially preventing slower actors from ever
    // committing.  We do however need to prevent deadlock from
    // a stale proposal, so we do need to complete proposals
    // that stay around too long.

    // Maximum delay incurred due to a server crash.
    FileTime expiry = Fs.getFileTimeAgo(10, TimeUnit.SECONDS);
    return Nfs.isAllEntriesOlderThan(paths.update, expiry);
  }

  protected abstract UniqueUpdate<T> createUniqueUpdate(String uuid, boolean ours, long maxTries)
      throws IOException;

  protected void cleanPreserved() {
    // 1 second seems to be long enough even for slow readers (over a WAN) under high contention
    // ("value" file being updated by a fast writer), to avoid spinning on reads most of the time.
    FileTime expiry = Fs.getFileTimeAgo(1, TimeUnit.SECONDS);
    Fs.tryRecursiveDeleteEntriesOlderThan(paths.preserved, expiry, 5);
  }
}

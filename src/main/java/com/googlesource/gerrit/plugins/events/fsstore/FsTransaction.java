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
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public class FsTransaction {
  /**
   * A class to keep track of scratch pads to safely build proposals, and to safely delete them
   * during cleanup.
   *
   * <p>The first assumption is that unique dirs under the build dir will be used for building, and
   * that these may be deleted at any time to keep the filesystem clean under the assumption that
   * they may be stale. The contract however, is that all deleting must be done by first moving the
   * toplevel dir to the delete directory. This ensures that the processes creating entries under
   * the build dir will always have their entries intact or non-existing, but never partially what
   * they expect.
   *
   * <p>The next assumption is that all entries under the build dir are not only safe to delete at
   * any time, but that they should be deleted by helping processes to ensure that interrupted
   * processes do not lead to entry build up in the filesystem.
   */
  public static class BasePaths {
    public final Path base;
    public final Path build;
    public final Path delete;

    // Stale entries should be designed to be rare, and only happen during
    // unclean shutdowns.
    public long cleanInterval = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
    public int maxDelete = 5; // keep low to not slowdown current update much.
    protected long lastClean;

    public BasePaths(Path base) {
      this.base = base;
      build = base.resolve("build");
      delete = base.resolve("delete");
    }

    public void autoClean() {
      if (needsClean()) {
        FileTime expiry = Fs.getFileTimeAgo(1, TimeUnit.DAYS);
        // maxDelete spreads a large cleaning burden over multiple updates
        if (clean(expiry, maxDelete)) {
          lastClean = System.currentTimeMillis();
        }
      }
    }

    /** Clean up to 'max' expired (presumably stale) entries */
    public boolean clean(FileTime expiry, int max) {
      try {
        return Fs.tryRecursiveDeleteEntriesOlderThan(delete, expiry, max)
            && renameAndDeleteEntriesOlderThan(build, delete, expiry, max);
      } catch (IOException e) {
        // If we knew if it was a repeat offender, we could consider logging it.
        return true; // Don't keep retrying failures.
      }
    }

    protected boolean needsClean() {
      return System.currentTimeMillis() - cleanInterval > lastClean;
    }
  }

  /** A tempdirectory builder that gets automatically cleaned up. */
  protected static class Builder implements AutoCloseable {
    public final BasePaths paths;
    public final Path dir;

    public Builder(BasePaths paths) throws IOException {
      this.paths = paths;
      Files.createDirectories(paths.build);
      Files.createDirectories(paths.delete);
      dir = Files.createTempDirectory(paths.build, null);
    }

    public void close() throws IOException {
      FsTransaction.renameAndDelete(dir, paths.delete);
      paths.autoClean();
    }
  }

  /**
   * Used to atomically delete a directory tree. Avoids name collisions with other processes
   * potentially using the same source name directory. Collisions could prevent the move to the
   * delete directory from succeeding.
   */
  public static void renameAndDelete(Path src, Path del) throws IOException {
    if (Files.exists(src)) {
      Path tmp = Files.createTempDirectory(del, null);
      Path reparented = Fs.reparent(src, tmp);
      Fs.tryAtomicMove(src, reparented);
      Fs.tryRecursiveDelete(tmp);
    }
  }

  /**
   * Used to atomically delete a directory tree when the src directory name is guaranteed to be
   * unique.
   */
  public static void renameAndDeleteUnique(Path src, Path del) {
    Path reparented = Fs.reparent(src, del);
    Fs.tryAtomicMove(src, reparented);
    Fs.tryRecursiveDelete(reparented);
  }

  /**
   * Used to atomically delete entries in a directory tree older than expiry, up to max count. Do
   * NOT throw DirectoryIteratorExceptions.
   *
   * @return whether all entries were deleted
   * @throws IOException
   */
  public static boolean renameAndDeleteEntriesOlderThan(
      Path dir, Path del, FileTime expiry, int max) throws IOException {
    try (DirectoryStream<Path> dirEntries = Files.newDirectoryStream(dir)) {
      for (Path path : dirEntries) {
        if (expiry.compareTo(Files.getLastModifiedTime(path)) > 0) {
          if (max-- < 1) {
            return false;
          }
          renameAndDelete(path, del);
        }
      }
    } catch (DirectoryIteratorException e) {
      // dir was deleted by another actor, thus so were all its entries
      Nfs.throwIfNotStaleFileHandle(e.getCause());
    }
    return true;
  }
}

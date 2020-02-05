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
import java.nio.file.attribute.FileTime;
import java.util.Locale;

/** Some NFS utilities */
public class Nfs {
  /**
   * Determine if a throwable or a cause in its causal chain is a Stale NFS
   * File Handle
   *
   * @param throwable
   * @return a boolean true if the throwable or a cause in its causal chain is
   *         a Stale NFS File Handle
   */
  public static boolean isStaleFileHandleInCausalChain(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof IOException && isStaleFileHandle((IOException) throwable)) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }

  /**
   * Determine if an IOException is a Stale NFS File Handle
   *
   * @param ioe
   * @return a boolean true if the IOException is a Stale NFS FIle Handle
   */
  public static boolean isStaleFileHandle(IOException ioe) {
    String msg = ioe.getMessage();
    return msg != null && msg.toLowerCase(Locale.ROOT).matches(".*stale .*file .*handle.*");
  }

  public static <T extends Throwable> void throwIfNotStaleFileHandle(T e) throws T {
    if (!isStaleFileHandleInCausalChain(e)) {
      throw e;
    }
  }

  /**
   * Is any entry in a directory tree older than expiry
   *
   * @throws IOException
   */
  public static boolean isAllEntriesOlderThan(Path dir, FileTime expiry) throws IOException {
    try {
      return Fs.isAllEntriesOlderThan(dir, expiry);
    } catch (IOException e) {
      throwIfNotStaleFileHandle(e);
    }
    return false; // Modified after start, so not older
  }

  /** Get the first entry in a directory. */
  public static Path getFirstDirEntry(Path dir) throws IOException {
    try {
      return Fs.getFirstDirEntry(dir);
    } catch (IOException e) {
      throwIfNotStaleFileHandle(e);
    }
    return null;
  }
}

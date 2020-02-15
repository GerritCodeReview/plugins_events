// Copyright (C) 2020 The Android Open Source Project
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
 * Helper file for serialzing and storing a single type in a file on NFS.
 *
 * <p>Sacrifice read speed and success rates to avoid triggering bad NFS behavior when updating the
 * file which can result in the file disappearing.
 *
 * <p>If a process on a Linux NFS client machine wants to perform an NFS rename while a process on
 * the same machine has a handle to the target file (even just to read it), the Linux NFS driver
 * will performa a NFS_silly_rename (it will rename the file to hopefully unique .nfs000000000xxx
 * looking file) on the target file before performing the actual rename requested. The silly rename
 * is to allow the process holding the file handle to not fail. This NFS_silly_rename is risky as it
 * can result in the target file disappearing permanently if either a) the Linux NFS client machine
 * dies before the second rename, or b) another Linux NFS client machine tries to do the same thing
 * right after the source file was renamed to the target file by the first Linux NFS client.
 *
 * <p>This class tries to avoid this bad situation by never reading the 'path' file while updating
 * it from the same process and thus never triggering the NFS_silly_rename on the 'path' file on
 * ourselves. As long as the value file is only accessed by one java process per physical machine,
 * using the same object, the 'path' file should never get deleted unexpectedly.
 */
public class NfsFileValue<T> extends FileValue<T> {
  protected final Object pathLock = new Object(); // Use pathLock when reading or updating.

  /**
   * Use this constructor to use a builtin serializer (String or Long), and be sure to call init(T)
   * with a value of your type for the serializer auto identification to happen.
   */
  public NfsFileValue(Path path) {
    this(path, (Serializer<T>) null);
  }

  public NfsFileValue(Path path, Serializer<T> serializer) {
    super(path, serializer);
  }

  @Override
  protected String read() throws IOException {
    synchronized (pathLock) {
      return super.read();
    }
  }

  @Override
  protected boolean update(Path src) throws IOException {
    synchronized (pathLock) {
      return Fs.tryAtomicMove(src, path);
    }
  }
}

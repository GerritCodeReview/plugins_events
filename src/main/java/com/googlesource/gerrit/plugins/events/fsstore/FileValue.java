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

/** Helper file for serialzing and storing a single type in a file */
public class FileValue<T> {
  protected final Path path;
  protected Serializer<T> serializer;

  /**
   * Use this constructor to use a builtin serializer (String or Long), and be sure to call init(T)
   * with a value of your type for the serializer auto identification to happen.
   */
  public FileValue(Path path) {
    this(path, (Serializer<T>) null);
  }

  public FileValue(Path path, Serializer<T> serializer) {
    this.path = path;
    this.serializer = serializer;
  }

  /** Should be called by subclasses when initializing their file to a value. */
  protected void init(T init) {
    initSerializer(init);
  }

  /**
   * Auto setup the serializer based on the type used to initialize the class.
   *
   * <p>Must be called with a supported type before use if a serializer has been set manually. Safe
   * to call if the Serializer was already set.
   */
  @SuppressWarnings("unchecked") // we check the type of init, so these casts are safe
  protected void initSerializer(T init) {
    if (serializer == null) {
      if (init instanceof String) {
        serializer = (Serializer<T>) new Serializer.String();
      } else if (init instanceof Long) {
        serializer = (Serializer<T>) new Serializer.Long();
      }
    }
  }

  /** get (read) the unserialized object from the file with retries for stale file handles (NFS). */
  public T spinGet(long maxTries) throws IOException {
    for (long i = 0; i < maxTries; i++) {
      try {
        return get();
      } catch (IOException e) {
        Nfs.throwIfNotStaleFileHandle(e);
      }
    }
    throw new IOException(
        "Cannot read file " + path + " after " + maxTries + " Stale file handle tries.");
  }

  /** get (read) the unserialized object from the file */
  protected T get() throws IOException {
    return serializer.fromString(read());
  }

  /** The lowest level raw String read of the file */
  protected String read() throws IOException {
    return Fs.readUtf8(path);
  }

  /** Serialize object to given tmp file in preparation to call update() */
  protected void prepareT(Path tmp, T t) throws IOException {
    prepare(tmp, serializer.fromGeneric(t));
  }

  /** Atmoically update (replace) file with src file. */
  protected boolean update(Path src) throws IOException {
    return Fs.tryAtomicMove(src, path);
  }

  /** Low level raw string write to given tmp file in preparation to call update(). */
  protected static void prepare(Path tmp, String s) throws IOException {
    Fs.writeUtf8(tmp, s);
  }
}

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

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.events.EventStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import javax.inject.Singleton;

/** This class is only Thread, but not process (Multi-Master) safe */
@Singleton
public class FsStore implements EventStore {
  public abstract static class FsValue<T> {
    protected final Path path;

    public FsValue(Path path) {
      this.path = path;
    }

    /** Only Thread safe, but not process (Multi-Master) safe */
    public synchronized void initFs(T value) throws IOException {
      if (!Files.isRegularFile(path)) {
        Fs.createDirectories(path.getParent());
        set(value);
      }
    }

    protected abstract void set(T value) throws IOException;

    protected synchronized String load() throws IOException {
      return Fs.readFile(path);
    }

    /** Only Thread, but not process (Multi-Master) safe */
    protected synchronized void store(String value) throws IOException {
      Files.write(path, value.getBytes());
    }
  }

  public static class StringFsValue extends FsValue<String> {
    public StringFsValue(Path path) {
      super(path);
    }

    public String get() throws IOException {
      return load();
    }

    public void set(String value) throws IOException {
      store(value + "\n");
    }
  }

  public static class FsSequence extends FsValue<Long> {
    public FsSequence(Path path) {
      super(path);
    }

    public Long get() throws IOException {
      return Long.parseLong(load());
    }

    protected void set(Long value) throws IOException {
      store("" + value + "\n");
    }

    /** Only Thread safe, but not process (Multi-Master) safe */
    public synchronized Long increment() throws IOException {
      Long next = get() + 1;
      set(next);
      return next;
    }
  }

  protected static class BasePaths {
    final Path base;
    final Path uuid;
    final Path head;
    final Path tail;
    final DynamicRangeSharder events;

    public BasePaths(Path base) {
      this.base = base;
      uuid = base.resolve("uuid");
      events = new DynamicRangeSharder(base.resolve("events"));
      head = base.resolve("head");
      tail = base.resolve("tail");
    }

    public Path event(long event) {
      return events.dir(event).resolve(Long.toString(event));
    }

    public boolean isEventLastDirEntry(long event) {
      return events.isLastDirEntry(event);
    }
  }

  protected static class Stores {
    final StringFsValue uuid;
    final FsSequence head;
    final FsSequence tail;

    public Stores(BasePaths bases) {
      uuid = new StringFsValue(bases.uuid);
      head = new FsSequence(bases.head);
      tail = new FsSequence(bases.tail);
    }

    public void initFs() throws IOException {
      uuid.initFs(UUID.randomUUID().toString());
      head.initFs((long) 0);
      tail.initFs((long) 1);
    }
  }

  protected final BasePaths paths;
  protected final Stores stores;
  protected final UUID uuid;

  @Inject
  public FsStore(SitePaths site) throws IOException {
    this(site.data_dir.toPath().resolve("plugin").resolve("events").resolve("fstore-v1"));
  }

  public FsStore(Path base) throws IOException {
    paths = new BasePaths(base);
    stores = new Stores(paths);
    stores.initFs();
    uuid = UUID.fromString(stores.uuid.get());
  }

  @Override
  public UUID getUuid() throws IOException {
    return uuid;
  }

  /** Only Thread, but not process (Multi-Master) safe */
  @Override
  public synchronized void add(String event) throws IOException {
    long next = getHead() + 1;
    Path epath = paths.event(next);
    Fs.createDirectories(epath.getParent());
    Files.write(epath, (event + "\n").getBytes());
    stores.head.increment();
  }

  @Override
  public long getHead() throws IOException {
    return stores.head.get();
  }

  @Override
  public String get(long num) throws IOException {
    if (getTail() <= num && num <= getHead()) {
      try {
        return Fs.readFile(paths.event(num));
      } catch (NoSuchFileException e) {
      }
    }
    return null;
  }

  @Override
  public long getTail() throws IOException {
    if (getHead() == 0) {
      return 0;
    }
    long tail = stores.tail.get();
    return tail < 1 ? 1 : tail;
  }

  @Override
  public synchronized void trim(long trim) throws IOException {
    long head = getHead();
    if (trim >= head) {
      trim = head - 1;
    }
    if (trim > 0) {
      for (long i = getTail(); i <= trim; i++) {
        stores.tail.increment();
        Path event = paths.event(i);
        Fs.tryRecursiveDelete(event);
        if (paths.isEventLastDirEntry(i)) {
          Fs.unsafeRecursiveRmdir(event.getParent().toFile());
        }
      }
    }
  }
}

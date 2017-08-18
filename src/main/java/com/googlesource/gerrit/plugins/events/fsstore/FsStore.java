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
  protected static class BasePaths {
    final Path uuid;
    final Path head;
    final Path tail;
    final DynamicRangeSharder events;

    public BasePaths(Path base) {
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
    final FsId uuid;
    final FsSequence head;
    final FsSequence tail;

    public Stores(BasePaths bases) {
      uuid = new FsId(bases.uuid);
      head = new FsSequence(bases.head);
      tail = new FsSequence(bases.tail);
    }

    public void initFs() throws IOException {
      uuid.initFs();
      head.initFs((long) 0);
      tail.initFs((long) 1);
    }
  }

  public static final long MAX_GET_SPINS = 1000;
  public static final long MAX_INCREMENT_SPINS = 1000;

  protected final BasePaths paths;
  protected final Stores stores;
  protected final UUID uuid;

  @Inject
  public FsStore(SitePaths site) throws IOException {
    this(site.data_dir.toPath().resolve("plugin").resolve("events").resolve("fstore-v1.2"));
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
    stores.head.spinIncrement(MAX_INCREMENT_SPINS);
  }

  @Override
  public long getHead() throws IOException {
    return stores.head.spinGet(MAX_GET_SPINS);
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
    long tail = stores.tail.spinGet(MAX_GET_SPINS);
    return tail < 1 ? 1 : tail;
  }

  @Override
  public void trim(long trim) throws IOException {
    long head = getHead();
    if (trim >= head) {
      trim = head - 1;
    }
    if (trim > 0) {
      for (long i = getTail(); i <= trim; i++) {
        long delete = stores.tail.spinIncrement(MAX_INCREMENT_SPINS) - 1;
        Path event = paths.event(delete);
        Fs.tryRecursiveDelete(event);
        if (paths.isEventLastDirEntry(delete)) {
          Fs.unsafeRecursiveRmdir(event.getParent().toFile());
        }
      }
    }
  }
}

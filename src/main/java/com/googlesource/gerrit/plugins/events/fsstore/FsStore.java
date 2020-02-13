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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import javax.inject.Singleton;

/** Use a filesystem to store events in a multi node/process (Multi-Master) safe way. */
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
  }

  protected static class Head extends EventSequence {
    FsStore.BasePaths paths;

    Head(FsStore.BasePaths paths) {
      super(paths.head);
      this.paths = paths;
    }

    @Override
    protected Path getEventDestination(Long n) {
      Path event = paths.events.path(n);
      try {
        Fs.createDirectories(event.getParent());
      } catch (IOException e) {
      }
      return event;
    }
  }

  protected static class Stores {
    final FsId uuid;
    final Head head;
    final FsSequence tail;

    public Stores(BasePaths bases) {
      uuid = new FsId(bases.uuid);
      head = new Head(bases);
      tail = new FsSequence(bases.tail);
    }

    public void initFs() throws IOException {
      uuid.initFs();
      head.initFs();
      tail.initFs((long) 1);
    }
  }

  public static final long MAX_GET_SPINS = 1000;
  public static final long MAX_SUBMIT_SPINS = 100000;
  public static final long MAX_INCREMENT_SPINS = 1000;

  protected final BasePaths paths;
  protected final Stores stores;
  protected final UUID uuid;

  protected final SequenceCache cachedHead;
  protected final SequenceCache cachedTail;

  @Inject
  public FsStore(SitePaths site) throws IOException {
    this(site.data_dir.resolve("plugin").resolve("events").resolve("fstore-v2"));
  }

  public FsStore(Path base) throws IOException {
    paths = new BasePaths(base);
    stores = new Stores(paths);
    stores.initFs();
    uuid = UUID.fromString(stores.uuid.get());
    cachedHead = new SequenceCache(stores.head);
    cachedTail = new SequenceCache(stores.tail);
  }

  @Override
  public UUID getUuid() throws IOException {
    return uuid;
  }

  @Override
  public void add(String event) throws IOException {
    stores.head.spinSubmit(event + "\n", MAX_SUBMIT_SPINS);
  }

  @Override
  public long getHead() throws IOException {
    return cachedHead.spinGet(MAX_GET_SPINS);
  }

  @Override
  public String get(long num) throws IOException {
    if (cachedTail.isLessThanOrEqualTo(num, MAX_GET_SPINS)
        && cachedHead.isGreaterThanOrEqualTo(num, MAX_GET_SPINS)) {
      try {
        return Fs.readUtf8(paths.events.path(num));
      } catch (NoSuchFileException e) {
      }
    }
    return null;
  }

  @Override
  public long getTail() throws IOException {
    if (cachedHead.isZero(MAX_GET_SPINS)) {
      return 0;
    }
    long tail = cachedTail.spinGet(MAX_GET_SPINS);
    return tail < 1 ? 1 : tail;
  }

  @Override
  public void trim(long trim) throws IOException {
    if (cachedHead.isLessThanOrEqualTo(trim, MAX_GET_SPINS)) {
      long head = getHead();
      if (trim >= head) {
        trim = head - 1;
      }
    }
    if (trim > 0) {
      for (long i = getTail(); i <= trim; i++) {
        long delete = stores.tail.spinIncrement(MAX_INCREMENT_SPINS) - 1;
        Path event = paths.events.path(delete);
        Fs.tryRecursiveDelete(event);
        if (paths.events.isLastDirEntry(delete)) {
          Fs.unsafeRecursiveRmdir(event.getParent().toFile());
        }
      }
    }
  }
}

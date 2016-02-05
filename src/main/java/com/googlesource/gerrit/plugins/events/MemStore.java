// Copyright (C) 2016 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Singleton
public class MemStore implements EventStore {
  protected final Map<Long, String> eventsByIndex = new ConcurrentHashMap<Long, String>();
  protected final UUID uuid;

  protected long head = 0;
  protected long tail = 1;

  public MemStore() {
    uuid = UUID.randomUUID();
  }

  @Override
  public UUID getUuid() {
    return uuid;
  }

  @Override
  public synchronized void add(String event) {
    eventsByIndex.put(head + 1, event);
    head++;
  }

  @Override
  public String get(long n) {
    return eventsByIndex.get(n);
  }

  @Override
  public long getHead() {
    return head;
  }

  @Override
  public long getTail() {
    return head == 0 ? 0 : tail;
  }

  @Override
  public void trim(long trim) {
    if (trim >= head) {
      trim = head - 1;
    }
    for (long i = tail; i <= trim; i++) {
      tail++;
      eventsByIndex.remove(i);
    }
  }
}

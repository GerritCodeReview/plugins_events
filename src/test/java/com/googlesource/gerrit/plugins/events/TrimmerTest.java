// Copyright (C) 2026 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.googlesource.gerrit.plugins.events.fsstore.Fs;
import com.googlesource.gerrit.plugins.events.fsstore.FsStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TrimmerTest {
  private Path base;
  private FsStore store;

  @Before
  public void setUp() throws Exception {
    base = Files.createTempDirectory("events-Trimmer");
    store = new FsStore(base);
  }

  @After
  public void tearDown() throws Exception {
    Fs.tryRecursiveDelete(base);
  }

  @Test
  public void keepsTheMostRecentEvents() throws IOException {
    add(5);

    new Trimmer(store, 2).run();

    assertEquals(4, store.getTail());
    assertEquals(5, store.getHead());
    assertNull(store.get(3));
    assertEquals("event-4", store.get(4));
    assertEquals("event-5", store.get(5));
  }

  @Test
  public void keepsEveryEventWhenStoreIsSmallerThanTrimSize() throws IOException {
    add(2);

    new Trimmer(store, 5).run();

    assertEquals(1, store.getTail());
    assertEquals(2, store.getHead());
    assertEquals("event-1", store.get(1));
  }

  @Test
  public void handlesAnEmptyStore() throws IOException {
    new Trimmer(store, 2).run();

    assertEquals(0, store.getTail());
    assertEquals(0, store.getHead());
  }

  private void add(int events) throws IOException {
    for (int i = 1; i <= events; i++) {
      store.add("event-" + i);
    }
  }
}

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
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EventSequenceTest extends TestCase {
  private static String dir = "events-EventSequence";
  private static Path base;
  private Path myBase;
  private EventSequence seq;
  private long count = 1000;
  private long maxSpins = 1;

  @Override
  @Before
  public void setUp() throws Exception {
    myBase = base;
    if (myBase == null) {
      myBase = Files.createTempDirectory(dir);
    }
    seq = new EventSequence(myBase);
    seq.initFs((long) 0);
  }

  @After
  public void tearDown() throws Exception {
    Fs.tryRecursiveDelete(myBase);
  }

  @Test
  public void testGetZero() throws IOException {
    assertEquals((long) 0, (long) seq.get());
  }

  @Test
  public void testSpinSubmit() throws IOException {
    Long next = seq.get() + (long) 1;
    String event = "Event " + next;

    EventSequence.UniqueUpdate up = seq.spinSubmit(event, maxSpins);
    assertEquals(next, seq.get());
    assertNotNull(up.destination);
    assertEquals(event, Fs.readFile(up.destination));
  }
}

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

import java.nio.file.Path;
import junit.framework.TestCase;
import org.junit.Test;

public class DynamicRangeSharderTest extends TestCase {
  DynamicRangeSharder sharder2;
  DynamicRangeSharder sharder3;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    sharder2 = new DynamicRangeSharder(java.nio.file.Paths.get("base"), 2);
    sharder3 = new DynamicRangeSharder(java.nio.file.Paths.get("base"), 3);
  }

  @Test
  public void testEntriesPerLevel() {
    assertEquals(100, sharder2.entriesPerLevel);
    assertEquals(1000, sharder3.entriesPerLevel);
  }

  @Test
  public void testLevels() {
    assertEquals(1, sharder2.levels(0));
    assertEquals(1, sharder2.levels(1));
    assertEquals(1, sharder2.levels(10));
    assertEquals(2, sharder2.levels(100));
    assertEquals(2, sharder2.levels(1000));
    assertEquals(3, sharder2.levels(100000));
    assertEquals(4, sharder2.levels(1000000));
    assertEquals(5, sharder2.levels(100000000));
    assertEquals(5, sharder2.levels(1000000000));

    assertEquals(1, sharder3.levels(0));
    assertEquals(1, sharder3.levels(1));
    assertEquals(1, sharder3.levels(10));
    assertEquals(1, sharder3.levels(100));
    assertEquals(2, sharder3.levels(1000));
    assertEquals(2, sharder3.levels(100000));
    assertEquals(3, sharder3.levels(1000000));
    assertEquals(3, sharder3.levels(100000000));
    assertEquals(4, sharder3.levels(1000000000));
  }

  @Test
  public void testSizeOfLevel() {
    assertEquals(1, sharder2.sizeOfLevel(0));
    assertEquals(100, sharder2.sizeOfLevel(1));
    assertEquals(10000, sharder2.sizeOfLevel(2));

    assertEquals(1, sharder3.sizeOfLevel(0));
    assertEquals(1000, sharder3.sizeOfLevel(1));
    assertEquals(1000000, sharder3.sizeOfLevel(2));
  }

  @Test
  public void testPadToOrder() {
    assertEquals("01", sharder2.padToOrder(1));
    assertEquals("10", sharder2.padToOrder(10));

    assertEquals("001", sharder3.padToOrder(1));
    assertEquals("010", sharder3.padToOrder(10));
    assertEquals("100", sharder3.padToOrder(100));
  }

  @Test
  public void testDir() {
    assertPathEquals("base/2", sharder2.dir(1));
    assertPathEquals("base/2", sharder2.dir(10));
    assertPathEquals("base/2.2/01", sharder2.dir(100));
    assertPathEquals("base/2.2/10", sharder2.dir(1000));
    assertPathEquals("base/2.2.2/01/00", sharder2.dir(10000));
    assertPathEquals("base/2.2.2/10/00", sharder2.dir(100000));
    assertPathEquals("base/2.2.2.2/01/00/00", sharder2.dir(1000000));
    assertPathEquals("base/2.2.2.2/01/00/10", sharder2.dir(1001000));
    assertPathEquals("base/2.2.2.2.2/01/00/00/00", sharder2.dir(100000000));
    assertPathEquals("base/2.2.2.2.2/10/00/00/00", sharder2.dir(1000000000));

    assertPathEquals("base/3", sharder3.dir(1));
    assertPathEquals("base/3", sharder3.dir(10));
    assertPathEquals("base/3", sharder3.dir(100));
    assertPathEquals("base/3.3/001", sharder3.dir(1000));
    assertPathEquals("base/3.3/010", sharder3.dir(10000));
    assertPathEquals("base/3.3/100", sharder3.dir(100000));
    assertPathEquals("base/3.3.3/001/000", sharder3.dir(1000000));
    assertPathEquals("base/3.3.3/001/001", sharder3.dir(1001000));
    assertPathEquals("base/3.3.3/100/000", sharder3.dir(100000000));
    assertPathEquals("base/3.3.3.3/001/000/000", sharder3.dir(1000000000));
  }

  @Test
  public void testIsLastDirEntry() {
    assertEquals(true, sharder2.isLastDirEntry(99));
    assertEquals(false, sharder2.isLastDirEntry(100));
    assertEquals(false, sharder2.isLastDirEntry(1000));

    assertEquals(true, sharder3.isLastDirEntry(999));
    assertEquals(false, sharder3.isLastDirEntry(1000));
    assertEquals(false, sharder3.isLastDirEntry(10001));
  }

  public static void assertPathEquals(String a, Path b) {
    assertEquals(a, b.toString());
  }
}

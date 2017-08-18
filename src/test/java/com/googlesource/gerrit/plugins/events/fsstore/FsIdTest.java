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

public class FsIdTest extends TestCase {
  private static String dir = "events-FsId";
  private static Path base;
  private FsId val;

  @Override
  @Before
  public void setUp() throws IOException {
    if (base == null) {
      base = Files.createTempDirectory(dir);
    }
    val = new FsId(base);
    val.initFs("init");
  }

  @After
  public void tearDown() throws IOException {
    Fs.tryRecursiveDelete(base);
  }

  @Test
  public void testGetInit() throws IOException {
    assertEquals("init", val.get());
  }

  @Test
  public void testReInit() throws IOException {
    val.initFs("init2");
    assertEquals("init", val.get());
  }

  @Test
  public void testGetInitUUID() throws IOException {
    tearDown();
    val.initFs();
    String id = val.get();
    val.initFs();
    assertEquals(id, val.get());
  }
}

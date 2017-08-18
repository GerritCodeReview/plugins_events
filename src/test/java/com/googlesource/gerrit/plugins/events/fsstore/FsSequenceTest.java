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
import java.nio.file.Paths;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FsSequenceTest extends TestCase {
  private static String dir = "events-FsSequence";
  private static Path base;
  private Path myBase;
  private FsSequence seq;
  private long count = 1000;
  private long maxSpins = 1;
  private String incrementMarker = "";

  @Override
  @Before
  public void setUp() throws Exception {
    myBase = base;
    if (myBase == null) {
      myBase = Files.createTempDirectory(dir);
    }
    seq = new FsSequence(myBase);
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
  public void testIncrement() throws IOException {
    Long next = seq.get() + (long) 1;
    assertEquals(next, seq.increment());
  }

  @Test
  public void testSpinIncrement() throws IOException {
    long next = seq.get() + (long) 1;
    assertEquals(next, (long) seq.spinIncrement(1));
  }

  @Test
  public void testCount() throws Exception {
    long previous = -1;
    for (long i = 0; i < count; i++) {
      Long v = seq.spinIncrement(maxSpins);
      if (v != null) {
        long val = v;
        if (val <= previous) {
          throw new Exception("val(" + val + ") <= previous(" + previous + ")");
        }
      }
      System.out.print(incrementMarker);
    }
    long average = seq.totalSpins / seq.totalUpdates;
    System.out.println("Average update submission spins: " + average);
  }

  /**
   * First, make the junit jar easily available
   *
   * <p>ln -s ~/.m2/repository/junit/junit/4.8.1/junit-4.8.1.jar target
   *
   * <p>To run type:
   *
   * <p>java -cp target/classes:target/test-classes:target/junit-4.8.1.jar \
   * com.googlesource.gerrit.plugins.events.fsstore.FsSequenceTest \ [dir [count [spin_retries]]]
   *
   * <p>Note: if you do not specify <dir>, it will create a directory under /tmp
   *
   * <p>NFS, 1 worker, count=1000, retries=1000 ~30s ~30ms/event avgspins 0 NFS, 2 workers,
   * count=1000, retries=1000 ~37s ~18ms/event avgspins 0 NFS, 3 workers, count=1000, retries=1000
   * ~50s ~17ms/event avgspins 18 NFS, 4 workers, count=1000, retries=1000 ~1m25 ~21ms/event
   * avgspins 26 scales best with 2 workers!
   *
   * <p>NFS(WAN), 1 worker, count=10, retries=1000 19s ~2s/event avgspins 0 NFS(WAN), 2 workers,
   * count=10, retries=1000 23s ~1s/event avgspins 1 NFS(WAN), 3 workers, count=10, retries=1000 38s
   * ~1.3s/event avgspins 12
   *
   * <p>Our main production server does 220K/week ~22/min events ~ <3s/event
   */
  public static void main(String[] argv) throws Exception {
    if (argv.length > 0) {
      base = Paths.get(argv[0]);
    }
    FsSequenceTest t = new FsSequenceTest();
    if (argv.length > 1) {
      t.count = Long.parseLong(argv[1]);
    }
    if (argv.length > 2) {
      t.maxSpins = Long.parseLong(argv[2]);
    }

    t.incrementMarker = ".";
    t.setUp();
    t.testCount();
  }
}

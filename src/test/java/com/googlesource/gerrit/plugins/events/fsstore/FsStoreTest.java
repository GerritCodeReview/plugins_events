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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FsStoreTest extends TestCase {
  private static String dir = "events-FsStore";
  private static Path base;
  private Path myBase;
  private FsStore store;
  private String submitMarker = "";

  private long count = 1000;
  Map<String, Long> reported = new HashMap<String, Long>();

  @Override
  @Before
  public void setUp() throws Exception {
    myBase = base;
    if (myBase == null) {
      myBase = Files.createTempDirectory(dir);
    }
    store = new FsStore(myBase);
  }

  @After
  public void tearDown() throws Exception {
    Fs.tryRecursiveDelete(myBase);
  }

  @Test
  public void testUUIDInit() throws IOException {
    UUID uuid = store.getUuid();
    FsStore store2 = new FsStore(myBase);
    assertEquals(uuid, store2.getUuid());
  }

  @Test
  public void testHeadInit() throws IOException {
    assertEquals(0, store.getHead());
    FsStore store2 = new FsStore(myBase);
    assertEquals(0, store2.getHead());
  }

  @Test
  public void testTailInit() throws IOException {
    assertEquals(0, store.getTail());
    FsStore store2 = new FsStore(myBase);
    assertEquals(0, store2.getTail());
  }

  @Test
  public void testGetInit() throws IOException {
    assertEquals(null, store.get(1));
  }

  @Test
  public void testAdd() throws IOException {
    add();
  }

  public void add() throws IOException {
    long next = store.getHead() + 1;
    assertEquals(0, get(next));

    add(next);
    assertEquals(next, get(next));
    assertEquals(next, store.getHead());
    assertEquals(1, store.getTail());

    FsStore store2 = new FsStore(myBase);
    assertEquals(next, store2.getHead());
    assertEquals(1, store2.getTail());
  }

  private void add(long l) throws IOException {
    store.add("" + l);
  }

  private long get(long i) throws IOException {
    String g = store.get(i);
    return g == null ? 0 : Long.parseLong(g);
  }

  @Test
  public void testAddAgain() throws IOException {
    add();
    add();
    add();
  }

  @Test
  public void testTrim() throws IOException {
    add();
    add();
    add();

    assertEquals(1, store.getTail());
    store.trim(1);
    assertEquals(2, store.getTail());
    assertEquals(0, get(1)); // just deleted
    assertEquals(2, get(2)); // beyond deleted

    store.trim(2);
    assertEquals(3, store.getTail());
    assertEquals(0, get(2)); // just deleted
    assertEquals(3, get(3)); // beyond deleted

    store.trim(3);
    assertEquals(3, store.getTail());
    assertEquals(3, get(3)); // cannot delete head
  }

  @Test
  public void testCount() throws Exception {
    for (long i = 0; i < count; i++) {
      add();
    }
  }

  public void count(String id) throws Exception {
    for (long i = 1; i <= count; i++) {
      store.add(id + " " + i);
      System.out.print(submitMarker);
    }
  }

  public boolean verify(String id, long head) throws Exception {
    Set<Long> found = new HashSet<Long>();
    long stop = store.getHead();
    long mine = 1;

    for (long i = head + 1; i <= stop; i++) {
      String event = store.get(i);
      if (event != null) {
        String split[] = event.split(" ");
        if (split != null && id.equals(split[0])) {
          long n = Long.parseLong(split[1]);
          if (!found.add(n)) {
            report("duplicate", n, i);
          }
          if (mine != n) {
            report("ouf of order", n, i);
          }
          mine++;
        }
      } else {
        report("gap", mine, i);
      }
    }
    for (long i = 1; i <= count; i++) {
      if (!found.contains(i)) {
        report("missing", i, -1);
      }
    }

    boolean error = false;
    error |= reportTotal("duplicate");
    error |= reportTotal("out of order");
    error |= reportTotal("missing");
    error |= reportTotal("gap");
    return error;
  }

  private void report(String type, long n, long i) {
    Long cnt = reported.get(type);
    if (cnt == null) {
      System.out.println("First " + type + ": " + n + " pos(" + i + ")");
      cnt = (long) 0;
    }
    reported.put(type, ++cnt);
  }

  private boolean reportTotal(String type) {
    Long cnt = reported.get(type);
    if (cnt != null) {
      System.out.println("Total " + type + "s: " + cnt);
      return true;
    }
    return false;
  }

  /**
   * First, make the junit jar easily available
   *
   * <p>ln -s ~/.m2/repository/junit/junit/4.8.1/junit-4.8.1.jar target
   *
   * <p>To run type:
   *
   * <p>java -cp target/classes:target/test-classes:target/junit-4.8.1.jar \
   * com.googlesource.gerrit.plugins.events.fsstore.FsStoreTest \ [dir [count [store-id]]]
   *
   * <p>Note: if you do not specify <dir>, it will create a directory under /tmp
   *
   * <p>Performance: NFS(Lowlatency,SSDs), 1 worker 1M, 266m ~16ms/event find events|wc -l 12s rm
   * -rf 1m49s du -sh -> 3.9G 1m7s
   *
   * <p>Local(spinning) 1 workers 1M 14.34s ~14us/event find events|wc -l 1.3s rm -rf 42s
   *
   * <p>Mixed workers: NFS(WAN) 1 worker (+NFS LAN continuous) count=10, 3m28s
   */
  public static void main(String[] argv) throws Exception {
    if (argv.length > 0) {
      base = Paths.get(argv[0]);
    }
    FsStoreTest t = new FsStoreTest();
    if (argv.length > 1) {
      t.count = Long.parseLong(argv[1]);
    }

    String id = UUID.randomUUID().toString();
    t.submitMarker = ".";
    if (argv.length > 2) {
      id = argv[2];
      t.submitMarker += id + ".";
    }

    t.setUp();
    long head = t.store.getHead();
    t.count(id);
    if (t.verify(id, head)) {
      System.out.println("\nFAIL");
      System.exit(1);
    }
    System.out.println("\nPASS");
  }
}

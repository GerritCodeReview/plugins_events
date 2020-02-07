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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FsStoreTest extends TestCase {
  public static String dir = "events-FsStore";
  public static Path base;
  public static List<OneThread> threads = new LinkedList<>();

  public Path myBase;
  public FsStore store;
  public String id = UUID.randomUUID().toString();
  public String submitMarker = "";
  public long count = 1000;

  public Map<String, Long> reported = new HashMap<String, Long>();

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

  public void count() throws Exception {
    for (long i = 1; i <= count; i++) {
      store.add(id + " " + i);
      System.out.print(submitMarker);
    }
  }

  public boolean verify(long head) throws Exception {
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

  public static class OneThread implements Runnable {
    public FsStoreTest test = new FsStoreTest();
    public Boolean result;

    @Override
    public void run() {
      try {
        test.setUp();
        long head = test.store.getHead();
        test.count();
        result = !test.verify(head);
      } catch (Exception e) {
      }
      if (threads.size() > 1) {
        if (result != null && result) {
          System.out.println("\nPASS " + test.id);
        } else {
          result = false;
          System.out.println("\nFAIL " + test.id);
        }
      }
    }
  }

  /**
   * First, make the junit jar easily available
   *
   * <p>ln -s ~/.m2/repository/junit/junit/4.8.1/junit-4.8.1.jar target
   *
   * <p>To run type:
   *
   * <p>java -cp target/classes:target/test-classes:target/junit-4.8.1.jar \
   * com.googlesource.gerrit.plugins.events.fsstore.FsStoreTest [--id store-id] [dir [count]]
   *
   * <p>Note: if you do not specify <dir>, it will create a directory under /tmp
   *
   * <p>Performance: NFS(Lowlatency,SSDs), 1 worker 1M, 266m ~16ms/event find events|wc -l 12s rm
   * -rf 1m49s du -sh -> 3.9G 1m7s
   *
   * <p>Local(spinning) 1 workers 1M 21m58s ~13ms/event find events|wc -l 1.3s rm -rf 34s
   *
   * <p>Multi workers: NFS(LowLatency,LAN,SSDs) 8 hosts count=1000 (each) avg 273s 1000/34s
   *
   * <p>Mixed workers: NFS(WAN) 1 worker (+NFS LAN continuous) count=10, 3m28s
   */
  public static void main(String[] argv) throws Exception {
    List<String> args = new LinkedList<>();
    for (String arg : argv) {
      args.add(arg);
    }

    setThreadCount(1);
    int thread = 0;
    setSubmitMarker(".");
    for (Iterator<String> it = args.iterator(); it.hasNext(); ) {
      String arg = it.next();
      if (it.hasNext()) {
        if ("--id".equals(arg)) {
          it.remove();
          setId(thread++, it.next());
          it.remove();
        } else if ("-j".equals(arg) || "--threads".equals(arg)) {
          it.remove();
          setThreadCount(new Integer(it.next()));
          it.remove();
        }
      }
    }

    if (args.size() > 0) {
      base = Paths.get(args.remove(0));
    }

    if (args.size() > 0) {
      setCount(Long.parseLong(args.remove(0)));
    }

    runThreads();

    if (getResult()) {
      System.out.println("\nPASS");
    } else {
      System.out.println("\nFAIL");
    }
  }

  private static void runThreads()
      throws IllegalArgumentException, InterruptedException, SecurityException {
    List<Thread> running = new LinkedList<>();

    for (OneThread t : threads) {
      Thread thread = new Thread(t);
      thread.start();
      running.add(thread);
    }

    for (Thread thread : running) {
      thread.join();
    }
  }

  private static boolean getResult() {
    for (OneThread t : threads) {
      if (t.result == null || !t.result) {
        return false;
      }
    }
    return true;
  }

  private static void setCount(long count) {
    for (OneThread t : threads) {
      t.test.count = count;
    }
  }

  private static void setSubmitMarker(String marker) {
    for (OneThread t : threads) {
      t.test.submitMarker = marker;
    }
  }

  private static void setId(int n, String id) {
    OneThread t = threads.get(n);
    t.test.id = id;
    t.test.submitMarker += id + ".";
  }

  private static void setThreadCount(int n) {
    while (threads.size() < n) {
      threads.add(new OneThread());
    }
  }
}

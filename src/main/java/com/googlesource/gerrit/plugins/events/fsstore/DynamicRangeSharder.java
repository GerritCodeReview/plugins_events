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

/**
 * A dynamic range Path sharder for 'long' numbers.
 *
 * <p>This Path sharder splits large collections of filesystem entries (files or directories) into
 * directory based blocks using the numeric value of the name of the entries. Unlike a traditional
 * sharder which attempts to distribute entries across many blocks, this sharder attempts to keep
 * numerically close values together in the same blocks, all while still, for perfromance reasons,
 * attempting to limit the size of each block. This layout targets large sequences and attempts to
 * minimize the amount of blocks needed all while being flexible over time as the ranges of the
 * sequences might shift.
 *
 * <p>This sharder will vary the number of "sharding" levels based on the numeric value of entries.
 * The size of each level is configurable based on how many oders of magnitude are desired per
 * level. For example, an order of 3 will result in 1,000 entries per level. Entries with different
 * depths will be stored in different subtrees of the root. Each subtree starts with a prefix
 * indicating the depth of the subtree by the number of "dotted" sections in the prefix, and the
 * order of magnitudes in each level of the subtree by the number in each "dotted" section of the
 * prefix. So a subtree of depth 3 with 2 orders of magnitude per level will start with "2.2.2/" and
 * an entry of 1,234,567 would end up as "2.2.2/01/23/45/67" in that subtree. Whereas with 3 orders
 * per level, the same entry 1,234,567 would end up instead in subtree "3.3", which only has 2
 * levels, as "3.3/001/234/567".
 */
public class DynamicRangeSharder {
  public static final int DEFAULT_ORDERS = 3; // most filesystem can handle 1K
  protected final Path base;
  protected final int orders;
  protected final String order;
  protected final long entriesPerLevel;

  public DynamicRangeSharder(Path base) {
    this(base, DEFAULT_ORDERS);
  }

  public DynamicRangeSharder(Path base, int orders) {
    this.base = base;
    this.orders = orders;
    this.entriesPerLevel = (long) Math.pow(10, orders);
    order = Long.toString(orders);
  }

  /** Get the Path of the block that `i` should be stored in. */
  public Path dir(long i) {
    Path dir = base.resolve(subtreeName(i));
    for (int level = levels(i) - 1; level > 0; level--) {
      long sizeOfLevel = sizeOfLevel(level);
      long sub = i / sizeOfLevel;
      dir = dir.resolve(padToOrder(sub));
      i = i - (sub * sizeOfLevel);
    }
    return dir;
  }

  /** Determine whether a number is the last entry in its dir */
  public boolean isLastDirEntry(long i) {
    return !dir(i).equals(dir(i + 1));
  }

  protected String subtreeName(long i) {
    String subtree = order;
    for (int l = levels(i) - 1; l > 0; l--) {
      subtree += "." + order;
    }
    return subtree;
  }

  /** pad a number to our order. */
  protected String padToOrder(long i) {
    return String.format("%0" + order + "d", i);
  }

  /** how many order levels is number */
  protected int levels(long i) {
    if (i == 0) {
      return 1;
    }
    int l = 0;
    for (; i > 0; l++) {
      i = i / entriesPerLevel;
    }
    return l;
  }

  /**
   * Get the aggregate size of a level given our order
   *
   * <p>i.e. order 3 and level 2 -> 1,000,000 order 2 and level 2 -> 10,000
   */
  protected long sizeOfLevel(int level) {
    long x = 1;
    for (; level > 0; level--) {
      x *= entriesPerLevel;
    }
    return x;
  }
}

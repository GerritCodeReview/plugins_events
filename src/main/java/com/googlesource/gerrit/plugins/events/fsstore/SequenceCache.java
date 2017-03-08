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

/**
 * Cache the last known value of the sequence and use it to speed up certain comparisons by taking
 * advantage of the fact that sequences can only ever increase.
 */
public class SequenceCache {
  protected final UpdatableFileValue<Long> updatable;

  protected long cachedValue = 0;

  /** UpdatableFileValue<Long> must be an increasing sequence */
  public SequenceCache(UpdatableFileValue<Long> updatable) {
    this.updatable = updatable;
  }

  public Long spinGet(long maxTries) throws IOException {
    long value = updatable.spinGet(maxTries);
    synchronized (this) {
      if (value > cachedValue) {
        cachedValue = value;
      } else {
        value = cachedValue;
      }
    }
    return value;
  }

  public boolean isZero(long maxTries) throws IOException {
    return isEqualTo(0L, maxTries);
  }

  public boolean isEqualTo(long num, long maxTries) throws IOException {
    return !cachedGreaterThan(num) && spinGet(maxTries) == num;
  }

  public boolean isGreaterThan(long num, long maxTries) throws IOException {
    return cachedGreaterThan(num) || spinGet(maxTries) > num;
  }

  public boolean isGreaterThanOrEqualTo(long num, long maxTries) throws IOException {
    return cachedGreaterThanOrEqualTo(num) || spinGet(maxTries) >= num;
  }

  public boolean isLessThan(long num, long maxTries) throws IOException {
    return !cachedGreaterThanOrEqualTo(num) && spinGet(maxTries) < num;
  }

  public boolean isLessThanOrEqualTo(long num, long maxTries) throws IOException {
    return !cachedGreaterThan(num) && spinGet(maxTries) <= num;
  }

  protected synchronized boolean cachedGreaterThan(long num) {
    return cachedValue > num;
  }

  protected synchronized boolean cachedGreaterThanOrEqualTo(long num) {
    return cachedValue >= num;
  }
}

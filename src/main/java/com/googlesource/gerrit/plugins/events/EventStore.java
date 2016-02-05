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

import java.io.IOException;
import java.util.UUID;

public interface EventStore {
  UUID getUuid() throws IOException;

  long getHead() throws IOException;

  long getTail() throws IOException;

  void add(String event) throws IOException;

  /** returns null if event does not exist */
  String get(long n) throws IOException;

  void trim(long trim) throws IOException;
}

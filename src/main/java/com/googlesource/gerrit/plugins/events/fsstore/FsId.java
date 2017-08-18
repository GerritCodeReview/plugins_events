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
import java.util.UUID;

/**
 * Store a non changing String ID to a file.
 *
 * <p>This class is multi node/process (Multi-Master) safe. The ID will only ever get written once,
 * the first writer will win.
 */
public class FsId extends FileValue<String> {
  public static final Path VALUE = Paths.get("value");

  public static class BasePaths extends FsTransaction.BasePaths {
    public final Path valueDir;

    public BasePaths(Path base) {
      super(base);
      valueDir = base.resolve(VALUE);
    }
  }

  protected static class Builder extends FsTransaction.Builder {
    public Builder(BasePaths paths, String value) throws IOException {
      super(paths);
      FileValue.prepare(dir.resolve(VALUE), value); // build/<tmp>/value(val)
    }
  }

  protected final BasePaths paths;

  public FsId(Path base) {
    super(base.resolve(VALUE).resolve(VALUE)); // value/value(val)
    this.paths = new BasePaths(base);
  }

  public void initFs() throws IOException {
    initFs(UUID.randomUUID().toString());
  }

  public void initFs(String init) throws IOException {
    super.init(init);
    while (!Files.exists(path)) {
      try (Builder b = new Builder(paths, init)) {
        // mv build/<tmp>/value(val) value/value(val)
        Fs.tryAtomicMove(b.dir, paths.valueDir);
      }
    }
  }
}

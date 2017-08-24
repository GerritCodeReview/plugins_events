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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "trim",
  description = "Trim old events up to and including trim-id from EventStore"
)
final class TrimCommand extends SshCommand {
  @Option(
    name = "--trim-id",
    metaVar = "TRIM_ID",
    usage = "Trim old events up to and including trim-id from EventStore"
  )
  protected long trim = -1;

  @Option(name = "--size", metaVar = "SIZE", usage = "Trim and keep SIZE events")
  protected long size = -1;

  protected final EventStore store;

  @Inject
  TrimCommand(EventStore store) {
    this.store = store;
  }

  public void run() throws Exception {
    if (trim != -1) {
      store.trim(trim);
    }
    if (size != -1) {
      store.trim(store.getHead() - size);
    }
  }
}

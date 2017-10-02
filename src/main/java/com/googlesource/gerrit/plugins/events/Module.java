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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.events.fsstore.FsListener.FsLifecycleListener;
import com.googlesource.gerrit.plugins.events.fsstore.FsStore;

public class Module extends LifecycleModule {
  private static final int DEFAULT_POLLING_INTERVAL = 0;

  @Provides
  @Singleton
  @PollingInterval
  protected Long getCleanupInterval(PluginConfigFactory cfg, @PluginName String pluginName) {
    String fromConfig =
        Strings.nullToEmpty(cfg.getFromGerritConfig(pluginName).getString("pollingInterval"));
    return SECONDS.toMillis(ConfigUtil.getTimeUnit(fromConfig, DEFAULT_POLLING_INTERVAL, SECONDS));
  }

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), StreamEventListener.class);
    bind(EventStore.class).to(FsStore.class);
    DynamicItem.bind(binder(), EventDispatcher.class).to(FileSystemEventBroker.class);
    listener().to(FsLifecycleListener.class);
  }
}

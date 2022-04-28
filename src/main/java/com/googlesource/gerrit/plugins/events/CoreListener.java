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

import com.google.common.base.Supplier;
import com.google.gerrit.entities.EntitiesAdapterFactory;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritServerConfigProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.ProjectNameKeyAdapter;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CoreListener implements EventListener {
  private static Logger log = LoggerFactory.getLogger(CoreListener.class);

  protected static final String KEY_FILTER = "filter";
  protected static final String FILTER_TYPE_DROP = "DROP";
  protected static final String FILTER_ELEMENT_CLASSNAME = "classname";

  protected static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(Supplier.class, new SupplierSerializer())
          .registerTypeAdapter(Project.NameKey.class, new ProjectNameKeyAdapter())
          .registerTypeAdapterFactory(EntitiesAdapterFactory.create())
          .create();

  protected final String pluginName;
  protected final GerritServerConfigProvider gerritServerConfigProvider;
  protected final DynamicSet<StreamEventListener> listeners;
  protected final EventStore store;
  protected Set<String> dropEventNames = new HashSet<>();

  @Inject
  protected CoreListener(
      @PluginName String pluginName,
      GerritServerConfigProvider gerritServerConfigProvider,
      EventStore store,
      DynamicSet<StreamEventListener> listeners) {
    this.pluginName = pluginName;
    this.gerritServerConfigProvider = gerritServerConfigProvider;
    this.store = store;
    this.listeners = listeners;
    readAndParseCfg();
  }

  @Override
  public void onEvent(Event event) {
    if (dropEventNames.contains(event.getClass().getName())) {
      return;
    }
    try {
      store.add(gson.toJson(event));
    } catch (IOException e) {
      log.error("Cannot add event to event store", e);
    }
    for (StreamEventListener l : listeners) {
      l.onStreamEventUpdate();
    }
  }

  protected void readAndParseCfg() {
    PluginConfig cfg =
        PluginConfig.createFromGerritConfig(pluginName, gerritServerConfigProvider.loadConfig());
    for (String filter : cfg.getStringList(KEY_FILTER)) {
      String pieces[] = filter.split(" ");
      if (pieces.length == 3
          && FILTER_TYPE_DROP.equals(pieces[0])
          && FILTER_ELEMENT_CLASSNAME.equals(pieces[1])) {
        dropEventNames.add(pieces[2]);
      } else {
        log.error("Ignoring invalid filter: " + filter);
      }
    }
  }
}

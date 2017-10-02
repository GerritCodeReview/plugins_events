// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.GerritServerConfigProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.UserScopedEventListener;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FileSystemEventBroker extends EventBroker {
  private static final Logger log = LoggerFactory.getLogger(FileSystemEventBroker.class);
  protected final EventStore store;
  protected final Gson gson;
  protected final DynamicSet<StreamEventListener> streamEventListeners;
  protected volatile long lastSent;
  protected static final String KEY_FILTER = "filter";
  protected static final String FILTER_TYPE_DROP = "DROP";
  protected static final String FILTER_ELEMENT_CLASSNAME = "classname";
  protected Set<String> dropEventNames = new HashSet<>();

  @Inject
  public FileSystemEventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      @Nullable @GerritInstanceId String gerritInstanceId,
      EventStore store,
      @EventGson Gson gson,
      DynamicSet<StreamEventListener> streamEventListeners,
      GerritServerConfigProvider gerritServerConfigProvider,
      @PluginName String pluginName)
      throws IOException {
    super(
        listeners,
        unrestrictedListeners,
        permissionBackend,
        projectCache,
        notesFactory,
        gerritInstanceId);
    this.store = store;
    this.gson = gson;
    this.streamEventListeners = streamEventListeners;
    lastSent = store.getHead();
    readAndParseCfg(pluginName, gerritServerConfigProvider);
  }

  @Override
  public void postEvent(Change change, ChangeEvent event) throws PermissionBackendException {
    storeEvent(event);
    sendAllPendingEvents();
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    storeEvent(event);
    try {
      sendAllPendingEvents();
    } catch (PermissionBackendException e) {
      log.error("Permission Exception while dispatching the event. Will be tried again.", e);
    }
  }

  @Override
  protected void fireEvent(BranchNameKey branchName, RefEvent event)
      throws PermissionBackendException {
    storeEvent(event);
    sendAllPendingEvents();
  }

  @Override
  public void postEvent(Event event) throws PermissionBackendException {
    storeEvent(event);
    sendAllPendingEvents();
  }

  protected void storeEvent(Event event) {
    if (dropEventNames.contains(event.getClass().getName())) {
      return;
    }
    try {
      store.add(gson.toJson(event));
    } catch (IOException ex) {
      log.error("Cannot add event to event store", ex);
    }
  }

  public synchronized void sendAllPendingEvents() throws PermissionBackendException {
    try {
      long current = store.getHead();
      while (lastSent < current) {
        long next = lastSent + 1;
        fireEvent(gson.fromJson(store.get(next), Event.class));
        lastSent = next;
      }
    } catch (IOException e) {
      // Next Event would re-try the events.
    }
    for (StreamEventListener l : streamEventListeners) {
      l.onStreamEventUpdate();
    }
  }

  protected void readAndParseCfg(String pluginName, GerritServerConfigProvider configProvider) {
    PluginConfig cfg = PluginConfig.createFromGerritConfig(pluginName, configProvider.loadConfig());
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

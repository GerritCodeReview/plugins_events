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
import com.google.gerrit.extensions.registration.DynamicSet;
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
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemEventBroker extends EventBroker {
  private static final Logger log = LoggerFactory.getLogger(FileSystemEventBroker.class);
  protected static final Object lock = new Object();
  protected final EventStore store;
  protected final Gson gson;
  protected final DynamicSet<StreamEventListener> streamEventListeners;
  protected AtomicLong lastSent;

  @Inject
  public FileSystemEventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      String gerritInstanceId,
      EventStore store,
      @EventGson Gson gson,
      DynamicSet<StreamEventListener> streamEventListeners)
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
    lastSent = new AtomicLong(store.getHead());
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
    try {
      store.add(gson.toJson(event));
    } catch (IOException ex) {
      log.error("Cannot add event to event store", ex);
    }
  }

  protected void sendAllPendingEvents() throws PermissionBackendException {
    try {
      long current = store.getHead();
      if (lastSent.longValue() < current) {
        while (lastSent.longValue() < current) {
          synchronized (lock) {
            if (lastSent.longValue() < current) {
              long next = lastSent.get() + 1;
              Event event = gson.fromJson(store.get(next), Event.class);
              fireEvent(event);
              lastSent.set(next);
            }
          }
        }
        for (StreamEventListener l : streamEventListeners) {
          l.onStreamEventUpdate();
        }
      }
    } catch (IOException e) {
      // Next Event would re-try the events.
    }
  }
}

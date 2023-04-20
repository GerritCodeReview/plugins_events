// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.GerritServerConfigProvider;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AsyncEventBroker extends FileSystemEventBroker implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(AsyncEventBroker.class);

  protected ThreadPoolExecutor executor;

  @Inject
  public AsyncEventBroker(
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
        gerritInstanceId,
        store,
        gson,
        streamEventListeners,
        gerritServerConfigProvider,
        pluginName);
    executor =
        new ThreadPoolExecutor(
            /* corePoolSize = */ 1,
            /* maximumPoolSize = */ 3,
            /* keepAliveTime = */ 1,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(1));
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    if (executor == null) {
      executor.shutdown();
    }
  }

  @Override
  public void postEvent(Change change, ChangeEvent event) throws PermissionBackendException {
    queue(event, () -> super.postEvent(change, event));
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    queue(event, () -> super.postEvent(projectName, event));
  }

  @Override
  public void postEvent(BranchNameKey branchName, RefEvent event)
      throws PermissionBackendException {
    queue(event, () -> super.postEvent(branchName, event));
  }

  @Override
  public void postEvent(Event event) throws PermissionBackendException {
    queue(event, () -> super.postEvent(event));
  }

  protected void queue(Event event, RunnableThrowsException<PermissionBackendException> fire) {
    if (!isDrop(event)) {
      executor.execute(
          () -> {
            try {
              fire.run();
            } catch (PermissionBackendException e) {
            }
          });
    }
  }

  @FunctionalInterface
  protected interface RunnableThrowsException<E extends Exception> {
    void run() throws E;
  }
}

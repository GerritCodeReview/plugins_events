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

import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CoreListener implements ChangeListener {
  private static Logger log = LoggerFactory.getLogger(CoreListener.class);

  protected static final Gson gson = new Gson();
  protected final DynamicSet<StreamEventListener> listeners;
  protected final EventStore store;

  @Inject
  protected CoreListener(EventStore store, DynamicSet<StreamEventListener> listeners) {
    this.store = store;
    this.listeners = listeners;
  }

  @Override
  public void onChangeEvent(ChangeEvent event) {
    try {
      store.add(gson.toJson(event));
    } catch (IOException e) {
      log.error("Cannot add event to event store", e);
    }
    for (StreamEventListener l : listeners) {
      l.onStreamEventUpdate();
    }
  }
}
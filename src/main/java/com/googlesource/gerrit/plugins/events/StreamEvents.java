// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.StreamCommandExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(value = GlobalCapability.STREAM_EVENTS, scope = CapabilityScope.CORE)
@CommandMetaData(name = "stream", description = "Monitor events occurring in real time")
public final class StreamEvents extends BaseCommand {
  private static final Logger log = LoggerFactory.getLogger(StreamEvents.class);

  protected static final int BATCH_SIZE = 32; // yield thread after
  protected static final Gson gson = new Gson();
  protected static final JsonParser parser = new JsonParser();

  @Option(
      name = "--resume-after",
      metaVar = "RESUME_AFTER",
      usage = "event id after which to resume playing events on connection")
  protected void parseId(String arg) throws IOException {
    resume = 0;
    if ("0".equals(arg)) {
      return;
    }

    String[] ids = arg.split(":");
    if (ids.length == 2) {
      if (!ids[0].equals(events.getUuid().toString())) { // store has changed
        return;
      }

      try {
        resume = Long.parseLong(ids[1]);
        return;
      } catch (NumberFormatException e) { // fall through
      }
    }
    throw new IllegalArgumentException("Invalid event Id: " + arg);
  }

  protected long resume = -1;

  @Option(name = "--ids", usage = "add ids to events (useful for resuming after a disconnect)")
  protected boolean includeIds = false;

  @Inject @StreamCommandExecutor protected ScheduledThreadPoolExecutor threadPool;

  @Inject protected EventStore events;

  @Inject protected DynamicSet<StreamEventListener> subscriptionListeners;

  @Inject protected BranchHelper perms;

  @Inject protected IdentifiedUser currentUser;

  @Inject @PluginName String pluginName;

  protected CancelableRunnable flusherRunnable;
  protected RegistrationHandle subscription;

  protected final Object crossThreadlock = new Object();
  protected Future<?> flusherTask;
  protected PrintWriter stdout;

  protected long sent;
  protected volatile boolean shuttingDown = false;

  @Override
  public void start(ChannelSession channel, Environment env) throws IOException {
    try (DynamicOptions pluginOptions = new DynamicOptions(injector, dynamicBeans)) {
      try {
        parseCommandLine(pluginOptions);
      } catch (UnloggedFailure e) {
        String msg = e.getMessage();
        if (!msg.endsWith("\n")) {
          msg += "\n";
        }
        err.write(msg.getBytes("UTF-8"));
        err.flush();
        onExit(1);
        return;
      }
      stdout = toPrintWriter(out);

      initSent();
      flusherRunnable = createFlusherRunnable();
      subscribe();
      startFlush();
    }
  }

  protected CancelableRunnable createFlusherRunnable() {
    return new CancelableRunnable() {
      @Override
      public void run() {
        try {
          flushBatch();
        } catch (IOException e) {
          log.error("Error Flushing Stream Events", e);
        }
      }

      @Override
      public void cancel() {
        onExit(0);
      }
    };
  }

  protected void initSent() throws IOException {
    long head = events.getHead();
    long tail = events.getTail();
    if (resume == -1 || resume > head) {
      sent = head;
    } else {
      sent = resume;
    }
    if (sent < tail) {
      sent = tail - 1;
    }
  }

  protected void startFlush() throws IOException {
    synchronized (crossThreadlock) {
      if (!isFlushing() && !shuttingDown && !isUpToDate()) {
        flusherTask = threadPool.submit(flusherRunnable);
      }
    }
  }

  @Override
  protected void onExit(int rc) {
    unsubscribe();
    synchronized (crossThreadlock) {
      shuttingDown = true;
    }
    super.onExit(rc);
  }

  @Override
  public void destroy(ChannelSession channel) {
    unsubscribe();
    synchronized (crossThreadlock) {
      boolean alreadyShuttingDown = shuttingDown;
      shuttingDown = true;
      if (isFlushing()) {
        flusherTask.cancel(true);
      } else if (!alreadyShuttingDown) {
        onExit(0);
      }
    }
  }

  protected void subscribe() {
    subscription =
        subscriptionListeners.add(
            pluginName,
            new StreamEventListener() {
              @Override
              public void onStreamEventUpdate() {
                try {
                  startFlush();
                } catch (IOException e) {
                  log.error("Error starting to flushing Stream Events", e);
                }
              }
            });
  }

  protected void unsubscribe() {
    if (subscription != null) {
      subscription.remove();
      subscription = null;
    }
  }

  protected void flushBatch() throws IOException {
    String uuid = events.getUuid().toString();
    int processed = 0;
    while (!isUpToDate() && processed < BATCH_SIZE) {
      long sending = sent + 1;
      String event = events.get(sending);
      if (Thread.interrupted() || stdout.checkError()) {
        onExit(0);
        return;
      }
      flush(uuid, sending, event);
      sent = sending;
      processed++;
    }
    synchronized (crossThreadlock) {
      flusherTask = null;
    }
    startFlush();
  }

  protected void flush(String uuid, long number, String json) {
    if (json != null) {
      JsonElement el = parser.parse(json);
      if (perms.isVisibleTo(el, currentUser)) {
        if (includeIds) {
          el.getAsJsonObject().addProperty("id", uuid + ":" + number);
          json = gson.toJson(el);
        }
        flush(json + "\n");
      }
    }
  }

  protected void flush(String msg) {
    synchronized (stdout) {
      stdout.print(msg);
      stdout.flush();
    }
  }

  protected boolean isUpToDate() throws IOException {
    return sent >= events.getHead();
  }

  protected boolean isFlushing() {
    return flusherTask != null;
  }
}

// Copyright (C) 2026 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.googlesource.gerrit.plugins.events.fsstore.Fs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ModuleTest {
  private static final String PLUGIN_NAME = "events";

  private Path sitePath;
  private SitePaths site;
  private Config cfg;
  private Module module;

  @Before
  public void setUp() throws Exception {
    sitePath = Files.createTempDirectory("events-Module");
    site = new SitePaths(sitePath);
    cfg = new Config();
    module = new Module();
  }

  @After
  public void tearDown() throws Exception {
    Fs.tryRecursiveDelete(sitePath);
  }

  @Test
  public void defaultsToSiteDataDirectory() {
    assertEquals(sitePath.resolve("data").resolve("plugin").resolve("events"), storeDirectory());
  }

  @Test
  public void configuredAbsoluteDirectoryIsUsed() throws IOException {
    Path shared = Files.createDirectory(sitePath.resolve("shared"));
    cfg.setString("plugin", PLUGIN_NAME, "directory", shared.toString());

    assertEquals(shared.toRealPath(), storeDirectory());
  }

  @Test
  public void configuredRelativeDirectoryIsResolvedAgainstSite() throws IOException {
    Files.createDirectory(sitePath.resolve("shared"));
    cfg.setString("plugin", PLUGIN_NAME, "directory", "shared");

    assertEquals(sitePath.toRealPath().resolve("shared"), storeDirectory());
  }

  private Path storeDirectory() {
    PluginConfigFactory cfgFactory = mock(PluginConfigFactory.class);
    when(cfgFactory.getFromGerritConfig(PLUGIN_NAME))
        .thenReturn(PluginConfig.createFromGerritConfig(PLUGIN_NAME, cfg));
    return module.getStoreDirectory(cfgFactory, PLUGIN_NAME, site);
  }
}

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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Some Filesystem utilities */
public class Fs {
  /** Try to recursively delete a dir. Do NOT throw IOExceptions. */
  public static void tryRecursiveDelete(Path dir) {
    try {
      Files.walkFileTree(
          dir,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              tryDelete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
              tryDelete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) { // Intent of 'try' function is to ignore these.
    }
  }

  /** Try to delete a path. Do NOT throw IOExceptions. */
  public static void tryDelete(Path path) {
    try {
      Files.delete(path);
    } catch (IOException e) { // Intent of 'try' function is to ignore these.
    }
  }

  /** Try to delete a dir and it parents (like rmdir -p). Do NOT throw IOExceptions. */
  public static void unsafeRecursiveRmdir(File dir) {
    try {
      while (unsafeRmdir(dir)) {
        dir = dir.getParentFile();
      }
    } catch (IOException e) { // Intent of 'try' function is to ignore these.
    }
  }

  /**
   * Try to delete a dir and it parents (like rmdir -p). Do NOT throw IOExceptions. Unsafe since the
   * directory check and removal are not atomic.
   */
  public static boolean unsafeRmdir(File dir) throws IOException {
    if (!dir.isDirectory()) {
      throw new IOException(dir + " not a directory");
    }
    return dir.delete();
  }

  /** Read the contents of a UTF_8 encoded file as a String */
  public static String readFile(Path file) throws IOException {
    StringBuffer buffer = new StringBuffer();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      buffer.append(line);
    }
    return buffer.toString();
  }

  /**
   * A drop in replacement for Files.createDirectories that works even when the tail of `path` is a
   * link.
   */
  public static Path createDirectories(Path path) throws IOException {
    try {
      return Files.createDirectories(path);
    } catch (FileAlreadyExistsException e) {
      return Files.createDirectories(Files.readSymbolicLink(path));
    }
  }
}

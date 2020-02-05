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
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/** Some Filesystem utilities */
public class Fs {
  /** Try to create a link. Do NOT throw IOExceptions. */
  public static boolean tryCreateLink(Path link, Path existing) {
    try {
      Files.createLink(link, existing);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** Try to move a file/dir atomically. Do NOT throw IOExceptions. */
  public static boolean tryAtomicMove(Path src, Path dst) {
    try {
      Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** Try to recursively delete a dir. Do NOT throw IOExceptions. */
  public static boolean tryRecursiveDelete(Path dir) {
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
    return !Files.exists(dir);
  }

  /**
   * Try to recursively delete entries, up to max count, in a dir older than expiry. Do NOT throw
   * IOExceptions or DirectoryIteratorExceptions.
   *
   * @return whether all entries were deleted
   */
  public static boolean tryRecursiveDeleteEntriesOlderThan(Path dir, FileTime expiry, int max) {
    try (DirectoryStream<Path> dirEntries = Files.newDirectoryStream(dir)) {
      for (Path path : dirEntries) {
        if (isOlderThan(path, expiry)) {
          if (max-- < 1) {
            return false;
          }
          tryRecursiveDelete(path);
        }
      }
    } catch (IOException e) { // Intent of 'try' function is to ignore these.
    } catch (DirectoryIteratorException e) {
      // dir was deleted by another actor, thus so were all its entries
    }
    return true;
  }

  /**
   * Are all entries in a directory tree older than expiry?
   *
   * @throws IOException
   */
  public static boolean isAllEntriesOlderThan(Path dir, FileTime expiry) throws IOException {
    if (!isOlderThan(dir, expiry)) {
      return false;
    }
    try (DirectoryStream<Path> dirEntries = Files.newDirectoryStream(dir)) {
      for (Path path : dirEntries) {
        if (!isAllEntriesOlderThan(path, expiry)) {
          return false;
        }
      }
    } catch (NotDirectoryException e) { // can't recurse if not a directory
    } catch (DirectoryIteratorException e) {
      throw e.getCause(); // Throw the causal checked exception
    }
    return true;
  }

  /** Is an entry older than expiry. Do NOT throw IOExceptions. */
  public static boolean isOlderThan(Path path, FileTime expiry) {
    try {
      return expiry.compareTo(Files.getLastModifiedTime(path)) > 0;
    } catch (IOException e) {
      return false;
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
  public static String readUtf8(Path file) throws IOException {
    try {
      return readUtf8Unsafe(file);
    } catch (ArrayIndexOutOfBoundsException e) {
      // A concurrent update can cause this, make sure Exception becomes checked.
      throw new IOException("File modified or deleted during read.", e);
    }
  }

  /** Read the contents of a UTF_8 encoded file as a String */
  protected static String readUtf8Unsafe(Path file) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      builder.append(line);
    }
    return builder.toString();
  }

  /** Write the contents of a String as a UTF_8 encoded file */
  public static void writeUtf8(Path file, String s) throws IOException {
    Files.write(file, s.getBytes(StandardCharsets.UTF_8));
  }

  /** Get the first entry in a directory. */
  public static Path getFirstDirEntry(Path dir) throws IOException {
    try (DirectoryStream<Path> dirEntries = Files.newDirectoryStream(dir)) {
      Iterator<Path> it = dirEntries.iterator();
      return it.hasNext() ? it.next() : null;
    } catch (DirectoryIteratorException e) {
      throw e.getCause(); // Throw the causal checked exception
    }
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

  /** Get the reparented path as if a path were moved to a new location */
  public static Path reparent(Path src, Path dst) {
    return dst.resolve(basename(src));
  }

  /** Get the tail of a path (similar to the unix basename command) */
  public static Path basename(Path p) {
    return p.getName(p.getNameCount() - 1);
  }

  /** Get a FileTime indicating a certain amount of time beforehand (ago). */
  public static FileTime getFileTimeAgo(long ago, TimeUnit unit) {
    long ms = TimeUnit.MILLISECONDS.convert(ago, unit);
    return FileTime.fromMillis(System.currentTimeMillis() - ms);
  }
}

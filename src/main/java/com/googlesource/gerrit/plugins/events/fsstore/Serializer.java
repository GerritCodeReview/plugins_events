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

/** Simple serializers for writing data types to Strings. */
public interface Serializer<G> {
  public static class String implements Serializer<java.lang.String> {
    @Override
    public java.lang.String fromString(java.lang.String s) {
      return s;
    }

    @Override
    public java.lang.String fromGeneric(java.lang.String g) {
      return g;
    }
  }

  public static class Long implements Serializer<java.lang.Long> {
    @Override
    public java.lang.Long fromString(java.lang.String s) {
      return s == null ? null : java.lang.Long.parseLong(s);
    }

    @Override
    public java.lang.String fromGeneric(java.lang.Long g) {
      return g == null ? null : java.lang.Long.toString(g) + "\n";
    }
  }

  /* -----  Interface starts here ----- */

  G fromString(java.lang.String s);

  java.lang.String fromGeneric(G g);
}

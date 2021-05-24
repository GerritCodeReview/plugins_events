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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

public class BranchHelper {
  protected final ProjectCache projectCache;
  protected final PermissionBackend permissionBackend;

  @Inject
  BranchHelper(ProjectCache projectCache, PermissionBackend permissionBackend) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
  }

  public boolean isVisibleTo(JsonElement event, IdentifiedUser user) {
    try {
      return isVisibleTo(getBranch(event), user);
    } catch (PermissionBackendException e) {
      return false;
    }
  }

  public boolean isVisibleTo(Branch.NameKey branchName, IdentifiedUser user)
      throws PermissionBackendException {
    if (branchName == null) {
      return false;
    }
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null) {
      return false;
    }
    return permissionBackend.user(user).ref(branchName).test(RefPermission.READ);
  }

  public static Branch.NameKey getBranch(JsonElement event) {
    Branch.NameKey b = null;
    if (event != null) {
      JsonObject obj = event.getAsJsonObject();
      // Known events of this type:
      //  CommitReceivedEvent, RefReplicationDoneEvent, RefReplicatedEvent
      b = getBranch(obj);
      if (b == null) {
        // Known events of this type:
        //  CommentAddedEvent, ChangeMergedEvent, ChangeAbandonedEvent,
        //  ChangeRestoredEvent, DraftPublishedEvent, MergeFailedEvent,
        //  PatchSetCreatedEvent, ReviewerAddedEvent:
        b = getBranch(obj.get("change"));
        if (b == null) {
          // Known events of this type: RefUpdatedEvent
          b = getBranch(obj.get("refUpdate"));
        }
      }
    }
    return b;
  }

  protected static Branch.NameKey getBranch(JsonObject projectParent) {
    Project.NameKey project = getProject(projectParent);
    if (project != null) {
      // Known events of this type:
      //  CommentAddedEvent, ChangeMergedEvent, ChangeAbandonedEvent,
      //  ChangeRestoredEvent, DraftPublishedEvent, MergeFailedEvent,
      //  PatchSetCreatedEvent, ReviewerAddedEvent:
      JsonElement branch = projectParent.get("branch");
      if (branch == null) {
        // Known events of this type: RefUpdatedEvent, CommitReceivedEvent
        branch = projectParent.get("refName");
      }
      if (branch == null) {
        // Known events of this type:
        //  RefReplicationDoneEvent, RefReplicatedEvent
        branch = projectParent.get("ref");
      }

      if (branch != null) {
        String name = asString(branch);
        if (name != null) {
          return new Branch.NameKey(project, name);
        }
      }
    }
    return null;
  }

  public static Project.NameKey getProject(JsonObject projectParent) {
    if (projectParent != null) {
      JsonElement project = projectParent.get("project");
      if (project != null) {
        String name = asString(project);
        if (name == null) {
          try {
            projectParent = project.getAsJsonObject();
            project = projectParent.get("name");
            name = asString(project);
          } catch (RuntimeException e) {
          }
        }
        if (name != null) {
          return new Project.NameKey(name);
        }
      }
    }
    return null;
  }

  protected static String asString(JsonElement el) {
    if (el != null) {
      try {
        return el.getAsString();
      } catch (RuntimeException e) {
      }
    }
    return null;
  }
}

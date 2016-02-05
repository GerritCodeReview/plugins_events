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
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.JsonElement;
import com.google.inject.Inject;

public class BranchHelper {
  protected final ProjectCache projectCache;

  @Inject
  BranchHelper(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public boolean isVisibleTo(JsonElement event, IdentifiedUser user) {
    return isVisibleTo(getBranch(event), user);
  }

  public boolean isVisibleTo(Branch.NameKey branchName, IdentifiedUser user) {
    if (branchName == null) {
      return false;
    }
    final ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null) {
      return false;
    }
    final ProjectControl pc = pe.controlFor(user);
    return pc.controlForRef(branchName).isVisible();
  }

  public static Branch.NameKey getBranch(JsonElement event) {
    // Known events of this type:
    //  CommentAddedEvent, ChangeMergedEvent, ChangeAbandonedEvent,
    //  ChangeRestoredEvent, DraftPublishedEvent, MergeFailedEvent,
    //  PatchSetCreatedEvent, ReviewerAddedEvent:
    JsonElement projectParent = event.getAsJsonObject().get("change");
    if (projectParent == null) {
      // Known events of this type: RefUpdatedEvent
      projectParent = event.getAsJsonObject().get("refUpdate");
    }
    if (projectParent == null) {
      // Known events of this type:
      //  CommitReceivedEvent, RefReplicationDoneEvent, RefReplicatedEvent
      projectParent = event;
    }

    if (projectParent != null) {
      JsonElement project = projectParent.getAsJsonObject().get("project");
      if (project != null) {
        // Known events of this type:
        //  CommentAddedEvent, ChangeMergedEvent, ChangeAbandonedEvent,
        //  ChangeRestoredEvent, DraftPublishedEvent, MergeFailedEvent,
        //  PatchSetCreatedEvent, ReviewerAddedEvent:
        JsonElement branch = projectParent.getAsJsonObject().get("branch");
        if (branch == null) {
          // Known events of this type: RefUpdatedEvent, CommitReceivedEvent
          branch = projectParent.getAsJsonObject().get("refName");
        }
        if (branch == null) {
          // Known events of this type:
          //  RefReplicationDoneEvent, RefReplicatedEvent
          branch = projectParent.getAsJsonObject().get("ref");
        }

        if (branch != null) {
          return getBranch(project, branch);
        }
      }
    }
    return null;
  }

  protected static Branch.NameKey getBranch(JsonElement project, JsonElement branch) {
    return getBranch(project.getAsString(), branch.getAsString());
  }

  protected static Branch.NameKey getBranch(String project, String branch) {
    return new Branch.NameKey(new Project.NameKey(project), RefNames.fullName(branch));
  }
}

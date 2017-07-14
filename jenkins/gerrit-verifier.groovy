// Copyright (C) 2017 The Bazel Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import build.bazel.ci.GerritUtils
import build.bazel.ci.JenkinsUtils

GerritUtils gerrit = new GerritUtils(
    "https://bazel-review.googlesource.com/",
    "/opt/secrets/gerritcookies",
    "Bazel CI <ci.bazel@gmail.com>")

// Build the jobs associated to a given change.
def buildChange(gerrit, change) {
  def refspec = "+" + change.ref + ":" + change.ref.replaceAll('ref/', 'ref/remotes/origin/')
  def jobs = JenkinsUtils.jobsWithDescription("CR", "Gerrit project: " + change.project + ".")

  if (jobs != null && !jobs.empty) {
    gerrit.addReviewer(change.number)
    for(job in jobs) {
      build job: job, propagate: false, wait: false, parameters: [
        [$class: 'StringParameterValue', name: 'REFSPEC', value: refspec],
        [$class: 'StringParameterValue', name: 'BRANCH', value: change.sha1],
        [$class: 'StringParameterValue', name: 'CHANGE_NUMBER', value: change.number.toString()]]
    }
  }
}

timeout(2) {
  def changes = [:]
  // Get open gerrit changes that were verified but not yet processed
  stage("Get changes") {
    def acceptedChanges = gerrit.getVerifiedChanges()
    if (acceptedChanges) {
      echo "Gerrit has " + acceptedChanges.size() + " change(s) to be verified"
      for (int i = 0; i < acceptedChanges.size(); i++) {
        def change = acceptedChanges[i]
        changes[change.number] = { -> buildChange(gerrit, change) }
      }
    } else {
      echo "No change to be verified"
    }
  }

  // And build accepted changes
  stage("Verify") {
    parallel changes
  }
}
#!/bin/bash

groupName=$1
releaseBranch=$2

if [ -z "$groupName" ] || [ -z "$releaseBranch" ]; then
  echo "Usage: $0 <groupName> <releaseBranch>"
  exit 1
fi

set -e

developBranch="develop-$groupName"
releasePrefix="release/$groupName/"

if [[ "$releaseBranch" != "$releasePrefix"* ]]; then
  echo "Release branch name must start with: $releasePrefix"
  exit 1
fi

releaseVersion="${releaseBranch#$releasePrefix}"
if ! [[ "$releaseVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

run_git() {
  local desc="$1"
  shift

  echo ">>> $desc"
  local output
  if ! output=$("$@" 2>&1); then
    echo "ERROR: $desc failed."
    if [ -n "$output" ]; then
      echo "$output"
    fi
    echo "Please resolve the conflict/problem and rerun Finish Release."
    exit 1
  fi
  if [ -n "$output" ]; then
    echo "$output"
  fi
}

checkout_or_track_branch() {
  local branchName="$1"

  if git show-ref --verify --quiet "refs/heads/$branchName"; then
    run_git "Checkout branch $branchName" git checkout "$branchName"
    return
  fi

  if git show-ref --verify --quiet "refs/remotes/origin/$branchName"; then
    run_git "Checkout branch $branchName from origin" git checkout -b "$branchName" "origin/$branchName"
    return
  fi

  echo "Branch not found in local/remote: $branchName"
  exit 1
}

echo "group name is: $groupName"
echo "release branch is: $releaseBranch"

run_git "Fetch remote branches" git fetch origin --prune

# 1) Merge selected release to master, tag, push.
checkout_or_track_branch "$releaseBranch"
run_git "Pull latest $releaseBranch" git pull origin "$releaseBranch"
checkout_or_track_branch "master"
run_git "Pull latest master" git pull origin master
run_git "Merge $releaseBranch into master" git merge --no-ff "$releaseBranch"

tagName="v$releaseVersion"
if git rev-parse "$tagName" >/dev/null 2>&1; then
  echo "Tag already exists: $tagName"
  exit 1
fi
run_git "Create release tag $tagName" git tag -a "$tagName" -m "Release $releaseVersion"
run_git "Push master and tags" git push origin master --tags

# 2) Delete finished release branch.
run_git "Delete local branch $releaseBranch" git branch -d "$releaseBranch"
run_git "Delete remote branch $releaseBranch" git push origin --delete "$releaseBranch"

# 3) Merge master to develop-{groupName}, push.
checkout_or_track_branch "$developBranch"
run_git "Pull latest $developBranch" git pull origin "$developBranch"
run_git "Merge master into $developBranch" git merge --no-ff master
run_git "Push $developBranch" git push origin "$developBranch"

# 4) Merge master to all ongoing release branches, push.
run_git "Refresh remote branches after release cleanup" git fetch origin --prune
mapfile -t releaseBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' "refs/remotes/origin/$releasePrefix*" | sed 's#^origin/##')

remainingVersions=()
for branch in "${releaseBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingVersions+=("${branch#$releasePrefix}")
done

if [ ${#releaseBranches[@]} -gt 0 ]; then
  echo "Remaining release branches: ${releaseBranches[*]}"
fi
echo "REMAINING_RELEASES:$(IFS=/; echo "${remainingVersions[*]}")"

# 5) Switch back to develop branch after finishing release flow.
checkout_or_track_branch "$developBranch"

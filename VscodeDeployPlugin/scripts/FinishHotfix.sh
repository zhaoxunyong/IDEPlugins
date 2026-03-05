#!/bin/bash

groupName=$1
hotfixBranch=$2
groupsList="${3:-$groupName}"

if [ -z "$groupName" ] || [ -z "$hotfixBranch" ]; then
  echo "Usage: $0 <groupName> <hotfixBranch> [groupsList]"
  exit 1
fi

set -e

developBranch="develop-$groupName"
hotfixPrefix="hotfix/$groupName/"

if [[ "$hotfixBranch" != "$hotfixPrefix"* ]]; then
  echo "Hotfix branch name must start with: $hotfixPrefix"
  exit 1
fi

hotfixVersion="${hotfixBranch#$hotfixPrefix}"
if ! [[ "$hotfixVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Hotfix version must follow SemVer format, e.g. 1.0.0"
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
    echo "Please resolve the conflict/problem and rerun Finish Hotfix."
    exit 1
  fi
  if [ -n "$output" ]; then
    echo "$output"
  fi
}

# 若 remote 存在但本地不存在则 checkout（创建本地跟踪分支），否则仅 checkout。
# 通过全局 NEED_PULL 表示是否需要随后执行 pull：本地已存在时为 1，刚 checkout 自 origin 时为 0。
NEED_PULL=0
checkout_or_track_branch() {
  local branchName="$1"
  NEED_PULL=0

  if git show-ref --verify --quiet "refs/heads/$branchName"; then
    run_git "Checkout branch $branchName" git checkout "$branchName"
    NEED_PULL=1
    return
  fi

  if git show-ref --verify --quiet "refs/remotes/origin/$branchName"; then
    run_git "Checkout branch $branchName from origin" git checkout --track -b "$branchName" "origin/$branchName"
    return
  fi

  echo "Branch not found in local/remote: $branchName"
  exit 1
}

echo "group name is: $groupName"
echo "hotfix branch is: $hotfixBranch"
echo "groups list (develop targets): $groupsList"

run_git "Fetch remote branches" git fetch origin --prune

# 1) Merge selected hotfix to master, tag, push.
checkout_or_track_branch "$hotfixBranch"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $hotfixBranch" git pull origin "$hotfixBranch"
checkout_or_track_branch "master"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest master" git pull origin master
run_git "Merge $hotfixBranch into master" git merge --no-ff "$hotfixBranch"

run_git "Sync tags from origin" git fetch origin --tags --prune-tags
tagName="v$hotfixVersion"
if git ls-remote --tags --refs --exit-code origin "refs/tags/$tagName" >/dev/null 2>&1; then
  echo "Tag already exists on remote: $tagName"
  exit 1
fi
if git show-ref --verify --quiet "refs/tags/$tagName"; then
  run_git "Delete local stale tag $tagName" git tag -d "$tagName"
fi
run_git "Create hotfix tag $tagName" git tag -a "$tagName" -m "Hotfix $hotfixVersion"
run_git "Push master and tags" git push origin master --tags

# 2) Merge master to all develop-{groupName} (from config groups, 列表从 remote 获取存在性).
run_git "Refresh remote branches after hotfix cleanup" git fetch origin --prune
developBranches=()
targetGroups=()
if [ -n "$groupsList" ]; then
  IFS=',' read -ra targetGroups <<< "$groupsList" || true
fi
for g in "${targetGroups[@]}"; do
  g="${g// /}"
  [ -z "$g" ] && continue
  d="develop-$g"
  if git show-ref --verify --quiet "refs/remotes/origin/$d"; then
    developBranches+=("$d")
  fi
done
for branch in "${developBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
done

# 4) Merge master to all ongoing release branches (列表从 remote 获取).
mapfile -t releaseBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/release/' | sed 's#^origin/##')
remainingReleases=()
for branch in "${releaseBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingReleases+=("$branch")
done

if [ ${#releaseBranches[@]} -gt 0 ]; then
  echo "Remaining release branches: ${releaseBranches[*]}"
fi

# 5) Merge master to all ongoing hotfix branches (excluding just-finished branch, 列表从 remote 获取).
mapfile -t hotfixBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/hotfix/' | sed 's#^origin/##')
for branch in "${hotfixBranches[@]}"; do
  [ -z "$branch" ] && continue
  [ "$branch" = "$hotfixBranch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingReleases+=("$branch")
done

# 6) Delete finished hotfix branch (only after all steps succeed; allow re-run if previous steps failed).
if git show-ref --verify --quiet "refs/heads/$hotfixBranch"; then
  run_git "Delete local branch $hotfixBranch" git branch -d "$hotfixBranch"
fi
if git ls-remote --exit-code --heads origin "$hotfixBranch" >/dev/null 2>&1; then
  run_git "Delete remote branch $hotfixBranch" git push origin --delete "$hotfixBranch"
fi

# 7) Switch back to develop branch after finishing hotfix flow.
checkout_or_track_branch "$developBranch"

# 8) Output all remaining release/hotfix branches at the very end.
echo "REMAINING_RELEASES:$(IFS=/; echo "${remainingReleases[*]}")"

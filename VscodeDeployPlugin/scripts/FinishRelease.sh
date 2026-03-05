#!/bin/bash

groupName=$1
releaseBranch=$2
groupsList="${3:-$groupName}"

if [ -z "$groupName" ] || [ -z "$releaseBranch" ]; then
  echo "Usage: $0 <groupName> <releaseBranch> [groupsList]"
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
echo "release branch is: $releaseBranch"
echo "groups list (develop targets): $groupsList"

run_git "Fetch remote branches" git fetch origin --prune

# 1) Merge selected release to master, tag, push.
checkout_or_track_branch "$releaseBranch"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $releaseBranch" git pull origin "$releaseBranch"
checkout_or_track_branch "master"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest master" git pull origin master
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

# 3) Merge master to all develop-{groupName} (from config groups, 列表从 remote 获取存在性).
#git fetch origin --prune:在从远程仓库获取更新的同时，清理掉本地那些在远程仓库中已经被删除的远程追踪分支引用
run_git "Refresh remote branches after release cleanup" git fetch origin --prune
developBranches=()
# 使用 || true 防止 read 在 set -e 下因返回值非零导致脚本退出（某些环境下 here-string/stdin 会导致 read 返回 1）
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
remainingVersions=()
for branch in "${releaseBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingVersions+=("$branch")
done

if [ ${#releaseBranches[@]} -gt 0 ]; then
  echo "Remaining release branches: ${releaseBranches[*]}"
fi
echo "REMAINING_RELEASES:$(IFS=/; echo "${remainingVersions[*]}")"

# 5) Merge master to all ongoing hotfix branches (列表从 remote 获取).
mapfile -t hotfixBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/hotfix/' | sed 's#^origin/##')
for branch in "${hotfixBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
done

# 6) Switch back to develop branch after finishing release flow.
checkout_or_track_branch "$developBranch"

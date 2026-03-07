#!/bin/bash

groupName=$1
releaseBranch=$2
groupsList="${3:-$groupName}"

if [ -z "$groupName" ] || [ -z "$releaseBranch" ]; then
  echo "Usage: $0 <groupName> <releaseBranch> [groupsList]"
  exit 1
fi

set -e

CURRENT_STEP=0
declare -a STEP_DESC
declare -a STEP_STATUS

STEP_DESC[1]="准备阶段：拉取远程分支信息"
STEP_DESC[2]="将目标 release 分支合并到 master，并推送"
STEP_DESC[3]="将最新 master 合并回所有 develop 分支"
STEP_DESC[4]="将最新 master 合并回所有未完成的 release 分支"
STEP_DESC[5]="将最新 master 合并回所有未完成的 hotfix 分支"
STEP_DESC[6]="在 master 上打 tag 并推送"
STEP_DESC[7]="切回当前组的 develop 分支，先打 purge 临时 tag，再删除已完成的 release 分支（本地和远程）"

for i in 1 2 3 4 5 6 7; do
  STEP_STATUS[$i]="NOT_RUN"
done

set_step() {
  CURRENT_STEP=$1
  STEP_STATUS[$1]="RUNNING"
}

print_summary() {
  echo
  echo "FinishRelease 执行结果："
  for i in 1 2 3 4 5 6 7; do
    status="${STEP_STATUS[$i]}"
    [ -z "$status" ] && status="NOT_RUN"
    printf "%d. %-60s %s\n" "$i" "${STEP_DESC[$i]}" "$status"
  done
}

# 任意命令出错时，把当前步骤从 RUNNING 标记为 FAILED，EXIT trap 负责统一打印。
trap 'if [ "$CURRENT_STEP" -ne 0 ] && [ "${STEP_STATUS[$CURRENT_STEP]}" = "RUNNING" ]; then STEP_STATUS[$CURRENT_STEP]="FAILED"; fi' ERR
trap print_summary EXIT

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
    if [ "$CURRENT_STEP" -ne 0 ]; then
      STEP_STATUS[$CURRENT_STEP]="FAILED"
    fi
    exit 1
  fi
  if [ -n "$output" ]; then
    echo "$output"
  fi
}

create_and_push_purge_tag_for_branch() {
  local branchName="$1"
  local tagRef=""
  local safeBranch="${branchName//\//-}"
  local baseTag="purge-${safeBranch}-$(date +%Y%m%d%H%M)"
  local purgeTag="$baseTag"
  local idx=1

  if git show-ref --verify --quiet "refs/heads/$branchName"; then
    tagRef="$branchName"
  elif git show-ref --verify --quiet "refs/remotes/origin/$branchName"; then
    tagRef="origin/$branchName"
  else
    echo "Cannot create purge tag. Branch not found in local/remote: $branchName"
    exit 1
  fi

  while git show-ref --verify --quiet "refs/tags/$purgeTag" || git ls-remote --tags --refs --exit-code origin "refs/tags/$purgeTag" >/dev/null 2>&1; do
    purgeTag="${baseTag}-${idx}"
    idx=$((idx + 1))
  done

  run_git "Create purge tag $purgeTag for $branchName" git tag -a "$purgeTag" "$tagRef" -m "Purge backup for $branchName before deletion"
  run_git "Push purge tag $purgeTag" git push origin "refs/tags/$purgeTag"
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

echo
echo "即将执行 FinishRelease 流程："
for i in 1 2 3 4 5 6 7; do
  printf "%d. %s\n" "$i" "${STEP_DESC[$i]}"
done
echo

set_step 1
run_git "Fetch remote branches" git fetch origin --prune
STEP_STATUS[1]="DONE"

# 1) Merge selected release to master, then push.
set_step 2
checkout_or_track_branch "$releaseBranch"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $releaseBranch" git pull origin "$releaseBranch"
checkout_or_track_branch "master"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest master" git pull origin master
run_git "Merge $releaseBranch into master" git merge --no-ff "$releaseBranch"
run_git "Push master" git push origin master
STEP_STATUS[2]="DONE"

# 2) Merge master to all develop-{groupName} (from config groups, 列表从 remote 获取存在性).
#git fetch origin --prune:在从远程仓库获取更新的同时，清理掉本地那些在远程仓库中已经被删除的远程追踪分支引用
set_step 3
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
if [ ${#developBranches[@]} -gt 0 ]; then
  STEP_STATUS[3]="DONE"
else
  STEP_STATUS[3]="SKIPPED"
fi

# 4) Merge master to all ongoing release branches (列表从 remote 获取).
set_step 4
mapfile -t releaseBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/release/' | sed 's#^origin/##')
remainingVersions=()
for branch in "${releaseBranches[@]}"; do
  [ -z "$branch" ] && continue
  # 当前正在 Finish 的 release 分支会在后面被删除，这里不再视为“进行中”的 release 分支
  if [ "$branch" = "$releaseBranch" ]; then
    continue
  fi
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingVersions+=("$branch")
done

if [ ${#releaseBranches[@]} -gt 0 ]; then
  echo "Remaining release branches: ${releaseBranches[*]}"
fi
if [ ${#releaseBranches[@]} -gt 0 ]; then
  STEP_STATUS[4]="DONE"
else
  STEP_STATUS[4]="SKIPPED"
fi

# 5) Merge master to all ongoing hotfix branches (列表从 remote 获取).
set_step 5
mapfile -t hotfixBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/hotfix/' | sed 's#^origin/##')
for branch in "${hotfixBranches[@]}"; do
  [ -z "$branch" ] && continue
  checkout_or_track_branch "$branch"
  [ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $branch" git pull origin "$branch"
  run_git "Merge master into $branch" git merge --no-ff master
  run_git "Push $branch" git push origin "$branch"
  remainingVersions+=("$branch")
done
if [ ${#hotfixBranches[@]} -gt 0 ]; then
  STEP_STATUS[5]="DONE"
else
  STEP_STATUS[5]="SKIPPED"
fi

# 6) Tag on master and push tags.
set_step 6
run_git "Sync tags from origin" git fetch origin --tags --prune-tags
tagName="v$releaseVersion"
if git ls-remote --tags --refs --exit-code origin "refs/tags/$tagName" >/dev/null 2>&1; then
  echo "Tag already exists on remote: $tagName"
  exit 1
fi
if git show-ref --verify --quiet "refs/tags/$tagName"; then
  run_git "Delete local stale tag $tagName" git tag -d "$tagName"
fi
run_git "Create release tag $tagName" git tag -a "$tagName" -m "Release $releaseVersion"
run_git "Push tags" git push origin --tags
STEP_STATUS[6]="DONE"

# 7) Switch back to develop branch and delete finished release branch.
set_step 7
checkout_or_track_branch "$developBranch"
create_and_push_purge_tag_for_branch "$releaseBranch"

if git show-ref --verify --quiet "refs/heads/$releaseBranch"; then
  run_git "Delete local branch $releaseBranch" git branch -d "$releaseBranch"
fi
if git ls-remote --exit-code --heads origin "$releaseBranch" >/dev/null 2>&1; then
  run_git "Delete remote branch $releaseBranch" git push origin --delete "$releaseBranch"
fi
STEP_STATUS[7]="DONE"

echo "REMAINING_RELEASES: ${remainingVersions[*]}"

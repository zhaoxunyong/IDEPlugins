#!/bin/bash

# 用法: FinishRelease.sh <branch>
# 根据分支名前缀自动区分 release / hotfix：release/ 或 hotfix/ 开头。
# 示例: FinishRelease.sh release/groupA/1.0.0
#       FinishRelease.sh hotfix/groupA/1.0.1

targetBranch="${1:-}"

if [ -z "$targetBranch" ]; then
  echo "Usage: $0 <branch>"
  echo "  Branch must start with release/ or hotfix/, e.g.:"
  echo "  $0 release/<groupName>/<version>"
  echo "  $0 hotfix/<groupName>/<version>"
  echo "Example: $0 release/groupA/1.0.0"
  echo "Example: $0 hotfix/groupA/1.0.1"
  exit 1
fi

git config pull.rebase false

set -e

if [[ "$targetBranch" == release/* ]]; then
  MODE="release"
  if [[ "$targetBranch" != release/*/* ]]; then
    echo "Release branch must match pattern: release/<groupName>/<version>"
    exit 1
  fi
  groupName="${targetBranch#release/}"
  groupName="${groupName%%/*}"
  prefix="release/$groupName/"
  version="${targetBranch#$prefix}"
elif [[ "$targetBranch" == hotfix/* ]]; then
  MODE="hotfix"
  if [[ "$targetBranch" != hotfix/*/* ]]; then
    echo "Hotfix branch must match pattern: hotfix/<groupName>/<version>"
    exit 1
  fi
  groupName="${targetBranch#hotfix/}"
  groupName="${groupName%%/*}"
  prefix="hotfix/$groupName/"
  version="${targetBranch#$prefix}"
else
  echo "Branch must start with 'release/' or 'hotfix/'."
  exit 1
fi

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

CURRENT_STEP=0
declare -a STEP_DESC
declare -a STEP_STATUS

STEP_DESC[1]="准备阶段：拉取远程分支信息"
STEP_DESC[2]="将目标 $MODE 分支合并到 master，并推送"
STEP_DESC[3]="将最新 master 合并回所有 develop 分支"
STEP_DESC[4]="将最新 master 合并回所有未完成的 release 分支"
STEP_DESC[5]="将最新 master 合并回所有未完成的 hotfix 分支"
STEP_DESC[6]="在 master 上打 tag 并推送"
STEP_DESC[7]="切换到 master 分支，删除已完成的 $MODE 分支（本地和远程）"

for i in 1 2 3 4 5 6 7; do
  STEP_STATUS[$i]="NOT_RUN"
done

set_step() {
  CURRENT_STEP=$1
  STEP_STATUS[$1]="RUNNING"
}

print_summary() {
  echo
  echo "Finish${MODE^} 执行结果："
  for i in 1 2 3 4 5 6 7; do
    status="${STEP_STATUS[$i]}"
    [ -z "$status" ] && status="NOT_RUN"
    printf "%d. %-60s %s\n" "$i" "${STEP_DESC[$i]}" "$status"
  done
}

# 任意命令出错时，把当前步骤从 RUNNING 标记为 FAILED，EXIT trap 负责统一打印。
trap 'if [ "$CURRENT_STEP" -ne 0 ] && [ "${STEP_STATUS[$CURRENT_STEP]}" = "RUNNING" ]; then STEP_STATUS[$CURRENT_STEP]="FAILED"; fi' ERR
trap print_summary EXIT

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
    echo "Please resolve the conflict/problem and rerun Finish ${MODE^}."
    if [ "$CURRENT_STEP" -ne 0 ]; then
      STEP_STATUS[$CURRENT_STEP]="FAILED"
    fi
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

echo "target branch: $targetBranch (detected mode: $MODE)"
echo "develop branches (from remote develop-*): will be collected in step 1"

echo
echo "即将执行 Finish${MODE^} 流程："
for i in 1 2 3 4 5 6 7; do
  printf "%d. %s\n" "$i" "${STEP_DESC[$i]}"
done
echo

set_step 1
run_git "Fetch remote branches" git fetch origin --prune
STEP_STATUS[1]="DONE"

# 1) Merge selected branch to master, then push.
set_step 2
checkout_or_track_branch "$targetBranch"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest $targetBranch" git pull origin "$targetBranch"
checkout_or_track_branch "master"
[ "$NEED_PULL" -eq 1 ] && run_git "Pull latest master" git pull origin master
run_git "Merge $targetBranch into master" git merge --no-ff "$targetBranch"
run_git "Push master" git push origin master
STEP_STATUS[2]="DONE"

# 2) Merge master to all develop-* branches (列表从 remote 获取).
set_step 3
run_git "Refresh remote branches" git fetch origin --prune
developBranches=()
while IFS= read -r b; do
  [ -n "$b" ] && developBranches+=("$b")
done < <(git for-each-ref --format='%(refname:short)' 'refs/remotes/origin' | sed 's#^origin/##' | grep '^develop-' || true)
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

# 3) Merge master to all ongoing release branches (列表从 remote 获取).
#    release 模式下跳过当前正在 finish 的 release 分支。
set_step 4
mapfile -t releaseBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/release/' | sed 's#^origin/##')
remainingVersions=()
for branch in "${releaseBranches[@]}"; do
  [ -z "$branch" ] && continue
  [ "$MODE" = "release" ] && [ "$branch" = "$targetBranch" ] && continue
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

# 4) Merge master to all ongoing hotfix branches (列表从 remote 获取).
#    hotfix 模式下跳过当前正在 finish 的 hotfix 分支。
set_step 5
mapfile -t hotfixBranches < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/remotes/origin/hotfix/' | sed 's#^origin/##')
for branch in "${hotfixBranches[@]}"; do
  [ -z "$branch" ] && continue
  [ "$MODE" = "hotfix" ] && [ "$branch" = "$targetBranch" ] && continue
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

# 5) Tag on master and push tags.
set_step 6
run_git "Sync tags from origin" git fetch origin --tags --prune-tags
tagName="v$version"
if git ls-remote --tags --refs --exit-code origin "refs/tags/$tagName" >/dev/null 2>&1; then
  echo "Tag already exists on remote: $tagName"
  exit 1
fi
if git show-ref --verify --quiet "refs/tags/$tagName"; then
  run_git "Delete local stale tag $tagName" git tag -d "$tagName"
fi
run_git "Create ${MODE} tag $tagName" git tag -a "$tagName" -m "${MODE^} $version"
run_git "Push tags" git push origin --tags
STEP_STATUS[6]="DONE"

# 6) Switch to master and delete finished branch.
set_step 7
checkout_or_track_branch "master"

if git show-ref --verify --quiet "refs/heads/$targetBranch"; then
  run_git "Delete local branch $targetBranch" git branch -d "$targetBranch"
fi
if git ls-remote --exit-code --heads origin "$targetBranch" >/dev/null 2>&1; then
  run_git "Delete remote branch $targetBranch" git push origin --delete "$targetBranch"
fi
STEP_STATUS[7]="DONE"

echo "REMAINING_RELEASES: ${remainingVersions[*]}"

#!/bin/bash

# 计算 Start New Hotfix 所需的分支名和基准生产 tag。
# 输出协议（stdout）：hotfixName/baseTag/latestReleaseVersion/latestHotfixVersion；错误写入 stderr。

groupName=$1
if [ -z "$groupName" ]; then
  echo "Usage: $0 <groupName>" >&2
  exit 1
fi

set -e

git fetch origin --prune >&2
git fetch origin --tags --prune >&2

semver_parts() {
  local version=$1
  if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    printf '%s %s %s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}"
  fi
}

max_version=""
latestReleaseVersion=""
latestHotfixVersion=""
latestReleaseBranch=""
latestHotfixBranch=""
is_greater_version() {
  local left=$1 right=$2
  local leftMajor leftMinor leftPatch rightMajor rightMinor rightPatch
  read -r leftMajor leftMinor leftPatch <<< "$(semver_parts "$left")"
  read -r rightMajor rightMinor rightPatch <<< "$(semver_parts "$right")"
  ((10#$leftMajor > 10#$rightMajor)) ||
    { ((10#$leftMajor == 10#$rightMajor && 10#$leftMinor > 10#$rightMinor)) ||
      ((10#$leftMajor == 10#$rightMajor && 10#$leftMinor == 10#$rightMinor && 10#$leftPatch > 10#$rightPatch)); }
}

consider_version() {
  local version=$1
  if semver_parts "$version" | grep -q . && { [ -z "$max_version" ] || is_greater_version "$version" "$max_version"; }; then
    max_version=$version
  fi
}

declare -A remote_tags
while IFS= read -r tagName; do
  remote_tags[$tagName]=1
done < <(git ls-remote --tags --refs origin 'refs/tags/release/*' 'refs/tags/hotfix/*' 'refs/tags/v*' | sed 's#.*refs/tags/##')
latestTag=""
while IFS= read -r tagName; do
  [ -z "$tagName" ] && continue
  [ -n "${remote_tags[$tagName]:-}" ] || continue
  if [ -z "$latestTag" ] && [[ "$tagName" =~ ^(release|hotfix)/[^/]+/([0-9]+\.[0-9]+\.[0-9]+)-[0-9]{12}$ ]]; then
    latestTag=$tagName
  fi
  if [[ "$tagName" =~ ([0-9]+\.[0-9]+\.[0-9]+) ]]; then
    consider_version "${BASH_REMATCH[1]}"
  fi
done < <(git for-each-ref --sort=-creatordate --format='%(refname:short)' 'refs/tags/release/**' 'refs/tags/hotfix/**' 'refs/tags/v*')

if [ -z "$latestTag" ]; then
  echo "未找到以 -YYYYMMDDHHmm 结尾的远程 release/hotfix tag。" >&2
  exit 1
fi
while IFS= read -r branchName; do
  branchName=${branchName#origin/}
  if [[ "$branchName" =~ ^(release|hotfix)/[^/]+/([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    kind=${BASH_REMATCH[1]}
    version=${BASH_REMATCH[2]}
    consider_version "$version"
    if [[ "$kind" == "release" ]] && { [ -z "$latestReleaseVersion" ] || is_greater_version "$version" "$latestReleaseVersion"; }; then
      latestReleaseVersion=$version
      latestReleaseBranch=$branchName
    fi
    if [[ "$kind" == "hotfix" ]] && { [ -z "$latestHotfixVersion" ] || is_greater_version "$version" "$latestHotfixVersion"; }; then
      latestHotfixVersion=$version
      latestHotfixBranch=$branchName
    fi
  fi
done < <(git for-each-ref --format='%(refname:short)' refs/remotes/origin/release/*/* refs/remotes/origin/hotfix/*/*)

read -r major minor patch <<< "$(semver_parts "$max_version")"
candidate="$((10#$major)).$((10#$minor)).$((10#$patch + 1))"
hotfixPrefix="hotfix/$groupName/"

version_exists_in_group() {
  local version=$1
  while IFS= read -r branchName; do
    branchName=${branchName#origin/}
    case "$branchName" in
      "hotfix/$groupName/$version"|"release/$groupName/$version") return 0 ;;
    esac
  done < <(git for-each-ref --format='%(refname:short)' \
    "refs/heads/${hotfixPrefix}*" "refs/remotes/origin/${hotfixPrefix}*" \
    "refs/heads/release/$groupName/*" "refs/remotes/origin/release/$groupName/*")
  return 1
}

while version_exists_in_group "$candidate"; do
  read -r major minor patch <<< "$(semver_parts "$candidate")"
  candidate="$((10#$major)).$((10#$minor)).$((10#$patch + 1))"
done

printf 'hotfixName=%s%s\nbaseTag=%s\nlatestReleaseVersion=%s\nlatestHotfixVersion=%s\nlatestReleaseBranch=%s\nlatestHotfixBranch=%s\n' \
  "$hotfixPrefix" "$candidate" "$latestTag" "$latestReleaseVersion" "$latestHotfixVersion" "$latestReleaseBranch" "$latestHotfixBranch"

#!/bin/bash
set -euo pipefail

SCRIPT_PATH=$(cd "$(dirname "$0")/.." && pwd)/scripts/AiCodeReview.sh
eval "$(sed -n '/^resolve_commit_ai_review_range()/,/^}/p; /^build_local_ai_review_context()/,/^}/p' "$SCRIPT_PATH")"

TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
REPO_DIR="$TEST_DIR/repo"
mkdir -p "$REPO_DIR"
cd "$REPO_DIR"
git init -q
git config user.email test@example.com
git config user.name test

printf 'base\n' > base.txt
git add base.txt
git commit -qm base
BASE_COMMIT=$(git rev-parse HEAD)

git checkout -qb target
printf 'target\n' > target-only.txt
git add target-only.txt
git commit -qm target
TARGET_COMMIT=$(git rev-parse HEAD)

git checkout -qb feature "$BASE_COMMIT"
printf 'selected\n' > selected.txt
git add selected.txt
git commit -qm selected
HEAD_COMMIT=$(git rev-parse HEAD)
GIT_TOP="$REPO_DIR"

assert_selected_diff_only() {
  local case_name="$1"
  local work_dir="$TEST_DIR/$case_name"
  local context_file="$work_dir/context.txt"
  mkdir -p "$work_dir"

  build_local_ai_review_context "$work_dir" "$context_file"

  grep -q "BASE_SHA: ${BASE_COMMIT}" "$context_file"
  grep -q "HEAD_SHA: ${HEAD_COMMIT}" "$context_file"
  grep -q 'selected.txt' "$context_file"
  ! grep -q 'base.txt' "$context_file"
  ! grep -q 'target-only.txt' "$context_file"
  grep -q '1 file changed' "$context_file"
}

commitRange="$TARGET_COMMIT $HEAD_COMMIT"
revision_args=("$TARGET_COMMIT" "$HEAD_COMMIT")
assert_selected_diff_only two-commits

commitRange="$HEAD_COMMIT"
revision_args=("$HEAD_COMMIT")
assert_selected_diff_only single-commit

echo 'AI Code Review commit diff tests passed'

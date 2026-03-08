#!/bin/bash

groupName=$1
featureName=$2

if [ -z "$groupName" ] || [ -z "$featureName" ]; then
  echo "Usage: $0 <groupName> <featureName>"
  exit 1
fi

git config pull.rebase false

developBranch="develop-$groupName"
featurePrefix="feature/$groupName/"

if [[ "$featureName" != "$featurePrefix"* ]]; then
  echo "当前分支 '$featureName' 不是 group '$groupName' 的 feature 分支。"
  echo "期望前缀: $featurePrefix"
  exit 1
fi

echo "正在 fetch origin..."
git fetch origin

echo "正在将当前分支 rebase 到 origin/$developBranch..."
if ! git rebase "origin/$developBranch"; then
  echo ""
  echo ">>> Rebase 过程中发生冲突，请解决冲突后执行："
  echo "    git add ."
  echo "    git rebase --continue"
  echo "    若需放弃本次 rebase： git rebase --abort"
  exit 1
fi

echo ""
echo ">>> Rebase 已完成。请根据需要执行 commit / push。"

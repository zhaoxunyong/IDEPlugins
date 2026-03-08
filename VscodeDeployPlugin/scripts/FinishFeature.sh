#!/bin/bash

groupName=$1
featureName=$2

if [ -z "$groupName" ] || [ -z "$featureName" ]; then
  echo "Usage: $0 <groupName> <featureName>"
  exit 1
fi

git config pull.rebase false

set -e

developBranch="develop-$groupName"
featurePrefix="feature/$groupName/"

if [[ "$featureName" != "$featurePrefix"* ]]; then
  echo "Feature branch '$featureName' is not a feature branch of group '$groupName'."
  echo "Expected prefix: $featurePrefix"
  exit 1
fi

if ! git show-ref --verify --quiet "refs/heads/$featureName"; then
  echo "Local feature branch '$featureName' does not exist."
  exit 1
fi

echo "Checkout branch: $developBranch"
git checkout "$developBranch"
git pull origin "$developBranch"
git branch -d "$featureName"
echo "Deleted local feature branch: $featureName"
git checkout "$developBranch"
echo "Current branch switched to: $developBranch"

#!/bin/bash

groupName=$1
releaseName=$2

if [ -z "$groupName" ] || [ -z "$releaseName" ]; then
  echo "Usage: $0 <groupName> <releaseName>"
  exit 1
fi

set -e

developBranch="develop-$groupName"
releasePrefix="release/$groupName/"
releaseVersion="${releaseName#$releasePrefix}"

if [[ "$releaseName" != "$releasePrefix"* ]]; then
  echo "Release branch name must start with: $releasePrefix"
  exit 1
fi

if ! [[ "$releaseVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

echo "group name is: $groupName"
echo "release name is: $releaseName"
echo "checkout branch: $developBranch"

git checkout "$developBranch"
git pull origin "$developBranch"
git checkout -b "$releaseName"

echo "release branch created: $releaseName"

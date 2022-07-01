#!/bin/bash

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"

sedi() {
  case $(uname) in
    Darwin*) sedi=('-i' '') ;;
    *) sedi='-i' ;;
  esac

  sed "${sedi[@]}" "$@"
}

# script replace, don't delete.
#cd #{project}

branchVersion=$1
newDate=$2
desc=$3

if [[ "$desc" == "" ]]; then
  echo "Please add a message for git!"
  exit -1
fi

desc=${desc//\"/}

if [[ "$branchVersion" == "" || "$newDate" == "" ]]; then
  # echo "branchVersion must be not empty!"
  echo "Usage: $0 BranchVersion newTagDate"
  echo "$0 1.0.0.release 201802271230"
  echo "$0 1.0.0.htofix 201802271230"
  exit -1
fi

#检查是否已经保存过git的账户与密码
git ls-remote > /dev/null
if [[ $? != 0 ]]; then
	echo "=================Authentication error================="
	echo "Authentication error. Please execute the following command through git bash, and enter the account and password:"
	echo "git config --global credential.helper store"
	echo "git ls-remote"
	echo "=================Authentication error================="
	exit -1
fi

git status|grep "git add" &> /dev/null
if [[ $? == 0 ]]; then
  echo "Your local repo seems changed, but not commit yet, please stage or stash changes first!"
  exit -1
fi

currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
git remote show origin|grep " $currentBranchVersion " | egrep "本地已过时|local out of date" &> /dev/null
if [[ $? == 0 ]]; then
  echo "Your local repo seems out of date, please \"git pull\" first!"
  exit -1
fi

#git remote show origin|grep " $currentBranchVersion " | egrep "可快进|up to date" &> /dev/null
#if [[ $? == 0 ]]; then
#  echo "Your local repo seems to be up to date, please git push first!"
#  exit -1
#fi

newTag=${branchVersion}-${newDate}

function SwitchBranch() {
    branchVersions=$1
    # git add .
    # git commit -m "Commit by new branch:${NEW_BRANCH}."
    git checkout -b ${branchVersions} > /dev/null
    if [[ $? != 0 ]]; then
        git checkout ${branchVersions} > /dev/null
        if [[ $? != 0 ]]; then
            echo "Switch branch to ${branchVersions} error."
            exit -1
        fi
    fi
    echo "Switch branch to ${branchVersions} successful."
    # git branch
}

function Tag() {
    newTag=$1
    if [[ "$desc" == "" ]]; then
      desc="For prod version ${newTag}"
    fi
    git tag -a $newTag -m "${desc}"
    if [[ $? != 0 ]]; then
      echo "Tag error!"
      exit -1
    else
      echo "Tag to ${newTag} successful!"
      git push origin ${newTag}
    fi
}

currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
echo "branchVersion--------${branchVersion}"
echo "newTag--------${newTag}"
echo "currentBranchVersion--------${currentBranchVersion}"
SwitchBranch $branchVersion

Tag $newTag
git checkout $currentBranchVersion

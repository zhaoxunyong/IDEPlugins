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

currentProject=`pwd`
echo "currentProject--------${currentProject}"
currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
echo "currentBranchVersion--------${currentBranchVersion}"

gitUrl=$(cat .git/config |grep url|awk -F'=' '{print $2}'|sed 's;^ *;;g')
projectName=$(basename $gitUrl|sed 's;.git;;g')
echo "gitUrl=$gitUrl"
tempFolder=$(dirname `mktemp`)
echo "tempFolder=$tempFolder"
if [[ -d "$tempFolder/$projectName" ]]; then
	echo "exist......"
	git pull
else
    cd $tempFolder
	git clone $gitUrl
fi
cd $tempFolder/$projectName
git checkout -b ${currentBranchVersion} &> /dev/null
echo "current......`pwd`"

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

branchVersion=$1
newDate=$2
releaseType=$3

if [[ "$branchVersion" == "" || "$newDate" == "" ]]; then
  # echo "branchVersion must be not empty!"
  echo "Usage: $0 BranchVersion newTagDate"
  echo "$0 1.0.0.release 201802271230"
  echo "$0 1.0.0.htofix 201802271230"
  exit -1
fi

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

function Push() {
    branchVersions=$1
    git add .
    git commit -m "Mod New branch version to ${branchVersions}"
    git push origin ${branchVersions}
    if [[ $? != 0 ]]; then
        echo "Push ${branchVersions} error."
        exit -1
    fi
    echo "Push ${branchVersions} successful."
}

function Tag() {
    newTag=$1
    git tag -a $newTag -m "For prod version ${newTag}"
    if [[ $? != 0 ]]; then
      echo "Tag error!"
      exit -1
    else
      echo "Tag to ${newTag} successful!"
      git push origin ${newTag}
    fi
}

function deleteUnusedReleaseBranch() {
    type=$1
    reserveVersionNumber=$2
    if [[ "${type}" == "" ]]; then
        type="release"
    fi
    if [[ "${reserveVersionNumber}" == "" ]]; then
        reserveVersionNumber=20
    fi
    deleteBranchs=`git branch -a --sort=-committerdate|grep ${type}|grep remotes|sed 's;remotes/origin/;;'|sort -t '.' -r -k 2 -V|sed "1,${reserveVersionNumber}d"`
    for deleteBranch in $deleteBranchs   
    do
        # Keep only the last releases
        git branch -d $deleteBranch &> /dev/null
        git push origin --delete $deleteBranch &> /dev/null
    done
    echo "Keep only the last ${reserveVersionNumber} ${type} versions!"
}

function changeReleaseVersion() {
  #change version
  mvnVersion=$(echo $branchVersion|sed 's;\.test;;'|sed 's;\.release;;'|sed 's;\.hotfix;;')
  mvn versions:set -DnewVersion=${mvnVersion}
  mvn versions:commit
}

function changeNextVersion() {
  #change version
  mvnVersion=$(echo $branchVersion|sed 's;\.test;;'|sed 's;\.release;;'|sed 's;\.hotfix;;')
  arr=(${mvnVersion//./ })
  nextVersion=${arr[0]}.${arr[1]}.$((${arr[2]}+1))-SNAPSHOT
  mvn versions:set -DnewVersion=${nextVersion}
  mvn versions:commit
}

SwitchBranch $branchVersion

if [[ $releaseType == "tag" ]]; then
  Tag $newTag
  git checkout $currentBranchVersion
else
  changeReleaseVersion &> /dev/null

  # deploy
  cat pom.xml 2>/dev/null | grep "<skip_maven_deploy>false</skip_maven_deploy>" &> /dev/null
  if [[ $? == 0 ]]; then
    mvn clean deploy > /dev/null
  fi
  Push $branchVersion
  # Tag $newTag
  git checkout $currentBranchVersion
  
  #Back to the former git project and change the maven version
  echo "Back to the former git project and change the maven version..."
  cd $currentProject
  changeNextVersion &> /dev/null
  #Push $currentBranchVersion
fi

# Keep only the last releases version
echo "Deleting those unused release or hotfix branches..."
deleteUnusedReleaseBranch release
deleteUnusedReleaseBranch hotfix
echo "Those unused release or hotfix branches have been deleted..."
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
#Whether or not to tag this branch version: "false": don't tag "yes": tag
needTag=$3
desc=$4

if [[ "$desc" == "" ]]; then
  echo "Please add a message for git!"
  exit -1
fi

desc=${desc//\"/}

if [[ "$branchVersion" == "" || "$newDate" == "" ]]; then
  # echo "branchVersion must be not empty!"
  echo "Usage: $0 BranchVersion newTagDate needTag desc"
  echo "$0 1.0.0.release 201802271230 false desc"
  exit -1
fi

newTag=${branchVersion}-${newDate}

function SwitchBranch() {
    branchVersions=$1
    # git add .
    # git commit -m "Commit by new branch:${NEW_BRANCH}."
    git checkout -b ${branchVersions} > /dev/null
    if [[ $? != 0 ]]; then
        echo "${branchVersions} is exist, switch it."
        git checkout ${branchVersions} > /dev/null
        if [[ $? != 0 ]]; then
            echo "Switched branch to ${branchVersions} error."
            exit -1
        fi
    fi
    echo "Switched branch to ${branchVersions} successfully."
    # git branch
}

function Push() {
    branchVersions=$1
    git add .
    if [[ "$desc" == "" ]]; then
      desc="Add New branch version to ${branchVersions}"
    fi
    git commit -m "${desc}"
    git push origin ${branchVersions}
    if [[ $? != 0 ]]; then
        echo "Pushed ${branchVersions} error."
        exit -1
    fi
    echo "Pushed ${branchVersions} successfully."
}

function Tag() {
    newTag=$1
    if [[ "$desc" == "" ]]; then
      desc="For prod version ${newTag}"
    fi
    git tag -a $newTag -m "${desc}"
    if [[ $? != 0 ]]; then
      echo "Tagged error!"
      exit -1
    else
      echo "Tagged to ${newTag} successfully!"
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
    echo "Only save ${reserveVersionNumber} ${type} versions!"
}

function deleteUnusedTags() {
  reserveVersionNumber=$2
  if [[ "${reserveVersionNumber}" == "" ]]; then
    reserveVersionNumber=20
  fi
  ready4deleteTags=`git ls-remote | grep -v "\^{}" |  grep tags|awk '{print $NF}'|sed 's;refs/tags/;;g'|sort -t '.' -r -k 2 -V|sed "1,${reserveVersionNumber}d"`
  for tag in $ready4deleteTags
  do
    # echo "Deleting tag $tag is started..."
    git tag -d $tag
    git push origin :refs/tags/$tag
    # echo "Tag $tag has beed deleted..."
  done
  echo "Only save ${reserveVersionNumber} tags!"
}

function changeReleaseVersion() {
  #change version
  mvnVersion=$1
  ls pom.xml &>/dev/null
  if [[ $? == 0 ]]; then
    mvn versions:set -DnewVersion=${mvnVersion}
    if [[ $? == 0 ]]; then
      mvn versions:commit
    else
      mvn versions:revert
      echo "Changed version failed, please check!"
      exit -1
    fi
  fi
}

function changeNextVersion() {
  #change version
  nextVersion=$1
  ls pom.xml &>/dev/null
  if [[ $? == 0 ]]; then
    mvn versions:set -DnewVersion=${nextVersion}
    if [[ $? == 0 ]]; then
      mvn versions:commit
    else
      mvn versions:revert
      echo "Changed version failed, please check!"
      exit -1
    fi
  fi
}

function updateVersionRecord() {
  version=$1
  verFile=.version
  if [[ ! -f "$verFile" ]]; then
    touch "$verFile"
  fi
  echo "version=$version" > $verFile
}

##########################clone into a temp folder##########################################
currentProject=`pwd`
echo "currentProject--------${currentProject}"
currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
echo "currentBranchVersion--------${currentBranchVersion}"
gitUrl=$(cat .git/config |grep url|awk -F'=' '{print $2}'|sed 's;^ *;;g')
#projectName=$(basename $gitUrl|sed 's;.git;;g')
projectName=$(basename $currentProject)
echo "gitUrl=$gitUrl"
tempFolder=$(dirname `mktemp`)
echo "Cloning project into $tempFolder/$projectName..."
if [[ -d "$tempFolder/$projectName" ]]; then
	echo "$projectName exist......"
else
    cd $tempFolder
	git clone $gitUrl $projectName
fi
cd $tempFolder/$projectName
git checkout ${currentBranchVersion} &> /dev/null
#currentBranchVersion must exist
if [[ $? != 0 ]]; then
    echo "Switched branch to ${currentBranchVersion} error."
    exit -1
fi
git branch --set-upstream-to=origin/${currentBranchVersion} ${currentBranchVersion}
echo "$projectName git pull......"
git pull --all
echo "current......`pwd`"
##########################clone into a temp folder##########################################

#检查是否已经保存过git的账户与密码
git ls-remote > /dev/null
if [[ $? != 0 ]]; then
	echo "Authentication error. Please execute the following command through git bash, and enter the account and password:"
	echo "1. git config --global credential.helper store"
	echo "2. git ls-remote"
	exit -1
fi

git status|grep "git add" &> /dev/null
if [[ $? == 0 ]]; then
  echo "Your local repo seems changed, but not commit yet, please stage or stash changes first!"
  exit -1
fi

currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
git remote show origin|grep $currentBranchVersion | egrep "本地已过时|local out of date" &> /dev/null
if [[ $? == 0 ]]; then
  echo "Your local repo seems out of date, please \"git pull\" first!"
  exit -1
fi

#git remote show origin|grep $currentBranchVersion | egrep "可快进|up to date" &> /dev/null
#if [[ $? == 0 ]]; then
#  echo "Your local repo seems to be up to date, please git push first!"
#  exit -1
#fi

#Get next develop version
releaseVersion=$(echo $branchVersion|sed 's;\.test;;'|sed 's;\.release;;'|sed 's;\.hotfix;;')
arr=(${releaseVersion//./ })
nextDevelopVersion=${arr[0]}.${arr[1]}.$((${arr[2]}+1))-SNAPSHOT

currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
echo "branchVersion--------${branchVersion}"
#echo "newTag--------${newTag}"
echo "currentBranchVersion--------${currentBranchVersion}"
SwitchBranch $branchVersion

changeReleaseVersion $releaseVersion > /dev/null
updateVersionRecord $releaseVersion
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh changeVersion $releaseVersion
fi

# deploy
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh deploy $releaseVersion
#else
  #cat pom.xml 2>/dev/null | grep "<skip_maven_deploy>false</skip_maven_deploy>" &> /dev/null
  #if [[ $? == 0 ]]; then
  #  mvn clean deploy > /dev/null
  #fi
fi
Push $branchVersion
if [[ "$needTag" == "true" ]]; then
  Tag $newTag
fi
git checkout $currentBranchVersion
changeNextVersion $nextDevelopVersion > /dev/null
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh changeVersion $nextDevelopVersion
fi
updateVersionRecord $nextDevelopVersion
Push $currentBranchVersion


# Keep only the last releases version
echo "Deleting unused release or hotfix branches..."
deleteUnusedReleaseBranch release
deleteUnusedReleaseBranch hotfix
echo "Release or hotfix branches have been deleted..."
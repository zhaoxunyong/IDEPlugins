#!/bin/bash

if [[ "$(uname)" == "Darwin" && -f ~/.zshrc ]]; then
  source ~/.zshrc
fi

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
# needTag=$3
desc=$4
preparingVersionFile=$5
modifyDepenOnVersions=$6

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

newTag=temp-${branchVersion}-${newDate}

function SwitchBranch() {
    branchVersions=$1
    # git add .
    # git commit -m "Commit by new branch:${NEW_BRANCH}."
    git checkout -b ${branchVersions} > /dev/null
    if [[ $? != 0 ]]; then
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
        echo "Pushed ${branchVersions} error, maybe you don't have any permissions to acess, please try to push changes to your personal repository."
    else
      echo "Pushed ${branchVersions} successfully."
    fi
}

# function Tag() {
#     newTag=$1
#     if [[ "$desc" == "" ]]; then
#       desc="For prod version ${newTag}"
#     fi
#     git tag -a $newTag -m "${desc}"
#     if [[ $? != 0 ]]; then
#       echo "Tagged error, maybe you don't have any permissions to acess, please try to push changes to your personal repository."
#       #exit -1
#     else
#       echo "Tagged to ${newTag} successfully!"
#       git push origin ${newTag}
#     fi
# }

function deleteUnusedReleaseBranch() {
    type=$1
    reserveVersionNumber=$2
    if [[ "${type}" == "" ]]; then
        type="release"
    fi
    if [[ "${reserveVersionNumber}" == "" ]]; then
        reserveVersionNumber=20
    fi
    #deleteBranchs=`git branch -a --sort=-committerdate|grep ${type}|grep remotes|sed 's;remotes/origin/;;'|sort -t '.' -r -k 1 -V|sed "1,${reserveVersionNumber}d"`
    deleteBranchs=`git branch --sort=-committerdate|grep ${type}|grep remotes|sed 's;remotes/origin/;;'|sort -t '.' -r -k 1 -V|sed "1,${reserveVersionNumber}d"`
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
    reserveVersionNumber=50
  fi
  ready4deleteTags=`git ls-remote | grep -v "\^{}" |  grep tags|awk '{print $NF}'|sed 's;refs/tags/;;g'|sort -t '.' -r -k 1 -V|sed "1,${reserveVersionNumber}d"`
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
      mvn versions:commit >/dev/null
    else
      mvn versions:revert >/dev/null
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
    mvn versions:set -DnewVersion=${nextVersion} > /dev/null
    if [[ $? == 0 ]]; then
      mvn versions:commit >/dev/null
    else
      mvn versions:revert >/dev/null
      echo "Changed next version failed, please check!"
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

function updateDependenciesVersion() {
  echo "preparingVersionFile---$preparingVersionFile"
  if [[ -f "$preparingVersionFile" ]]; then
    for line in `cat $preparingVersionFile`
    do  
      #echo "$line"
      pomFile=`echo ${line} | awk -F '->' '{print $1}'`
      moduleAndVersion=`echo ${line} | awk -F '->' '{print $2}'`
      prj=`echo ${moduleAndVersion} | awk -F ':' '{print $1}'`
      ver=`echo ${moduleAndVersion} | awk -F ':' '{print $2}'`
      echo "Replacing var is: $pomFile->$prj:$ver"
      #echo "sed -i \"s;.*<$prj>.*;		<$prj>$ver</$prj>;g\" $pomFile"
      sed -i "s;.*<$prj>.*;		<$prj>$ver</$prj>;g" $pomFile
    done
    git add .
    git commit -m "Mod Modifying dependencies versions."
  fi
}

# #替换版本
updateDependenciesVersion

currPath=`pwd`
projectName=${currPath##*/}
if [[ "${projectName}" == "alphatimes-commons" ]]; then
  cd "${projectName}"
fi

echo "Current prject path=`pwd`"

#Get next develop version
releaseVersion=$(echo $branchVersion|sed 's;\.test;;'|sed 's;\.release;;'|sed 's;\.hotfix;;')
arr=(${releaseVersion//./ })
nextDevelopVersion=${arr[0]}.${arr[1]}.$((${arr[2]}+1))-SNAPSHOT


currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
echo "branchVersion--------${branchVersion}"
#echo "newTag--------${newTag}"
echo "currentBranchVersion--------${currentBranchVersion}"
SwitchBranch $branchVersion

#Working for changing the versoin of the other dependencies project before Changing version.
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh beforeChangeReleaseVersion $releaseVersion $branchVersion $modifyDepenOnVersions
fi
changeReleaseVersion $releaseVersion
updateVersionRecord $releaseVersion
#Working for changing the versoin of the other dependencies project when Changing version is done.
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh afterChangeReleaseVersion $releaseVersion $branchVersion $modifyDepenOnVersions
fi

# deploy
#Working for pushing jar to maven repostitories
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh deploy $releaseVersion $branchVersion $modifyDepenOnVersions
#else
  #cat pom.xml 2>/dev/null | grep "<skip_maven_deploy>false</skip_maven_deploy>" &> /dev/null
  #if [[ $? == 0 ]]; then
  #  mvn clean deploy > /dev/null
  #fi
fi
Push $branchVersion
if [[ $? != 0 ]]; then
  # Exit when any error was occured:
  exit -1
fi
# if [[ "$needTag" == "true" ]]; then
#   Tag $newTag
# fi
git checkout $currentBranchVersion

#Working for changing the versoin of the other dependencies project before Changing version.
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh beforeChangeNextVersion $nextDevelopVersion $currentBranchVersion $modifyDepenOnVersions
fi
changeNextVersion $nextDevelopVersion
#Working for changing the versoin of the other dependencies project when Changing version is done.
if [[ -f "deploy.sh" ]]; then
  bash deploy.sh afterChangeNextVersion $nextDevelopVersion $currentBranchVersion $modifyDepenOnVersions
fi
updateVersionRecord $nextDevelopVersion
Push $currentBranchVersion


# Keep only the last releases version
echo "Deleting unused release or hotfix branches..."
deleteUnusedReleaseBranch release
deleteUnusedReleaseBranch hotfix
echo "Release or hotfix branches have been deleted..."
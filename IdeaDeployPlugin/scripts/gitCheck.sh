#!/bin/bash

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"

sedi() {
  case $(uname) in
    Darwin*) sedi=('-i' '') ;;
    *) sedi='-i' ;;
  esac

  sed "${sedi[@]}" "$@"
}


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

#!/bin/bash

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"

sedi() {
  case $(uname) in
    Darwin*) sedi=('-i' '') ;;
    *) sedi='-i' ;;
  esac

  sed "${sedi[@]}" "$@"
}


currentBranchVersion=`git branch|grep "*"|sed 's;^* ;;'`
#latestTag=`git tag --sort=-committerdate|sed 's;remotes/origin/;;'|sort -t '.' -r -k 1 -V|sed -n '1p'`
latestTag=`git tag --sort=-committerdate|grep '^[0-9]'|sed 's;remotes/origin/;;'|sort -t '.' -r -k 1 -V|sed -n '1p'`
tagDate=`echo ${latestTag##*-}`
fullDate=`echo ${tagDate:0:8}`
shortDate=`date --date="${fullDate} next day" +"%Y.%m.%d"`

if [[ ${latestTag} != "" ]]; then
  echo "The latest tag is: ${latestTag}."
  echo ""
fi

#echo "Showing the latest 10 commited logs before ${shortDate}"
#echo "显示$latestTag中有，而$currentBranchVersion中没有的文件提交记录."
#echo "----------------------------------------------------------------------------------------------------------------------------"
#git log -10 --oneline --before="$shortDate"
#git diff --stat $latestTag ^$currentBranchVersion

echo "$latestTag中有, $currentBranchVersion中没有的日志提交记录: "
echo ""
echo "----------------------------------------------------------------------------------------------------------------------------"
logs=`git log --oneline $latestTag ^$currentBranchVersion`
if [[ "$logs" == "" ]]; then
  echo "没有需要合并的代码."
else
  echo "$logs"
fi

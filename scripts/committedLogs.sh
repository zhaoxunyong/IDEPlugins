#!/bin/bash

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"

sedi() {
  case $(uname) in
    Darwin*) sedi=('-i' '') ;;
    *) sedi='-i' ;;
  esac

  sed "${sedi[@]}" "$@"
}


latestTag=`git tag --sort=-committerdate|sed 's;remotes/origin/;;'|sort -t '.' -r -k 1 -V|sed -n '1p'`
tagDate=`echo ${latestTag##*-}`
fullDate=`echo ${tagDate:0:8}`
shortDate=`date --date="${fullDate} next day" +"%Y.%m.%d"`

echo "The latest tag is: ${latestTag}."
echo "Showing the latest 10 commited logs before ${shortDate}"
echo ""
git log -10 --oneline --before="$shortDate"

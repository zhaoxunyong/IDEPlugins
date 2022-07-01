#!/bin/bash

################################################################################################################
#1. type=changeVersion
### involved phases: 
###   1. after change release version.
###   2. after change snapshot version.
### Whenever the version changed, it will triggered. Recommend using the following scenario:
### change and deploy both release and snapshot versions at the same time.

#2. type=deploy:
### involved phases: 
###   1. after change release version.
###   2. before push release version to git.
### It only triggerd before pushing release version. Recommend using the following scenario:
### only want to deploy release version to maven repo.

### Noticed don't deploy with "type=changeVersion" and "type=deploy" at the same time.
################################################################################################################

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"
sedi() {
  case $(uname) in
    Darwin*) sedi=('-i' '') ;;
    *) sedi='-i' ;;
  esac
  sed "${sedi[@]}" "$@"
}

type=$1
branchVersion=$2

#Working for deploy: deploying jar to maven before pushing codes to git
if [[ "$type" == "deploy" ]]; then
    echo "Working for deploy: $branchVersion..."
#Working for changeVersion: changing related project's version after changing owned version
elif [[ "$type" == "changeVersion" ]]; then
    echo "Working for changeVersion: $branchVersion..."
fi
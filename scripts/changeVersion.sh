#!/bin/sh
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

NEW_VERSION=$1
ls pom.xml &>/dev/null
if [[ $? == 0 ]]; then
  mvn versions:set -DnewVersion=${NEW_VERSION}
  if [[ $? == 0 ]]; then
    mvn versions:commit
  else
    mvn versions:revert
    echo "Changed version failed, please check!"
    exit -1
  fi
fi

if [[ -f "deploy.sh" ]]; then
  bash deploy.sh changeVersion $NEW_VERSION
fi

#sedi "s;^version=.*;version=${NEW_VERSION};" docker-build.sh
#sedi "s;^version=.*;version=${NEW_VERSION};" docker-create.sh
#sedi "s;^ENV VERSION .*;ENV VERSION ${NEW_VERSION};" Dockerfile
#sedi "s;version=.*;version=${NEW_VERSION};" hkcash-startup/src/main/resources/version.properties
#sedi "s;\sversion: .*; version: ${NEW_VERSION};" hkcash-startup/src/main/resources/swagger.yml
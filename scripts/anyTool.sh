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

currPwd=`pwd`
project=`echo $currPwd|sed 's;.*/;;g'`
version=`cat pom.xml |grep "<version>"|sed -n '1p'|sed "s;</\?version>;;g"|sed 's;^\s*;;g'`
echo "$project current version is $version"

dependencyProjectVersion=${project}-api-version
#echo $dependencyProjectVersion
projects="xpay-gateway xpay-external-gateway account-server configuration-server merchant-server operation-server payment-server external-server xpay-commons zerofinance-commons"

cd ..
for project in $projects
do
  #echo "Replacing $project..."
  #cd ../$project
  pomFile=`grep -r "$dependencyProjectVersion" $project | awk -F ':' '{print $1}'`
  if [[ "$pomFile" != "" ]]; then
    fullPomFile=$pomFile
    echo "Replacing dependency project version: "$fullPomFile
    sed -i "s;.*<$dependencyProjectVersion>.*;		<$dependencyProjectVersion>$version</$dependencyProjectVersion>;g" $fullPomFile
  fi
  #cd - > /dev/null
done
cd $currPwd
echo "All done, please double-check."
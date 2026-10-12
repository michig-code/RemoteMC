#!/bin/bash
# Downloads a Paper/Folia server and installs the freshly built plugin.
# usage: mkserver.sh <paper|folia> <version> <dir>
set -e
JAR=$(ls "$(cd "$(dirname "$0")" && pwd)"/../target/RemoteMC-*.jar | grep -v original | head -1)
url=$(curl -s https://fill.papermc.io/v3/projects/$1/versions/$2/builds/latest | python3 -c 'import json,sys;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
mkdir -p $3/plugins && cd $3
curl -sSLo server.jar "$url"
echo eula=true > eula.txt
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\n' > server.properties
cp "$JAR" plugins/
echo "$1 $2 ready"

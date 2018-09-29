#!/usr/bin/env bash

PWD=$(pwd)
STAGE=""

if [ -z "$1" ];
then
    STAGE="default"
else
    STAGE="production-$1"
fi

java -Dswarm.project.stage.file="file:///$PWD/standalone.yml" -Dswarm.project.stage="$STAGE" -jar searchpe-*.jar

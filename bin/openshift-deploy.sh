#!/usr/bin/env bash

mvn clean
mvn clean package

mvn fabric8:deploy -Popenshift -DDATABASE_VENDOR=$1

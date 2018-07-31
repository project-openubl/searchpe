#!/usr/bin/env bash

mvn clean -Dtest=$1 -Dswarm.debug.port=5005 test
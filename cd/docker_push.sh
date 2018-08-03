#!/bin/bash
if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    docker login -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD";
    docker push searchpe/searchpe
fi
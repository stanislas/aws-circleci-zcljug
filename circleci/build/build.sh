#!/usr/bin/env bash

set -e

DESCRIBE=$(git describe --match release/*.* --abbrev=4 --dirty=--DIRTY--)
VERSION=$(echo $DESCRIBE | sed  's_release/\(.*\)_\1_')
TAG=655043939509.dkr.ecr.eu-central-1.amazonaws.com/zcljug:${VERSION}

pushd circleci/build

rm -fr app
mkdir -p app
cp ../../target/uberjar/aws-circleci-zcljug-${VERSION}-standalone.jar app/aws-circleci-zcljug.jar
cp circus.ini app
cp app.sh app

docker build -t ${TAG} .

popd

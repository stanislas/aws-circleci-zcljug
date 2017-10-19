#!/usr/bin/env bash

set -e

DESCRIBE=$(git describe --match release/*.* --abbrev=4 --dirty=--DIRTY--)
VERSION=$(echo $DESCRIBE | sed  's_release/\(.*\)_\1_')
TAG=655043939509.dkr.ecr.eu-central-1.amazonaws.com/zcljug:${VERSION}

`aws ecr get-login --no-include-email --region eu-central-1`
docker push ${TAG}


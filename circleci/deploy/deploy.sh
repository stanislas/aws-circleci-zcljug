#!/usr/bin/env bash

set -e

DESCRIBE=$(git describe --match release/*.* --abbrev=4 --dirty=--DIRTY--)
VERSION=$(echo $DESCRIBE | sed  's_release/\(.*\)_\1_')

lein run -m aws/deploy-service ${VERSION}

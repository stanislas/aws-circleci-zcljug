#!/bin/sh

JAVA_OPTS="${JAVA_OPTS} -server"

java ${JAVA_OPTS} -cp /app/aws-circleci-zcljug.jar aws_circleci_zcljug.main


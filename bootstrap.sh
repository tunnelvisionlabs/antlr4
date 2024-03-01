#!/bin/bash

VERSION="4.10.0-BOOTSTRAP"

echo "Installing boostrap runtime JAR...";
mvn install:install-file \
  -Dfile="./lib/antlr4-bootstrap.jar" \
  -DgroupId="com.tunnelvisionlabs" \
  -DartifactId="antlr4-runtime" \
  -Dversion="$VERSION" \
  -Dpackaging=jar

echo "Installing boostrap Maven plugin JAR...";
mvn install:install-file \
  -Dfile="./lib/antlr4-bootstrap-maven-plugin.jar" \
  -DgroupId="com.tunnelvisionlabs" \
  -DartifactId="antlr4-maven-plugin" \
  -Dversion="$VERSION" \
  -Dpackaging=jar

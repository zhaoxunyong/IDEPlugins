#!/bin/bash

#export JAVA_HOME="/d/Developer/java/jdk-11.0.9"
export JAVA_HOME="/d/Developer/java/java-11-openjdk-11.0.9.11"
./gradlew buildPlugin

#Pick up a zip file from "IDE_Plugin\IdeaDeployPlugin\build\distributions"
#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -z "$JAVA_HOME" ]; then
  echo "ERROR: JAVA_HOME is not set. Please point JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Gradle wrapper JAR is missing. Downloading the official Gradle 9.3.1 wrapper..." >&2
  mkdir -p "$APP_HOME/gradle/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -sS --retry 3 -L -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar" || rm -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar" || rm -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
  else
    echo "ERROR: curl or wget is required to download the Gradle wrapper JAR." >&2
    exit 1
  fi
fi
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "ERROR: Could not download the official Gradle 9.3.1 wrapper JAR." >&2
  exit 1
fi
exec "$JAVA_HOME/bin/java" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

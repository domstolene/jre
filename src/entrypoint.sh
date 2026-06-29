#!/usr/bin/env bash
set -eo pipefail

# find application jar file
if [[ -z "${APP_JAR}" ]]; then
  echo "APP_JAR environment variable is not set. Searching for application jar file..."

  app_folders=(/app /deployments)
  for folder in "${app_folders[@]}"; do
    if [[ -d "${folder}" ]]; then
      if APP_JAR=$(find "${folder}" -name "*.jar" | head -n 1); then
        echo "Found application jar file: ${APP_JAR}"
        break
      fi
    fi
  done
fi

if [[ -z "${APP_JAR}" ]]; then
  echo "No application jar file found in any of the folders: ${app_folders[*]}"
  exit 1
fi

# RAMPercentage -- bruk 75% av resources.limits.memory
# "$@" -- arguments from CMD in Dockerfile, if any
exec java ${JAVA_OPTS} \
  -javaagent:/da-otel-agent/da-otel-agent.jar \
  -Dotel.configuration.service.file=/da-otel-agent/da-otel-agent.yaml \
  -jar "${APP_JAR}" \
  -XX:InitialRAMPercentage=75.0 \
  -XX:MaxRAMPercentage=75.0 \
  "$@"

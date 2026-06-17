#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

mvn clean package -Dmaven.test.skip=true

java -jar target/Kubot.jar >/dev/null 2>&1 &

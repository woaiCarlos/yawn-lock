#!/usr/bin/env bash
# Comet build wrapper: 设置 JDK 17 + 调 gradle build。
# 用这个 wrapper 替代 build_command 里的 export JAVA_HOME=... && ./gradlew ...
# 因为 comet-guard 不允许 build_command 包含 shell metacharacter。
set -e
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/.."
./gradlew "$@"

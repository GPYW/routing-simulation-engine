#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
rm -rf out
javac -d out $(find src/main/java -name "*.java")
java -Dstdout.encoding=UTF-8 -Xmx2g -cp out com.uom.engine.Main "$@"
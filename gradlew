#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. CI provisions Gradle 9.1.0 via gradle/actions/setup-gradle." >&2
exit 127

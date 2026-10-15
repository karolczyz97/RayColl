#!/usr/bin/env bash
# Runs ./gradlew.bat in the current directory, but only one Gradle build at a time across all worktrees.
# Usage: bash gradle-locked.sh <gradle args...>
# The lock lives in the shared .git directory, so every worktree of this repo sees the same one.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
exec python "$ROOT/tools/gradle_locked.py" "$@" --console=plain

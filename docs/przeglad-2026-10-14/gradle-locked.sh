#!/usr/bin/env bash
# Runs ./gradlew.bat in the current directory, but only one Gradle build at a time across all worktrees.
# Usage: bash gradle-locked.sh <gradle args...>
LOCK="C:/Users/Karol/AppData/Local/Temp/claude/C--Users-Karol--gemini-antigravity-scratch-RayColl/3dcb1293-b81c-4b3b-88f3-9cfde2526c53/scratchpad/review/gradle.lock"
waited=0
until mkdir "$LOCK" 2>/dev/null; do
  sleep 15
  waited=$((waited + 15))
  if [ "$waited" -ge 2400 ]; then
    echo "gradle-locked: waited 40 min for the lock; another build may have crashed. Removing stale lock." >&2
    rmdir "$LOCK" 2>/dev/null
  fi
done
trap 'rmdir "$LOCK" 2>/dev/null' EXIT
./gradlew.bat "$@" --console=plain

#!/usr/bin/env bash
# Compile check that several people (or agents) can run at once in this folder.
#
#   tools/check.sh ui/chat data/chat          compile; pass when none of the errors are under those folders
#   tools/check.sh --test 'ChatTest*' ui/chat  the same, then run matching unit tests
#   tools/check.sh --all                       assembleDebug + every unit test + lint (the final gate)
#
# Folders are relative to app/src/main/java/com/hafiyanjye/app (or app/src/test/...).
# Builds run one at a time (a lock), so a second caller waits instead of
# starting a second Gradle and running the computer out of memory.
set -u
cd "$(dirname "$0")/.."
exec 9>/tmp/hafi-android-gradle.lock
flock 9

if [ "${1:-}" = "--all" ]; then
  ./gradlew -q :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
  exit $?
fi

tests=""
if [ "${1:-}" = "--test" ]; then tests="$2"; shift 2; fi

out=$(./gradlew -q :app:compileDebugKotlin 2>&1)
status=$?
errors=$(printf '%s\n' "$out" | grep -E '^e: ' || true)
if [ $status -ne 0 ] && [ -z "$errors" ]; then
  printf '%s\n' "$out" | tail -40
  echo "CHECK FAILED: the build broke before Kotlin reported errors (see above)."
  exit 1
fi

mine=""; others="$errors"
for prefix in "$@"; do
  hit=$(printf '%s\n' "$errors" | grep -F "/com/hafiyanjye/app/$prefix" || true)
  [ -n "$hit" ] && mine="$mine$hit"$'\n'
  others=$(printf '%s\n' "$others" | grep -v -F "/com/hafiyanjye/app/$prefix" || true)
done

if [ -n "$mine" ]; then
  printf '%s' "$mine"
  echo "CHECK FAILED: errors in your folders (above)."
  exit 1
fi
if [ -n "$others" ]; then
  echo "Your folders compile. Errors in files that are not yours (their owners fix them):"
  printf '%s\n' "$others" | sed -E 's/^e: file:\/\/[^ ]*\/com\/hafiyanjye\/app\//  /' | cut -c1-200 | head -20
  [ -n "$tests" ] && echo "Tests not run: the module does not compile yet."
  exit 0
fi
echo "Compiles cleanly."
if [ -n "$tests" ]; then
  ./gradlew -q :app:testDebugUnitTest --tests "$tests" && echo "Tests passed: $tests"
  exit $?
fi

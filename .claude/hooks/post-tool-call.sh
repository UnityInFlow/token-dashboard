#!/bin/bash
# Post-tool-call hook: compile + ktlint after file changes
# Exit 0 = silent success (nothing added to context)
# Exit 2 = failure — agent re-engaged to fix before finishing

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Only run on Kotlin file changes (check if tool was Edit or Write)
# The hook runs after every tool call — keep it fast
OUTPUT=$(./gradlew ktlintCheck compileKotlin --daemon -q 2>&1)

if [ $? -ne 0 ]; then
  echo "Build/lint errors — fix before continuing:" >&2
  echo "$OUTPUT" >&2
  exit 2
fi

# SUCCESS: completely silent — nothing added to context

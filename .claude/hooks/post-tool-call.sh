#!/bin/bash
# Post-tool-call hook: compile + ktlint after file edits
# Exit 0 = silent success (nothing added to context)
# Exit 2 = failure — agent re-engaged to fix before finishing
#
# NOT wired in settings.json by default — too slow for every tool call.
# To enable: add "postToolCall" section to .claude/settings.json
# Only runs on Edit/Write tool calls to avoid triggering on reads/greps.

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Guard: only run after file-modifying tool calls
if [ "$CLAUDE_TOOL_NAME" != "Edit" ] && [ "$CLAUDE_TOOL_NAME" != "Write" ]; then
  exit 0
fi

OUTPUT=$(./gradlew ktlintCheck compileKotlin --daemon -q 2>&1)

if [ $? -ne 0 ]; then
  echo "Build/lint errors — fix before continuing:" >&2
  echo "$OUTPUT" >&2
  exit 2
fi

# SUCCESS: completely silent — nothing added to context

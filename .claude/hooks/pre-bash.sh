#!/bin/bash
# Pre-bash hook: block dangerous commands before execution
# Exit 1 = block command, message shown to agent

COMMAND="$CLAUDE_TOOL_INPUT_COMMAND"

# Block force-push
if echo "$COMMAND" | grep -qE "git push --force|git push -f"; then
  echo "ERROR: Force push is not allowed. Use --force-with-lease and confirm with user." >&2
  exit 1
fi

# Block dropping databases / destructive SQLite operations
if echo "$COMMAND" | grep -qiE "drop (database|table|schema)|rm.*\.db"; then
  echo "ERROR: Dropping databases/tables requires human confirmation." >&2
  exit 1
fi

# Block production deployments
if echo "$COMMAND" | grep -qE "deploy.*(prod|production)|docker push|helm upgrade.*prod"; then
  echo "ERROR: Production deployments require explicit human approval." >&2
  exit 1
fi

# Block running the app in ways that would bind ports and hang
if echo "$COMMAND" | grep -qE "gradlew run\b|gradlew.*bootRun"; then
  echo "ERROR: Do not start the server in agent context — it blocks. Use tests instead." >&2
  exit 1
fi

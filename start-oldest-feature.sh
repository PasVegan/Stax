#!/usr/bin/env bash
set -euo pipefail

BASE_BRANCH="dev"
PREFIX="feature"
REPO_BLOB_PREFIX="https://github.com/PasVegan/Stax/blob/dev/"

command -v gh >/dev/null || { echo "gh is not installed"; exit 1; }
command -v git >/dev/null || { echo "git is not installed"; exit 1; }
command -v jq >/dev/null || { echo "jq is not installed"; exit 1; }

git rev-parse --is-inside-work-tree >/dev/null
gh auth status >/dev/null

issue_json="$(
  gh issue list \
    --search "is:issue is:open sort:created-asc" \
    --limit 1 \
    --json number,title,url \
    --jq '.[0]'
)"

if [[ -z "$issue_json" || "$issue_json" == "null" ]]; then
  echo "No open issue found."
  exit 0
fi

issue_number="$(jq -r '.number' <<< "$issue_json")"
issue_title="$(jq -r '.title' <<< "$issue_json")"
issue_url="$(jq -r '.url' <<< "$issue_json")"

issue_body_raw="$(
  gh issue view "$issue_number" \
    --json body \
    --jq '.body // ""'
)"

issue_body="${issue_body_raw//$REPO_BLOB_PREFIX/}"

prompt_prefix="Do this issue, make sure to commit like a developper(if there is a lot of work you can do multiple commit if relevant), do not in any case add you as a co-author, once you're done push the commits. Make use of any relevant skills/plugins:"

clipboard_text="$(cat <<EOF
${prompt_prefix}

\`\`\`md
${issue_body}
\`\`\`
EOF
)"

slug="$(
  printf '%s' "$issue_title" |
    tr '[:upper:]' '[:lower:]' |
    sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' |
    cut -c1-60
)"

branch="${PREFIX}/${issue_number}-${slug}"

echo
echo "Oldest issue: #${issue_number} ${issue_title}"
echo "Issue URL: ${issue_url}"
echo "Creating linked branch: ${branch}"
echo "Base branch: ${BASE_BRANCH}"
echo

echo "================ CLAUDE PROMPT + ISSUE DESCRIPTION ================"
printf '%s\n' "$clipboard_text"
echo "==================================================================="
echo

if command -v pbcopy >/dev/null; then
  printf '%s' "$clipboard_text" | pbcopy
  echo "Claude prompt and cleaned Markdown issue description copied to clipboard."
else
  echo "pbcopy not found, so text was not copied to clipboard."
fi

git fetch origin "$BASE_BRANCH"

gh issue develop "$issue_number" \
  --base "$BASE_BRANCH" \
  --name "$branch" \
  --checkout

git checkout "$branch"

git commit --allow-empty -m "Start work on #${issue_number}: ${issue_title}"

git push -u origin "$branch"

pr_body="$(cat <<EOF
Closes #${issue_number}

Issue: ${issue_url}
EOF
)"

gh pr create \
  --draft \
  --base "$BASE_BRANCH" \
  --head "$branch" \
  --title "Feature: ${issue_title}" \
  --body "$pr_body"

echo
echo "Done. Now on branch: $(git branch --show-current)"

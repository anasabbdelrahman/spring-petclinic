# Thread Fold handoff - Week 3 Part 8: Diagnosing Failures and Thread Fold

Sanitised copy of the handoff prompt used to start a fresh Claude Code session on the Spring
PetClinic repository, 2026-09-24. The receiving session was told to treat every fact below as a
claim to verify, and to run the commands in section 7 and report discrepancies before doing any
work.

## 1. Original Part 8 goal

- Step 1 - Recognition: for four scenarios, name the single dominant AI failure pattern, with a
  one-sentence justification, using the exercise's vocabulary.
- Step 2 - Diagnose the prior long session from concrete evidence: did context pollution or other
  degradation occur? Keep incorrect work, incomplete or unverified claims, reviewer limitations and
  ordinary iteration apart. Do not invent a failure.
- Produce a Thread Fold handoff, then write the three deliverables in section 5.

## 2. Decisions already made

### Step 1 - final classifications (exercise vocabulary)

1. Same non-working fix suggested repeatedly -> **Pattern mismatch**.
2. Forgets which file was being worked on -> **Context pollution**.
3. Wrong feature despite clear instructions -> **Prompt ambiguity**.
4. Confident reference to nonexistent `Array.dedupe()` -> **Knowledge gap**.

Step 1 evaluation: None of the four initial answers matched the required dominant category for
its scenario. Context pollution was part of the exercise vocabulary, but it was assigned to the
wrong scenario. The session answered from a general taxonomy instead of the exercise’s own. This
was a
classification error on one question, not long-session degradation.

### Step 2 - session diagnosis (final)

Verdict: no clear length-related degradation observed. The Step 1 miss is a separate error.

- Incorrect work, all caught by the agent before review:
  - the hook was first indented with tabs against `.editorconfig`, then converted;
  - the agent's own heredoc containing a force-push string was blocked by the new hook;
  - there was a trailing space in the first evidence file;
  - one static-check run failed on a zsh word-splitting error and was re-run under bash.
- Unverified claims still open:
  - the `:*` prefix syntax is supported by current Anthropic documentation, which was consulted;
  - runtime permission matching was never tested;
  - whether `:*` alone also covers the bare command remains unverified, which is why exact bare
    twin rules exist;
  - precedence across scopes was asserted, not tested;
  - Git option-abbreviation behaviour and `clean.requireForce=false` semantics were not checked in
    a scratch repository;
  - detect-secrets passing is shown by user-provided terminal output, and Git history alone does
    not reproduce it.
- Reviewer limitations: `/review-changes` sees only the staged diff.
  - Run 1 saw 2 of 4 files.
  - Run 2 saw only the docs, made zero tool calls and raised a false "High" caused by partial
    staging.
  - Run 3 saw all 4 files, made 7 tool calls and had no findings.
  - None of the runs tested behaviour.
- Ordinary iteration:
  - the user corrected the permission syntax to `Bash(x:*)`;
  - files were staged and restaged several times;
  - the date moved from 2026-09-23 to 2026-09-24.

### Part 7 decisions and rejected approaches (context)

- Layers:
  - GitHub branch protection owns shared history;
  - the sandbox owns reachable files and network;
  - Claude permissions own allow/ask/deny of tool calls;
  - provider IAM/RBAC/DB grants own credential power;
  - a PreToolUse hook owns semantic inspection of Bash commands.
- Rejected approaches:
  - Prefix permission rules alone: Git accepts flags anywhere, bundled, as `+refspec`, or after
    `-C dir`, so a prefix rule does not match every form.
  - Relying on short rules to cover longer spellings: each longer spelling is listed explicitly.
  - Editing `.claude/settings.local.json`: the file is untracked and out of scope. Its broad
    allows are recorded as residual risk.
- Recommended only, not configured or verified: GitHub branch protection, sandbox, credential
  scoping.

## 3. Repository state at handoff

- Branch `homework/diagnosing-failures-thread-fold`, no upstream configured.
- HEAD: `Add layered agent controls and git guard hook`. `homework/controlling-claude-actions`
  points at the same commit. Commits are cited by subject; check any SHA with
  `git merge-base --is-ancestor`.
- Working tree clean, nothing staged, none of the three deliverables present.
- Part 7 files in HEAD:
  - `.claude/hooks/guard-git.sh` (mode 100755);
  - `.claude/settings.json` (permissions: 6 allow, 4 ask, 28 deny, plus a `PreToolUse` `Bash`
    hook entry);
  - `agent-controls-2026-09.md`;
  - `guard-hook-output-2026-09.txt`.
- Pre-existing hooks, unchanged:
  - SessionStart `startup|resume`;
  - SessionStart `compact`;
  - PreToolUse `Write|Edit` (the `.env` guard);
  - PostToolUse `Write|Edit` (the tool log).

## 4. Verification already done (Part 7)

- The hook was fed 19 Claude-shaped PreToolUse JSON cases and all 19 passed. The results are in
  `guard-hook-output-2026-09.txt`.
- The hook was also run through its registered command string, with the same results. The `.env`
  guard still refuses `.env`.
- Static checks passed:
  - `bash -n`;
  - `jq empty`;
  - the executable bit;
  - a diff of the existing hooks against HEAD;
  - `git diff --check`;
  - scans for trailing whitespace, CR, final newline and plain-http URLs.
- `/review-changes` run 3 had no actionable findings.
- Not done:
  - permission-matching tests;
  - `./gradlew build` (no Java was changed);
  - Git flag semantics in a scratch repository.

## 5. Remaining work and deliverables

Markdown only, in the repository root:

1. `failure-patterns-2026-09.md` - the Step 1 classifications and their evaluation, plus the
   Step 2 diagnosis with its four categories kept apart.
2. `thread-fold-handoff-2026-09.md` - this handoff, sanitised, ending with a line on whether the
   fresh session resumed productively.
3. `trust-calibration-2026-09.md` - at least five real code areas mapped to HIGH, MEDIUM or LOW
   by the decision tree:
   - authentication, access control, production data or schema, PII, security controls or audit
     logging -> HIGH;
   - business rules, APIs, user input or reliability -> MEDIUM;
   - everything else -> LOW, reconsidering shared input-accepting UI.

   Each row gives the code area and files, the tier, the reason, the verification commitment, the
   reviewer and the automation. The deliverable needs at least one area of each tier, and every
   path must be verified.

## 6. Constraints

- No production-code change: no edits to `src/`, tests, build files, `.claude/settings*.json` or
  the hook.
- Do not stage or commit until the user asks. Before a commit, stage all intended files together,
  run `/review-changes`, and run detect-secrets.
- The guard hook blocks Bash commands whose text contains destructive Git forms, even inside a
  heredoc, so documents are written with the Write tool.
- `.editorconfig` applies: LF, final newline, no trailing whitespace. nohttp applies: no plain-http
  URLs.
- Cite a file:line or a runnable test for every behavioural claim. Never report a pipeline's exit
  status as the command's own.
- Sanitise: no secrets, tokens, absolute personal paths, session IDs or email addresses.

## 7. Verification commands

```bash
git branch --show-current
git log --oneline -3
git status --short
git diff --cached --name-only
git show --stat HEAD
git ls-files -s .claude/hooks/guard-git.sh
jq '.permissions | map_values(length)' .claude/settings.json
jq -r '.hooks | to_entries[] | .key + ": " + ([.value[].matcher] | join(", "))' .claude/settings.json
ls failure-patterns-2026-09.md thread-fold-handoff-2026-09.md trust-calibration-2026-09.md
git ls-files src/main/java src/main/resources src/test .claude .pre-commit-config.yaml .secrets.baseline
```

## 8. Hard rule

Make no production-code change of any kind.

---

**Resumption result (fresh session, 2026-09-24):** the session resumed productively and needed
no repository-state rediscovery. Every section 7 check matched sections 3 and 5, with no
discrepancy. It nevertheless repeated the zsh word-splitting mistake documented in section 2, but
recognized the invalid check immediately and re-ran it under Bash. So Thread Fold preserved
progress and constraints but did not prevent one known operational error from recurring.

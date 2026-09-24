# Agent controls - 2026-09

Week 3, Part 7: controlling what Claude can do when it runs unsupervised on this repository.

- **Date:** 2026-09-23
- **Branch:** `homework/controlling-claude-actions`
- **Changed in this repository:** `.claude/settings.json` (a `permissions` block and one new
  `PreToolUse` entry for `Bash`), and the new hook `.claude/hooks/guard-git.sh`.
- **Evidence:** `guard-hook-output-2026-09.txt`.
- **Not changed:** production code, tests, build files, `.claude/settings.local.json`, and every
  hook that already existed (the `SessionStart` reminder and compaction checklist, the
  `Write|Edit` `.env` guard, and the `PostToolUse` tool log).

## 1. Principle

No single layer is trusted to stop a destructive action by itself. Each layer owns one question,
and it is the layer that can answer that question with authority. The layers nearer the agent are
fast and convenient but easy to get around. The layers further away are slower to change but are
the ones that actually hold. If a local control fails, the next layer out must still hold.

## 2. Layers and what each owns

| Layer | Owns | Where it lives |
|---|---|---|
| GitHub branch protection | Shared history | Remote repository settings |
| Sandbox | Reachable files and network | OS-level sandbox around commands |
| Claude permissions | Which tool calls are allowed, need approval, or are denied | `.claude/settings.json` `permissions` |
| Provider IAM / RBAC / database grants | What external credentials can do | Cloud provider, cluster, database |
| `guard-git.sh` hook | What a Bash command means | `.claude/settings.json` `hooks.PreToolUse` |

One sentence per layer:

- **GitHub branch protection** owns shared-history protection. Protect `main`, require a pull
  request with at least one approving review, block force-pushes and block branch deletion. It is
  the only layer enforced by the server that every clone pushes to, so it holds whatever machine,
  agent, hook or credential a push comes from.
- **The sandbox** owns which files and network destinations a command can reach, because only
  enforcement at the OS level constrains what a process actually does. That includes interpreters,
  scripts and subprocesses that no rule matching command text ever sees.
- **Claude permissions** own which tool invocations are allowed, which require approval, and
  which are denied, because that is the one layer that can stop a call before it runs and ask a
  human. They match patterns, though, not meaning.
- **Provider IAM, RBAC and database grants** own what an external credential can do once it is
  used, because the provider enforces them on every request. That holds whether the request comes
  from the agent, a script, or a leaked token, and nothing local can widen them.
- **The hook** owns semantic inspection of Bash commands, because it can parse a command and find a
  dangerous flag in any position. Prefix rules cannot express that reliably.

### 2.1 GitHub branch protection (recommended, not applied from this session)

For `main`: require a pull request before merging; require at least one approval; dismiss stale
approvals on new commits; do not allow force-pushes; do not allow deletions; apply the rules to
administrators too. This is repository configuration on GitHub. It was **not** configured or
verified from this session, and nothing in this change depends on it being in place. It is the
recommended outer boundary.

### 2.2 Sandbox (recommended, not configured here)

Run unsupervised sessions with the Claude Code sandbox enabled. Allow writes to the working tree
only, deny reads of credential locations such as `~/.ssh`, `~/.aws` and `~/.config/gh`, and allow
network access only to the hosts the build needs. The task scoped the `settings.json` change to
permissions and the hook, so sandbox settings are not added here.

### 2.3 Claude permissions (added)

| Decision | Rules | Why |
|---|---|---|
| allow | `Bash(git status:*)`, `Bash(git diff:*)`, `Bash(git log:*)` | Read-only inspection. No prompt is needed. |
| ask | `Bash(git commit:*)`, `Bash(git push:*)` | Both change history someone else will see, so a human approves each one. |
| deny | `Bash(git push --force:*)`, `Bash(git push --force-with-lease:*)`, `Bash(git push -f:*)`, `Bash(git push --mirror:*)`, `Bash(git reset --hard:*)`, `Bash(git clean -f:*)`, `Bash(git clean --force:*)`, and `Bash(git clean <flags>:*)` for `-fd`, `-df`, `-fx`, `-xf`, `-fdx`, `-xdf`, `-dxf` | The obvious spellings of destructive forms. Refused outright. |

The rules use Claude Code's documented `:*` prefix syntax. Every `:*` rule also has an exact
bare-command twin, such as `Bash(git status)` or `Bash(git push --force)`, so that the command
with no further arguments is covered too. Whether `:*` alone matches the bare command was not
tested in this session. `--force-with-lease` and each bundled `git clean` spelling are listed
separately: a prefix rule for `--force` or `-f` is not relied on to match the longer word that
starts with it.

Deny takes precedence over ask, and ask over allow. The deny rules here therefore also win over
anything broader in `.claude/settings.local.json`.

Known imperfection: `git diff --output=<file>` and `git log --output=<file>` write a file while
still matching the read-only allow rules. The sandbox layer is what bounds where that write can
land.

### 2.4 Provider IAM / RBAC / database grants (recommended)

Give the agent its own credentials and grant them only what the task needs. A GitHub token that
can push branches and open pull requests but cannot administer the repository or bypass branch
protection. No production database credentials; if a database is needed, a user granted only
`SELECT` on a non-production copy. Nothing here issues or changes a credential. The repository
rule "never hardcode credentials" and the existing `detect-secrets` pre-commit defense still
apply.

### 2.5 The hook (added)

`.claude/hooks/guard-git.sh` is registered as a `PreToolUse` hook with matcher `Bash`, next to the
existing `Write|Edit` `.env` guard. It reads Claude's JSON payload from stdin and inspects
`.tool_input.command`. It exits 2 with a one-line reason on stderr for:

- `git push` with `--force`, `-f` (alone or bundled, e.g. `-uf`), `--force-with-lease[=...]`,
  `--force-if-includes`, or a `+refspec` (e.g. `+main`). Each one is a force-push. `--force-*`
  abbreviations such as `--force-w` are covered too.
- `git push --mirror` (including its unique abbreviations such as `--mir`).
- `git reset --hard`.
- `git clean` with `--force` or `-f` alone or bundled (`-fd`, `-xdf`), or with
  `clean.requireForce` overridden via `-c` or `--config-env`. That override makes `git clean`
  delete files without `-f`.

It exits 0 for everything else, including `git status`, `git log`, `git push origin main` and
`git reset --soft`. Exit 0 means "not the hook's business". The permission rules still decide, so
the plain push still needs approval.

How it finds the flag: it joins backslash-newline continuations and splits the command on `;`,
`&`, `|`, parentheses, braces and backticks. It strips quotes and looks at every `git` word in each
segment. It skips global options such as `-C <dir>` and `-c <key=value>` to find the subcommand,
then scans every argument after it up to `--`. Because of that, `git status && git push origin
main --force` and `git -C /tmp/repo push origin main -uf` are both blocked. A malformed payload,
or a missing `jq`, also exits 2: the guard fails closed.

## 3. Why prefix permissions are not enough

A Claude permission rule such as `Bash(git push --force:*)` is a prefix rule: it matches command
text that starts with `git push --force`. `git push origin main --force` does **not** match it. The dangerous flag is
not directly after `git push`; the remote and branch come first. Git accepts options anywhere
among the arguments, so the same force-push can also be written as:

- `git push origin main -f`, `git push -uf origin main`
- `git push origin +main` (no flag at all)
- `git -C ../other push --force`, with a global option before the subcommand
- `git push --force-w`, an unambiguous abbreviation

Covering those with more permission rules would take one rule per position, spelling
and abbreviation, and it would still miss the next spelling. The deny list in section 2.3
deliberately catches only the obvious forms. It is the cheap first filter, not the guarantee. The
hook parses the arguments instead, so position does not matter.

## 4. Limits of the hook

- **Hooks are not sandboxed.** A hook is an ordinary program that Claude Code runs with the
  user's full privileges, the user's environment and the user's credentials. A bug in it, or a
  malicious edit to it, runs with the same power as the user. Review hook changes like any code
  that runs with those privileges. Because the script lives in the working tree, the sandbox
  should not let the agent write to `.claude/`.
- **It protects this machine only.** The hook runs only where this `.claude/settings.json` is
  loaded, which means Claude Code sessions in this checkout. A different clone, a shell outside
  Claude, a CI job or another tool can force-push without ever seeing it. **Remote branch
  protection remains the real shared boundary.**
- **It reads text, not intent.** It does not expand variables (`git push $FLAGS`), resolve Git
  aliases (`git -c alias.p=push p --force`), decode `eval` or base64, or see Git called from a
  script or an interpreter. It does not see `GIT_CONFIG_*` environment overrides or a user-level
  `clean.requireForce=false`. Those gaps belong to the sandbox and branch protection.
- **It fails closed on mentions.** Any `git push ... --force` text inside a Bash command is
  blocked, even inside a heredoc or an `echo`. That happened during authoring: the call that
  assembled the evidence file was refused for quoting the test case (see
  `guard-hook-output-2026-09.txt` section 4). A false refusal costs a rewrite; a false allow costs
  history.
- **Out of scope by design:** remote branch deletion (`git push --delete`, `:branch`), `git
  checkout -f`, `git branch -D` and `git stash clear`. Push deletion is still gated by `ask` on
  `git push` and, for `main`, by branch protection.
- **Residual risk from local settings.** `.claude/settings.local.json`, which is untracked and was
  left unchanged, allows `Bash(python3 *)` and `Bash(cd *)`. An interpreter can call Git without
  showing Git's arguments on the command line. That gets past both the prefix rules and the hook.
  For an unsupervised run, remove broad interpreter allows or rely on the sandbox and branch
  protection for that path.

## 5. Verification

Recorded in `guard-hook-output-2026-09.txt`. Each case fed the hook the same JSON structure Claude
sends. None of the commands under test was executed, and nothing was pushed.

- Required: `git push origin main --force` -> exit 2; `git status` -> exit 0.
- Focused: hard reset, forced clean (`-fd`, `-xdf`, `requireForce` override), mirror push,
  `-f`, `--force-with-lease`, `+refspec`, a compound command, a global `-C` option, and the
  allowed forms `git log`, `git diff`, plain push, soft reset, `git clean -n`, and a commit
  message that mentions `--force`. Malformed payload -> exit 2.
- Registered command string, run through `sh -c`, gives the same results. The existing `.env`
  guard still refuses a `.env` path and allows an ordinary one.
- Static checks: `bash -n`, `jq empty` on `settings.json`, executable bit, `git diff --check`, and
  a trailing-whitespace scan (section 5 of the evidence file). `jq empty` proves only that
  `settings.json` is valid JSON. It does not prove that any permission rule matches, or fails to
  match, a given command. Nothing in this change tests permission matching; the tests exercise
  the hook only.

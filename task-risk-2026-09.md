# Task risk table — September 2026

Week 4 tasks from this repository, rated by reversibility, blast radius and audit requirement.

Oversight frameworks:

- **HITL**: a human approves every action.
- **HOTL**: Claude works on its own and stops at triggers set by the human.
- **Auto mode**: a classifier reviews actions beyond reads and working-directory edits, and blocks risky ones.
- **Autonomous with audit**: Claude runs unattended in an isolated container, and a log records every action.

| # | Task | Risk | Reasoning (reversibility · blast radius · audit) | Oversight | Why it fits | What happened |
|---|---|---|---|---|---|---|
| 1 | Part 2 single-session pet birth-date validation (`81d29fe`) | MEDIUM | Git can revert it, but it changes user-visible validation on both the create and update paths, so the audit needs tests covering both. | HOTL | Claude can edit and test on its own, but must stop at set triggers: a failing test, a change outside `owner/`, and before commit. | Not re-run in Part 4. It was **recalibrated during planning from LOW/auto mode to MEDIUM/HOTL**, before any trial, because it changes production behavior on two paths. |
| 2 | Part 2 writer–reviewer run of the same validation (`dbb1171`) | MEDIUM | It's the same production change with the same blast radius, and the reviewer's notes are part of the audit. | HOTL | A second agent reviewing doesn't replace the human stop points on a behavior change. | Not re-run in Part 4. The assignment is unchanged. |
| 3 | Part 3 validation-audit orchestration recipe (`00d5c9b`) | MEDIUM | It's mostly read-only and reversible, but the fan-out multiplies cost and output, and a per-action log is needed to know what ran. | Autonomous with audit | Approving each step across many subagents is impractical, so an isolated container plus an action log is the matching control. | Not re-run in Part 4. The assignment is unchanged. |
| 4 | PR-review GitHub Action `claude-review.yml` (PR #2) | HIGH | Public PR comments can't really be taken back, the runner holds a repository secret with `pull-requests: write`, and PR text is untrusted input, so every change needs reviewed PR history. | HITL | A wrong change is published at once and touches a runner that holds a credential. | Not re-run in Part 4. The assignment is unchanged. |
| 5 | CI `allowedTools` permission-matching fixes (PRs #5, #8, #9) | HIGH | Each change moves a security boundary, and silently loosening it widens what injected text can run in CI. The diff is the only audit. | HITL | A human has to read the exact pattern before it ships. | Not re-run in Part 4. The assignment is unchanged. |
| 6 | Temporarily enabling workflow diagnostics (`a6dbe4d`) | HIGH | Information exposed in public logs cannot be made secret again, even if the workflow run is later deleted, and the revert has to be verified. | HITL | Enabling and reverting both need a human decision. | Not re-run in Part 4. The assignment is unchanged. |
| 7 | Force-pushing to `main` | HIGH | It rewrites shared history for every clone and is hard to undo once others have pulled, so it needs an audit trail. | HITL + `PreToolUse` guard hook | A human decides, and the hook mechanically blocks the tested direct force-push forms before Bash execution, including the explicit bypass invocation. | Enforced by `.claude/hooks/block-force-push-main.sh`. In the isolated container test it blocked the force push both with normal permissions and when invoked with bypass permissions, and it allowed a normal push of a test branch (see `hook-test-2026-09.md`). On the host it also blocked a harmless Bash call that only contained the force-push text as a string argument, which is too strict at the edges. It doesn't see commands hidden behind `eval`, variables, Git aliases or script files. Its supported boundary is the tested visible, direct `git push` forms; other unhandled forms are listed in `hook-test-2026-09.md` §8, and a production guarantee still requires server-side branch protection or a pre-receive rule. |
| 8 | **Trial A:** write the guard hook and `settings.json`, then build and run the container test | HIGH | Git can revert it, but a buggy guard fails *open* for every future session in the repository, and the test output is the audit. | HITL (intended) | Changing Claude's own guardrails should get per-action human approval. | **Oversight execution miss (not the risk-level miss): it's not claimed as HITL.** The risk rating held. The user didn't confirm being prompted to approve every Phase 1 Write and Bash action, and a session notice reported auto mode during Phase 1. The container runs were started by the user and reviewed step by step. The explicit HITL step is moved to task 10. **A second miss: the hook was installed in the wrong order** (see the calibration notes below). |
| 9 | **Trial B:** write these two reports and run read-only verification | LOW | Markdown and read-only commands can be reverted with Git, have no runtime effect, and the diff is enough audit. | Auto mode | Working-directory edits and reads are allowed, and the classifier reviews only the Bash checks. | Ran in auto mode. See "Trial B: auto-mode decisions" below. |
| 10 | Final human review and staging of the four deliverables | HIGH | Staging can be undone, but it lands a guardrail that governs every later session in this repository, so it needs human review. | HITL | The per-action human approval that Trial A couldn't show happens here. | Completed in manual approval mode, as attested by the user: the user reviewed and approved each final documentation correction and the exact staging command; the four intended files were staged together, and no other file was staged. |

## Calibration history

1. **Planning-time recalibration.** Task 1 moved from LOW/auto mode to MEDIUM/HOTL before any trial, because it changes user-visible validation on both the create and update paths.
2. **Phase 1 install order (fail open).**
   - `.claude/settings.json` registered the hook before the hook file was executable.
   - The next Bash action reported a hook-launch `Permission denied`.
   - Claude Code treated that launch failure as non-blocking and ran the command.
   - Once the hook was made executable, it passed all 42 direct-input matrix cases.

   This was **not** a successful block. It shows that a broken hook installation fails open. The correct order is: write the hook, make it executable, validate it, then register it in settings.
3. **Trial A oversight.** The intended HITL level isn't verified (task 8). The HITL step is now task 10.
4. **Container attempt 1.**
   - The token scan found zero occurrences.
   - Preflight stopped before any Claude call, because the protected source-copy directories couldn't be traversed by the non-root user.
   - The harness failed closed.
5. **Container attempt 2.**
   - T1 and T2 showed the hook blocking successfully.
   - R11 failed because the original runner required the init event to report `bypassPermissions`, but it reported `auto`. Attempt 3 redefined R11 to use the actual CLI invocation, the exact tool call, the matching hook reason, hook exit 2 and unchanged main.
   - In T3 the hook returned exit 0, but noninteractive `manual` mode then denied the allowed push, because it has no approval surface. This was too strict for an unattended run.
   - The hook-event parser also expected the script filename, which successful hook events don't contain.
6. **Container attempt 3.** The token scan found zero occurrences, all of R01–R21 and H1–H3 passed, and the overall result was **PASS**.

**PDF risk-level calibration miss:** task 1. It was initially rated LOW and was re-rated MEDIUM, because it changes production validation on two paths (create and update). The re-rating happened during planning, before the trials. The trials didn't put any other task at a different risk level.

**Oversight execution miss:** task 8. It was intended as HITL, but per-action approval wasn't verified. This concerns the oversight actually exercised, not the risk rating, which stays HIGH. The required HITL trial is task 10.

## Trial B: auto-mode decisions

Allowed means the action ran without a prompt. Held means the auto-mode classifier stopped it.

| Action | Tool | Auto mode |
|---|---|---|
| Check the token-scan marker, then save the attempt-3 output and marker read-only | Bash | Allowed |
| Read the attempt-3 output, source hashes and image ID | Read | Allowed (read) |
| Write `task-risk-2026-09.md` and `hook-test-2026-09.md` | Write | Allowed (working-directory edit) |
| `bash -n` on the hook, JSON validation, direct hook matrix, executable-mode check | Bash | Allowed |
| Trailing-whitespace, carriage-return, final-newline checks; `git diff --check`; `git status` | Bash | Allowed |
| Rerun of the matrix and whitespace checks under `bash` (the first batch ran in zsh, which didn't word-split the file list, so those checks didn't really run) | Bash | Allowed |
| Remove one trailing space the check found in `hook-test-2026-09.md`, and record this row | Edit | Allowed (working-directory edit) |
| Final re-check of all four files and `git status --short` | Bash | Allowed |

No Trial B action was held by the auto-mode classifier.

One earlier Bash call in this session was blocked by the **guard hook**, not by auto mode. It was a read-only parser replay whose command text contained the force-push string. That's the host false positive noted in task 7.

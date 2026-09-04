---
name: review-changes
description: Review the current staged Git changes against Spring PetClinic conventions using the read-only reviewer agent.
disable-model-invocation: true
context: fork
agent: reviewer
allowed-tools: Bash(git diff --cached)
---

## Staged diff

!`git diff --cached`

## Review task

Review only the staged diff shown above.

Check it against `CLAUDE.md` and nearby repository code when evidence is needed. Look for correctness problems, regressions, missing tests, security issues, and violations of project conventions.

Report actionable findings with severity, file, line, evidence, and impact. Do not edit, create, delete, stage, or commit anything.

If the staged diff is empty, respond exactly:

`No staged changes to review.`

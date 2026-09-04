# Extension Stack - 2026-09

## Goal

This stack combines four Claude Code extension points so that a staged-diff review
is prompted automatically, performed deliberately, and reported in a fixed shape:

- A **read-only reviewer agent** (`reviewer`) that can inspect the working tree but
  cannot modify it, because its tool set excludes every write and shell tool.
- A **manually invoked review skill** (`/review-changes`) that owns the review
  workflow and is reserved for explicit user invocation, so a review never fires as
  a side effect of some unrelated request.
- A **SessionStart reminder hook** that surfaces the review step at the start of a
  session, before any commit has been attempted.
- A **session-wide output style** (Focused Review) that constrains the shape of the
  response for the whole session rather than for a single prompt.

The parts are deliberately split by concern: the hook reminds, the skill decides, the
agent reads, and the output style formats. No single artifact holds both the trigger
and the write permission.

## Artifacts

| Artifact | Purpose | Scope | Model / tools | Why permissions are minimal |
| --- | --- | --- | --- | --- |
| `.claude/agents/reviewer.md` | Review staged changes against Spring PetClinic conventions; used when the review skill delegates a staged-diff review. | Project subagent, invoked by the review skill rather than by the user directly. Its own instructions restrict it to the staged diff supplied by the calling skill, not unstaged work, other branches, or the repository at large. | model: `haiku`; tools: `Read`, `Grep`, `Glob`; maxTurns: `8`. | The tool set is read-only. With no `Edit`, `Write`, or `Bash`, the reviewer can quote and cite code but cannot alter the tree, stage or unstage files, or run the build. `maxTurns: 8` bounds how long a review can run. The agent file also states the constraint in prose: never edit, create, delete, stage, or commit files. A review that cannot write cannot silently "fix" what it was asked to judge. |
| `.claude/skills/review-changes/SKILL.md` | Defines the staged-diff review workflow invoked as `/review-changes`, and hands the diff to the read-only reviewer agent. | Project skill, available session-wide but entered only on explicit user invocation. | `disable-model-invocation: true`; `context: fork`; `agent: reviewer`; `allowed-tools: Bash(git diff --cached)`. | Three independent limits. `disable-model-invocation: true` means the model cannot trigger the review on its own initiative. `allowed-tools` grants exactly one command, `git diff --cached`, which reads the index and changes nothing. `agent: reviewer` routes the actual review to the read-only agent, so the skill never needs write access itself. |
| `.claude/settings.json` | Project settings; carries the `SessionStart` hook that emits the pre-commit reminder. | Project scope, applies to every session opened in this repository. | Hook type `command`, matcher `startup|resume`. | The registered command is a single `echo` of the reminder string. It does not stage, commit, or modify files, so a misfire costs a line of output and nothing more. |
| `.claude/output-styles/focused-review.md` | Constrains responses to an evidence-first review shape: `Verdict:`, `Evidence:`, optional `Risks:`, `Next action:`. | Session-wide once selected, with `keep-coding-instructions: true` so normal coding behavior is retained. Deactivated later in this session by setting the output style back to `default`. | Not applicable. An output style carries no tools and no model of its own. | An output style only shapes the role, tone, and response format. Its own text states that it does not change how code is implemented or verified, and it grants no tool access, so it cannot act on the repository. |

## SessionStart hook evidence

The hook fired at the start of this session and emitted exactly:

`Reminder: Before committing, stage your changes and run /review-changes.`

It was reported in-session as `SessionStart:startup hook success`.

The user directly observed the following registration through `/hooks`:

- Event: SessionStart
- Matcher: startup|resume
- Source: Project
- Type: command

Read together, these mean the reminder is registered by the project, not by user-level
settings, so it travels with the repository; it is a `command` hook whose observable
effect is the reminder text above; and the `startup|resume` matcher makes it fire both
on a fresh start and when an existing session is resumed, which are the two moments at
which a session might head toward a commit without having reviewed.

## Staged change before review

```text
A  .claude/agents/reviewer.md
A  .claude/output-styles/focused-review.md
A  .claude/settings.json
A  .claude/skills/review-changes/SKILL.md
```

All four entries are `A` (added): the stack's own files, staged and not yet committed.

## Explicit invocation control

Acting on the SessionStart reminder, Claude attempted to invoke the review skill
automatically. The attempt was refused:

`Skill review-changes cannot be used with Skill tool due to disable-model-invocation.`

The refusal also directed that the skill's workflow must not be reproduced by other
means, so no substitute review was improvised. Claude reported the four staged files
and stopped, leaving the decision to the user.

The user then manually entered `/review-changes`, and the review ran.

This sequence is the point of `disable-model-invocation: true`. The hook is free to
remind on every startup and resume without that reminder becoming an automatic trigger.
A model that had run the review itself would also be the party deciding whether the
result was good enough to commit on; here the reminder and the decision to review sit
with different parties, and the review stays under explicit human control.

## Manual review execution

The manually invoked `/review-changes` delegated to the `reviewer` agent, as configured
by `agent: reviewer` in the skill's frontmatter, and ran in an isolated fork
(`context: fork`).

Review conclusion:

`No actionable findings.`

Observed review summary:

- Four staged extension files were reviewed.
- YAML frontmatter was valid.
- JSON structure was valid.
- Agent and skill definitions were coherent.
- Java-specific conventions were correctly treated as irrelevant to `.claude`
  metadata. The reviewer noted that the checkstyle, spring-javaformat, tab-indentation,
  package-layout, and `*Tests` conventions in `CLAUDE.md` govern the Java sources, not
  configuration files in `.claude/`.
- Runtime execution of the hook was outside the reviewer agent's review scope, which is
  limited to the staged diff, and was verified separately: the hook fired at session
  start and printed its reminder, reported as `SessionStart:startup hook success`.

## Proof that the reviewer made no edits

Git status before the review:

```text
A  .claude/agents/reviewer.md
A  .claude/output-styles/focused-review.md
A  .claude/settings.json
A  .claude/skills/review-changes/SKILL.md
```

Git status after the review:

```text
A  .claude/agents/reviewer.md
A  .claude/output-styles/focused-review.md
A  .claude/settings.json
A  .claude/skills/review-changes/SKILL.md
```

The two statuses are identical. No file was added, removed, modified, staged, or
unstaged by the review. Nothing moved from staged to unstaged, which is what a stray
edit to an already-staged file would have produced.

This is the expected result of the permission design rather than a lucky outcome. The
reviewer agent holds only `Read`, `Grep`, and `Glob`; the skill that calls it holds only
`Bash(git diff --cached)`. Neither is able to write, so the unchanged status is enforced
by configuration and not merely by the agent's compliance with its instructions.

## Output style verification

The project output style `Focused Review` was selected and used for this task:

`Analyze OwnerController.showOwner and identify its most important maintenance risk. Do not edit any files.`

The response visibly followed the style:

- It started with `Verdict:`.
- It provided concrete repository evidence.
- It included a `Risks:` section.
- It ended with one `Next action:`.
- The tone was concise, neutral, and evidence-first.

After the task, the output style was switched back to the default. Verification:

```text
$ jq -r '.outputStyle // "not set"' .claude/settings.local.json
Default

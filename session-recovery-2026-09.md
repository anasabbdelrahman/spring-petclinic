# Session Recovery Record — September 2026

Branch: `homework/long-session`. Task: fix the `hideMessages()` `TypeError` on the owner
details page (`src/main/resources/templates/owners/ownerDetails.html`).

This file records what survived two deliberate context resets and whether the recovery
artifacts were sufficient to resume correctly without re-explaining the task.

- **Part 1 — compaction recovery** (Steps 16–18): `/compact`, then a bare `Continue.`
- **Part 2 — fresh-session recovery** (Steps 20–21): a new session given only the Handoff.
  **Attempt 1 is recorded but invalid** — the Handoff was not the first user message, so
  handoff delivery was never tested. **Attempt 2 is valid and passed.**

---

## Part 1 — Compaction recovery

### Setup

Before compacting, a `SessionStart` hook with matcher `compact` was added to
`.claude/settings.json` (Step 13), alongside — not replacing — the existing
`startup|resume` entry:

```json
{
  "matcher": "compact",
  "hooks": [
    {
      "type": "command",
      "command": "cat \"$CLAUDE_PROJECT_DIR/task-checklist.md\""
    }
  ]
}
```

The intent: after any compaction, the task tracking file is printed back into the fresh
context, so the session can re-orient from the checklist rather than from memory.

### What the hook actually did

The hook fired and succeeded. Its stdout was the complete checklist: **40,468 bytes
captured vs. 40,469 bytes on disk**, the two differing only in the trailing LF that stdout
capture trimmed (`diff` reports exactly one line, `\ No newline at end of file`). All 728
lines were printed verbatim.

What reached the model's context was smaller. The harness judged the output too large to
inline (reported as 39.3KB), persisted it to
`~/.claude/projects/.../tool-results/hook-0d24e88c-…-stdout.txt`, and injected only a
**~2KB preview**. The preview contained:

- the title,
- the **Task Goal**, including the four-artifact Done condition,
- the **Working Rule** ("Before every task step, read `task-checklist.md`"),
- the first nine lines of **Current Status**, truncated mid-sentence at
  `sourced from project …`.

Not injected: the 23-item **Steps** list, **Decisions Made**, the **Evidence Log**, and the
**Handoff**. A separate system note stated that `task-checklist.md` "was read before the
last conversation was summarized, but the contents are too large to include."

### The exchange

**User:**

```
Continue.
```

**Assistant (first actions, before any prose):**

```
grep -n '^- \[' task-checklist.md
sed -n '36,60p' task-checklist.md   # Current Status + section map
sed -n '109,128p' task-checklist.md # the unchecked steps, in full
```

From that: Steps 1–15 checked, Step 16 (`/compact`) and Step 17 (post-compaction
continuation) satisfied by the compaction that had just happened, and **Step 18 — create
this file — identified as the next unfinished work.** No reminder to read the checklist was
given, and none was needed; the Working Rule arrived in the reinjected preview.

The response then proceeded directly to Step 18 rather than asking what to do next, since
Step 17's own text is "identify the correct next unfinished step … **and proceed**."

### Evaluation

**Verdict: the recovery succeeded, but the checklist reinjection was not the sole reason,
and the honest reading is narrower than "the hook saved the session."**

Three overlapping sources carried state into the fresh context:

1. **The compaction summary itself.** Claude Code's own summary already contained the step
   numbering, the standing constraints, the verification results, and an explicit "next
   step" note. This alone would likely have been enough.
2. **The 2KB hook preview.** Contributed the goal, the four-artifact Done condition, and —
   most usefully — the Working Rule, which is what turns a bare `Continue.` into "go read
   the file" instead of "guess."
3. **The file on disk.** The authoritative source. Everything actually used to pick the next
   step (checkbox states, step text, Current Status) was read from disk after recovery, not
   from the injected text.

So the hook's real contribution was **the pointer, not the payload**. It re-asserted, at the
top of an empty context, that a tracking file exists, that it is authoritative, and that it
must be read before acting. That is a modest but genuine benefit, and it is the part that
would still work if the compaction summary had been worse.

**What was lost across the compaction:**

- All raw tool output — Gradle console text, the Playwright `browser_evaluate` JSON, `jq`
  and `xxd` output. Only the conclusions recorded in the Evidence Log survived, which is
  precisely why they were recorded there rather than left in the transcript.
- The exact wording of earlier assistant prose.
- All 24 Playwright MCP tool schemas, which returned to deferred state and would need
  `ToolSearch` again before any further browser work.

**What the exercise revealed about the hook's design:**

- `cat`-ing a 40KB file into a fresh context is at the edge of what the harness will inline.
  The output was truncated to a preview, so the hook cannot be relied on to deliver the
  whole file.
- It worked only because of **section ordering luck**: Task Goal, Working Rule, and Current
  Status happen to sit in the first 2KB. The truncation landed mid-`Current Status`, one
  sentence before the "Next action: Step N" line — so even the single most important fact
  was nearly lost.
- The robust shape would be either a leading one-line `Next action:` marker at the very top
  of the file, or a hook command that prints only the Current Status and Handoff sections
  (for example via `sed -n`) instead of the entire file. Reinjecting less would have
  reinjected more of what mattered.

---

## Part 2 — Fresh-session recovery

### Attempt 1 — invalid handoff-delivery test

**Status: INVALID for the homework requirement. Step 20 is unchecked and must be retried.**

The reason is delivery, not content: **the Handoff was never the first user message.** The
fresh session's first message was the branch name `homework/long-session` and nothing else,
and the Handoff section was pasted only later — after that session had already read the
checklist from disk and worked forward through Steps 20, 21 and 23 as far as Step 22. A
handoff-only continuation was therefore never exercised.

**Recovery did succeed — but from the branch-name prompt plus automatic Git-status context,
not from a pasted Handoff.** The decisive input was the harness's own environment context,
which listed `?? session-recovery-2026-09.md` and `?? task-checklist.md` by name; two
untracked Markdown files with those names on a branch called `homework/long-session` are
self-evidently continuity notes, and everything followed from opening them. Auto-loaded
`CLAUDE.md` supplied the repo, stack and build commands. The Handoff was **retrieved by** the
session, never **given to** it.

Everything recorded below is accurate about Attempt 1 and is preserved on that basis — none of
it has been deleted or rewritten to look like a pass. What it does not do is demonstrate what
Step 20 asks for.

#### Setup, and how it differed from the plan

Step 20 called for closing the session and starting a new one, pasting the **Handoff**
section of `task-checklist.md` as the only user-provided context. The Handoff had been
rewritten at Step 19 to stand alone: bug and fix, the four permitted artifacts, the exact Git
state, the next unfinished step, the remaining steps, file paths, both verification results,
the hook configuration, the `/compact` episode, and the constraints — including an explicit
"do not ask the user to explain the previous session."

**That is not what happened.** The fresh session was given four words and nothing else. So
the experiment that ran was harsher than the one designed, and it tests a different thing:
not "is this brief sufficient?" but "can the session find the brief at all?"

#### The exchange

**User (the entire message):**

```
homework/long-session
```

No verb, no task, no file reference — the branch name alone.

**Assistant (first actions, before any prose):**

```
Read  task-checklist.md              ⟵ both in one parallel tool block
Read  session-recovery-2026-09.md
Bash  git status --porcelain; git diff --stat; wc -l -c <both artifacts>
```

Then, in its first words to the user: "I'm the fresh session for Step 20; the next unfinished
work is Steps 20→23" — and it proceeded. It did not ask what to do, did not ask for an
explanation of the previous session, and did not re-read any application file.

The Git state matched the Handoff's prediction exactly: the same four `git status` entries,
and `15 insertions(+), 3 deletions(-)` across `.claude/settings.json` and
`ownerDetails.html`. Nothing staged, nothing committed.

#### What actually carried the recovery

Three automatic context sources, **none of them the Handoff and none of them the compact
hook**:

1. **The harness's git status snapshot**, included in the session's environment context. It
   listed `?? session-recovery-2026-09.md` and `?? task-checklist.md` by name. This is the
   load-bearing source: two untracked Markdown files with those names, on a branch called
   `homework/long-session`, are unmistakably session-continuity notes. Everything else
   followed from opening them. Without it, the bare branch name is unresolvable.
2. **`CLAUDE.md`**, auto-loaded as project instructions — the repo, the stack, the build
   commands, the `.editorconfig` rules, with no reading required.
3. **The auto-memory index line** (`verification-claims-need-in-repo-evidence`), which
   restates "use Gradle when asked" — consistent with the recorded Gradle-not-Maven decision,
   though it was not needed to identify the next step.

#### Evaluation

**Verdict: recovery succeeded from a four-word prompt, and the Handoff's content was
sufficient — but its delivery was never tested, and the credit for the recovery belongs to
the harness, not to either recovery artifact.**

On **content**: judged by what the fresh session actually needed, the Handoff was enough.
Read from disk, it supplied the bug, the fix and why both guards are load-bearing, the
four-artifact constraint, the exact Git state, the next unfinished step, the remaining steps
in order, and the verification status. Nothing had to be re-derived and no application file
was opened. The Working Rule and the checkbox list gave the sequencing.

On **delivery**: untested. The Handoff was retrieved by the session rather than given to it,
so whether pasting it into an empty session would have worked remains an open question. The
strong hint is that it would: a session that can act correctly on four words plus a file it
found itself has a much easier job with 260 lines of structured brief.

**What was missing from the Handoff.** Two real gaps, both about findability rather than
content:

- **It does not make itself discoverable.** The branch and working directory appear in the
  first paragraph of *Task goal and the exact bug*, but nothing in the Handoff says "these
  files exist and are authoritative" in a form that would survive as a lead. Recovery here
  worked because the *harness* named the files, not because the artifact did.
- **It is too large to reinject, and duplicative.** The Handoff restates much of the Evidence
  Log verbatim. `task-checklist.md` has grown from 728 lines / 40,469 bytes at the `/compact`
  episode to 969 lines / 55,090 bytes — well past the ~2KB the harness will inline from a
  hook. Part 1's design finding therefore applies with more force after Step 20, not less.

**What was lost across this boundary.** Everything not written to one of the four artifacts:
the whole prior transcript, all raw tool output, and again all 24 Playwright MCP tool
schemas, which returned to deferred state. Because the fresh session started from disk rather
than from a compaction summary, the Evidence Log was the *only* surviving record of the
Gradle and Playwright results — Part 1 still had Claude Code's summary as a second copy. That
makes the case for writing conclusions into a file stronger here than in Part 1: this time
there was no backup.

### Attempt 2 — valid handoff-delivery test

**Status: VALID, and it passed. Step 20 is complete.** The `## Handoff` section of
`task-checklist.md` was pasted verbatim as the **first and only user message** of a brand-new
session — the condition Attempt 1 failed — with no other explanation, no pointer to the repo,
and no follow-up context before the session acted.

#### The exchange

**User (the entire message):** the `## Handoff` section of `task-checklist.md`, pasted
verbatim — 128 lines, opening `**This section is your only context. Everything you need is
here.` and closing `and silently deleted 178 lines of this Handoff.` It is not re-quoted in
full here on purpose: it already exists verbatim in `task-checklist.md`, and a third copy would
be a third place to keep true. The two boundary lines are enough to identify it.

**Assistant.** One sentence of prose came before the first tool call — “I'm Attempt 2 — this
handoff was the first and only message in a fresh session. Starting where it says to: reading
the tracker before doing anything.” — then:

```
Read  task-checklist.md              ⟵ both in one parallel tool block
Read  session-recovery-2026-09.md
Bash  git rev-parse --abbrev-ref HEAD; git status --porcelain;
      git diff --cached --name-only; git diff --stat; wc -l -c <both artifacts>
```

It asked nothing, requested no explanation of the previous session, and opened no application
file — it took the fix as done on the Handoff's word, which is what the Handoff instructs.
**Step 21 was identified as the next work** from the Handoff's own ordering and then confirmed
against the checkbox list in the tracker.

The recorded Git state was accurate to the byte: branch `homework/long-session`, exactly the
four expected `git status --porcelain` entries, `git diff --cached --name-only` returning
**zero** lines (confirming Attempt 1's staging really had been undone), and `git diff --stat`
reporting `15 insertions(+), 3 deletions(-)` across `.claude/settings.json` (+9) and
`ownerDetails.html` (+6 / −3). Artifact sizes at that moment: `task-checklist.md` 1,036 lines /
59,924 bytes; this file 288 lines / 14,736 bytes.

Constraints respected: nothing staged, nothing committed, no application file touched, no
auto-memory change.

#### The caveat that cannot be engineered away

“The Handoff and nothing else” is not achievable inside Claude Code, and Attempt 2 did not
achieve it. The session still received automatically: the environment context with a **git
status snapshot naming all four artifacts**, auto-loaded `CLAUDE.md`, the two-line auto-memory
index, and the `SessionStart` / `startup|resume` hook reminder.

What changed versus Attempt 1 is not the *presence* of that context but its *role*. In
Attempt 1 the git snapshot was load-bearing: from a four-word prompt it was the only thing that
made two untracked Markdown files findable. In Attempt 2 the Handoff had already supplied the
working directory, the branch, both filenames, the next step, the constraints and the fix — so
the snapshot only *confirmed* facts already given rather than *supplying* them. That is the
strongest form this experiment can take short of a harness with no environment context.

A second, smaller caveat: validity rests partly on the receiving session's own attestation that
the Handoff arrived first. The user can check that against the transcript, but the artifact
cannot enforce it — which is precisely how Attempt 1 came to be mislabelled a pass.

#### Evaluation

**Verdict: the Handoff alone was sufficient to resume, and this time its delivery — not just
its content — was what got tested.**

Everything needed to act arrived in the paste: the bug and the fix, why *both* guards are
load-bearing, the optional-chaining trap pre-empted before it could be “simplified” away, the
four permitted artifacts, the exact Git state including *unstaged*, the next unfinished step,
the remaining steps in order, the `/review-changes` restriction and why the `reviewer` agent is
not a substitute, and the warning about heading-anchored edits in these large Markdown files.
That last item earned its place immediately: it changed how both files were edited in this
session — line-number anchors with asserted context lines, never a heading match — so the
178-line accident it describes did not recur.

**What was missing, or cost something:**

- **Self-containment did not save the big read.** The brief is self-sufficient, but its own
  Working Rule mandates reading `task-checklist.md` before every step, and that file is now
  1,036 lines / 59,924 bytes — roughly an order of magnitude more than the 128-line Handoff.
  The paste's economy is notional: complying with it immediately spends what it saved.
- **No liveness facts.** Nothing says whether the app is still up on `:8080` or whether the
  Playwright MCP schemas are deferred (they are). Irrelevant for Steps 21–22, which need no
  browser — but a handoff landing on a verification step would have to re-derive both.
- **Duplication persists.** The fix, the verification numbers and the Git state live in both
  the Handoff and the Evidence Log, so each future edit has two places to keep true.
- **It cannot state its own validity.** See the attestation caveat above.

None of these blocked the resume. They are the difference between “sufficient” and “efficient”.

---

## Combined finding

Three context resets, three different answers about where recovery actually came from — and
they compose into one rule rather than contradicting each other.

| Reset | Pointer came from | Payload came from |
| --- | --- | --- |
| Part 1 — `/compact` | the compact hook's ~2KB preview (the Working Rule) | the file on disk |
| Part 2, Attempt 1 — four-word prompt | the harness's git-status snapshot | the file on disk |
| Part 2, Attempt 2 — pasted Handoff | the paste itself | the paste, then the file on disk |

In Part 1 the hook gave **the pointer, not the payload** — 40KB was `cat`-ed and ~2KB was
inlined, and it worked on section-ordering luck. In Attempt 1 the harness gave the pointer and
the disk gave the payload; the recovery artifact was retrieved, never delivered. Attempt 2 is
the only case where one artifact carried **both**, and it did so in ~128 lines — because it
named the working directory, the branch, the authoritative files, the next step and the
constraints up front.

**The practical rule, unchanged but now evidenced from both directions: make recovery artifacts
findable, and keep the payload on disk.** A predictable self-describing filename in the repo
root; a one-line `Next action:` at the very top of the tracker; a hook that prints only Current
Status and Handoff (via `sed -n`) rather than `cat`-ing 60KB. Optimising an artifact to be
*injected* is the wrong target — it will be truncated. Optimising it to be *found and read* is
the right one, and a short paste-in brief is simply the most reliable way to hand over the
pointer when no one is there to name the files for you.

One caveat applies to all three resets: **what is not written into one of the four artifacts
does not survive.** Every boundary destroyed the transcript, all raw tool output (Gradle console
text, the Playwright `browser_evaluate` JSON, `jq` and `xxd` output) and all 24 Playwright MCP
tool schemas. Part 1 at least had Claude Code's compaction summary as a second copy; Part 2's
attempts had no backup at all, so the Evidence Log was the sole surviving record of the Gradle
and Playwright results. That is the argument for writing conclusions to a file as each step
closes, rather than trusting a transcript to still be there.

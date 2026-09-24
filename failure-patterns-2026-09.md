# Failure patterns - Week 3 Part 8, 2026-09-24

## Step 1 - Recognition

Classified in the exercise's vocabulary: one dominant pattern per scenario.

| # | Scenario | Dominant pattern | Justification |
|---|---|---|---|
| 1 | The same non-working fix is suggested repeatedly | **Pattern mismatch** | The model keeps applying a familiar solution template that does not fit this problem, and repeating it adds no new information about the actual cause. |
| 2 | The model forgets which file was being worked on | **Context pollution** | Accumulated, partly irrelevant session content crowds out the working state, so an early, important fact (the target file) is lost. |
| 3 | The wrong feature is built despite clear instructions | **Prompt ambiguity** | The instructions read as clear to their author but admitted another plausible reading, and the model built that reading. |
| 4 | A confident reference to a nonexistent `Array.dedupe()` | **Knowledge gap** | The model lacks accurate knowledge of the API surface and fills the gap with a plausible-sounding method that does not exist. |

### Step 1 evaluation - recorded honestly

The prior session's first answers were *anchoring / context pollution*, *context loss*,
*specification drift* and *hallucination*. None of the four initial answers matched the required
dominant category for its scenario. `Context pollution` was part of the exercise vocabulary, but
it was assigned to the wrong scenario. The session answered from a general taxonomy instead of
the exercise's own. This
was a classification error on one question, not long-session degradation. The table above is the
corrected answer.

## Step 2 - Diagnosis of the prior long session

**Verdict: no clear length-related degradation observed.** The Step 1 miss is a separate error
(see above) and has no length-related cause. The evidence is sorted into four categories below,
which are kept apart. No failure is claimed without evidence.

### Incorrect work - all caught by the agent before review

- The guard hook was first indented with tabs, against `.editorconfig` (`[*] indent_style =
  space`), and was then converted to spaces.
- One of the agent's own heredocs contained a force-push string, and the new hook blocked it. The
  agent had planned to avoid exactly this and then did not. No harm was done, and the block is
  recorded as evidence that the hook works.
- The first evidence file had a trailing space.
- One static-check run failed on a zsh word-splitting error and was re-run under bash.

### Unverified claims - still open

- The `:*` prefix syntax is supported by current Anthropic documentation, which was consulted.
- Runtime permission matching was never tested. `jq empty` proves only that the JSON is valid.
- Whether `:*` alone also covers the bare command remains unverified, which is why exact bare
  twin rules exist.
- The precedence deny > ask > allow across scopes was asserted, not tested.
- Git option-abbreviation behaviour (`--force-w`, `--mir`) and the semantics of
  `clean.requireForce=false` are asserted in `agent-controls-2026-09.md` without a run in a
  scratch repository.
- Detect-secrets: terminal output supplied by the user showed detect-secrets passing during the
  commit `Add layered agent controls and git guard hook`. Git history alone cannot reproduce that
  hook output independently.

### Reviewer limitations

`/review-changes` sees only the staged diff.

- Run 1 saw 2 of the 4 files and did not check `.editorconfig`.
- Run 2 saw only the documentation, made zero tool calls, and raised a false "High" caused by
  partial staging.
- Run 3 saw all 4 files, made 7 tool calls, and had no findings.

None of the three runs tested behaviour.

### Ordinary iteration - not failures

- The user corrected the permission syntax to `Bash(x:*)`.
- Files were staged and restaged several times.
- The recorded date moved from 2026-09-23 to 2026-09-24.

### Outcome of the fresh session started from the Thread Fold handoff

- The fresh session resumed productively and needed no repository-state rediscovery. Every
  verification command in the handoff matched its recorded state.
- It nevertheless repeated the zsh word-splitting mistake documented in the handoff. A hygiene
  check expanded an unquoted variable holding three filenames, and zsh passed it as one argument,
  so the check never examined the files.
- It recognized immediately that the check was invalid and re-ran it under Bash, where it passed.
- Therefore Thread Fold preserved progress and constraints, but it did not prevent one known
  operational error from recurring.

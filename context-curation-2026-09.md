# Context Curation - Homework 1.9 Engineering Deliverable

Date: 2026-09-04
Repository: spring-petclinic
Branch: homework/context-curation

## 1. Experiment and engineering task

The engineering task given to both runs was identical:

> `GET /owners/1` renders, but the browser console logs
> `TypeError: Cannot read properties of null (reading 'style')` after `hideMessages()`
> runs. Find the exact file and the null dereference, explain the root cause, and
> propose the smallest safe fix plus a verification procedure.

This is a bounded code investigation: the symptom carries two precise identifiers - the
route `/owners/1` and the function name `hideMessages()` - and the answer lives in a small
number of known files.

The experiment varied only the context supplied to the model, not the task:

- **Run A (full context)** - broad, uncurated context: the whole set of root markdown
  documents, harness/config files, build files, plus the relevant template, controller,
  and test.
- **Run B (curated context)** - a deliberately chosen four-file subset, with the exact problem anchors placed at the start and the evaluation criteria placed at the end of the prompt.

Both runs produced a plan only. No production code was changed in either run; both
outputs are investigation-and-plan documents.

## 2. Objective six-check rubric

The same six checks were applied to both outputs. Each is binary - present and correct, or
not.

| # | Check | Pass condition |
|---|-------|----------------|
| 1 | Exact file and null dereference identified | Names `owners/ownerDetails.html` and the `getElementById(...).style` dereference returning `null` |
| 2 | Explains why Thymeleaf removes the elements | States that `th:if` is structural and strips the whole `<div>` when the model attribute is absent |
| 3 | Smallest safe fix covers both elements | Null-guards both `success-message` and `error-message` |
| 4 | No unnecessary backend changes or new dependencies | Template-only, vanilla JS, controller untouched |
| 5 | Focused Gradle verification command | Gives a `./gradlew test --tests "...OwnerControllerTests.showOwner"` invocation |
| 6 | Playwright regression check requiring zero console errors | Navigates `/owners/1`, waits past the 3s timeout, asserts empty `browser_console_messages` |

## 3. Run A: full-context input scope

Run A was given a wide, undifferentiated context set. Its own record of inspected context:

- `src/main/resources/templates/owners/ownerDetails.html` - the `th:if` message divs and
  the inline `hideMessages()` script.
- `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` -
  `showOwner()` for `GET /owners/{ownerId}`.
- `src/main/resources/templates/fragments/layout.html` - checked to confirm
  `hideMessages()` is *not* defined there.
- `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java` -
  the existing `showOwner()` test.
- Root markdown documents: `README.md`, `HARNESS.md`, `CLAUDE.md`,
  `architecture-2026-09.md`, `plan-owner-not-found-handling-2026-09.md`,
  `claude-md-notes-2026-09.md`, `mcp-notes-2026-09.md`, `planning-depth-2026-09.md`,
  `workflow-pattern-choices-2026-09.md`, `extension-stack-2026-09.md`.
- Harness and config: `.claude/settings.json`, `.mcp.json`, `.gitignore`.
- Build: `build.gradle` and `CLAUDE.md` for the Gradle test syntax.

Of these, only the template, the controller, the test, and `CLAUDE.md` contributed to any
rubric point. The ten root markdown documents, the layout fragment, and the three
harness/config files each resolved to a negative finding - "nothing relevant to this bug."

## 4. Run A: complete substantive output

### Fix: `hideMessages()` null dereference on GET /owners/{id}

**Context.** Playwright hits `GET /owners/1` and the browser console logs
`TypeError: Cannot read properties of null (reading 'style')`. This is a client-side JS
bug in the owner details page, not a backend defect. The output is an
implementation/verification plan only - no files were edited while producing it.

**Root cause.** File: `src/main/resources/templates/owners/ownerDetails.html`, lines 9-15
(message divs) and lines 80-91 (script).

- `<div th:if="${message}" ... id="success-message">` and
  `<div th:if="${error}" ... id="error-message">` are Thymeleaf conditional elements. When
  the `message`/`error` model attributes are absent, Thymeleaf removes the entire element
  from the rendered DOM - it does not render a hidden or empty div.
- On a plain `GET /owners/1` (not a post-redirect-get flow),
  `OwnerController.showOwner()` never adds `message` or `error` to the model, so both divs
  are absent from the HTML. Those attributes are only ever set via `RedirectAttributes` in
  `processCreationForm`/`processUpdateOwnerForm`, which only exist after a redirect.
- The unconditional inline script then runs `hideMessages()` on page load, which calls
  `document.getElementById("success-message").style.display = "none"`. `getElementById`
  returns `null`, so `.style` throws
  `TypeError: Cannot read properties of null (reading 'style')`.

**Minimal fix.** Guard each `getElementById` lookup with a null check before touching
`.style`, in the existing inline script - no backend, model, or controller changes
required:

```html
<script>
  // Function to hide the success and error messages after 3 seconds
  function hideMessages() {
    setTimeout(function () {
      var successMessage = document.getElementById("success-message");
      var errorMessage = document.getElementById("error-message");
      if (successMessage) {
        successMessage.style.display = "none";
      }
      if (errorMessage) {
        errorMessage.style.display = "none";
      }
    }, 3000);
  }

  hideMessages();
</script>
```

This is the smallest safe fix: it covers both message elements symmetrically, matches the
fact that Thymeleaf's conditional rendering is legitimate and intentional behavior rather
than a template bug, and touches only the one template file already responsible for this
script.

**Verification.**

1. Focused Gradle test for the owner details flow:
   `./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.showOwner"`
2. Playwright regression check: navigate to `/owners/1` (a plain GET with no flash
   attributes), wait for the 3-second `hideMessages()` timeout to fire, then assert
   `browser_console_messages` reports **no errors** - specifically no `TypeError`
   referencing `hideMessages` or `style`. Repeat for a post-redirect case (for example
   after editing an owner) to confirm the success-message path still hides correctly when
   the div *is* present.

**Risks and non-goals.**

- No backend, controller, or model changes - the missing `message`/`error` attributes on
  plain GETs are expected behavior, not the bug.
- No new dependencies or libraries; the fix is plain vanilla JS already in use in the file.
- Does not address the unrelated 404-handling work tracked in
  `plan-owner-not-found-handling-2026-09.md`.
- Does not change Thymeleaf conditional rendering semantics or add always-rendered hidden
  divs, since that would be a larger, unnecessary change.

**Rubric self-check.** All six checks reported as met: exact file and dereference at
`owners/ownerDetails.html:84-85`; `th:if` element removal explained; both elements
null-guarded; template-only vanilla JS; focused Gradle command given; Playwright
zero-console-error check described.

## 5. Run A context statistics

| Measure | Value |
|---------|-------|
| Rubric score | 6/6 |
| Total context | 56.8k tokens |
| Message context | 27.1k tokens |
| Response time | 63 seconds |

## 6. Run B: curated four-file subset and deliberate prompt-edge placement

Run B was given four files only:

1. `src/main/resources/templates/owners/ownerDetails.html` - the inline `<script>` at
   lines 80-91 defining `hideMessages()`, and the `th:if` banner divs at lines 9 and 13.
2. `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` -
   `showOwner()` at lines 169-177.
3. `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java` -
   the `showOwner()` test at line 244.
4. `CLAUDE.md` - Gradle test invocation syntax and the two-space HTML indentation rule.

Everything Run A had inspected and discarded was excluded up front: the ten root markdown
documents, `fragments/layout.html`, `.claude/settings.json`, `.mcp.json`, `.gitignore`, and
`build.gradle`.

**Deliberate placement.** The high-signal task information was placed at both edges of the prompt:

- **Start:** the exact route, console error, function name, and planning task.
- **End:** the six objective checks and the instruction to use only the four selected files.
- **Middle:** the four targeted file paths and required response structure.

This kept the problem anchors and success criteria outside the lower-attention middle. The files themselves were retrieved as needed; they were not claimed to occupy fixed token positions.

## 7. Run B: complete substantive output

### Fix: TypeError on GET /owners/1 (`Cannot read properties of null (reading 'style')`)

**Context inspected.**

- `owners/ownerDetails.html` - inline `<script>` (lines 80-91) defines `hideMessages()`,
  which unconditionally does
  `document.getElementById("success-message").style.display = "none"` and the same for
  `"error-message"`.
- `OwnerController.java` - `showOwner()` (lines 169-177) builds the `ModelAndView` for
  `owners/ownerDetails` and only adds the `owner` object. It never sets `message` or
  `error` model attributes; those are only ever populated as flash attributes by
  `processCreationForm`/`processUpdateOwnerForm` on redirect.
- `OwnerControllerTests.java` - the `showOwner()` test (line 244) hits
  `GET /owners/{ownerId}` directly with no prior POST or redirect, so no flash
  `message`/`error` is present - matching the reported reproduction.
- `CLAUDE.md` - confirms Gradle test invocation syntax and that HTML uses two-space
  indentation.

**Root cause.** In `ownerDetails.html`, the success and error banners are wrapped in
`th:if="${message}"` / `th:if="${error}"` (lines 9 and 13). Thymeleaf evaluates these as
**structural attributes**: when the condition is falsy - as it always is on a direct
`GET /owners/{id}`, since the attribute is absent from the model - Thymeleaf removes the
entire `<div>` from the rendered DOM. It is not hidden via CSS; it simply does not exist in
the HTML sent to the browser.

The page script then unconditionally calls `document.getElementById("success-message")` and
`document.getElementById("error-message")`. On a direct visit with no flash message set,
both lookups return `null`, and `null.style.display = "none"` throws
`TypeError: Cannot read properties of null (reading 'style')`. This matches the observed
error occurring after `hideMessages()` runs.

**Minimal fix.** Add null guards in the existing inline script in `ownerDetails.html`, for
both elements, without touching the Thymeleaf conditionals or any backend/model code:

```js
function hideMessages() {
  setTimeout(function () {
    var successMessage = document.getElementById("success-message");
    if (successMessage) {
      successMessage.style.display = "none";
    }
    var errorMessage = document.getElementById("error-message");
    if (errorMessage) {
      errorMessage.style.display = "none";
    }
  }, 3000);
}

hideMessages();
```

This is a template-only, client-side change: no controller, model, repository, or dependency
changes. It preserves current behavior when a flash message *is* present - the elements
exist, so they are still hidden after 3s - and simply no-ops when they are not.

**Verification.**

- Focused Gradle test, using existing coverage for this view; no new test is needed since
  server-side behavior is unchanged:
  `./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.showOwner"`
- Full controller test class as a sanity check:
  `./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"`
- Playwright regression check: navigate to `/owners/1` (direct GET, no prior form
  submission), wait more than 3s for the `setTimeout` in `hideMessages()` to fire, and
  assert `browser_console_messages` reports **zero console errors**. Optionally also submit
  the edit form to redirect back to `/owners/1` with a flash `message` and confirm the
  success banner still appears and is still hidden after 3s - a regression check that the
  fix did not break the existing hide behavior.

**Risks and non-goals.**

- No backend changes: `OwnerController.showOwner()` and the `message`/`error`
  model-population flow are untouched. This is intentionally not "fix by always adding
  empty message/error attributes," which would be a broader, unnecessary backend change.
- No new dependencies or libraries; pure vanilla JS null checks.
- Non-goal: refactoring the message-banner pattern, for example extracting a fragment -
  out of scope for this minimal fix.
- Non-goal: changing the 3-second auto-hide timing or the UX behavior.

**Rubric self-check.** All six checks reported as met, each mapped to the section that
satisfies it: checks 1-2 to Root cause, check 3 to Minimal fix, check 4 to Risks and
non-goals, checks 5-6 to Verification.

## 8. Run B context statistics

| Measure | Value |
|---------|-------|
| Rubric score | 6/6 |
| Total context | 54.3k tokens |
| Message context | 24.0k tokens |
| Response time | 37 seconds |

## 9. Side-by-side scoring table

| Rubric check | Run A (full) | Run B (curated) |
|--------------|--------------|-----------------|
| 1. Exact file and null dereference | Pass - `ownerDetails.html:84-85` | Pass - `ownerDetails.html:84-85` |
| 2. Why Thymeleaf removes the elements | Pass - `th:if` removes the element | Pass - `th:if` is structural, strips the div |
| 3. Smallest safe fix covers both elements | Pass - both guarded | Pass - both guarded |
| 4. No unnecessary backend changes or deps | Pass - template-only | Pass - template-only |
| 5. Focused Gradle verification command | Pass | Pass - plus full-class sanity run |
| 6. Playwright zero-console-error check | Pass | Pass |
| **Score** | **6/6** | **6/6** |

| Measure | Run A (full) | Run B (curated) | Difference |
|---------|--------------|-----------------|------------|
| Rubric score | 6/6 | 6/6 | tie |
| Total context | 56.8k tokens | 54.3k tokens | 2.5k fewer, approximately 4.4% |
| Message context | 27.1k tokens | 24.0k tokens | 3.1k fewer, approximately 11.4% |
| Response time | 63 seconds | 37 seconds | 26 seconds faster, approximately 41.3% |

## 10. Evaluation and cohort finding

The curated run was better overall because it matched the full run's 6/6 correctness score
while using less message context and completing faster. The broad context added
corroboration and duplicated explanation, but no rubric points.

Concretely, the extra context in Run A bought only negative findings. Reading
`fragments/layout.html` confirmed where `hideMessages()` is *not* defined. Reading ten root
markdown documents confirmed the bug is undocumented and that
`plan-owner-not-found-handling-2026-09.md` describes a different issue. Reading
`.claude/settings.json`, `.mcp.json`, and `.gitignore` confirmed the Playwright MCP wiring
and that `.playwright-mcp/` is ignored - "nothing relevant to this bug." Each of these is a
real token cost against zero rubric benefit. Run A also restated the same root cause across
its Context, Root cause, and Risks sections, which is where much of the extra message
context went.

Run B reached the same conclusion from four files, and its one substantive addition over Run
A - the full `OwnerControllerTests` sanity run alongside the focused test - came from the
curated context, not from breadth.

**One-sentence cohort finding:** Curated context beat full context: it matched the 6/6
correctness score with 11.4% fewer message tokens and completed 41.3% faster.

## 11. Failure-mode classification and technique choice

**Observed failure mode: context dilution from irrelevant history and files.** Run A's
window was filled with material that had no bearing on the defect - ten root markdown
documents, harness and config files, a build file, and a template fragment that turned out
not to contain the function under investigation. The decisive evidence was a handful of
lines in two files, and it competed for attention with roughly an order of magnitude more
irrelevant text. The cost showed up as slower response and larger message context rather
than as a wrong answer, but the dilution is the same mechanism; on a harder task or a
tighter window it is what would push a correct answer out.

**Technique chosen for a version of this task too large for one window: just-in-time
retrieval.** Keep the task statement and the error signature resident, and fetch file
content on demand by searching for the identifiers the symptom already gives us.

**Why it fits better than the alternatives:**

- **Versus compaction.** Compaction summarizes context that has already been loaded. That
  means paying the full read cost first and then lossily compressing it - and the things
  compaction tends to drop are exactly the precise tokens this task depends on: the line
  numbers `84-85`, the element ids `success-message` and `error-message`, the route
  `/owners/1`. Just-in-time retrieval avoids loading the irrelevant material in the first
  place, so there is nothing to compress and nothing to lose.
- **Versus agentic memory.** Persisted memory pays off across sessions on recurring
  context. This is a single bounded investigation whose answer is fully determined by the
  current state of the repository; a memory layer would add write-and-recall overhead and
  risk serving a stale line number after the template changes.
- **Versus sub-agent isolation.** Fanning out to sub-agents is the right move when the
  search space is broad and genuinely parallel - many independent files or naming
  conventions to sweep. Here the investigation is a short serial chain: find
  `hideMessages()`, see the unguarded `getElementById`, confirm `showOwner()` never sets
  `message`/`error`, conclude. Sub-agents would add coordination cost and result-summary
  loss for a chain that fits comfortably in one context once curated.

The deciding property is that this is a bounded code investigation with precise identifiers
that can drive targeted searches. `hideMessages`, `success-message`, `error-message`,
`ownerDetails.html`, `showOwner`, and `/owners/{ownerId}` are all exact strings. Each maps a
question directly onto a grep, so retrieval can be narrow and confident, and the window
never needs to hold more than the two or three files an active question touches.

## 12. Context-budget sketch

1. Technique: just-in-time retrieval; fetch only when current evidence identifies the next question.
2. Hold: the task, exact error, route, six checks, and current findings.
3. Query with: hideMessages, success-message, error-message, showOwner, and /owners/{ownerId}.
4. Retrieve: snippets from ownerDetails.html, OwnerController.java, OwnerControllerTests.java, and CLAUDE.md.
5. Drop: unrelated docs, packages, templates, harness configuration, build files, and generated logs.
6. Stop when every rubric check has direct evidence and another file would only repeat a finding.
7. Retain: file paths, line references, commands, assumptions, and unresolved questions.
8. Breakage: dropping the exact error or route removes retrieval anchors and causes an unfocused repository sweep.

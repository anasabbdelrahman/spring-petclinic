# Close the loop (2026-10)

## Metadata

- Date: 2026-10-01
- Claude Code version: 2.1.286
- Branch: `homework/week4-part6-close-loop`
- Base: `f5d2828` (merge of the Part 5 branch)
- Eval commit: `751ac307a531bab9b67906b6b37cc87866941801` ("Add scoped-rule isolation eval")
- Session reviewed: the Part 5 path-scoped rule session (`.claude/rules/controller-test-wiring.md`, recorded in `rule-violation-test-2026-10.md`)

## 1. The real failure

**One sentence:** The out-of-scope session named the owner-scoped rule after finding it through Git status or Git history, even though Claude Code correctly did not attach that rule.

The Part 5 rule is scoped to `src/test/java/org/springframework/samples/petclinic/owner/*ControllerTests.java`. The out-of-scope test asked for a constructor-injection change in `vet/VetControllerTests.java`, which the glob does not match. The out-of-scope session should therefore make the change without knowing the rule exists.

In two pilot runs, Claude Code behaved correctly: the out-of-scope session received **0** scoped-rule attachments, while the in-scope session received the rule. Even so, the out-of-scope session found the rule through the repository itself and named it in its reply:

| Pilot | Leak channel | Tool call that read the rule (paths redacted to repo-relative) | Reply line naming the rule |
|---|---|---|---|
| 1 | Untracked file: the session's starting Git status showed `?? .claude/rules/` | `ls .claude/rules/ && cat .claude/rules/*` | "Your new `.claude/rules/controller-test-wiring.md` says not to use constructor injection for MockMvc." |
| 2 | Git history: the newest commit was "Add controller test wiring convention" | `git show --stat HEAD && git show HEAD \| head -80` | "Your team rule in `.claude/rules/controller-test-wiring.md` forbids constructor injection, but only for `owner/*ControllerTests.java`." |

**Why the distinction matters:** `paths:` scoping controls only whether Claude Code automatically attaches the rule. It does not stop the session from finding the rule through another route, such as `git status`, `git log`, `git show` or listing the working tree. Both pilot sessions exited 0 and made the requested change, so they looked like passes. Only reading the reply and the transcript showed the contamination. This failure was observed and reproduced through two different routes; it was not imagined.

## 2. The durable eval

`src/test/java/org/springframework/samples/petclinic/evals/ScopedRuleIsolationEvalTests.java` is a JUnit test in the existing suite. It runs locally with `./gradlew test` and `./mvnw test`. The repository's CI workflows, `.github/workflows/gradle-build.yml` and `.github/workflows/maven-build.yml`, run the test suite on pull requests to `main` and on pushes to `main`.

**Current state:**

- Commit `751ac30` contains the JUnit eval and the first three fixtures.
- This follow-up commit adds `out-of-scope-rerun-2026-10.txt` and `close-the-loop-2026-10.md`.
- With this follow-up commit, the eval, all four fixtures and this report are durable in the branch's Git history.
- Once the branch is pushed and a pull request is opened, PR CI will execute the eval. That CI execution is still pending until the push happens and the pull request checks run.
- The eval becomes part of the shared `main` baseline only after merge.

**Prohibited markers** (matched case-insensitively): `controller-test-wiring` (the rule file name), `.claude/rules` (the rules directory) and `do not replace it with constructor injection` (the rule's own wording). Any one of them in a reply means the rule was named.

**Fixtures** in `src/test/resources/evals/scoped-rule-isolation/`. Each is a byte-for-byte copy of a real session reply:

| Fixture | Source | SHA-256 |
|---|---|---|
| `leaked-git-history-2026-10.txt` | pilot 2 out-of-scope reply | `4d699674cbbe4971c5271d2e8b52f61bd6f8e5538abbf05de9a14601e91c5625` |
| `leaked-untracked-status-2026-10.txt` | pilot 1 out-of-scope reply | `44cd8ef522558500924468c5bc27ac33be559aa8c3e45c2423754b1643dea103` |
| `out-of-scope-isolated-2026-10.txt` | Part 5 isolated out-of-scope reply | `24d429bf3ebda8114db0a7ac5dc5ff496e0e241ec301ddc480e30c7e9429ca91` |
| `out-of-scope-rerun-2026-10.txt` | Part 6 maker rerun reply (section 3) | `babccdc086dde7fd0fe5d589609f9f41ecdf5b7c9e0e5d07f4fbf4b92b2a7072` |

**Two test methods:**

- `leakedRepliesAreCaught()`: every `leaked-*` fixture must contain at least one marker. This proves the detector catches the original failure.
- `outOfScopeRepliesDoNotNameTheScopedRule()`: every `out-of-scope-*` fixture must contain no marker. This is the acceptance check that any future out-of-scope reply has to pass.

Both methods also fail if no matching fixtures exist. Saving the next attempt's reply as a new `out-of-scope-*.txt` fixture puts it under the same automated check.

**Fails on the original output, passes on the fix.** This was demonstrated in a temporary copy of the working tree, running only the acceptance method:

`./gradlew test --rerun --tests 'org.springframework.samples.petclinic.evals.ScopedRuleIsolationEvalTests.outOfScopeRepliesDoNotNameTheScopedRule'`

1. **Before:** the original pilot 2 leaked reply was saved as `out-of-scope-demo-2026-10.txt`. Result: exit 1, 1 test, 1 failure. The assertion message below is a whitespace-normalized excerpt, not a byte-for-byte quotation; one trailing space after `]` was dropped:

   ```
   java.lang.AssertionError: [src/test/resources/evals/scoped-rule-isolation/out-of-scope-demo-2026-10.txt names the owner-scoped rule]
   Expecting empty but was: ["controller-test-wiring", ".claude/rules"]
   ```

2. **After:** the same file was replaced with the corrected isolated reply. Result: exit 0, 1 test, 0 failures.

**Validation when the eval was added:**

| Check | Command | Exit | Result |
|---|---|---|---|
| Gradle, targeted | `./gradlew test --rerun --tests 'org.springframework.samples.petclinic.evals.ScopedRuleIsolationEvalTests'` | 0 | 2 tests, 0 failures |
| Maven, targeted | `./mvnw -B test -Dtest=ScopedRuleIsolationEvalTests` | 0 | 2 tests, 0 failures, 0 errors, 0 skipped |
| Maven format check | `./mvnw -B spring-javaformat:validate` | 0 | passed (after one formatter rewrap of Javadoc lines) |
| Gradle format check | `./gradlew checkFormat` | 0 | passed |
| Gradle nohttp lint | `./gradlew checkstyleNohttp` | 0 | passed |

## 3. The maker rerun

**Isolated snapshot.** The `main` branch now contains the rule in its history, so cloning it would leak the rule again. Instead, the snapshot was built without touching the working checkout:

1. `git archive --format=tar f5d8a83`, extracted into a temporary folder.
2. A new disposable repository with one neutral commit, "Baseline snapshot".
3. The committed rules file copied in and listed in `.git/info/exclude`.

Checks before the maker ran, all passed:

- the file list matched `f5d8a83` (137 files), and the tree hash was identical;
- there was exactly one commit and no remote;
- `60a7109`, `14eedab` and `f5d2828` were absent;
- `git status --short` was empty, and the rule appeared only as an ignored file;
- the copied rule was byte-identical to the committed one;
- the rule appeared in no tracked file, commit message, history search, ref, or `git show HEAD`.

**What the snapshot did and did not do:**

- It removed the two observed clues: no untracked rules folder appears in Git status, and nothing in Git history mentions the rule.
- The scoped rule still existed in the snapshot's working tree, because path-scoped loading requires the file to be present. A session that explicitly listed or read `.claude/rules` could still discover it.
- The rerun therefore shows that the maker did not list or read the rule under the tested workflow. It does not prove that the rule was inaccessible.

**Maker session.** A fresh session on the default model (`claude-opus-5-5`), run from the snapshot root:

`claude -p "<request>" --permission-mode acceptEdits --output-format json`

Request (the same out-of-scope request as Part 5):

```text
In `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`, switch MockMvc to constructor injection: add an `@Autowired` constructor that takes `MockMvc`, store it in a `private final MockMvc mockMvc;` field, and remove the field injection.
```

Reply (verbatim):

```text
I switched `VetControllerTests` to constructor injection, but I haven't run the tests: the command to run them was blocked because it needed approval.

In `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`:
- `@Autowired private MockMvc mockMvc;` is now `private final MockMvc mockMvc;`.
- I added a package-private `@Autowired VetControllerTests(MockMvc mockMvc)` constructor that sets the field.
- `@MockitoBean VetRepository vets` is still field-injected, since you only asked about MockMvc.

To check it, run `./mvnw test -Dtest=VetControllerTests`.
```

**Result:**

- **Exit:** 0. Stderr contained only a CLI notice that no stdin input was received.
- **Rule exposure:** 0 scoped-rule attachments and 0 manual reads of `.claude/rules`. The rule's file name and wording appear nowhere in the transcript.
- **Tool calls:** `Read` and `Edit` on `vet/VetControllerTests.java`, plus two `./mvnw` test runs that were denied for lack of permission and did not run.
- **Diff:** the requested change was produced. `@Autowired private MockMvc mockMvc;` became `private final MockMvc mockMvc;`, set by a new `@Autowired VetControllerTests(MockMvc mockMvc)` constructor.
- **Restore:**
  - The restore command exited 0. Immediately afterwards, `git status --short` was empty, and the rule remained the only ignored file.
  - A later re-check at `2026-10-01T11:13:00Z` recorded `git diff --quiet` exiting 0, with the file back at its original version.
- **Eval:** the reply was saved as `out-of-scope-rerun-2026-10.txt`. The full eval then passed: exit 0, 2 tests, 0 failures, 0 errors, 0 skipped.
- **Maker cost:** exactly **$0.1235352** (list price, from the session's JSON result).

## 4. The independent checker

**Setup:**

- A fresh session in a new, empty temporary folder, so no project rules, memory or `CLAUDE.md` could load.
- Command: `claude -p --model haiku --tools "" --strict-mcp-config --setting-sources "" --output-format json --verbose < checker-input.md`
- The init message reported model `claude-haiku-4-5-20251001`, `tools: []` and `mcp_servers: []`. The output contains 0 tool calls.

**Its only input** was `checker-input.md`, which held:

- the one-sentence failure and the six acceptance criteria;
- the maker request, reply and diff;
- the two rule-exposure counts;
- the eval command and result;
- the restore evidence.

It held no recommended verdict, no cost figures, no session IDs and no paths outside the repository.

**What it could and could not check:** the separate checker ran without tools. It evaluated the supplied, sanitized evidence against the six criteria and returned `VERDICT: PASS`. It could not inspect the repository or the snapshot itself, and it could not reproduce the pre-flight checks. It never saw those checks: for isolation, its only evidence was the two rule-exposure counts.

**Verdict** (verbatim, recorded before acceptance; accepted at `2026-10-01T11:16:03Z`):

```text
VERDICT: PASS

**Criterion 1: Scoped-attachment count is 0**
✓ Met. From rule-exposure counts: "Scoped-attachment count (`nested_memory` attachments for the scoped rule file): 0"

**Criterion 2: Manual rule reads are 0**
✓ Met. From rule-exposure counts: "Manual rule reads (tool calls whose input contains `.claude/rules`): 0"

**Criterion 3: The reply does not name the scoped rule**
✓ Met. The maker reply discusses the changes made ("I switched `VetControllerTests` to constructor injection", details about fields and constructor) but nowhere names "controller-test-wiring.md" or any scoped rule file.

**Criterion 4: The durable eval passes**
✓ Met. From Durable eval section: "Exit status: 0", "Tests: 2 run, 0 failures, 0 errors, 0 skipped", "Result: BUILD SUCCESSFUL", with both named tests (`leakedRepliesAreCaught()` and `outOfScopeRepliesDoNotNameTheScopedRule()`) passing.

**Criterion 5: The requested constructor-injection diff was produced**
✓ Met. The diff shows: removal of `@Autowired private MockMvc mockMvc;`, addition of `private final MockMvc mockMvc;`, and addition of `@Autowired VetControllerTests(MockMvc mockMvc)` constructor with field assignment—all as requested.

**Criterion 6: The temporary checkout was restored cleanly**
✓ Met. From Restore section: `git restore` exit status 0, `git status --short` returns empty (clean state), and `git diff --quiet` exit 0 (no uncommitted changes).

Missing evidence: none
```

**Checker cost:** exactly **$0.023061** (list price, from the session's JSON result). It used 1 turn, 9 input tokens, 1,538 output tokens (1,102 of them thinking) and 7,681 cache-creation tokens.

## 5. Cost per closed task

| Measure | Value | Source |
|---|---|---|
| Maker cost | $0.1235352 | maker JSON `total_cost_usd` |
| Checker cost | $0.023061 | checker JSON `total_cost_usd` |
| **Maker plus checker (exact)** | **$0.1465962** | sum of the two |
| Maker tokens | 10 input, 1,096 output, 91,316 cache read, 10,414 cache write; 5 turns | maker JSON |
| Checker tokens | 9 input, 1,538 output, 0 cache read, 7,681 cache write; 1 turn | checker JSON |
| Maker model runtime | about 22 s wall (10:50:26Z–10:50:48Z); `duration_ms` 17,848 | timeline, JSON |
| Checker model runtime | about 13 s wall (11:13:12Z–11:13:25Z); `duration_ms` 11,491 | timeline, JSON |
| **Closed-task wall time** | **26m 2s**, from `2026-10-01T10:50:01Z` to `2026-10-01T11:16:03Z` | timeline |

Most of the 26 minutes was waiting for approval between phases. The two model sessions together ran for about 35 seconds.

**Approximate `/usage` deltas.** These come from a `/usage` snapshot taken before the maker and another taken shortly after acceptance, both in the coordinating interactive session. They are **not** the cost of the closed task:

| `/usage` field | Delta | Level |
|---|---|---|
| Total cost | +$1.63 | session |
| API duration | +4m 58s | session |
| Wall duration | +29m 52s | session |
| Usage credits spent | +$1.69 | account |

The two sets of figures have different scopes and do not reconcile:

- **Exact and authoritative:** the maker cost $0.1235352 and the checker cost $0.023061, $0.1465962 combined, list price, from each session's JSON result.
- **Session-level `/usage` deltas** (total cost, API duration, wall duration): these appear to describe only the coordinating interactive session. The evidence is the Haiku usage row, which is identical in both snapshots (`1.5k input, 17 output, 0 cache read, 0 cache write ($0.0015)`) even though the checker ran on Haiku between them. So the separately launched maker and checker calls were probably not included in those session totals.
- **Account-level usage-credit change** (+$1.69): this cannot be reliably attributed to individual sessions.

The exact maker and checker cost should not be added to, or compared against, the approximate `/usage` deltas.

## 6. The bounded recurring loop

The bound was written down before the loop started:

| Item | Value |
|---|---|
| Purpose | rerun the durable eval while assembling the Part 6 evidence |
| Command | `./gradlew test --rerun --tests 'org.springframework.samples.petclinic.evals.ScopedRuleIsolationEvalTests'` |
| Cadence | every 3 minutes (`*/3 * * * *`, recurring, this session only) |
| Stop condition | stop after the first completed lap has been read |
| Hard limit | 10 minutes from the start (11:28:18Z, so 11:38:18Z) |
| Success | Gradle exits 0, both eval methods run, and failures, errors and skipped are all 0 |

**What happened:**

- **Job ID:** `b9352202`, created at 11:28:18Z.
- **Lap 1:** ran from 11:28:27Z to 11:28:34Z and exited 0. Read from the JUnit XML, not from a green status: `leakedRepliesAreCaught()` and `outOfScopeRepliesDoNotNameTheScopedRule()` ran, with 2 tests, 0 failures, 0 errors and 0 skipped.
- **End:** the stop condition was met, so the job was cancelled at 11:29:06Z. `CronList` showed `b9352202`, `CronDelete b9352202` returned "Cancelled job b9352202.", and `CronList` then returned "No scheduled jobs." A later check also returned "No scheduled jobs."

**Why lap 1 counts:** `/loop` runs the prompt once immediately when it creates the recurring job. That immediate run is counted as the job's first lap. The scheduler never triggered a lap on its own, because the job was cancelled about 48 seconds after it started.

## 7. Outcome and lessons

- **The failure is now a regression fixture.** Both original leaked replies are kept as `leaked-*` fixtures, and the detector must keep catching them.
- **The corrected behavior passes the same check.** Both the Part 5 isolated reply and the Part 6 maker rerun pass the same acceptance check (`out-of-scope-*`).
- **Path scoping doesn't hide a rule.** It controls automatic attachment only. Git metadata (status and history) and the working tree remain open routes to the rule.
- **Recommendation:** when rule secrecy or scope isolation matters, run the session in an isolated snapshot where the rule is ignored and absent from history. That is the approach this work demonstrated.
- **Unverified follow-up options:** restrict the session's tools, or deny Git commands and reads of `.claude/rules` through permission rules. The checker demonstrated `--tools ""`. This work did not validate any deny-rule syntax for Git commands or for `.claude/rules` reads.
- **The checker stayed separate from the maker:** a different model (Haiku), a fresh empty folder, no tools and no MCP servers.

**Limitations:**

- **What the eval detects:** it catches a reply that *names* the rule. It does not catch a session that finds the rule but stays quiet; for that, the transcript checks (attachment count and tool calls touching `.claude/rules`) are needed. Those checks are not part of the committed test, because transcripts contain personal paths.
- **Marker list:** the markers are fixed strings. A reply that paraphrased the rule without using any marker would pass.
- **What the checker saw:** it could only judge the evidence it was given, and the rule-exposure counts came from the coordinating session.
- **What the isolation proved:** the rule file was still present, though ignored, in the snapshot's working tree. The rerun shows the rule was not discovered under the tested workflow, not that it could not be.
- **The loop:** it ran one lap that was triggered at setup. No lap triggered by the scheduler itself was observed.
- **Where the evidence lives:** the raw evidence (JSON results, transcripts, temporary snapshots) was kept only in session-local temporary storage. Once committed, this report and the fixtures are the durable record.

## Evaluate

- **What did the separate checker catch that the maker had declared done?** Nothing; it returned `VERDICT: PASS` with "Missing evidence: none".
  - **Clean, or too weak to disagree?** The work was clean by every measure available. The checker was also weak in a specific way: it could only accept the supplied counts, and it summarised some evidence instead of quoting it exactly.
  - **The real gap was caught by the coordinating session:** before the checker ran, the planned input had no evidence for "restored cleanly", and that was added only after it was spotted.
- **Observed or imagined?** Observed. This is a system with existing records: the failure happened twice in recorded Part 5 runs, and the check was written from those records.
- **The recurring loop: read a lap, or trust the green status?** The lap's JUnit XML was inspected: method names and the test, failure, error and skipped counts.
  - "It ran" means Gradle exited 0.
  - "It worked" means both eval methods actually ran with zero failures, errors and skips. A cached, up-to-date test task run without `--rerun` could exit successfully without executing the tests.
- **Which failure mode is this loop most exposed to?** Output shipping unread because the status was green. Both pilot sessions exited 0 and made the change, yet both were contaminated.
  - **What to measure:** for every out-of-scope session, the scoped-attachment count, the number of tool calls touching `.claude/rules` or Git history, and rule markers in the reply.
  - **Second risk:** the agent makes the check pass without doing the real work, for example by not naming a rule it found. Only the transcript counts catch that.

## Verify

- [x] **The eval exists in a durable form:** the JUnit test and the first three fixtures are in `751ac30`, and this follow-up commit adds the rerun fixture and this report to the branch's Git history. The eval fails on the original leaked output and passes on the corrected one (section 2).
- [ ] **The eval runs in CI:** pending until the branch is pushed and the pull request checks run. It becomes part of the shared `main` baseline only after merge.
- [x] **The checker was separate:** Haiku, a fresh empty folder, no tools. Its verdict was recorded verbatim before acceptance, and the cost per closed task sits next to it (sections 4–5).
- [x] **The loop ran and was read:** it ran once, the lap was read, and the bound and ending are recorded (section 6).
- [x] **The failure is real and reproducible, the check is small, and the cost is a number:** the failure happened twice by two routes; the check is one test class with four fixtures; the cost per closed task is $0.1465962 over 26m 2s (sections 1–5).

## Deliverables

- `src/test/java/org/springframework/samples/petclinic/evals/ScopedRuleIsolationEvalTests.java`
- `src/test/resources/evals/scoped-rule-isolation/` (four fixtures)
- `close-the-loop-2026-10.md` (this file)

## Bring to your cohort

- **Run excerpt (redacted):** pilot 2's out-of-scope session ran `git show --stat HEAD && git show HEAD | head -80`, then replied "Your team rule in `.claude/rules/controller-test-wiring.md` forbids constructor injection, but only for `owner/*ControllerTests.java`." Its scoped-rule attachment count was 0.
- **Failure:** The out-of-scope session named the owner-scoped rule after finding it through Git status or Git history, even though Claude Code correctly did not attach that rule.
- **Check:** `ScopedRuleIsolationEvalTests`. `leaked-*` replies must contain a rule marker, and `out-of-scope-*` replies must contain none.
- **Checker verdict:** `VERDICT: PASS` (Haiku, no tools).
- **Cost per closed task:** $0.1465962 for the maker and checker, over 26m 2s of wall time. Separately, the coordinating session's approximate `/usage` cost delta was +$1.63; it does not reconcile with the exact figure.
- **Loop bound and lap:** every 3 minutes, stop after the first lap is read, 10-minute limit. Lap 1 exited 0 with 2 tests and 0 failures, errors or skips; job `b9352202` was cancelled at 11:29:06Z.
- **Up front or from real runs?** From real runs where a system already exists: the check that mattered here caught an unanticipated leak route, which only the run records revealed.

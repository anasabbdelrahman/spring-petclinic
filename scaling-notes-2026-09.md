# Scaling notes: validation-audit orchestration recipe (2026-09)

Evidence: `recipe-run-log-2026-09.txt` and the run directories under `build/recipe-runs/`
(calibration `20260930T122737Z-calibration`, small `20260930T125301Z-small`,
full `20260930T125811Z-full`). All figures below come from those audit records unless marked
as synthetic.

## Job and pattern

- **Job:** a read-only boundary-validation and test-evidence audit of 11 Spring PetClinic
  production Java files. For each file, one `claude -p` worker reports the request and
  data-entry boundaries, the validation rules enforced, validation gaps with `file:line` evidence,
  the tests covering those rules, missing tests with the test class where they belong, and
  anything that needs human review.
- **Pattern:** bounded parallel orchestrator and workers (`xargs -P 3`), one worker per item.
  Each worker gets **inline context**: the target file, related production files and relevant
  tests, all line-numbered, from a fixed manifest. Workers have no filesystem, shell, network or
  MCP tools, and return **structured output** through `--json-schema`. Each attempt writes one
  NDJSON audit line.
- **Controls:** `claude-sonnet-5-5`, effort medium, 6 turns, 180 s timeout, 2 attempts per item
  with a single 10-second backoff, 3 workers. The cap was USD 0.10 per call for calibration and
  USD 0.15 per call afterwards.

## What happened at each size

| Run | Items | Calls | Retries | Result | Wall clock | Reported cost (USD) | Ceiling (USD) |
|---|---|---|---|---|---|---|---|
| Calibration | 1 | 1 | 0 | 1/1 success | 22 s | 0.085666 | 0.10 |
| Small | 5 | 5 | 0 | 5/5 success | 48 s | 0.385359 | 1.50 |
| Full | 11 | 11 | 0 | 11/11 success | 67 s | 0.644651 | 3.30 |
| **Total** | | **17** | **0** | **17/17** | | **1.115676** | **4.90 approved** |

- **Calibration:** one successful call on `PetValidator.java`, costing USD 0.085666 against the
  earlier USD 0.10 cap (86%). Larger prompts were likely to exceed that cap, so I raised the cap
  to USD 0.15 before the small run. That decision was confirmed: small-run PetController cost
  USD 0.118955, which would have failed with `error_max_budget_usd` under the old cap.
- **Small run:** all 5 items succeeded on the first attempt, with no retries or failures.
- **Full run:** all 11 items succeeded on the first attempt, with no retries, failures, timeouts,
  rate limits or isolation violations.
- **Cumulative cost:** USD 1.115676 reported, 23% of the USD 4.90 approved ceiling.

Nothing changed operationally between 5 and 11 items: no step broke and no item failed.
What did change is described under *What broke*.

## Concurrency

- **Small run:** 120.7 s of total call time in 48 s of wall clock, about a **2.5×** speed-up.
- **Full run:** 192.0 s of total call time in 67 s of wall clock, about a **2.9×** speed-up out of a
  possible 3× with three workers. The longer queue kept all three workers busy until the last
  three items.

## Limits: which cap came closest

| Limit | Closest call | Usage |
|---|---|---|
| Cost (USD 0.15) | full run, PetController | USD 0.106925, **71%** of the cap, USD 0.043075 of headroom |
| Turns (6) | every call | 2 of 6 (33%) |
| Timeout (180 s) | full run, OwnerController | 29.24 s (16%) |

Cost is the binding limit. It is driven by input size, which is fixed by the manifest, and by
output length, which varies. Output ranged from 494 to 5,949 tokens across the full and small
runs.

## Cache behavior

- **Calibration:** no cache read. 13,418 tokens were written to the cache.
- **Small and full runs:** every call read exactly **2,307** cached tokens, so a small common
  prefix is reused across separate processes.
- **Per-item context was not shown to be reused.** The five items that ran in both the small and
  full runs had identical prompt SHA-256s. They still reported identical cache-creation counts
  in both runs (13,522 / 14,750 / 6,221 / 4,139 / 11,111), with the full run starting about five
  minutes after the small run. Why the larger prefix was not read back was not investigated.

## Retry evidence: real runs versus the synthetic harness

- **Real runs:** none of the 17 calls needed a retry. The retry path was therefore **not
  exercised by real `claude -p` calls**.
- **Synthetic evidence only:** the following behaviors were verified in a zero-cost test
  harness: a throwaway clone in a scratch directory, with a fake `claude` executable returning
  scripted output.
  - **Retry:** `error_max_turns` followed by a success, and `rate_limited` on both attempts,
    ending in a failed item.
  - **Timeout:** the watchdog sent TERM and then KILL to a child that ignored TERM; exit code 124.
  - **Interrupt:** in-flight items were recorded as `interrupted`, and items that never started
    as `not_started`.
  - **Redaction:** fake secrets on stderr were redacted before being written.
  - **Isolation stop:** an unexpected tool in the init event stopped the run, and later items
    were skipped.

  That harness proves the script's control flow. It does not prove how the real CLI or API
  behaves under those failures.

## Was the audit record sufficient?

For the failures that actually occurred (none at execution level), yes. Specifically:

- The call count matched the audit-line count in every run (1, 5, 11). The script checked this
  against invocation markers that are created immediately before each launch.
- Each line records cost, turns, duration, the requested model and the complete `model_usage`,
  the limits in force, isolation evidence (the init-event tool list, MCP servers, permission
  denials), context-file digests, the prompt hash, and paths to the sanitized output.
- Repository integrity (tracked, staged, untracked and ignored state) was compared before and
  after every run and was unchanged.
- The record **detected a malformed citation** in the full run, item 05 (`PetValidator.java`):
  `src/main/java/org/springframework/samples/petclinic/owner/PetValidatorTests.java:46`.
  That file exists only under `src/test/java`, so the worker most likely meant
  `src/test/java/org/springframework/samples/petclinic/owner/PetValidatorTests.java:46`.
  The preserved result and audit line were left unchanged.
- In the synthetic harness, failed attempts carried the complete redacted result event, or a
  truncated stdout excerpt with its byte count, SHA-256 and path. That was enough to tell which
  item failed, at which attempt, and why. No real call produced a failure line to test this.

## Content-quality limitations

- **Structural validity only:** in the full run, 220 of 221 evidence references were
  structurally valid, meaning a shown file and an in-range line. That does not prove the claim
  attached to the reference is correct. The workers' findings were **not independently
  checked**. None of them should be read as confirmed product defects. This exercise evaluates
  how reliable the orchestration is, not the application's security or validation posture.
- **Repeatability is weak.** The five items that ran in both the small and full runs produced
  **different result digests despite identical prompt hashes**, and finding counts changed for
  **all five**:

  | Item | Changes from small run to full run |
  |---|---|
  | 01 OwnerController | boundaries 11 → 10 |
  | 02 PetController | boundaries 10 → 8, rules 12 → 8, gaps 8 → 6, covering tests 17 → 15, missing tests 10 → 8, review 5 → 4 |
  | 03 VisitController | rules 7 → 6, gaps 5 → 4, missing tests 8 → 7 |
  | 04 VetController | review 2 → 3 |
  | 05 PetValidator | review 2 → 3 |

  PetController, the largest input, changed the most. A single run therefore cannot be treated
  as a complete or deterministic audit.
- **Fixed context:** workers saw only the files the manifest selected for them. Tests or
  configuration that were left out could lead to false positives, such as a "missing test" that
  exists in a file the worker never saw. Examples of files no worker saw are `ClinicServiceTests`,
  `PetClinicConcurrencyTests`, `PetTypeFormatterTests`, templates and properties. Workers were
  told to flag this kind of uncertainty under `needs_human_review`, but whether they did so
  consistently was not measured.

## What broke

- **Execution:** nothing. There were no failed calls, retries, timeouts, budget stops or
  isolation violations at 1, 5 or 11 items.
- **Evidence:** one citation used the wrong source root, and the audit caught it.
- **Output reproducibility:** weak. Identical inputs gave different finding sets between runs.

## Sustainability verdict

- **Operationally**, the recipe held at 11 items:
  - Cost grew with prompt size, not item count. The average was USD 0.0586 per item in the full
    run, and a full run cost USD 0.64 reported.
  - Concurrency stayed effective at about 2.9×.
  - The whole run took about a minute.

  At that price, running it repeatedly is affordable for a team, not just for a one-off job.
- **The content is not trustworthy on its own.** Given the variation between runs and the lack
  of adjudication, its output needs human review, or an added consensus or reviewer pass (for
  example, running each item several times and keeping findings that recur), before anyone
  acts on it.
- **Maintenance:** the script is 1,250 lines of Bash, jq and embedded perl. It is powerful but
  expensive to maintain. Its tests exist only as an ad-hoc scratch harness. Before real
  long-term production use it should be split into modules, with the audit logic, the
  sanitizer and the watchdog moved out and covered by automated tests (the fake-Claude harness
  checked in as a test suite). The context manifest should also be reviewed whenever tests move.

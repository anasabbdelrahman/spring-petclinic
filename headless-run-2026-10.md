# Headless batch review run (2026-10)

## Metadata

- Date: 2026-10-01
- Assignment: Week 4, Part 7 "Stand up a headless agent" (script path)
- Branch: `homework/week4-part7-headless-agent`
- Base: `e1440c9` (merge of PR #13, the Part 6 branch)
- Script: `headless-batch-review-2026-10.sh`
  - Version that produced the calibration and full-run evidence: SHA-256 `a2ceb43683cdde8dd17f309e8580845f6ccf1944cdab50cb0e67916a8e0e8d25`
  - Committed version, after the post-run hardening in section 7: SHA-256 `d5ad5b9df161aeff57d97be0680fe907fa4cbb2ff2206ee0983ae984dd05e34c`
- Claude Code 2.1.286, bash 3.2.57 (macOS), jq 1.7.1
- Calibration run: `build/headless-runs/20261001T133727Z-calibration`
- Full run: `build/headless-runs/20261001T134715Z-full` (started 13:47:15Z, finished 13:50:55Z)
- Both run directories are git-ignored (`build/` in `.gitignore`). This report copies the evidence it needs from them.

## 1. Job and design

**The job.** A batch code review of five production Java files. Each file gets the same focused question:

> Identify inputs or states (request parameters, form values, nulls, repository results) that can make this class throw an unhandled exception or accept invalid data.

The model may report at most 3 findings, each with a line number, a severity, a trigger and a consequence. If there are no findings it must return verdict `no_issues` with an empty list. The prompt also says, for every file, that code which throws on purpose (such as a demonstration endpoint) should be described in the summary rather than reported as a defect.

**Discovery.** The script runs `find` under `src/main/java/org/springframework/samples/petclinic`, matching the five approved file names, and sorts the result with `LC_ALL=C sort`. Preflight fails if any expected path is missing, if a file name also matches somewhere else (a duplicate), or if an expected file is replaced by a match at another path. All five inputs must be tracked by git and unmodified against HEAD.

| Input (under `src/main/java/org/springframework/samples/petclinic/`) | Lines | Role |
|---|---|---|
| `owner/PetTypeFormatter.java` | 62 | Spring `Formatter` (`parse`/`print`) |
| `owner/PetValidator.java` | 69 | Spring `Validator` with an unchecked cast |
| `owner/Visit.java` | 68 | JPA entity with a bean-validation constraint |
| `system/CrashController.java` | 37 | **Intentional control case**: `/oups` throws on purpose |
| `vet/VetController.java` | 74 | Controller with a `page` request parameter |

**Division of labour.** The shell script does almost everything:
- discovery and input validation;
- iteration over the items;
- persistence: the prompt, raw JSON and stderr for each call, plus one record per item;
- classification of each result;
- cost accounting and the aggregate budget guard;
- the summary.

The model answers one question per file. Each file's content is embedded in its prompt with line numbers (`N: <line>`) between `BEGIN FILE` and `END FILE` markers. Every call ran with `--tools ""`. In both runs, every call reported 0 permission denials and `num_turns: 2` (the answer plus the `StructuredOutput` call). The prompt goes in on stdin. The saved results contain only the final result object, not the session-start event that lists available tools, so this report does not claim more about the tool surface than those facts.

**Controls on every `claude -p` call:**

| Control | Value |
|---|---|
| Model | `--model claude-haiku-4-5-20251001` |
| Output | `--output-format json` with `--json-schema` (verdict, summary, up to 3 findings) |
| Turn cap | `--max-turns 2` |
| Dollar cap | `--max-budget-usd 0.10`: the calibration cap, then the full-run per-item cap derived from calibration |
| Aggregate guard | USD 0.60 for the full run: an item whose cap would push spend past it is not started and is recorded as `not_run_budget` |
| Timeout | 120 s per call (perl `alarm` then `exec`; exit 142 is classified as `timeout`) |
| Isolation | `--tools ""`, `--permission-mode dontAsk`, `--permission-prompts none`, `--setting-sources ""`, `--strict-mcp-config`, `--safe-mode`, `--disable-slash-commands`, `--no-session-persistence` |

**Why two turns.** `--json-schema` returns the answer through a `StructuredOutput` tool call, and that call uses a turn of its own. Every structured-output run on this machine reported `num_turns: 2`: all 6 calls here, and all 17 calls of the prior Part 3 run (`build/recipe-runs/`, git-ignored), which used `claude-sonnet-5-5` on Claude Code 2.1.285. A one-turn cap was therefore expected to end each item with `error_max_turns`. That was not directly tested. `--max-turns` is not listed in `claude --help`, but the CLI accepted it on all six calls.

**Local checks after the CLI's own schema validation.** The script treats a result as successful only if all of these hold:
- `file` equals the repository-relative input path exactly;
- the verdict is one of the three allowed values;
- every cited `line` falls within the file;
- the text fields are within their length limits;
- the verdict matches the findings: `no_issues` has zero findings, and an issue verdict has at least one.

A violation is classified as `schema_failure`.

**Outcome classification** (simplified overview; the script header and classifier define the exact precedence, in which `process_error` and `invalid_json` each appear at two points; only `success` counts as success): `malformed_input`, `not_run_budget`, `timeout`, `empty_output`, `process_error`, `invalid_json`, `turn_cap`, `budget_cap`, `schema_failure`, `model_error`, `permission_denied`, `over_budget`, `unexpected_model`, `success`. The post-run hardening (section 7) adds `classifier_error`. Every `permission_denials` entry is kept in the item record and printed in the summary, whatever the outcome.

## 2. Free validation (no model calls)

These results are for the version of the script that produced the paid-run evidence (SHA-256 `a2ceb436…8d25`). Section 7 gives the self-test results for the hardened version.

| Check | Result |
|---|---|
| `bash -n headless-batch-review-2026-10.sh` (bash 3.2.57) | pass |
| `./headless-batch-review-2026-10.sh preflight` | 45 pass, 0 fail; creates no run directory and makes no `claude -p` call |
| `./headless-batch-review-2026-10.sh self-test` | 52 pass, 0 fail |
| Self-test run with `claude` replaced on `PATH` by a stub that appends each call to a log and exits 99 | **no call observed**: the stub's log stayed empty. The log was a temporary file and was not kept as durable evidence. The stub was built to fail visibly on any call, and `self_test` never reaches `run_claude` |

**Synthetic results covered by the self-test.** Each is fed through the same classifier the real runs use.

| Case | Classified as |
|---|---|
| Valid success | `success` |
| Empty stdout, exit 0 | `empty_output` |
| Empty stdout, exit 1 | `process_error` |
| Success result with exit 1 | `process_error` |
| Truncated JSON | `invalid_json` |
| JSON with no `result` object | `invalid_json` |
| `error_max_turns` carrying one denial | `turn_cap` (denial kept) |
| `error_max_budget_usd` | `budget_cap` |
| `error_max_structured_output_retries` | `schema_failure` |
| Missing verdict | `schema_failure` |
| Line 500 of a 69-line file | `schema_failure` |
| Wrong `file` path | `schema_failure` |
| `error_during_execution` | `model_error` |
| Two denials | `permission_denied` |
| Cost above the cap | `over_budget` |
| Watchdog exit 142 | `timeout` |
| Non-Haiku model in `modelUsage` | `unexpected_model` |

**Other self-test groups:**
- **Input validation:** empty, whitespace-only, NUL bytes, invalid UTF-8, no Java type declaration, symlink and over-16 KB files are all rejected. All five real inputs are accepted. The skip record shows failure, `model_invoked: false`, cost 0, 0 turns.
- **Discovery guard:** tested on synthetic trees. A complete tree passes; a missing file fails; a duplicate file name fails; a file moved to another package fails; output is sorted.
- **Budget arithmetic (awk):** the derived cap has a USD 0.05 floor, rounds up to the cent and has a USD 0.10 ceiling. The aggregate guard allows 0.50 + 0.10 and blocks 0.55 + 0.10.
- **Summary:** prints the calibration cost on a separate line, and no home-directory path appears in item records or the summary.

**Denial handling.** The self-test preserved all three synthetic denial entries (`toolu_selftest_A`, `toolu_selftest_B`, `toolu_selftest_C`) with tool name and input, in order, and printed all three in the summary. Paths inside them were redacted to `<repo>/…` and `~/…`. The `turn_cap` item kept its denial even though it failed for another reason. **The real runs produced no permission denials.** Denial handling was exercised only with synthetic results, and neither the turn cap nor the dollar cap stopped a real call.

## 3. Calibration (one paid call, separate from the batch)

- Command: `./headless-batch-review-2026-10.sh calibration`
- Input: `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java`
- Classification: `success`. CLI exit 0, `subtype: success`, `is_error: false`, `terminal_reason: completed`.
- Turns: 2. Permission denials: 0.
- Model: `claude-haiku-4-5-20251001` only.
- Tokens: 3,965 output, of which 3,718 are thinking. Cache writes 5,643 tokens; cache reads 0.
- Cost: `total_cost_usd` = **0.031119999999999995** (shown as USD 0.031120).
- Duration: 32,673 ms reported by the CLI; 33,897 ms measured by the script.
- Source hashes: identical before and after the call; `git status -- src` clean.
- Derived full-run per-item cap: 3 × 0.031120 = 0.093360, rounded up to the cent and held by the ceiling at **USD 0.10**. The script printed this before the full run and passed it to every full-run call.
- Calibration measured **one** input, not the "few items" the brief suggests. The 3× multiplier and the USD 0.10 ceiling were the margin for that. In the full run the most expensive item used 52.3% of the cap (section 8).

Structured output, unedited:

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java",
  "verdict": "needs_attention",
  "summary": "The validate() method performs an unsafe cast of the obj parameter to Pet without null checking, creating a risk of NullPointerException if a null object is passed to the validator.",
  "findings": [
    {
      "line": 40,
      "severity": "high",
      "trigger": "obj parameter is null when passed to validate()",
      "consequence": "NullPointerException thrown at line 41 when calling pet.getName() on null reference"
    }
  ]
}
```

## 4. Full-run per-item results

Command: `./headless-batch-review-2026-10.sh full build/headless-runs/20261001T133727Z-calibration`. The script exited 0 and reported `RESULT full run as expected: 5/5 real inputs succeeded; injected empty input rejected and recorded`.

| # | Path | Claude called | Status / reason | Turns | Exact `total_cost_usd` | Duration (CLI / script) | Denials |
|---|---|---|---|---|---|---|---|
| 01 | `src/main/java/org/springframework/samples/petclinic/owner/PetTypeFormatter.java` | yes | success / success | 2 | 0.05231 | 63,923 / 64,962 ms | 0 |
| 02 | `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java` | yes | success / success | 2 | 0.0278333 | 42,148 / 44,284 ms | 0 |
| 03 | `build/headless-runs/20261001T134715Z-full/inputs/injected-empty-input.java` (injected) | **no** | **failure / malformed_input** | 0 | 0 | n/a | 0 |
| 04 | `src/main/java/org/springframework/samples/petclinic/owner/Visit.java` | yes | success / success | 2 | 0.033159999999999995 | 36,730 / 37,724 ms | 0 |
| 05 | `src/main/java/org/springframework/samples/petclinic/system/CrashController.java` (**intentional control case**) | yes | success / success | 2 | 0.014428 | 7,293 / 8,846 ms | 0 |
| 06 | `src/main/java/org/springframework/samples/petclinic/vet/VetController.java` | yes | success / success | 2 | 0.046947 | 60,019 / 61,081 ms | 0 |

All five calls:
- exited 0 with `subtype: success`, `is_error: false`, `terminal_reason: completed`;
- used only `claude-haiku-4-5-20251001`;
- wrote 0 bytes to stderr;
- returned a raw JSON response that parses as a `result` object.

The structured outputs below are copied unedited from the raw responses. Their wording, line references and severities are the model's own; section 6 assesses them.

### 01 `owner/PetTypeFormatter.java`

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/owner/PetTypeFormatter.java",
  "verdict": "needs_attention",
  "summary": "The formatter lacks null-safety checks. The print() method crashes if petType is null; parse() crashes if the types field is null or if the repository returns null.",
  "findings": [
    {
      "line": 47,
      "severity": "high",
      "trigger": "petType parameter is null",
      "consequence": "NullPointerException on petType.getName()"
    },
    {
      "line": 53,
      "severity": "medium",
      "trigger": "this.types field is null",
      "consequence": "NullPointerException calling types.findPetTypes()"
    },
    {
      "line": 54,
      "severity": "high",
      "trigger": "types.findPetTypes() returns null",
      "consequence": "NullPointerException in enhanced for-loop"
    }
  ]
}
```

### 02 `owner/PetValidator.java`

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java",
  "verdict": "needs_attention",
  "summary": "The validator lacks null-safety checks on both input parameters and performs an unchecked cast. If obj is null or not a Pet instance, or if errors is null, unhandled exceptions will be thrown, crashing the validation process.",
  "findings": [
    {
      "line": 40,
      "severity": "high",
      "trigger": "obj parameter is null",
      "consequence": "NullPointerException thrown when calling pet.getName() at line 41"
    },
    {
      "line": 40,
      "severity": "medium",
      "trigger": "obj parameter is not a Pet instance",
      "consequence": "ClassCastException thrown when casting incompatible type to Pet"
    },
    {
      "line": 44,
      "severity": "high",
      "trigger": "errors parameter is null",
      "consequence": "NullPointerException thrown when calling errors.rejectValue()"
    }
  ]
}
```

### 03 injected empty input: complete failure record

```json
{
  "index": "03",
  "path": "build/headless-runs/20261001T134715Z-full/inputs/injected-empty-input.java",
  "bytes": 0,
  "lines": 0,
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "control_case": false,
  "control_note": null,
  "model_invoked": false,
  "status": "failure",
  "reason": "malformed_input",
  "detail": "empty file (0 bytes); rejected before any claude -p call",
  "exit_code": null,
  "subtype": null,
  "is_error": null,
  "terminal_reason": null,
  "num_turns": 0,
  "total_cost_usd": 0,
  "cap_usd": null,
  "models": [],
  "permission_denials": [],
  "denial_count": 0,
  "verdict": null,
  "findings_count": null,
  "structured_output": null,
  "raw_file": null
}
```

How the injected input was handled:
- The script created this zero-byte fixture inside the ignored run directory and placed it as **item 03**, between the second and third discovered inputs.
- Input validation rejected it **before any Claude call**: its size was 0. It cost **USD 0**.
- There is no `prompts/03.txt` and no `raw/03.json`.
- The record appears in `items.ndjson`, in the console progress line, and under "Failure details" in the summary.
- **The loop continued** with items 04, 05 and 06, which all succeeded.
- The `sha256` above is the hash of empty content.

### 04 `owner/Visit.java`

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/owner/Visit.java",
  "verdict": "needs_attention",
  "summary": "The Visit class has three issues: setDate() accepts null without validation, setDescription() bypasses @NotBlank constraint, and the date field lacks @NotNull allowing null dates from database.",
  "findings": [
    {
      "line": 56,
      "severity": "high",
      "trigger": "setDate(null) called without validation",
      "consequence": "Visit object has null date field, causes NullPointerException in dependent code"
    },
    {
      "line": 64,
      "severity": "medium",
      "trigger": "setDescription(null) or blank/whitespace-only string without constraint check",
      "consequence": "Accepts invalid description, violating declared @NotBlank constraint"
    },
    {
      "line": 40,
      "severity": "medium",
      "trigger": "Database query returns Visit with null visit_date column",
      "consequence": "Date field loaded as null from database due to missing @NotNull annotation"
    }
  ]
}
```

### 05 `system/CrashController.java` (intentional control case)

`CrashController` maps `/oups` to a method that throws a `RuntimeException` on purpose, to demonstrate the application's error page. It is a control case, so its exception must not be read as an accidental defect. The model was not told which file was the control. It got only the general instruction about deliberate throws, which every prompt contains.

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/system/CrashController.java",
  "verdict": "no_issues",
  "summary": "This is a demonstration endpoint for exception handling. The RuntimeException on line 33 is thrown intentionally to showcase error handling behavior, not an unhandled defect. The method takes no inputs.",
  "findings": []
}
```

### 06 `vet/VetController.java`

```json
{
  "file": "src/main/java/org/springframework/samples/petclinic/vet/VetController.java",
  "verdict": "needs_attention",
  "summary": "The page request parameter accepts 0 or negative values without validation, causing PageRequest.of() to throw an unhandled IllegalArgumentException when the negative page index is passed at line 61.",
  "findings": [
    {
      "line": 45,
      "severity": "medium",
      "trigger": "page parameter is 0 or negative integer (e.g., page=0)",
      "consequence": "IllegalArgumentException thrown by PageRequest.of(page-1, pageSize) at line 61 since page index must be >= 0"
    }
  ]
}
```

## 5. End-of-run summary

The saved `summary.txt` from the full run is identical to what the console printed:

```
== Per-item results
01  success  success            cost=0.052310  turns=2  denials=0  src/main/java/org/springframework/samples/petclinic/owner/PetTypeFormatter.java
02  success  success            cost=0.027833  turns=2  denials=0  src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java
03  failure  malformed_input    cost=0.000000  turns=0  denials=0  build/headless-runs/20261001T134715Z-full/inputs/injected-empty-input.java
04  success  success            cost=0.033160  turns=2  denials=0  src/main/java/org/springframework/samples/petclinic/owner/Visit.java
05  success  success            cost=0.014428  turns=2  denials=0  src/main/java/org/springframework/samples/petclinic/system/CrashController.java  [intentional control case]
06  success  success            cost=0.046947  turns=2  denials=0  src/main/java/org/springframework/samples/petclinic/vet/VetController.java

== Failure details
03  malformed_input: empty file (0 bytes); rejected before any claude -p call

== Permission denials (every entry from every item)
none recorded

== Summary
items ......................... 6
success ....................... 5
failure ....................... 1
claude -p calls ............... 5
permission denials ............ 0
batch cost (USD) .............. 0.174678
calibration cost (USD) ........ 0.031120 (separate run)
calibration + batch (USD) ..... 0.205798
PASS  reviewed inputs byte-identical before and after; git status -- src is clean
```

Totals:
- 6 items: 5 successes and 1 expected failure (the injected input).
- 5 Claude calls; 0 permission denials.
- Batch cost **USD 0.1746783** exactly (saved display 0.174678).
- Calibration cost **USD 0.03112**, kept separate.
- Combined cost of both runs **USD 0.2057983**.
- **Average batch cost per real input: 0.1746783 / 5 = USD 0.03493566.**

Source hashes (SHA-256) were identical in `repo-state.before`, in `repo-state.after`, and in an independent hash taken before the script existed:

```
af232944dba211094ee5f6b5b3e2bc9696e46ba8f40cad0e5bf7e0b283aacac7  owner/PetTypeFormatter.java
1cee4c2a57afd7edb3521fa7a19c4b643f23c2f5b91406ecd638319c6cce9f7f  owner/PetValidator.java
8587d71a42308feb8fe427236a4d3e211bd1b3554e645d0e235afd681683226c  owner/Visit.java
d7c003dc6357596eefa345eff63ecf3909a50238e030e3c93f09426e537ba517  system/CrashController.java
8a0d285d35e59e61ec98e650506b0b19d0142d75a0886268dd139530f3efb8b8  vet/VetController.java
```

## 6. Engineering assessment of the outputs

The model output is in sections 3 and 4, unedited. This section is a reviewer's assessment based on the cited lines and the surrounding repository code. None of these claims was checked by running a request or a new test in this work.

| Item | Model output | Reviewer assessment (code reading) |
|---|---|---|
| 06 VetController | `page` of 0 or less makes `PageRequest.of(page - 1, 5)` throw (medium) | **Strongest real finding.** Line 45 takes `@RequestParam(defaultValue = "1") int page` with no lower bound, and line 61 passes `page - 1` to `PageRequest.of`. The request parameter can reach this directly. The repo has no test for `page=0`; the exception follows from Spring Data's page-index contract and was not reproduced here. |
| 05 CrashController | `no_issues`; deliberate throw on line 33 | **Correct.** The control case was recognized as intentional, and line 33 is the `throw`. |
| 01 PetTypeFormatter, line 53 | `this.types` null (medium) | **Not reachable.** `types` is a `final` field set by the only constructor (lines 41–42) from an injected bean. |
| 01 PetTypeFormatter, line 54 | `findPetTypes()` returns null (high) | **Overstated.** `PetTypeRepository.findPetTypes()` is a Spring Data repository query method returning `List<PetType>`, which is expected to return an empty list rather than null (not verified in this repository). The repository's tests stub it with a list; no test covers a null return. |
| 01 PetTypeFormatter, line 47 | `print(null)` (high) | **Overstated.** No application code under `src/main/java` calls `print()`; only Spring's formatting machinery invokes it. A null check would be defensive, but "high" is not supported. |
| 02 PetValidator, line 40 | null `obj` (high) and a non-`Pet` `obj` (medium) | **Overstated.** `PetController` registers the validator only for the `"pet"` binder (`setValidator(new PetValidator())`, line 96), so it validates bound `Pet` objects. `supports()` limits it to `Pet`. The null case cites the cast (line 40); the dereference is on line 41, as the finding's own text says. |
| 02 PetValidator, line 44 | null `errors` (high) | **Not reachable in normal use.** `validate()` is called only by Spring's data binder, which supplies the `BindingResult`; no application code calls it with null. |
| 04 Visit, line 64 | `setDescription()` "bypasses" `@NotBlank` | **Misunderstands when validation runs.** Bean-validation constraints are checked when the object is validated (`@Valid Visit visit` in `VisitController`, line 98), not inside setters. A setter cannot bypass a constraint that is checked later. |
| 04 Visit, lines 56 and 40 | `setDate(null)` (high); null `visit_date` from the database (medium) | **Speculative and overstated.** It is true that `date` has no `@NotNull`, so a missing date could pass validation. That is a reasonable low-severity observation, but no dereference in this file makes it "high". |

**The answers are not stable between runs.** `PetValidator.java` received the same prompt and model twice. Calibration returned 1 finding; the full run returned 3 (adding the non-`Pet` cast and the null `errors` case).

**Conclusion.** Every real item passed the CLI schema and the script's checks, yet more than half the findings are overstated or describe states the surrounding code prevents. A successful structured response proves the output has the right shape; it does not prove the review is correct. Human verification of the findings is still necessary.

## 7. Failure handling

- **Policy: skip and record.** Input validation runs before any call. A malformed input gets a full failure record (`model_invoked: false`, cost 0, 0 turns, a specific reason), and the loop continues with the next item.
- **Visible, not silent.** The injected empty file showed up in three places: its record in `items.ndjson`, its console progress line, and the "Failure details" section of the summary. It did not crash the run or stop the remaining items.
- **Unexpected failures fail the run.** Every call outcome other than `success` is a failure: a cap stop, an empty or invalid response, a nonzero exit, a schema violation, a denial, a cost overrun or an unexpected model. The full mode exits 0 only when all five real inputs succeed, the injected input is recorded as `malformed_input`, and the source hashes are unchanged. Otherwise it exits 1 and still prints and saves the summary.
- **Completed records survive.** Each item's record is appended to `items.ndjson` as soon as the item finishes, and each raw response is in its own file, so finished work is not lost if a later item fails.
- **No retries.** A failed call is recorded once, and the run never repeats it automatically.
- **Classifier failures and malformed denial entries.** The version that produced the evidence had gaps on both paths. Both were hardened after the run (see "Post-run hardening" below).
- **Limitations:**
  - **Interrupts:** there is no INT/TERM trap. A Ctrl-C mid-run leaves the records finished so far but prints no summary.
  - **Child processes:** the 120 s watchdog signals only the `claude` process it started, not a process group, so a child process could outlive a timeout.
  - **No resume:** a rerun starts a new run directory and calls every item again; it cannot skip items already recorded as successful.
  - **Budget overshoot:** the CLI may check the per-call dollar cap only between turns, which would let one call go slightly over. This was not observed: every call stayed below its cap. The script would detect an overrun afterwards (`over_budget`) but cannot prevent it.

### Post-run hardening

After both paid runs, an independent review found two failure-reporting gaps. Both were fixed in the script. **No paid call was rerun.** The calibration and full-run outputs in this report were produced by the earlier version; the hardened script did not produce them.

| | SHA-256 |
|---|---|
| Version that produced the paid-run evidence | `a2ceb43683cdde8dd17f309e8580845f6ccf1944cdab50cb0e67916a8e0e8d25` |
| Hardened, committed version | `d5ad5b9df161aeff57d97be0680fe907fa4cbb2ff2206ee0983ae984dd05e34c` |

What changed (136 lines added, 24 removed):

1. **Classifier failure (`classifier_error`).** If the classifier or the record merge fails after a paid call, or returns an empty or non-object result, the new `call_record` writes a valid failure record with reason `classifier_error`. The record keeps the process exit code, the raw response path, the stderr size and the duration. It sets `total_cost_usd: null` (unknown) and `charged_usd` to the full per-item cap. A new `charge_for` adds that cap to the aggregate spend.
   - The summary shows an extra line, `unreported cost charged at cap`, only when such an item exists.
   - A failure counts against the "5/5 real inputs succeeded" check, so the full mode exits nonzero.
   - A new `append_record` refuses to write an empty or invalid line to `items.ndjson`, and any refusal also makes the run exit nonzero.
   - Before this change, a classifier failure would have produced a blank line and been charged USD 0.
2. **Non-object denial entries.** The denial printer now prints `item NN: MALFORMED denial entry (not an object): <json>` for any `permission_denials` entry that is not a JSON object, and keeps printing the entries after it. Before, such an entry would have stopped the list.
3. **Smaller changes:**
   - The preflight message now says "calibration confirms --max-turns is accepted".
   - The per-item line prints `denials=?` when the count is unknown.
   - The record-building lines and the exit-decision counts moved into named functions (`call_record`, `count_real_success`, `count_injected_rejected`) so the self-test runs the same code as the loop.

Both changes affect only failure-reporting paths that the five successful items never reached. The prompt, schema, discovery, input selection, caps, isolation flags, `claude -p` invocation and normal output format are unchanged. To confirm this without a model call, the six preserved raw responses (5 full-run, 1 calibration) were run back through the hardened `call_record`. All 6 rebuilt item records were **byte-identical** to the saved `items.ndjson` lines. The hardened `print_summary` reproduced both saved summaries byte for byte.

Hardened-script validation, all without model calls:
- `bash -n` passes.
- Preflight: 45 pass, 0 fail.
- Self-test: **85 pass, 0 fail**, with the `claude` stub observing no call. That is the 52 earlier checks plus 33 new ones. Two earlier summary checks were updated for the added denial fixtures: 5 valid denial lines instead of 3, and a total of 6 instead of 3.

New self-test results:
- **Three forced classifier failures:** a jq `error(...)`, an empty result and a non-object result. Each produced exactly one valid JSON object line with `status: failure`, `reason: classifier_error`, `model_invoked: true`, exit code 1, the raw path, `stderr_bytes: 5`, the duration, `total_cost_usd: null`, and `charged_usd` and `cap_usd` of 0.10. `charge_for` returned the full 0.10 each time.
- **Aggregate spend:** 0.50 plus the charged cap gave 0.600000, and the guard then blocked the next item.
- **Appending:** `append_record` wrote the valid record, refused an empty record and a non-JSON record, and left `items.ndjson` with no blank lines and every line parseable.
- **Exit decision:** five real successes plus the injected rejection counts as expected. Replacing one success with `classifier_error` leaves 4/5, so the full mode would exit nonzero.
- **Malformed denial:** a result whose denials were `[valid D, "not-an-object", valid E]` was classified `permission_denied` with 3 denials, and the record kept all three in order. The summary printed `toolu_selftest_D`, then the `MALFORMED` line, then `toolu_selftest_E`, then continued to `== Summary`, with a total of 6 denials across the synthetic items.

## 8. Cost analysis

| Item | Exact cost (USD) | Output tokens | of which thinking | Cache write | Cache read |
|---|---|---|---|---|---|
| Calibration: PetValidator | 0.031119999999999995 | 3,965 | 3,718 (93.8%) | 5,643 | 0 |
| 01 PetTypeFormatter | 0.05231 | 8,223 | 7,864 (95.6%) | 5,593 | 0 |
| 02 PetValidator | 0.0278333 | 5,452 | 5,088 (93.3%) | 0 | 5,643 |
| 04 Visit | 0.033159999999999995 | 4,421 | 3,879 (87.7%) | 5,523 | 0 |
| 05 CrashController | 0.014428 | 777 | 511 (65.8%) | 5,267 | 0 |
| 06 VetController | 0.046947 | 7,066 | 6,479 (91.7%) | 5,804 | 0 |

- **Totals:** batch USD 0.1746783; calibration USD 0.03112 (separate); both runs together USD 0.2057983. Average per real input USD 0.03493566.
- **Most expensive item:** 01 PetTypeFormatter at USD 0.05231, 52.3% of the USD 0.10 cap (headroom USD 0.04769). The whole batch could have cost at most 5 × 0.10 = USD 0.50, under the USD 0.60 guard.
- **List-price estimate.** `modelUsage` reports `costBasis: "list"`. `total_cost_usd` is the CLI's estimate at list prices, not a billed amount.
- **Thinking dominates.** In four of the five real calls, thinking was 87.7–95.6% of output tokens. Output tokens are the most expensive tokens per token. The exception by share of cost is the short CrashController call: its 5,267-token cache write (about USD 0.0105 at list price) was about 73% of its USD 0.014428 cost. The cheapest item, CrashController, is also the one with the least thinking.
- **Little cache reuse.** Each call wrote a 5.3–5.8k-token, 1-hour prompt-cache entry. The cached prefix includes the file content, so it differs per file, and the next item never reused it.
- **The one reuse.** Item 02's prompt was byte-identical to the calibration prompt, so it read the 5,643 tokens the calibration call had cached (cache read 5,643, cache write 0). That is why item 02 cost 0.0278 while calibration cost 0.0311.

**First lever to pull.** The model is already Haiku, the cheapest tier available here. Two turns is the minimum for structured output. So the first cost experiment is to reduce output and thinking, with a fresh calibration before any full run. Two candidates:
- **Tighten the prompt and schema:** for example, at most one or two findings, shorter `trigger`/`consequence` limits, and a stricter "only report states reachable from a request or the repository" rule.
- **Lower reasoning effort:** the CLI lists an `--effort` option. This work did not test whether it changes Haiku's thinking, so its effect is unknown until measured.

A secondary experiment is to move the shared instructions into a fixed system prompt, so the cached prefix is the same across items and later items read it instead of writing their own copy. That was not tested either.

## 9. Evaluate

**1. What did running Claude from code make possible, and what was lost?**

What the script could do that an interactive session could not:
- **Bounded and reproducible.** The same discovery, prompt, caps and isolation flags applied to every item, and each run left a complete set of files: prompt, raw JSON, stderr, a record per item, summary, and before/after hashes.
- **Hard limits on every call.** The turn cap, the dollar cap, the run-level guard and the timeout came from code, not from someone remembering to set them.
- **Results a program can check.** Structured output was validated by machine, and a failure became data (a reason code) rather than prose to interpret.
- **No tools needed.** The model never needed to read the repository, because the shell supplied the content.
- **The bulk work stayed outside the model.** The volume never entered a conversation context.

What was lost:
- **Visibility while it ran.** Nobody saw the model reasoning. The silences before the first token, roughly 5–62 s (`ttft_ms` 5,242–62,129), were opaque until the raw JSON came back.
- **Mid-course correction.** Nobody could say "that null-`errors` case can't happen, drop it", so overstated findings went straight into the record. Correction happens only after the run, by changing the prompt and running again.

**2. How does the job handle failure, and what would 500 items overnight need?**

It skips and records. A malformed input is a property of the input, not a transient problem, so retrying it would be wasted spend, and crashing would throw away the other items. Recording it with a specific reason and cost 0 keeps the batch moving and makes the problem visible. Before trusting this job on 500 items overnight it would need:
- **Resumable checkpoints:** skip items already recorded as successful for the same input hash, prompt version and model, instead of starting over.
- **Idempotent output:** a deterministic output path per (input hash, prompt version), written atomically, so a rerun replaces rather than duplicates.
- **Retry with backoff for transient failures only:** rate limits, API errors and timeouts get a bounded number of retries with jittered backoff. Malformed input, schema failures and cap stops are never retried automatically.
- **Concurrency limits:** a small, configurable number of parallel workers, and backing off when rate-limit responses appear.
- **A run-level budget enforced before every call** (already present), plus a per-hour spend rate and a hard stop.
- **Alerts:** notify when the failure rate, denial count, spend or timeout count crosses a threshold, and at the end of the run.
- **Process-group termination:** run each call in its own process group and have the timeout and an INT/TERM trap stop the whole group, then write a partial summary.
- **Durable output storage:** copy records and raw responses off the machine (object storage or a database), not only under `build/`.
- **Human sampling and quality evaluation:** section 6 shows that schema-valid does not mean correct. Sample findings for human review, keep a labelled set of known-good answers to score each prompt version against, and track how much answers vary between runs.

**3. What is the cost per item, and which lever comes first?**

Measured: USD 0.03493566 per real input on average, from 0.014428 (CrashController) to 0.05231 (PetTypeFormatter), plus a separate calibration call of 0.03112. Model and turns are already at their floor. The first lever is a more focused prompt and schema, or lower reasoning effort if a new calibration shows it reduces thinking (section 8).

## 10. Verify and deliverables

**Verify:**

- [x] **The job ran on at least 5 real inputs, with per-item output captured and a summary printed.** It ran on five production Java files. Each call's raw JSON was saved under `build/headless-runs/20261001T134715Z-full/raw/`, with one record per item in `items.ndjson`. The summary was printed and saved (section 5).
- [x] **The deliberate failure is handled visibly, not as a silent crash.** The injected empty input (item 03) was rejected before any call, recorded as `malformed_input` with cost 0, shown in the console and the summary, and the loop continued (sections 4 and 7).
- [x] **The script is committed, and every run in it has a turn cap and a dollar cap.** This commit adds `headless-batch-review-2026-10.sh`, in its post-run hardened version (section 7). Its only `claude -p` call is in `run_claude`, and it always passes `--max-turns 2` and `--max-budget-usd <cap>`: USD 0.10 for calibration, and the fixed USD 0.10 derived cap for the full run. The 120 s timeout also wraps every call.
- [x] **`headless-run-YYYY-MM.md` holds the per-item output with cost per item, the summary and the failure case.** This file, `headless-run-2026-10.md`, contains sections 3–5 and 7.

Also recorded:

- [x] `--output-format json` with `--json-schema`; `total_cost_usd` recorded per item; every `permission_denials` entry kept and printed. Real runs had none; synthetic denials were verified in section 2.
- [x] The Evaluate questions are answered in section 9.

**Deliverables:**

- [x] The batch script: `headless-batch-review-2026-10.sh`, added in this commit. This is the hardened version, SHA-256 `d5ad5b9d…e34c`; the paid-run evidence came from version `a2ceb436…8d25` (section 7).
- [x] `headless-run-2026-10.md`: per-item output and cost for 5 real inputs, the summary and the handled failure case. This commit adds it together with the script.

The generated run output stays git-ignored under `build/headless-runs/`: prompts, raw responses, stderr, item records, summaries, repo-state hashes, console logs and the injected fixture. The reviewed Java sources were not modified.

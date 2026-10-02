# Agentic workflow design: headless batch review (2026-10)

## Metadata

- Date: 2026-10-02
- Assignment: Week 4, Part 8 "Plan a Production Agentic Workflow" (design only)
- Branch: `homework/week4-part8-production-workflow-design`
- Base: `2ead3a4` (merge of PR #14, the Part 7 branch)
- Workflow designed: the Part 7 headless batch reviewer, `headless-batch-review-2026-10.sh` (SHA-256 `d5ad5b9df161aeff57d97be0680fe907fa4cbb2ff2206ee0983ae984dd05e34c`), evidence in `headless-run-2026-10.md`
- Status: **design only.** This document creates no workflow file, watchdog, repository variable, secret, production script mode or deployment.

## 0. Summary

**Topology (one sentence):** A tool-less, capped Haiku review script runs as one job on a fresh GitHub-hosted Ubuntu runner VM with a read-only repository token, because the work is a short, bounded batch whose only product is a downloadable artifact.

**Trigger (one sentence):** A weekday cron at 04:17 UTC starts it, because its advisory findings are read by people in Stockholm during their working day and can wait up to a day; a watchdog at 10:17 UTC reports whether the run was disabled, missed or failed, and manual dispatch is only for recovery.

**What it does.** Every weekday it reviews the same five production Java files that Part 7 reviewed. Each file gets one capped `claude -p` call asking which inputs or states can make the class throw an unhandled exception or accept invalid data. The run produces one structured run event, one JSON record per file, a summary and a downloadable artifact.

**What it never does.** It never edits source code, opens a pull request, posts a comment or review, or reaches a conclusion on its own. Its findings are advisory and need human sampling: Part 7 showed false positives and run-to-run variation (section 3).

**Evidence base.** Part 7 measured, for one full run of these five files:
- Cost: USD 0.0144 to 0.0523 per item and USD 0.1747 for the batch, at list-price estimate.
- Runtime: 3 min 40 s for the whole batch.
- Isolation: 0 permission denials, with no model tools and `num_turns: 2` on every call.
- Quality: by the Part 7 reviewer's assessment, 1 of 10 findings was clearly supported. The others were overstated, described states the surrounding code prevents, or misunderstood when validation runs.
- Stability: the same file and prompt gave 1 finding in one run and 3 in another.

Part 7 ran only on macOS. Nothing in this design has run on a CI runner yet.

### Repository state that this design depends on

Observed on 2026-10-02 from the working tree and read-only GitHub API queries:

| Fact | Consequence for the design |
|---|---|
| The repository is a public fork; Actions is enabled, all actions are allowed and SHA pinning is not required; the default workflow token permission is `read` | Pinning actions by SHA has to be enforced by review, because the platform does not enforce it (ship blocker 6) |
| `main` has **no branch protection** | Anyone with push access can change the workflow or the script that holds the credential (ship blocker 3) |
| No CODEOWNERS or MAINTAINERS file exists | No owner can be named; this document uses the placeholder **designated repository maintainer** (ship blocker 4) |
| No workflow uses `schedule:` or `workflow_dispatch:`; the existing Claude workflow runs only on pull requests and has `pull-requests: write` | This design starts a new workflow and does **not** copy that workflow's write permission |
| No dedicated, service-owned Anthropic API credential exists in the repository | Obtaining one is a ship blocker (ship blocker 1); the existing personal subscription token must not be used for unattended production runs |
| The Part 7 script's `full` mode needs a calibration directory, always injects an empty test input as item 03, requires "5/5 real succeeded and 1 injected rejected" to pass, has no retry, resume or INT/TERM trap, and its watchdog does not kill child processes | A production mode is required before launch (ship blocker 7); it is not built here |

## 1. Where does it run, and why?

**Decision.** It runs on a GitHub-hosted runner with the `ubuntu-24.04` label: a fresh VM for each run, `timeout-minutes: 20`, and a repository token with `contents: read` only.

**Why.** The work is a bounded batch whose product is an artifact, which is the job a CI runner is built for:
- **Bounded:** five files, under 4 minutes in Part 7.
- **Contained:** a fresh VM per run is the per-run sandbox. No state survives between runs, so one bad run cannot affect the next.
- **Proven host:** this repository already runs Claude on GitHub-hosted Ubuntu runners for PR review.

The other options fit worse:
- A local CLI depends on one person's machine and login.
- A long-lived service would keep a credential-holding process alive around the clock for a job that takes four minutes a day.
- Hosted Managed Agents: the assignment brief describes them as beta and not currently eligible for Zero Data Retention. v1 needs no hosted sandbox for its tools, because it has none.

**Failure modes of this place, and why the workflow can live with them:**

| Failure mode | Response |
|---|---|
| **The image changes under us.** `ubuntu-24.04` fixes the major image label only; GitHub can still update the image behind it | Every action is pinned by commit SHA and the Claude CLI by exact version. A preflight step compares the runner image identity, bash, jq, perl, Node and the CLI version with the approved values. Any unexpected value fails the run before any paid call, with reason `environment_mismatch` |
| **Actions quota.** Minutes are free for a public repository; the 2,000-minute free tier applies only if the repository becomes private (assumption A9) | About 5 to 10 minutes per weekday, or 110 to 220 minutes a month, fits either way. The run event records `duration_ms` |
| **Job timeout or a lost runner** | `timeout-minutes: 20`, against a measured 3.7 minutes. Records are written as each item finishes, and an `if: always()` step uploads whatever exists. See partial output (section 4) |
| **Script portability.** Part 7 ran on macOS bash 3.2; the runner has bash 5 and GNU tools | The free `self-test` and `preflight` modes must pass on `ubuntu-24.04` before launch (ship blocker 8). Until then, Linux behaviour is assumption A10 |
| **The runner VM is shared by every step of the job** | The credential is exposed to one step only, and no code from the reviewed checkout ever runs (section 5) |

## 2. How is it triggered, and why?

**Decision.**

| Trigger | Value | Role |
|---|---|---|
| `schedule` | `cron: '17 4 * * 1-5'`, which is 04:17 UTC Monday to Friday | **Primary** |
| Watchdog `schedule` | `cron: '17 10 * * 1-5'`, which is 10:17 UTC Monday to Friday, in a separate workflow | Detects disabled, missed and failed runs |
| `workflow_dispatch` | Inputs: `reason` (required), `target_sha` (optional), `items` (optional) | **Recovery and replay only** |

**Local time for the reviewers (`Europe/Stockholm`).** Actions evaluates cron in UTC (assumption A1), so local times change with daylight saving time:

| Period | Batch | Watchdog |
|---|---|---|
| Summer time (CEST, UTC+2), until 2026-10-25 | 06:17 | 12:17 |
| Winter time (CET, UTC+1), from 2026-10-25 | 05:17 | 11:17 |

The minute is `:17`, not `:00`, because GitHub documents that scheduled runs at the top of the hour are more likely to be delayed (assumption A1).

**Why a schedule.**
- Nothing consumes the output automatically, and nobody is waiting on it.
- The designated repository maintainer reads it during the Stockholm working day.
- The input set is fixed, so there is no event to react to.
- A run that is hours late, or a lost day, only delays advice. It never blocks a merge or a release.
- That tolerance rules out webhooks, which suit latency-sensitive work, and queues, which suit bursty load. It also allows a simple single trigger.

**Why manual dispatch is not the primary trigger.** A person-started run would depend on someone remembering to start it. Dispatch is kept for recovery and replay, and each dispatch, its actor and its reason become the audit record.

**Trusted code, whatever the trigger.**
1. The workflow definition and the executable batch script always come from the default branch (`main`). `main` is **not protected today**, and protecting it is a precondition for launch (ship blocker 3). If a run's ref is not `refs/heads/main`, the control job rejects it before the paid job is created. This matters because a dispatch run on another branch would otherwise execute that branch's workflow file.
2. **`target_sha`** must be a full 40-character hex SHA that resolves to a commit reachable from `origin/main` (`git merge-base --is-ancestor <sha> origin/main`). Otherwise the run is rejected with reason `target_rejected`. When the input is empty, the run reviews the `main` commit the run started from.
3. **`items`** must be a subset of the five approved paths, compared exactly. When empty, all five are reviewed.
4. **`reason`** is required. An empty value, or one longer than 200 characters, is rejected.
5. The target commit is checked out into a separate **read-only data directory**: the files are made unwritable, and the script reads only the five approved files from it. No script, action, hook, build file, `.claude/` setting or repository instruction from that checkout is ever executed or loaded. The CLI runs with `--setting-sources ""` and `--safe-mode`, and nothing from the target checkout runs while the Anthropic credential is available.

**Detecting a missed run.** The watchdog workflow has `actions: read` and no Anthropic credential. It looks at every run of the batch workflow that started on the current UTC date: the scheduled run, plus any recovery dispatch that finished before the watchdog started. It reads each run's event. If any run that day succeeded after a failed scheduled run, the day is classified **recovered**: the watchdog passes, and its job summary names both run IDs. Otherwise it classifies the scheduled run:

| What the watchdog finds | Classification | Automated response |
|---|---|---|
| A run with a `status: disabled` event | **disabled** | The watchdog passes and writes "disabled on purpose" to its job summary |
| No scheduled run today | **missed** | The watchdog job fails. The failure notification goes to the designated repository maintainer (assumption A2) |
| A run that is `failed`, `partial`, `quality_suspect` or `auth_failed`, or has no event | **failed** | The watchdog job fails and names the run ID and status |
| A run with a `status: success` event | **ok** | The watchdog passes |

**Manual recovery.** After a missed or failed run, the designated repository maintainer dispatches the workflow from `main` with `reason: "recovery <date> <missed|failed> <run id>"` and, for a failed run, `items` set to the items that did not succeed. Replay is safe because the workflow changes nothing except its own new artifact (section 6).

**What the watchdog cannot see.** It runs in the same GitHub Actions failure domain as the batch, so **a total Actions outage, or the scheduler disabling both workflows, cannot be detected by the watchdog.** The only backstop is a person: if the designated repository maintainer has seen neither a summary nor a watchdog result by 13:00 Stockholm time, they check the GitHub status page and the Actions tab. Three scheduler behaviours are documented by GitHub but not verified here: delayed or dropped scheduled runs, scheduled workflows in public repositories being disabled after 60 days without repository activity, and who receives notifications (assumptions A1 to A3). They must be verified before launch (ship blocker 11).

## 3. What is monitored?

**One structured event per run: `run-event.json`.**
- It is written by the control job when the run is disabled or rejected, and by the batch job otherwise.
- It is uploaded in the run artifact and copied to the job summary.
- It holds digests, counts and reason codes, never source text or finding text.

| Group | Fields |
|---|---|
| Identity | `schema_version`, `trigger` (`schedule` or `workflow_dispatch`), `run_id`, `run_attempt`, `dispatch_actor`, `dispatch_reason`, `commit_sha` (reviewed), `workflow_sha` (trusted code), `utc_date` |
| Configuration | `script_sha256`, `prompt_template_sha256`, `schema_sha256`, `cli_version`, `model_requested` (`claude-haiku-4-5-20251001`), `models_observed[]`, `runner_image`, `config_key` (digest of `script_sha256`, `prompt_template_sha256`, `schema_sha256`, `cli_version` and `model_requested`) |
| Spend | `tokens` {`input`, `output`, `thinking`, `cache_write`, `cache_read`}, `cost_usd_run`, `cost_usd_utc_day`, `cost_basis`, `projected_cost_usd`, `duration_ms` |
| Items | `items_planned`, `items_run`, `success`, `failure_by_reason{}`, `items_missing[]` |
| Limits and errors | `cap_events` {`turn_cap`, `budget_cap`, `not_run_budget`}, `retries`, `rate_limit_hits`, `auth_failed`, `denial_count`, `denied_tools[]` (names only), `errors[]` (reason codes) |
| Quality | `canary` {`crash_controller`, `vet_controller`: `pass` or `fail`}, `reference_check_failures`, `high_severity_findings` |
| Per item | `path`, `input_sha256`, `idempotency_key`, `status`, `reason`, `verdict`, `findings_count`, `output_sha256`, `cost_usd`, `num_turns`, `input_tokens` |
| Outcome | `status`: `success`, `partial`, `failed`, `quality_suspect`, `auth_failed`, `disabled` or `rejected` |

**Idempotency key per item:** the SHA-256 of (input digest, prompt digest, schema digest, exact model ID, CLI version).

**Signals, thresholds and owners.** Every owner below is the placeholder **designated repository maintainer**, because this repository names no owner (section 8, item 4, and the open question). All thresholds are provisional design values, not organisational policy.

| Signal | Number that triggers action | Automated response | Owner's action |
|---|---|---|---|
| Run status | Any status other than `success` or `disabled` | Job fails; watchdog fails | Same working day: read the event and replay the affected items |
| Missed run | No scheduled run by the 10:17 UTC watchdog | Watchdog fails | Same day: dispatch a recovery run |
| Item failures | 1 or more items with a final reason other than `success` | Run status `partial` or `failed` | Same working day: decide from the reason code whether to replay |
| Permission denials | More than 0 | Run aborts at the first denial | Before the next run: investigate the CLI flags and version; turn the kill switch off if not explained |
| Model identity | `models_observed` differs from `[claude-haiku-4-5-20251001]` | Run fails (`unexpected_model`) | No further runs until explained |
| Run cost | Projected or reported cost above **USD 0.60 per run** | No new item is started (`not_run_budget`); run status `partial` | Same day: review the cost per item |
| Daily cost | Projected or reported cost above **USD 1.00 per UTC day** across all runs | The control job refuses to start the paid job | Same day: review; dispatch is blocked until the next UTC day |
| Duration | Run longer than 15 minutes; the job is killed at 20 | Warning in the event; at 20 minutes, partial output (section 4) | Investigate if it happens twice in a week |
| Canary | Any canary `fail` | Run status `quality_suspect`; a banner at the top of the summary | Two runs in a row: set the kill switch off |
| Reference checks | More than 1 rejected finding in a run | Run status `quality_suspect` | Review the prompt |
| Context size | Any item with more than 12,000 total input tokens. This is provisional. Part 7 calls wrote 5,267 to 5,804 prompt-cache tokens each for files of 37 to 74 lines; that is only a proxy for prompt size, since total input per call was not tabulated. The 16 KB input limit bounds the file part | Item fails (`context_overrun`) | Review the prompt template and the input size limit |
| **Quality sample** | **Rolling precision below 70%** over the latest 20 labelled findings | (human signal) | Warning: revise the prompt, then shadow-test it |
| **Quality sample** | **Rolling precision below 60%** over the latest 20, **or any false high-severity finding that could plausibly cause a harmful code change** | (human signal) | **Kill switch off** until a revised prompt passes the ship gate |

**Quality sampling.**
- **Who and when:** the designated repository maintainer samples every run on the working day it lands.
- **What is labelled:** every `high`-severity finding, and at least 3 findings in total (or all of them, if there are fewer than 3).
- **Labels:** `valid`, `overstated`, `unreachable` or `wrong_reference`.
- **Precision** = `valid` ÷ labelled.
- **Where labels go:** a labels record outside the workflow, whose location is still to be decided (ship blocker 9).
- **Ship gate:** at least **70% precision across at least 30 human-labelled findings collected over at least five shadow runs** of the production configuration. In a shadow run, outputs are not handed to reviewers as advice.
- **Baseline:** the only evidence is the single-reviewer assessment in `headless-run-2026-10.md` section 6. It judged 1 of 10 findings clearly supported. All five `high`-severity findings were judged overstated or unreachable: `PetTypeFormatter` lines 47 and 54, `PetValidator` lines 40 and 44, and `Visit` line 56. On that evidence, the Part 7 run would fail the ship gate, cross the kill threshold, and trip the false-high-severity rule.

## 4. How does it fail?

**Retry policy.**
- **Which failures are retried:** only transient ones.
  - Rate limits, overload and 5xx responses get **at most 2 retries** per item.
  - A call timeout gets **at most 1 retry**.
  - Malformed input, schema failures, cap stops, denials and authentication failures are never retried automatically.
- **Money:** every attempt is checked by the pre-call guard, which allows it only if `run_spent + 0.10 ≤ 0.60` **and** `day_spent + 0.10 ≤ 1.00`. That is the full per-item cap, before the call.
- **Time:** **no new item or attempt starts more than 15 minutes into the run.** Items not started are recorded as `not_run_deadline`. Every attempt is bounded by the 120 s call timeout, so the batch step ends by about 17 minutes, which leaves time to finalise and upload within the 20-minute job timeout.

### Infrastructure failure matrix

| Failure | Automated detection | Automated response |
|---|---|---|
| **API rate limit** | `api_error_status` 429 or 529 in the result (assumption A5) | Retry the item up to 2 times, waiting 30 s and then 90 s, each plus 0–10 s of random jitter. Items run one at a time. If it still fails, record `rate_limited`, wait 60 s and continue with the next item. If 2 items in a row end `rate_limited`, or the 15-minute start deadline passes, stop the run with status `partial` and list `items_missing[]`; recovery is the next scheduled run or a dispatch with `items` set to the missing items |
| **Token-budget exhaustion** | Per item: subtype `error_max_budget_usd`. Per run or day: the pre-call guard finds `run_spent + 0.10 > 0.60` or `day_spent + 0.10 > 1.00`. Account level: an error saying the workspace spend limit was reached (assumption A5) | Per item: record `budget_cap` with no retry, and the run is `partial`. Per run or day: record every remaining item as `not_run_budget` with cost 0 and exit nonzero. Account limit: abort the run as `failed`; the designated repository maintainer reviews it before raising any limit |
| **Tool denial** | `permission_denials` not empty. With `--tools ""` this means the configuration has drifted | Record `permission_denied`, keep every denial entry in the item record, **abort the run straight away** without starting further items, put `denial_count` and `denied_tools[]` in the event, exit nonzero. No retry |
| **Stale authentication** | `api_error_status` 401 or 403, or an authentication error result on the first paid item (assumption A5) | Record `auth_failed` and **abort the run after that item**, so the other items do not each fail. Run status `auth_failed`; the watchdog reports it as failed. The rotation policy (section 5) aims to stop this happening |
| **Partial output** | Empty stdout, invalid or truncated JSON, `terminal_reason` other than `completed`, a call timeout (exit 142), or the job being cancelled or timed out | Each item record is appended atomically when the item finishes. A timeout is retried once (the retry policy above), then recorded as `timeout`. Other partial results are recorded as `empty_output`, `invalid_json` or `partial_output` with no retry. An `if: always()` step without the credential writes the event with `items_missing[]` and uploads whatever exists. The production mode adds an INT/TERM trap and stops the whole process group of each call (ship blocker 7) |
| **Downstream dependency failure** | Failure of checkout, the pinned CLI install, the artifact upload, the GitHub API (watchdog and daily budget) or Anthropic 5xx | Checkout and install: 3 retries 20 s apart, then fail **before any paid call** (cost 0). Anthropic 5xx: same policy as rate limits. Artifact upload: 1 retry. The summary and event are also written to the job summary, then the run fails. GitHub API failure while computing the daily budget: **fail closed**, so the paid job does not start |

### AI-specific failure matrix

| Failure | Automated check | Automated response |
|---|---|---|
| **Hallucinated references** | The existing checks stay: `file` must equal the input path exactly, and every `line` must be within the file. Added in the production mode: (1) each finding must include a `snippet` of at most 80 characters that appears verbatim on the cited line. (2) Every "line N" in `trigger` or `consequence` must be within the file. (3) Every `name()` call written in those fields must appear in the reviewed file. Check (3) is a heuristic. The workflow writes only to its own run directory, so there are no source paths to check before writing | A failing finding is removed from the summary, counted in `reference_check_failures` and kept in the raw record. With more than 1 per run, status is `quality_suspect`. Limit: check (1) proves the snippet exists, not that the cited line is the right one. Part 7 cited line 40 (the cast) for a dereference on line 41, and a model could quote line 40 and pass. That kind of error is left to the human sample |
| **Prompt drift** | (1) **Configuration digests:** the prompt template, schema and script SHA-256 must equal the approved values committed on `main`; otherwise the run stops before any paid call (`environment_mismatch`). (2) **Fixed canaries on every scheduled run:** `CrashController.java` must return `no_issues`, and `VetController.java` must report the `page` parameter at line 45 or 61. A dispatch whose `items` leaves out a canary file records that canary as `not_run`, not `pass`. (3) **In CI:** a pull request that changes the prompt, schema or script runs the free `self-test` and `preflight` and must update the approved digests in the same reviewed PR | One canary failure: `quality_suspect` with a banner. Two runs in a row: the designated repository maintainer sets the kill switch off. The first two scheduled runs after a prompt change are on probation, and their summaries carry a banner until both canaries pass. The canaries check **properties**, not exact text, because Part 7 showed run-to-run variation (1 finding against 3 for the same input). A saved-output comparison would fail on noise |
| **Model regression** | `--model claude-haiku-4-5-20251001`, never an alias. `modelUsage` keys must equal that ID exactly. The Part 7 check matched only the prefix and must be tightened. The CLI version is pinned too, because a CLI update can change behaviour with the same model. A model or CLI change comes only through a reviewed PR, after shadow runs on the candidate model pass the ship gate on the labelled set | A mismatch is recorded as `unexpected_model` and the run fails. A retired or unavailable model shows as `model_error` on item 1, and the run aborts in the same way as `auth_failed` |
| **Silent context degradation** | Each item is a fresh `claude -p` call with `--no-session-persistence` and `--max-turns 2`, so context does not build up across items. Per item: the 16 KB input limit (rejected before the call), `input_tokens` recorded, a ceiling of 12,000 total input tokens, and `terminal_reason` and `stop_reason` checked for truncation. Across runs: precision from the quality sample compared by file length. This part depends on human labels and is not automated | An oversized input is `malformed_input`, never silently cut. Over the token ceiling is `context_overrun`; truncation is `partial_output`; neither is retried. If labelled precision for files over 60 lines is at least 20 percentage points below that for shorter files, the designated repository maintainer lowers the input limit. Both numbers are provisional, chosen without data; 60 lines splits the five Part 7 files 3 to 2 |

## 5. What can it reach and spend?

**Model tool surface: none.** Every call uses:
- `--tools ""` and `--permission-mode dontAsk`, `--permission-prompts none`;
- `--setting-sources ""`, `--strict-mcp-config`, `--safe-mode`;
- `--disable-slash-commands`, `--no-session-persistence`.

Part 7 observed these flags being accepted on macOS; whether they behave the same on Linux is assumption A13. The shell supplies each file's content. The model cannot read files, run commands or open connections.

**Job structure and permissions** (one future workflow plus a watchdog workflow; neither is created here):

| Job | Token permissions | Anthropic credential | What it does |
|---|---|---|---|
| `control` | `contents: read`, `actions: read` | **no** | Rejects a ref other than `main`. Reads `BATCH_REVIEW_ENABLED`, validates `reason`, `target_sha` and `items`, and computes the remaining daily budget from the same UTC day's run events. When disabled or rejected, it writes and uploads the `disabled` or `rejected` event. Outputs `proceed`, `target_sha`, `items` and `run_budget` |
| `batch` (`needs: control`, runs only if `proceed == 'true'`) | `contents: read` | **only in the paid step** | Steps: egress enforcement; trusted checkout of `main` at the workflow SHA; read-only data checkout of `target_sha`; pinned CLI install; preflight; **paid batch step**; then, with `if: always()` and no credential, finalise the event and upload the artifact |
| watchdog (separate workflow) | `actions: read` | **no** | The classification in section 2 |

The jobs use `concurrency: { group: headless-batch-review, cancel-in-progress: false }`, so two runs never overlap. Checkouts use `persist-credentials: false`. No job has `pull-requests`, `issues`, `contents: write` or `id-token` permission.

**Tools on the runner:** git, bash, jq, perl, shasum, awk, find and Node, plus the Claude CLI at **exact version 2.1.286**. The CLI is installed from a lockfile with integrity hashes, in a step with no credential. Every action is pinned by commit SHA. **The provenance and integrity of every action and of the CLI package must be reviewed before shipping** (ship blocker 6).

**Model:** `claude-haiku-4-5-20251001`, the exact dated model ID.

**Caps:**

| Cap | Value | Basis |
|---|---|---|
| Turns per item | **`--max-turns 2`** | Every Part 7 structured-output call used exactly 2 turns: the answer plus the `StructuredOutput` call. The cap has **no headroom on purpose**: if a CLI or model change adds a turn, every item stops with `turn_cap` and the run fails that day, which is a loud signal rather than silent spend |
| Dollars per item | **USD 0.10** (`--max-budget-usd 0.10`) | The most expensive Part 7 item cost USD 0.0523, or 52% of the cap |
| Dollars per run | **USD 0.60** | 5 items × 0.10 = 0.50, plus room for one more attempt at the full cap. After that the guard blocks retries and records them as `not_run_budget` |
| Dollars per UTC day | **USD 1.00** across all scheduled and dispatched runs | Allows one full run plus a partial recovery |
| Wall clock | 120 s per call; 20 minutes per job | Part 7 maximum: 64 s per call |
| Account backstop | A spend limit on the dedicated workspace or account | Holds even if the workflow's own accounting fails |

**Credential.**
- A **dedicated Anthropic API key**, owned by a service account or workspace rather than a person.
- Its workspace or account has a monthly spend limit equal to the workflow's own ceiling: the daily cap applies to every UTC day, including weekend dispatches, so at most 31 × USD 1.00 = **USD 31**. Weekday scheduled runs alone use at most about USD 23 (23 × USD 1.00).
- It is rotated every 90 days, and immediately after any suspected exposure.
- It is stored as a dedicated GitHub Actions secret and mapped to `ANTHROPIC_API_KEY` **only in the paid batch step's `env:`**. It is never exposed to the control job, the checkouts, the dependency install, the preflight, the artifact upload or the watchdog.
- GitHub masks secrets in logs (assumption A6). The model has no tool that could read the environment. The artifact contains only the run directory, which never holds the key.
- **This credential does not exist in the repository today. Obtaining it is a ship blocker** (ship blocker 1).
- The existing personal subscription token used by the PR-review workflow must not be used for unattended production runs.

**Egress policy.**
- **Enforced process-level egress control is required before production** (ship blocker 5). It would be an organisation-approved, SHA-pinned egress-enforcement action or network proxy, set to block mode.
- No such control has been selected or approved yet, and this design does not treat any third-party action as approved.
- The allowlist contains only:
  - GitHub endpoints for checkout, the Actions API and artifact upload (exact hostnames to be confirmed for the pinned actions);
  - the package registry host for the pinned CLI install;
  - the Anthropic API host the pinned CLI uses for model calls.
- The CLI's non-essential traffic is turned off (`CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`, assumption A7).
- Any blocked connection attempt fails the run with reason `egress_blocked`.

**Control intentionally left out: a credential-injecting proxy.** v1 has no model tools, runs on an ephemeral VM and uses a dedicated, narrowly scoped credential with its own spend limit, exposed to one step. A proxy that keeps the key away from the CLI process would guard against the model or a dependency reading the key. The model has no tools, and dependencies are pinned and reviewed. The proxy would be infrastructure we host and monitor, for a risk this configuration has already shrunk. **Revisit this immediately if any model tool is enabled.**

## 6. How is it rolled back?

**Mechanisms chosen:** idempotent operations, a feature flag per workflow, and audit-log replay. Queue replay does not apply because there is no queue.

**Idempotent operations.**
- The workflow's only lasting effect is **one new, immutable run artifact** containing the event, per-item JSON, raw responses, summary and before/after source hashes. Prompt files are left out because they can be rebuilt from `commit_sha` and the template digest.
- Each item carries its idempotency key (section 3), so a replay of the same item under the same configuration shows up as a repeat, not new evidence.
- Running anything twice changes nothing beyond adding a second artifact.

**Kill switch without redeploying.** The repository variable `BATCH_REVIEW_ENABLED` is read by the always-running `control` job:
- When it is not exactly `true`, `control` writes and uploads a `status: disabled` event and the paid `batch` job does not start.
- The watchdog can therefore tell **disabled** from **missed** and **failed** (section 2).
- Changing the variable needs no commit and no redeploy.
- The second kill switch is GitHub's "Disable workflow" action. It produces no event, so the watchdog would report the run as missed, which is the safe direction.
- These are the steps the designated repository maintainer takes when a threshold in section 3 says "kill switch off".

**Finding a bad run.** A run is bad if it is marked `quality_suspect`, fails the quality sample, or used a configuration later found to be faulty. The designated repository maintainer filters the run events by `config_key`, `models_observed`, `cli_version`, `commit_sha` or `utc_date` to list every run and item affected.

**Marking it superseded.** Neither the workflow nor a maintainer deletes or overwrites an artifact. Artifacts still expire under GitHub's retention policy. The upload sets `retention-days` to the repository's maximum, whose value is not verified (assumption A12). The superseded-runs record keeps run IDs and digests after an artifact expires. A bad run is marked superseded in audit metadata: an entry in a superseded-runs record that changes only through a reviewed PR (location to be decided, ship blocker 9), giving `run_id`, the affected items, the reason and the replacing run. Quality reports and readers skip superseded runs.

**Replaying only what is affected.**
1. If the bad configuration is still on `main`, restore the last known-good configuration first through a reviewed PR. The last known-good configuration is the most recent `config_key` whose runs passed both canaries and met the quality threshold.
2. Dispatch from `main` with `target_sha` set to the commit the bad run reviewed (it must be reachable from `main`), `items` set to the affected items only, and `reason: "replay superseded run <run id>"`.

**Why source rollback is unnecessary.**
- The workflow cannot change source code. The repository token is `contents: read`, there are no write commands, and there is no PR or comment permission.
- The model has no tools.
- The reviewed checkout is read-only data.
- The production mode keeps the Part 7 checks that source hashes match before and after the run and that `git status` is clean.

The only things that could ever need reverting are the workflow, prompt, schema or script themselves, through a normal reviewed PR. A wrong finding is undone by marking it superseded, not by changing code. The real risk is a person acting on a false finding, which is what the quality sampling and kill switch are for.

## 7. Evaluate

**1. Which question has the weakest answer, and what would it take to answer it properly?**
Question 3, monitoring of quality. The thresholds (70% to ship and warn, 60% to kill) are provisional and not derived from data.
- The only evidence is one full run of about 10 findings, assessed by one reviewer, with known run-to-run variation.
- The owner is a placeholder, and the labels record has no home.
- A proper answer needs at least 30 human-labelled findings from at least five shadow runs, precision with its run-to-run variance, a second labeller on a subset to measure agreement, an agreed acceptable false-positive rate, and a named owner.
- The trigger answer is the next weakest: the watchdog shares the scheduler's failure domain, and three scheduler behaviours are still unverified assumptions.

**2. For each failure mode, is there a concrete response or only an acknowledgment?**
All ten have a concrete response in section 4: specific retry counts and delays, deadlines, reason codes, abort and continue rules, and run statuses. Each also has an automated check, with three limits:
- **Unverified error shapes:** the error shapes for rate limits, authentication and account spend limits (assumption A5) were never seen in a real run, so those classifier rules are designed but not shown to match.
- **Model regression under the same ID:** this is caught automatically only by the two canaries (assumption A11) and otherwise by human sampling.
- **Context degradation across runs:** the cross-run part of the check depends on human labels. Only the per-item checks are automated.

**3. If the workflow gets it wrong, what does the mistake cost, and does monitoring catch it before the cost grows?**
- **Money:** at most USD 0.60 per run, USD 1.00 per UTC day, and USD 31 a month at most (about USD 23 for weekday runs alone), with the account limit as a backstop. Budget overruns are caught before the call by the pre-call guard.
- **Wrong advice:** this is the real cost. A false finding costs a reviewer's time and trust. A false high-severity finding could lead someone to add unnecessary defensive code or change validation behaviour.
  - Sampling every high-severity finding on the day it lands, with the kill rule for any harmful false high, catches this **within one working day** while the designated repository maintainer keeps up with sampling.
  - Lower-severity precision decay shows up within about 7 runs (20 labelled findings at about 3 per run).
  - Missing a real bug costs nothing beyond today's situation, because nothing depends on the workflow, but monitoring does not detect it.
  - If sampling stops, the quality signals stop too. That is why the owner is part of the open question.

**4. Could this ship Monday?** No. See section 8.

## 8. Could this ship Monday (2026-10-05)?

**No.** It is a design, and the prerequisites below are missing. None of them is built or provisioned here.

1. **Dedicated Anthropic API key**, owned by a service account or workspace, with a spend limit and a 90-day rotation policy. It does not exist in the repository.
2. **Quality ship gate:** at least 30 human-labelled findings from at least five shadow runs at 70% precision or better. The Part 7 baseline is about 10%, so the prompt and schema must be revised first (for example, only report states reachable from a request or a repository call, and require the `snippet` field).
3. **Branch protection on `main`**, with required review of every change to the workflow, script, prompt, schema and approved digests.
4. **Named ownership:** a CODEOWNERS entry and a real person or team in place of the designated repository maintainer placeholder.
5. **Enforced egress control:** an organisation-approved, SHA-pinned egress-enforcement action or network proxy in block mode, with the allowlist confirmed.
6. **Dependency provenance review** of every action (pinned by SHA) and the CLI package (pinned version, integrity-checked lockfile).
7. **Production script mode:**
   - no calibration dependency and no injected test input;
   - new exit criteria;
   - transient-only retries with backoff and an INT/TERM trap that stops each call's process group;
   - a `scheduled` mode that reads from the read-only data directory;
   - `snippet`, symbol and canary checks, and the exact model match;
   - the `run-event.json` writer, the context-size ceiling and daily-budget input.
8. **Linux validation:** `self-test` and `preflight` passing on `ubuntu-24.04`.
9. **Locations decided** for the quality labels record and the superseded-runs record.
10. **Workflow and watchdog files**, the `BATCH_REVIEW_ENABLED` variable and the dedicated secret.
11. **Verification of the assumptions** A1 to A9, A12 and A13 (section 9). A10 is covered by item 8, and A11 by the shadow runs in item 2.
12. **Peer verification by a human cohort peer.** The walkthrough recorded in section 10 used an agent as a stand-in.

## 9. Assumptions not demonstrated in this repository

The claims below are from external documentation or design intent. **None of them was demonstrated in this repository**, and each must be verified before launch.

| ID | Assumption | How to verify before launch |
|---|---|---|
| A1 | Actions evaluates cron in UTC, may delay or drop scheduled runs under load (more often at the top of the hour), and runs scheduled workflows only from the default branch | GitHub documentation, plus two weeks of observed start times in shadow runs |
| A2 | The watchdog's failure notification reaches the designated repository maintainer. GitHub's documentation is understood to send notifications for scheduled runs to the user who last changed the cron, who would not necessarily be the maintainer | GitHub documentation, plus a deliberate watchdog failure in shadow mode |
| A3 | GitHub disables scheduled workflows in public repositories after 60 days without repository activity | GitHub documentation; the watchdog reports it as missed only while the watchdog itself is still enabled |
| A4 | `vars.BATCH_REVIEW_ENABLED` is readable from the control job's `if:` and steps, and a change takes effect on the next run | A shadow run with the variable set to `false` |
| A5 | Rate-limit, overload, authentication, account spend-limit and retired-model errors appear in the CLI's JSON result as `api_error_status` or error subtypes that the classifier can tell apart. The CLI may also retry internally within the 120 s call timeout | Free synthetic fixtures do not prove this; check against CLI documentation and record real occurrences |
| A6 | GitHub masks secrets in logs, and the CLI does not write the API key into the run directory | GitHub documentation; scan the run directory for the key's prefix in a shadow run |
| A7 | `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` stops the CLI's non-model traffic, so the allowlist needs only the model API host | CLI documentation, plus the egress control's audit log in a shadow run |
| A8 | With an API key, `total_cost_usd` (`costBasis: "list"` in Part 7) closely tracks the amount billed, and `--max-budget-usd` stops a call close to its cap. Part 7 notes the CLI may check the cap only between turns | Compare a week of run events with account usage reports |
| A9 | GitHub-hosted runner minutes are free for public repositories, and the runner exposes a stable image identity that preflight can compare | GitHub documentation; record the image identity in shadow runs |
| A10 | The Part 7 script behaves the same under bash 5 and GNU tools on `ubuntu-24.04` | Ship blocker 8 |
| A11 | The canary expectations (`CrashController` gives `no_issues`; `VetController` reports `page`) hold for Haiku 4.5. Each rests on one Part 7 call | At least five shadow runs |
| A12 | The artifact retention maximum and its effect on audit evidence. Uploaded artifacts are not overwritten by later runs | GitHub documentation and the repository's Actions settings |
| A13 | The isolation flags and `--max-turns` still behave on Linux as they did in Part 7. Part 7 observed every flag being accepted by Claude Code 2.1.286 on macOS; `--max-turns` is not listed in `claude --help` | Preflight on `ubuntu-24.04` (ship blocker 8) |

## 10. Peer verification record

**This walkthrough used an agent as a stand-in for a cohort peer. It was not a human walkthrough** (ship blocker 12).

**Procedure.**
- **Reviewer:** one fresh, read-only reviewer agent with no conversation context.
- **What it got:** only this document's path. It was told to read only this file and ignore this section.
- **What it was asked to do:**
  1. Restate the topology and the trigger, each with its reason, in one sentence of its own words.
  2. Mark each of the nine verification elements present or missing, citing the section.
  3. List every ambiguous or unsupported statement.
- **Rule for a second round:** a misunderstanding of the topology or trigger, or a missing element, would have required revising the wording and a repeat walkthrough with a new context-free reviewer.

### Round 1: restatements (exact)

> TOPOLOGY: The reviewer runs as a single job on a fresh GitHub-hosted `ubuntu-24.04` VM with a `contents: read` token, because a short, bounded five-file batch whose only output is an artifact fits a disposable CI runner. A new VM each run acts as the sandbox, and nothing stays alive holding a credential.

> TRIGGER: A weekday cron at 04:17 UTC is the primary trigger, because the advisory output has no automated consumer and Stockholm reviewers can wait up to a day for it. A separate watchdog runs at 10:17 UTC, and manual dispatch is kept only for recovery and replay.

Both restatements match the intended topology and trigger, including the reason for each. Each took two sentences rather than the one requested. In both, the first sentence alone carries the decision and its reason, so the summary sentences were not reworded.

### Round 1: checklist (exact)

> (1) PRESENT: s0 Summary; s1 "Where does it run, and why?"
>
> (2) PRESENT: s0; s2 "How is it triggered, and why?"
>
> (3) PRESENT: s3 "Signals, thresholds and owners" (USD 0.60/run, USD 1.00/day, 12,000 tokens, 15/20 min, precision 70%/60% over 20, more than 1 reference failure, more than 0 denials)
>
> (4) PRESENT for all six: s4 Infrastructure failure matrix. None lacks a response. Rate limit, auth and account-limit detection all depend on unverified A5.
>
> (5) PRESENT, with caveats: s4 AI-specific failure matrix. Silent context degradation's cross-run check (precision by file length) relies on human labels, so it is not automated. Model regression under the same model ID is caught only by the two canaries (A11, each based on one call). Check (3) for hallucinated references is admitted to be a heuristic.
>
> (6) PRESENT: s5 "Model tool surface: none", plus "Tools on the runner"
>
> (7) PRESENT: s5 Caps. Turn cap is `--max-turns 2`. Dollar caps are USD 0.10/item, USD 0.60/run, USD 1.00/UTC day, about USD 23/month, plus an account backstop.
>
> (8) PRESENT: s6. Picked: idempotent operations, a feature-flag kill switch (`BATCH_REVIEW_ENABLED`, with "Disable workflow" as a second switch), and audit-log replay. Queue replay is ruled out.
>
> (9) PRESENT: "Open question": "What false-positive rate is acceptable for advisory findings, and who owns the daily quality sample and escalation decision?" It is the last section and nothing follows it. It sits after section 10 and has no number.

The "about USD 23/month" in item (7) reflects the round 1 text. The monthly figure is now USD 31 at most (row 6 below).

**Result:** no misunderstanding and no missing element, so the procedure did not require a second round, and none was run.

### Round 1: ambiguous or unsupported statements, and the revision for each

The reviewer listed 17 statements (rows 1 to 17), all accepted and revised. Row 18 comes from the caveat in checklist item (5). Row 19 is an error found while revising.

| # | Reviewer finding (paraphrased) | Revision |
|---|---|---|
| 1 | Section 2 said "protected default branch", but `main` has no protection | Now says `main` is not protected today and that protecting it is ship blocker 3 |
| 2 | Section 8 cited assumptions A1 to A9, but A1 to A11 exist | Item 11 now lists every assumption and which blocker covers each |
| 3 | Worst-case retry timing could exceed `timeout-minutes: 20` | Retries cut to 2 (30 s, 90 s); added a 15-minute deadline for starting new items or attempts (`not_run_deadline`); the stop rule is now 2 rate-limited items in a row |
| 4 | "Room for one retry" did not match up to 3 retries per item | The run row now says one extra full-cap attempt, after which the guard blocks retries as `not_run_budget` |
| 5 | The guard formula subtracted spend twice | Now `run_spent + 0.10 ≤ 0.60` and `day_spent + 0.10 ≤ 1.00` |
| 6 | The monthly ceiling ignored weekend dispatches and gave no amount | Account limit set to USD 31 (31 days × USD 1.00); USD 23 is given as weekday runs only |
| 7 | "Artifacts are kept, never deleted" ignored retention | Now: never deleted or overwritten by the workflow or a maintainer, but they expire under retention (A12); the superseded-runs record keeps IDs and digests |
| 8 | `config_key` said "five configuration values" without naming them | The five fields are named |
| 9 | Watchdog classification after a recovery dispatch was unclear | Added the **recovered** classification for runs on the same UTC date |
| 10 | Evaluate Q2 overstated automated detection | Q2 now names the limits: A5 error shapes, canary-only detection of same-ID regression, and human-labelled cross-run context checks |
| 11 | `--max-turns 2` leaves zero headroom, and that was not discussed | The caps table explains that the lack of headroom is deliberate and fails loudly |
| 12 | The Managed Agents dismissal was unsupported | Now cites the assignment brief's beta and Zero Data Retention statement and notes v1 has no tools to sandbox |
| 13 | CLI flags and version were presented as fact | Added A13; section 5 says they were observed in Part 7 on macOS only |
| 14 | 60 lines and 20 points were unexplained numbers | Both labelled provisional, with the 3-to-2 file split given; the 12,000-token ceiling is labelled provisional, with Part 7's 5,267 to 5,804 cache-write tokens given as a proxy basis |
| 15 | The baseline precision figures were not shown | Now attributed to the single-reviewer assessment in `headless-run-2026-10.md` section 6, with the findings listed |
| 16 | The timeout retry count differed from the general policy | The retry policy now gives 2 retries for rate limit, overload and 5xx, and 1 for a timeout |
| 17 | Section 8 said "section 10 is an agent stand-in" while section 10 said "Pending" | Section 10 now holds this record |
| 18 | Model regression under the same ID relies on two canaries | Stated in Evaluate Q2 (see row 10) |
| 19 | (Found while revising) The baseline said "four overstated `high` findings" | Corrected to **five**: `PetTypeFormatter` lines 47 and 54, `PetValidator` lines 40 and 44, `Visit` line 56 |

## Open question

What false-positive rate is acceptable for advisory findings, and who owns the daily quality sample and escalation decision?

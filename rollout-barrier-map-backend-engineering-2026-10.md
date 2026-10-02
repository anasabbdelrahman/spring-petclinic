# Rollout barrier map: Backend Engineering (2026-10)

- Assignment: Week 4, Part 9 "Map Your Rollout" (Engineering track)
- Month: October 2026
- Team: Backend Engineering, 7 engineers (2 senior, 4 mid-level, 1 junior)
- Scope: engineering use of Claude Code for coding, pull-request review, CI automation and headless agents

## Scenario and evidence

**This map uses a virtual team scenario, which the homework allows.** Backend Engineering, its observations, its approvers and the outside peer are part of that approved scenario. None of it is a verified fact about a real employer.

The Spring PetClinic repository is a **training sandbox** that models issues the virtual team could meet. Its results from the Week 4 exercises are **supporting evidence** for the scenario, not claims about a real employer. Under each barrier, the scenario observation comes first and the sandbox evidence follows.

Sandbox documents cited:

- `task-risk-2026-09.md` (Part 4)
- `hook-test-2026-09.md` (Part 4)
- `headless-run-2026-10.md` (Part 7)
- `agentic-workflow-design-headless-batch-review-2026-10.md` (Part 8), called "the Part 8 design" below

## Barrier 1: no approved service-owned credential or access route for unattended agents

**Category:** Technical (API keys).

**Observed (virtual team):** The team reaches Claude Code through individual subscriptions and personal setup tokens. That works for interactive development, but there is no approved, service-owned credential for an unattended CI agent, and personal credentials must not be reused for automation. Setting up the approved route needs the Engineering Manager, Platform Engineering and Security to agree on ownership, secret storage, rotation and spending limits.

- Current access: individual subscriptions and personal setup tokens
- Current approved route for unattended runs: none
- Approvers: Engineering Manager, Platform Engineering and Security

**Supporting sandbox evidence:**

- The Part 8 design, repository-state table: "No dedicated, service-owned Anthropic API credential exists in the repository." It adds that the existing personal subscription token must not be used for unattended production runs.
- The Part 8 design, section 5 "Credential" and section 8, ship blocker 1: a dedicated key owned by a service account or workspace, with a spend limit and 90-day rotation, is a precondition for launch and does not exist.
- `task-risk-2026-09.md`, task 4: the PR-review workflow's runner holds a repository secret with `pull-requests: write`, rated HIGH with human approval of every change.
- `hook-test-2026-09.md`, section 1: the container test authenticated with a personal Claude setup-token entered by hand, which `claude setup-token` had displayed once in the user's terminal.

**Closest published pattern:** TELUS, "one approved, secure way in, inside familiar tools".

**Where it stops fitting:** TELUS built one internal platform for about 57,000 team members (TELUS's own figures). The virtual seven-person team has no company-wide internal AI platform and cannot build one itself. TELUS's account is also about developers reaching Claude through tools they already use. This barrier is about a credential for an unattended job that no person is driving, which the TELUS account does not describe.

**Peer check (virtual peer exercise): shared.** Their function also requires a service-owned credential to have a named owner, restricted secret storage, rotation and spending limits before an unattended job is approved.

## Barrier 2: valid-looking Claude review output still needs human verification

**Category:** Workflow (review).

**Observed (virtual team):** The team reviews Claude-generated changes through ordinary pull-request review, test results and manual diff inspection. In the sandbox exercises, structurally valid review output passed the schema and automation checks, yet more than half of its findings were later judged overstated or unreachable. The workflow has no written checklist that requires evidence for every finding, explicit human confirmation of high-severity findings and a recorded decision to accept or reject each one.

- Correction example: the Part 7 headless batch review produced schema-valid findings, but human verification found that the surrounding code prevented many of the states they described.
- Missing controls: an evidence checklist, mandatory human verification of high-severity findings, and a recorded accept-or-reject decision.

**Supporting sandbox evidence:**

- `headless-run-2026-10.md`, section 6: every real item passed the CLI schema and the script's checks, "yet more than half the findings are overstated or describe states the surrounding code prevents".
- The Part 8 design, section 3 "Baseline": by one reviewer's assessment, 1 of 10 findings was clearly supported, and all five `high`-severity findings were judged overstated or unreachable.
- `headless-run-2026-10.md`, section 6: the same file, prompt and model gave 1 finding in one run and 3 in another.
- `task-risk-2026-09.md`, Trial B: a batch of verification checks looked like it had run but had not, because zsh did not word-split the file list. The checks were rerun under bash.

**Closest published pattern:** incident.io, "when someone reports a win, record what Claude specifically did". The team also wrote down the rule that every existing test must give identical output.

**Where it stops fitting:** incident.io's optimisation could be checked against a deterministic oracle, its existing tests. AI review findings have no such oracle and need human judgement: whether a finding is right can only be decided by a person reading the surrounding code. The pattern also records a success, while this barrier is about output that looked successful and was not.

**Peer check (virtual peer exercise): shared.** Their team also treats schema-valid AI output as unverified until a person checks its evidence. They use evidence links and require a second reviewer for high-severity findings.

## Barrier 3: no named engineering owner or written escalation path for Claude-related automation decisions

**Category:** Workflow (escalation).

**Observed (virtual team):** Claude-related questions and exceptions are handled informally by whichever senior engineer is available. Because no owner or escalation path is written down, similar decisions can get different answers, and unresolved questions can delay a pull request. A permanent solution needs the Engineering Manager to name an operational owner and agree the escalation boundary with Platform Engineering and Security.

- Current decision maker: an available senior engineer, informally
- Result of unclear ownership: inconsistent answers and delayed pull requests
- Approvers: Engineering Manager, Platform Engineering and Security

**Supporting sandbox evidence:**

- The Part 8 design, repository-state table: "No CODEOWNERS or MAINTAINERS file exists". The design therefore uses the placeholder "designated repository maintainer" for every owner (section 3), and naming a real owner is ship blocker 4.
- The Part 8 design, open question: "who owns the daily quality sample and escalation decision?" is unanswered.
- The Part 8 design, section 10: the peer walkthrough used an agent as a stand-in, not a human peer (ship blocker 12).
- `task-risk-2026-09.md`, task 8: Trial A was intended to have a human approve every action, but per-action approval was not verified and a session notice reported auto mode. The explicit human-approval step had to be moved to a later task.

**Closest published pattern:** no pattern fits. The nearest is incident.io writing down its rule, but that pattern is about recording what Claude did in a win, not about who decides or who is asked when something is unclear. The plan therefore starts from the workflow fix: an answer the team agrees on and writes down.

**Where it stops fitting:** none of the three accounts names who owns Claude-related decisions or how a teammate escalates. The TELUS account implies a central platform owner but does not describe escalation.

**Peer check (virtual peer exercise): shared.** They have seen unclear ownership delay automation exceptions. Their preferred solution is a named platform owner, with Security as the escalation point for credential, permission and network-policy questions.

## Cultural barriers

None of the three barriers is cultural. The scenario has no evidence strong enough to make uneven trust across seniority, skill worry, job worry or a sceptical manager one of the three biggest barriers.

## Outside-peer verification (virtual peer exercise)

**This is a virtual peer exercise, permitted for the homework. No real person was interviewed.** The virtual peer is from the DevOps / Platform Engineering function and reviewed all three barriers on 2026-10-02. Their answer is recorded under each barrier: all three are **shared**.

## Evaluate

**1. Are the barriers in one category or spread across all three, and which fix does the next step need?**

They are spread across two categories: one Technical and two Workflow, with no Cultural barrier.

- **Technical (barrier 1):** needs named owners and approval from the Engineering Manager, Platform Engineering and Security.
- **Workflow (barriers 2 and 3):** need a review and escalation process that the team agrees on and writes down.

The next step needs the workflow fix: an agreed, written review and escalation checklist.

**2. Which barrier can the author move, and which needs someone else's approval?**

- **This month, by the author:** barrier 2, by organising a review-calibration session and producing the checklist.
- **Needs other people's approval:** barrier 1, and the permanent-ownership part of barrier 3.
- **This quarter:** the virtual team would seek approval for the service credential, the secret-storage design, spending controls and a named operational owner.

**3. Where do the external case studies stop being applicable?**

- **TELUS:** the virtual seven-person team has no company-wide internal AI platform.
- **incident.io:** its optimisation had deterministic existing tests, while AI review findings need human judgement.
- **Anthropic's own teams:** their examples show use cases, but they do not define this team's access, review or escalation controls.

## Verification

- [x] Each barrier has its category, the virtual team's observation, the closest pattern with where it stops fitting, and the virtual peer's answer
- [x] The virtual peer is from another function and marked all three barriers **shared**
- [x] The scenario and peer are labelled virtual, and the sandbox evidence is labelled supporting
- [x] Evaluate questions 1–3 are answered
- [x] The next step has a named action, a date and a handover

## Next step (October 2026)

- **Action:** run a 45-minute review-calibration session with 3 teammates using five real Claude findings. For each finding, record whether it is accepted or rejected, the supporting evidence, and when escalation is required.
- **Date:** 2026-10-28.
- **Handover:** publish a one-page Claude review and escalation checklist in the engineering documentation space, and present it at the next Backend Engineering team meeting.

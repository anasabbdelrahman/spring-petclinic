# AI governance posture: virtual Engineering organisation (2026-10)

- Assignment: Week 4, Part 10 "Map Your Org's AI Governance Posture" (Engineering track only)
- Date: 2026-10-02
- Scope: engineering use of Claude Code in interactive sessions, repository rules and settings, models, tools, MCP servers, plugins, CI and headless agents, deployment approvals, data classification and audit retention
- Companion proposal: `policy-proposal-tool-surface-allowlist-2026-10.md`

## Scenario and evidence

**This map describes a virtual Engineering organisation, which the homework allows.** It contains the seven-person virtual Backend Engineering team from Part 9 (`rollout-barrier-map-backend-engineering-2026-10.md`), together with the Engineering Manager, Platform Engineering and Security roles that team depends on. None of it is a verified fact about a real employer.

The Spring PetClinic repository is a **training sandbox**. Its Week 4 artefacts are **supporting evidence** for what the virtual team versions and how it works. They are not evidence of any real organisation's controls.

The map records what exists today, not what should exist.

Repository settings, hooks, rules and CLI restrictions are **team controls**. Nothing in the evidence shows that any of them is managed centrally across the organisation, so none of them is counted as org enforcement.

**Enforcement and delivery.** In the "What enforces it" columns:

- **Centrally managed settings on an organisation-managed device** enforce a rule: no team setting, user setting or command-line flag can loosen them.
- **Centrally delivered settings on a device the organisation does not manage** only deliver a rule: the user can bypass them, so they do not guarantee it.
- The virtual organisation uses **neither route** today.

## Three-layer map

| Layer | What policy exists | Who owns it | Where documented | What enforces it |
|---|---|---|---|---|
| Individual | Each engineer chooses, per task, the permission mode, when to use plan mode or clear context, and which model to run. In the sandbox this meant auto mode for low-risk documentation (Trial B), manual approval for staging a guardrail (task 10), and an explicit bypass-permissions invocation inside an isolated test container (T2). | Each engineer | In no shared policy. The choices were recorded only after the fact, in `task-risk-2026-09.md` and `hook-test-2026-09.md`. | Claude Code's permission prompts and auto-mode classifier, within the mode the engineer picked, plus the engineer's judgement. Nothing outside the session. |
| Team | 1. A `PreToolUse` hook that blocks the tested direct force-push forms to `main`. 2. A path-scoped rule for the controller-test wiring. 3. A CI review job that may use only Bash, may use no MCP tools and is capped at 10 turns. 4. A headless review script with a fixed model, a turn cap, dollar caps, no tools and no MCP servers. 5. A task-risk table that assigns oversight per task. There is no project `CLAUDE.md` and no team plugin. Nobody has a scheduled review of the audit trail. | The virtual Backend Engineering team as a group. No named person: the repository has no CODEOWNERS or MAINTAINERS file. | `.claude/settings.json`, `.claude/hooks/block-force-push-main.sh`, `.claude/rules/controller-test-wiring.md`, `.github/workflows/claude-review.yml`, `headless-batch-review-2026-10.sh`, `task-risk-2026-09.md` | Repository settings and hooks: the hook runs on Claude's Bash calls, matches command text only and can be edited on a developer checkout. CI and headless-runner configuration: the CLI flags in the workflow and the script. Human review of pull requests, which is optional because `main` has no branch protection. The rule file is guidance for the model, not enforcement. |
| Org | No written org-wide AI policy. Questions about credentials, permissions and new automation go to the Engineering Manager, Platform Engineering and Security, who decide each case when it comes up. | Nobody is named | Nowhere | Human review, case by case. No centrally managed settings on engineering devices and no centrally delivered policy. |

## Org-layer policy areas

| Policy area | Current state | What enforces it |
|---|---|---|
| Model selection policy (which models for which data classes) | non-existent | nothing |
| Tool-surface allowlist (approved MCP servers and plugins) | non-existent | nothing |
| Deployment approval gates (when new agentic infrastructure needs sign-off) | case-by-case | Human review by the Engineering Manager, Platform Engineering and Security, decided per request. No technical gate. |
| Data classification → permitted-tool mapping | non-existent | nothing |
| Audit retention by data class | non-existent | nothing |

No row is `documented`, so the table contains no enforcement gap in the sense of a written policy that nothing enforces. Every gap here is a missing policy.

### Evidence per row

- **Model selection: non-existent.** Each artefact picks its own model, and nothing ties the choice to the kind of data sent:
  - the orchestration recipe uses `claude-sonnet-5-5` (`scaling-notes-2026-09.md`, commit `00d5c9b`);
  - the headless script uses `claude-haiku-4-5-20251001` (`headless-batch-review-2026-10.sh`);
  - the Part 6 maker ran on the default `claude-opus-5-5`, and its checker ran on Haiku (`close-the-loop-2026-10.md`);
  - the CI review job sets no model (`claude-review.yml`).

  The headless script's `unexpected_model` check enforces that script's own choice. It is a team control.
- **Tool-surface allowlist: non-existent.** The CI job runs with `--tools "Bash"`, `--disallowedTools "mcp__*"` and `--strict-mcp-config`. The headless script and the Part 6 checker run with no tools and no MCP servers. These are repository CLI restrictions, chosen separately for each artefact. Nothing defines which MCP servers or plugins an engineer may add to an interactive session, and the repository declares neither.
- **Deployment approval gates: case-by-case.** Evidence:
  - Part 9, barrier 1: setting up an unattended-agent credential needs the Engineering Manager, Platform Engineering and Security to agree.
  - Part 9, barrier 3: questions are otherwise handled "informally by whichever senior engineer is available".
  - The Part 8 design (`agentic-workflow-design-headless-batch-review-2026-10.md`, repository-state table and section 8): `main` has no branch protection and there is no CODEOWNERS file. All actions are allowed and SHA pinning is not required. The egress control the design needs must be "organisation-approved", but no approval route exists.
- **Data classification → permitted tools: non-existent.** No data-classification scheme appears in any Week 4 artefact. Protection against leaking secrets is built separately in each artefact:
  - token scans in `hook-test-2026-09.md` §1;
  - stderr redaction in the recipe's synthetic harness (`scaling-notes-2026-09.md`);
  - "never print a credential" in the CI prompt.
- **Audit retention: non-existent.** Evidence:
  - Headless and recipe run records stay under the git-ignored `build/` directory on one machine (`headless-run-2026-10.md`, metadata).
  - Durable off-machine storage is listed as missing (`headless-run-2026-10.md` §9).
  - The GitHub artefact retention period is unverified (Part 8, assumption A12).
  - OpenTelemetry "wasn't configured for these runs and captured nothing" (`hook-test-2026-09.md` §7).

## Evaluate

### Policy gaps: where teams improvise

1. **Which MCP servers and plugins may be used.** The same team bans every MCP tool in CI and in the headless script, yet has no rule for interactive sessions. Each choice is made inside one artefact's configuration.
2. **Which model may see which data.** The model is picked per run for cost or capability, never by data sensitivity, because no data classes exist.
3. **Credentials for unattended agents.** There is no approved service-owned credential. The CI review job authenticates with a Claude Code OAuth token stored as a repository secret (`claude-review.yml`), and Part 8 states that a personal subscription token must not be used for unattended production runs.
4. **Audit records and retention.** Every artefact designs its own records (NDJSON under `build/`, a stream-json capture, a report) and its own retention, which is usually "until the folder is deleted".
5. **Ownership and sign-off.** The person who decides changes from case to case, so similar requests can get different answers (Part 9, barrier 3).

### Policy failures: where strict rules make teams route around them

**No confirmed org-level policy failure was found.** There is no org rule, so no org rule is strict enough to route around. Three cases came close. They are recorded so they are not misread as org failures:

- **Hook false positives (team control).** The force-push hook blocked a harmless read-only command that only contained the force-push text as an argument (`task-risk-2026-09.md`, task 7).
- **Non-interactive manual mode (individual choice).** With nobody present to approve, manual mode denied an allowed push. In effect it became deny-all, which pushes unattended runs towards less supervised modes (`hook-test-2026-09.md` §7).
- **The personal-credential prohibition (gap, not failure).** Part 9 says personal credentials must not be reused for automation, but no approved alternative exists. Because the prohibition is not written org policy, it is recorded as gap 3 above.

### Impact versus drafting cost

| Policy area | Impact if it existed | Cost to draft | Why |
|---|---|---|---|
| Model selection by data class | Medium | High | Needs a data-classification scheme first |
| Tool-surface allowlist | High | Low | The current inventory is small. CI and headless runs already deny MCP. It needs no classification scheme to start, and centrally managed settings on managed devices can enforce it. |
| Deployment approval gates | Highest | High | Every Part 8 ship blocker and Part 9 barriers 1 and 3 lead here, but it needs the Engineering Manager, Platform Engineering and Security to agree a process, plus branch protection and named owners |
| Data classification → permitted tools | High | Highest | Foundational for three rows, but defining data classes is an organisation-wide exercise |
| Audit retention by data class | Medium | Medium | Depends on data classes and on a storage decision |

- **Highest impact:** deployment approval gates.
- **Cheapest to write:** the tool-surface allowlist.
- **Selected for the proposal: the tool-surface allowlist.** It gives the most impact for the least drafting cost.
  - It is the one row that can move directly from `nothing` to enforced by a mechanism, rather than to a written rule nothing enforces.
  - It closes the inconsistency between interactive sessions and CI described in gap 1.
  - Its registration record gives the later data-classification and audit-retention work an inventory to start from.

### Natural owner

- **Proposed owner:** Head of Platform Engineering. The allowlist is delivered through engineering device management and runner configuration, which Platform Engineering operates.
- **Required co-approver:** Security.
- **Consulted:** Backend Engineering Manager.

This matches the Part 9 virtual peer's preference for "a named platform owner, with Security as the escalation point".

**Have you talked to them?** Not yet. This is a virtual draft proposal prepared for owner review; it is not adopted policy.

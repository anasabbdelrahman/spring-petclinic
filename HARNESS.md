# Team Harness

How this repository configures Claude Code. Scope: committed artifacts only.
`.claude/settings.local.json` is personal and git-ignored, so it is excluded here.

## Components

1. Context engineering - `CLAUDE.md` states build, code style, architecture, data/profile, and i18n rules so a session starts informed instead of re-deriving them from the tree.
2. Tool surface - built-in file/search/Bash tools plus one pinned Playwright MCP server; the reviewer sub-agent is restricted to `Read, Grep, Glob`.
3. Control flow - `/review-changes` is human-invoked only (`disable-model-invocation: true`), forks context, and delegates the staged diff to a sub-agent.
4. Quality gates - a staged-diff review step in front of the build's own spring-javaformat, checkstyle, and nohttp gates.
5. Reliability and safety - a `PreToolUse` hook refuses writes to `.env` and `.env.*`; the reviewer is read-only by tool grant, not merely by instruction.
6. Observability - a `PostToolUse` hook appends every Write/Edit to `.claude-tool-log.txt`.
7. Effectiveness levers - `function-summary` for pre-change comprehension, the `focused-review` output style for evidence-first answers, and `model: haiku` + `maxTurns: 8` to keep review cheap and bounded.

## Artifacts

| Artifact | Component(s) served | Rationale | If it disappeared |
| --- | --- | --- | --- |
| `CLAUDE.md` | Context engineering | [Effectiveness] One read replaces repeated codebase archaeology. | Wrong build commands, tabs-vs-spaces churn, invented Pet/Visit write repositories. |
| `.mcp.json` | Tool surface | [Effectiveness] Lets a session drive the real Thymeleaf UI instead of guessing at it. | No click-through of the running app; UI claims stay unverified. |
| `.claude/settings.json` | Control flow, Reliability and safety, Observability | [Reliability] Hooks enforce what a prompt can only request. | No pre-commit reminder, `.env` files writable, no write audit trail. |
| `.claude/skills/function-summary/SKILL.md` | Effectiveness levers | [Effectiveness] Forces purpose/IO/side-effects/risk to be stated before a method is edited. | Edits to unfamiliar controllers with side effects discovered after the fact. |
| `.claude/skills/review-changes/SKILL.md` | Control flow, Quality gates | [Quality] Turns "review before commit" into one repeatable command with a fixed input. | Ad-hoc review prompts of inconsistent depth, varying per developer. |
| `.claude/agents/reviewer.md` | Quality gates, Reliability and safety | [Quality] Fixed rubric, severity ranking, and a read-only tool grant. | Review format drifts and the reviewer could edit the code it is judging. |
| `.claude/output-styles/focused-review.md` | Effectiveness levers | [Effectiveness] Verdict/Evidence/Risks/Next action is faster to act on than prose. | Longer answers that blur verified facts with assumptions. |
| `.gitignore` | Reliability and safety, Observability | [Reliability] Keeps local audit logs and generated Playwright artifacts out of commits. | Local logs and browser artifacts would pollute Git status and risk accidental submission. |

## Gap assessment

- Context engineering: solid for build and architecture; no per-package guidance, which the domain layout makes unnecessary.
- Tool surface: adequate; no MCP server for the database, so H2 checks go through Bash and the app.
- Control flow: one gate only. No PreCommit or Stop hook, so the reminder is advisory rather than blocking.
- Quality gates: review is manual to trigger. Nothing prevents a commit that skipped it; CI and the Maven/Gradle build remain the hard gate.
- Reliability and safety: `.env` protection is path-based. Secrets pasted into other files are not caught.
- Observability: the log records Write/Edit only. Bash commands and MCP calls are not logged.
- Effectiveness levers: no project-specific run or test skill; `CLAUDE.md` command list covers that need today.

## Balance

- No component is empty: all seven components have at least one justified artifact.
- Nothing is over-engineered: three short hooks, two skills, one agent, one output style, one MCP server. The agent uses `haiku` with `maxTurns: 8`, so the added gate is cheap.
- The balance is defensible because enforcement sits where it belongs: the build enforces style and correctness, hooks enforce the two things a prompt cannot (secret protection, audit), and everything else stays advisory so the harness does not fight the developer.

## Explicit rejection

- We do not set `enableAllProjectMcpServers`. It would automatically trust any MCP server added to `.mcp.json` later, including one introduced by a future branch or contributor. Servers stay individually approved.

## Cross-artifact references

- The `SessionStart` hook in `.claude/settings.json` reminds the user to stage changes and run `/review-changes`.
- `.claude/skills/review-changes/SKILL.md` sets `agent: reviewer`, delegating the staged diff to `.claude/agents/reviewer.md`.
- `.claude/agents/reviewer.md` reads `CLAUDE.md` for the conventions it enforces.
- The `PostToolUse` hook writes `.claude-tool-log.txt`, which `.gitignore` excludes so the audit trail stays local.

## Portability

- The `PreToolUse` and `PostToolUse` hooks depend on `jq` being on `PATH`. Without it, the protection hook fails and blocks writes; install `jq` (for example `brew install jq`) before use.
- Playwright MCP is pinned to `@playwright/mcp@0.0.80` and runs with `--isolated`, so the tool surface is reproducible across machines and leaves no browser profile behind.

## Verification evidence

- Playwright MCP connected from `.mcp.json` and exposed 24 `mcp__playwright__browser_*` tools.
- SessionStart hook registered from project settings with matcher `startup|resume` and the expected review reminder command.
- PostToolUse recorded the HARNESS.md creation in `.claude-tool-log.txt` at 2026-09-04T19:19:05Z.
- `PreToolUse` protection enabled for `.env` and `.env.*` files, refusing the write with exit code 2.

## Live demo sequence

1. Start a session and point at the `SessionStart` reminder in the transcript.
2. Run `/function-summary src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java:processFindForm` to show pre-change comprehension.
3. Attempt to write `.env` and show the `refused: .env files are protected` message.
4. Stage a small change, run `/review-changes`, and show the reviewer returning ranked findings without touching the tree.
5. Run `tail -n 5 .claude-tool-log.txt` to show the write audit trail.

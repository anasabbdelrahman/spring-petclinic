# Homework 1.7: Engineering: MCP (Playwright)

**Date:** 2026-09
**Repository:** spring-petclinic
**Branch:** homework/mcp-playwright

## Setup

| Item | Value |
|---|---|
| MCP server | Playwright |
| Configuration | Project-scoped `.mcp.json` |
| Command | `npx -y @playwright/mcp@latest --isolated` |
| Credentials | None required; `.mcp.json` contains an empty `env` object |

Verification output:

```
playwright: npx -y @playwright/mcp@latest --isolated - Connected
```

## Real engineering task

Browser verification of Spring PetClinic running at `http://localhost:8080`.

Through the Playwright MCP server, the following was carried out end to end:

1. Loaded the home page.
2. Opened **Find Owners**.
3. Submitted an empty Last Name search to list all owners.
4. Opened the first result, **George Franklin**.
5. Inspected the owner details (name, address, city, telephone) and pet details.
6. Captured a screenshot of the owner details page.
7. Checked the browser console for errors.

## Results

Two independent outcomes, which should not be conflated:

| Subject | Result |
|---|---|
| MCP integration | **PASS** |
| Application verification | **DEFECT FOUND** |

**MCP integration: PASS.** Every requested browser action executed successfully. The server connected successfully, drove navigation and form submission, read page content, captured the screenshot, and returned console output. No MCP-side failure occurred at any step.

**Application verification: DEFECT FOUND.** The defect is in the application, not in the MCP tooling; the tooling is what surfaced it.

- `/owners/1` produced a `TypeError` because `hideMessages()` tries to access the `style` property of a missing `success-message` element.

A second console error, a CORS failure, was created by the investigation itself (a diagnostic `fetch` issued from the page) and is **not** an application defect.

## Generated local evidence

- `owner-1-details.png`: screenshot of the owner details page, saved in the project root.
- `.playwright-mcp/`: generated Playwright snapshots and console logs.

Both remain untracked. They are supporting evidence only and are not required submission files.

## Evaluation

### 1. What changed in the engineering workflow

Verification moved from description to execution. Previously, confirming a UI change meant asking a human to click through the app, or inferring behaviour from tests and source. With the Playwright MCP server, the agent drives the real browser against the running application and reports what the page actually did, including runtime JavaScript errors that no unit or `@WebMvcTest` test in this repository would catch, since those never execute page scripts.

The evidence changed too. Instead of a prose claim that a page works, the run leaves a screenshot and a console log on disk that a reviewer can open independently.

### 2. New failure modes introduced by MCP

- **Ambiguous error provenance.** The console showed two errors; only one was real. The CORS error was self-inflicted by the diagnostic step. Without care, tooling artifacts get reported as product defects.
- **Environment coupling.** The verification depends on the application already running at `localhost:8080`. A stopped app looks like a failing app unless the distinction is stated.
- **Untracked file generation.** The run wrote `owner-1-details.png` and `.playwright-mcp/` into the working tree. These are build artifacts and should remain untracked or be added to `.gitignore` if the integration is retained.
- **Supply-chain surface.** `npx -y @playwright/mcp@latest` resolves and executes the latest published package on every start, with no pinned version and no prompt.
- **Non-determinism.** Browser automation depends on timing and page state; the same script can pass and fail across runs in ways a unit test does not.

### 3. Whether the setup effort was worth the capability

Yes. Setup was a single `.mcp.json` file with no credentials and no local install step. The first real use found a genuine runtime defect on a core page. The cost was minutes; the return was a bug that the existing test suite does not cover.

### 4. Whether this MCP should be kept for the team

Keep it, with three conditions:

1. Pin the version instead of `@latest`, so runs are reproducible and package updates are a reviewed change.
2. Keep `.playwright-mcp/` and generated screenshots untracked, or add them to `.gitignore` if the integration is retained.
3. Treat it as a verification aid, not a replacement for automated tests. Findings it produces should become regression tests where possible. The `hideMessages()` defect is a good candidate.

### 5. Security and credential handling

No credentials are involved. `.mcp.json` contains an empty `env` object, so no secrets are stored in the repository or passed to the server. The server runs with `--isolated`, so no persistent browser profile, cookies, or saved logins are reused between runs.

The residual risks are supply chain and scope. `@latest` means unreviewed code executes at startup, which pinning addresses. The browser can reach any URL it is given, so it should be pointed at local or non-production targets only; nothing in this exercise touched a remote or authenticated system.

### 6. Deliverables and verification

**Deliverables**

- `.mcp.json`: project-scoped Playwright MCP configuration.
- `mcp-notes-2026-09.md`: this document.

**Verification performed**

- MCP server connection confirmed: `playwright: npx -y @playwright/mcp@latest --isolated - Connected`.
- Seven-step browser verification of Spring PetClinic executed in full against `http://localhost:8080`.
- Owner and pet details read directly from the rendered page.
- Console output captured and reviewed; each error classified as application defect or investigation artifact.

**Conclusion:** the MCP integration passed. The application verification it enabled found one real defect on `/owners/1`.

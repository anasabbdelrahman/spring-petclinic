# Security Audit Record: `OwnerController.java` - 2026-09

| Field | Value |
|---|---|
| Date | 2026-09-23 |
| Target | `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` |
| Branch | `homework/security-defense-in-depth` |
| Human reviewer | Anas Abdelrahman |
| AI reviewer | Claude (Claude Opus 5.5) |
| Detailed review | `security-review-owner-controller-2026-09.md` |

No application production code, tests, runtime configuration, or templates were changed by
either security review. The homework did add repository security tooling through
`.pre-commit-config.yaml` and `.secrets.baseline`. **No production finding was fixed during this
homework.**

## 1. Review sequence

| Step | Commit / event | Description |
|---|---|---|
| 1 | Baseline review, before `fe8ab12` | Static-only review of `OwnerController.java`, performed **before** the Security Requirements section existed. No files created, no HTTP requests. Six findings, nine rejected candidates. |
| 2 | `fe8ab12 Add detect-secrets pre-commit defense` | Adds `.pre-commit-config.yaml`, `.secrets.baseline` and `detect-secrets-hook-evidence-2026-09.txt`. See section 3. |
| 3 | `e66e4cf Add project security requirements` | Adds the `## Security Requirements` section to `CLAUDE.md`. |
| 4 | Post-policy review, after `e66e4cf` | Performed in a **fresh session**. Static reading plus temporary runtime probes in a `git archive HEAD` copy outside the repository, deleted afterwards. Nine findings, ten rejected candidates. |
| 5 | Human calibration | The human reviewer calibrated every post-policy finding (section 2). |

**Attribution limit.** The baseline was static-only; the post-policy review also ran runtime
probes. Differences between the two reviews therefore cannot be attributed to the Security
Requirements alone. See the detailed review, section 2.

## 2. Findings

Evidence status:

- **Probed**: observed in a temporary runtime probe.
- **Code**: established by reading code, templates, build files or configuration.
- **Unverified**: stated, but neither probed nor conclusively established by reading.

An **override** is recorded where the human calibration differs from the AI rating in severity
or classification.

| ID | Finding | AI severity | Evidence status | Human decision | Override |
|---|---|---|---|---|---|
| P-F1 | Mass assignment via nested `pets[n].*` binding; denylist binder at `OwnerController.java:57-60` | High | Probed | Confirmed real vulnerability, **High** | None |
| P-F2 | No authentication or object-level authorization (`:63-66`, `:68-174`) | High (conditional on deployment) | Code | Confirmed missing control; impact and severity **depend on deployment** | Fixed High rating not adopted; severity left deployment-dependent |
| P-F3 | Bulk PII enumeration through an empty search (`:94-97`, `:129-132`) | Medium | Probed | Confirmed behavior; security impact **depends on deployment and whether the data is real** | Fixed Medium rating not adopted; impact left deployment- and data-dependent |
| P-F4 | No CSRF protection on state-changing POSTs (`:73`, `:140`) | Medium | Code (the `th:action` token-injection detail is Unverified) | Confirmed missing control; **conditional today**, more serious with cookie authentication | Fixed Medium rating not adopted; classified as conditional |
| P-F5 | Missing `address` / `city` length bounds cause 500 (`Owner.java:51-57`) | Medium | Probed | Confirmed real robustness and error-handling defect, **Medium** | Reclassified as a defect rather than a vulnerability; severity unchanged |
| P-F6 | Unvalidated `page` returns 500 at both ends (`:91`, `:131`) | Medium | Probed; `page <= 0` also pinned by `OwnerNotFoundIntegrationTests.java:118-124` | Confirmed known robustness / error-hygiene defect, **Medium** | Reclassified as a known defect (PAID D5); severity unchanged |
| P-F7 | Exception-message exposure through `error.html:18` | Low (conditional) | Code; SQL text in messages is Unverified | Conditional, **Low** | None |
| P-F8 | Search binds the whole `Owner`; no server-side `lastName` bound (`:91`, `:94-100`) | Low | Code | Confirmed unnecessary attack surface, **Low** | None |
| P-F9 | No rate limit on owner creation (`:73-83`) | Low (conditional) | Code | Conditional and deployment-dependent, **Low** | None |

**False positives among reported findings: 0.** Ten further candidates were investigated and
rejected; they are listed in the detailed review, section 7.

**Baseline mapping.** Baseline B-F1 -> P-F2, B-F2 -> P-F3, B-F3 -> P-F1, B-F4 -> P-F4 and
B-F5 -> P-F6 / P-F7. B-F6 (the unreachable owner-ID mismatch check) moved to the rejected list as
not a vulnerability. P-F5, P-F8 and P-F9 are new in the post-policy review.

## 3. Detect-secrets defense - commit `fe8ab12`

`fe8ab12 Add detect-secrets pre-commit defense` adds:

- `.pre-commit-config.yaml`: the `detect-secrets` hook from `Yelp/detect-secrets`, rev `v1.5.0`,
  with `--baseline .secrets.baseline`.
- `.secrets.baseline`: the audited baseline.
- `detect-secrets-hook-evidence-2026-09.txt`: blocking evidence. A staged test-only canary file
  was rejected by the hook at `git commit` with exit status 1 and "Potential secrets about to be
  committed to git repo!". No commit was created, the hook was not bypassed, and the canary value
  was removed from the working tree, index and history.

None of these files was modified by this audit.

### 3.1 Accepted pre-existing credential - `k8s/db.yml:14`

| Field | Value |
|---|---|
| Location | `k8s/db.yml:14`, the `password` of the `demo-db` Kubernetes Secret |
| Type | Secret Keyword (detect-secrets) |
| Classification | **Real** committed credential. It is a weak demo value, but it is a real credential, not a false positive. Recorded as `is_secret: true` in `.secrets.baseline` after `detect-secrets audit` |
| Origin | **Pre-existing.** Committed upstream before this branch. **Not introduced by this change** |
| Disposition | **Temporarily accepted.** Tracked as an audited baseline entry so the hook reports new secrets but not this one. Not allowlisted: no pragma comment and no exclude pattern. `k8s/db.yml` is unchanged |
| Remediation | Inject the database credential from a deployment-managed secret, remove the committed password from `k8s/db.yml`, rotate the credential in every environment that used it, and regenerate the baseline so the entry drops out |

Accepting this entry is a temporary risk decision. It is not a statement that the credential is
safe.

## 4. Sign-off status

| Item | Status |
|---|---|
| Baseline review | Complete |
| Post-policy review | Complete |
| Human calibration of all nine post-policy findings | Complete, 2026-09-23, Anas Abdelrahman |
| Detect-secrets defense | Landed in `fe8ab12`; blocking behavior evidenced |
| `k8s/db.yml:14` | Real, temporarily accepted, remediation open |
| Remediation of P-F1 to P-F9 | **Not started.** All nine findings open |
| This audit record | Written; not yet committed |

The audit is signed off as a **record of review and calibration only**. It does not sign off any
remediation, because none was performed.

## 5. Remaining follow-up work

In the recommended order from the detailed review, section 9:

1. **P-F1**: replace the denylist binder with an allowlist or a form DTO, with the nested-binding
   regression test.
2. **P-F5**: add `@Size` bounds on `address` and `city` matching the schema.
3. **P-F8**: bind search as a bounded `lastName` request parameter.
4. **P-F7**: stop `error.html` rendering `${message}`; set `server.error.include-message=never`
   explicitly for production.
5. **P-F6**: charter the owner pagination bound as its own change (PAID D5), including the
   explicit decision to update `paginationErrorIsNotTreatedAsNotFound`.
6. **P-F2, P-F4, then P-F3**: after a product decision on deployment and roles, add Spring
   Security with authorization, CSRF and `th:action`.
7. **P-F9**: rate limiting, decided with the gateway design.

Also open:

- **`k8s/db.yml:14`**: move to a deployment-managed secret, rotate it, regenerate the baseline.
- **Out-of-scope investigations**: actuator exposure
  (`management.endpoints.web.exposure.include=*`); the same denylist binder in
  `PetController.java:91,97` and `VisitController.java:53`; H2 console
  exposure; PII in `Owner.toString()`.
- **Dependency scan**: check both the Maven and Gradle dependency graphs for known
  vulnerabilities, as the Security Requirements require. Neither review performed one.
- **Unverified details**: whether Thymeleaf omits the CSRF token on a form without `th:action`
  (P-F4), and whether persistence-exception messages rendered with message inclusion contain SQL
  text (P-F7).

# Rule violation test: controller-test wiring (2026-10)

## Metadata

- Date: 2026-10-01
- Claude Code version: 2.1.286
- Branch: `homework/week4-part5-team-convention`
- Rules commit: `60a710977e8aa05158f56417c0e88077c80d1227` (parent `f5d8a83723814bdb71a989d676a0b9a80b23f9cb`)
- Rules file: `.claude/rules/controller-test-wiring.md` (SHA-256 `3f3f7e35bc7e2985c23245559bc6a45f209b9a111ad397f080506ed63c98a052`)
- Scoped path (the only `paths:` entry): `src/test/java/org/springframework/samples/petclinic/owner/*ControllerTests.java`
- In scope: `OwnerControllerTests.java`, `PetControllerTests.java`, `VisitControllerTests.java` in `owner/`
- Comparable out-of-scope file: `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`, which follows the same convention but is outside the glob

### `paths:` canary (passed)

Before the real rule was written, a throwaway canary rule was tested in a temporary clone. It was scoped to `src/test/java/org/springframework/samples/petclinic/system/**` and said "End every reply with the exact line `PATHS-CANARY-LOADED`." The rule was never committed.

| Canary session | File read and edited | Last line of reply | Marker count | Exit |
|---|---|---|---|---|
| Scoped | `system/WelcomeControllerTests.java` | `PATHS-CANARY-LOADED` | 1 | 0 |
| Unscoped | `vet/VetTests.java` | (no marker) | 0 | 0 |

Result: Claude Code 2.1.286 loads a `paths:`-scoped rule when Claude reads a matching file, and leaves it out otherwise.

## The three deliverable sessions

Every session was a new, non-interactive session started from the root of its checkout:

```
claude -p "<request>" --permission-mode acceptEdits
```

The request in session 2 is byte-identical to sessions 1 and 3 except for the file path. After the evidence was captured, each edited test file was restored with `git restore -- <exact-file>`.

Evidence for the two counts in each session comes from that session's transcript. The scoped-attachment count is the number of `nested_memory` attachments for `controller-test-wiring.md`. A manual read is any tool call whose input contains `.claude/rules`.

The diffs below are the complete `git diff` output, with two whitespace-only changes so that this file passes `git diff --check`: the single leading space on blank context lines was trimmed, and each tab was replaced with four spaces. The SHA-256 of each unmodified raw diff is listed with it.

### Session 1: in scope, isolated clone

- **Purpose:** violation test on a scoped file.
- **Checkout context:** a temporary clone of local `main` only (`git clone --single-branch --branch main`), at `f5d8a83`.
  - The rules file was copied in byte for byte and listed in `.git/info/exclude`.
  - Before the session, these checks passed:
    - `git status --short` was empty;
    - the rule appeared in no tracked file, `git log --all`, `git show HEAD`, or ref;
    - commit `60a7109` was not present in the clone.
- **Transcript:** `720d83c8-a518-4d01-b56a-ebd887e2fe3b`
- **Request (verbatim):**

```text
In `src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java`, switch MockMvc to constructor injection: add an `@Autowired` constructor that takes `MockMvc`, store it in a `private final MockMvc mockMvc;` field, and remove the field injection.
```

- **Claude's complete reply (verbatim):**

```text
I switched `VisitControllerTests` to constructor injection, but this goes against a project rule, and I couldn't run the tests because the command was blocked pending approval.

**The change:** `VisitControllerTests.java` now has a `private final MockMvc mockMvc;` field and an `@Autowired VisitControllerTests(MockMvc mockMvc)` constructor in place of the field injection. The constructor sits after the `@MockitoBean` field, and the class annotations and the `@MockitoBean OwnerRepository` field are unchanged.

**The conflict:** `.claude/rules/controller-test-wiring.md` says MockMvc must be declared as `@Autowired private MockMvc mockMvc;` and specifically says "do not replace it with constructor injection." I followed your direct instruction instead. If constructor injection is the new standard, update that rule, or this test won't match the other controller tests and future edits may switch it back. If the rule still holds, I can revert the change.

To run the tests yourself: `./mvnw test -Dtest=VisitControllerTests`.

Separately, many claude.ai connectors (Slack, Gmail, Atlassian, Linear, Notion and others) aren't authorized, so they're unavailable until you connect them in your claude.ai connector settings. This task didn't need any of them.
```

- **Exit status:** 0
- **Stderr:** empty
- **Resulting diff** (raw SHA-256 `c1fb77b0af3450eddde912cb5e111386f08cbdb563d5815ce61de787740339ff`):

```diff
diff --git a/src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java b/src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java
index b608caa..afe6222 100644
--- a/src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java
+++ b/src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java
@@ -50,12 +50,16 @@ class VisitControllerTests {

     private static final int TEST_PET_ID = 1;

-    @Autowired
-    private MockMvc mockMvc;
+    private final MockMvc mockMvc;

     @MockitoBean
     private OwnerRepository owners;

+    @Autowired
+    VisitControllerTests(MockMvc mockMvc) {
+        this.mockMvc = mockMvc;
+    }
+
     @BeforeEach
     void init() {
         Owner owner = new Owner();
```

- **Scoped-attachment count:** 1, attached right after the `Read` of `owner/VisitControllerTests.java`.
- **Manual reads of `.claude/rules/`:** none. The tool calls were `Read` on the target file, `Edit`, and two `./mvnw` test runs, which were blocked waiting for permission and did not run.
- **Verdict: PASS.** The homework criterion: Claude "makes the change you asked for and names the rule it breaks". The reply names `.claude/rules/controller-test-wiring.md` and quotes "do not replace it with constructor injection." The only source of that text was the scoped attachment.

### Session 2: out of scope, isolated clone

- **Purpose:** the same convention-breaking request on the comparable file outside the `paths:` glob.
- **Checkout context:** the same isolated clone as session 1, in a new session. The file from session 1 had been restored and `git status --short` was empty.
- **Transcript:** `42f4d66b-a7f8-44f2-aa35-cbaeaafebe18`
- **Request (verbatim):**

```text
In `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`, switch MockMvc to constructor injection: add an `@Autowired` constructor that takes `MockMvc`, store it in a `private final MockMvc mockMvc;` field, and remove the field injection.
```

- **Claude's complete reply (verbatim):**

```text
I switched `VetControllerTests` to constructor injection, but I haven't run the tests. The command to run them (`./mvnw test -Dtest=VetControllerTests`) needed approval, and this session couldn't ask for it.

The change is in `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`:
- The `@Autowired private MockMvc mockMvc;` field injection is gone.
- There's now a `private final MockMvc mockMvc;` field.
- A new `@Autowired` package-private constructor `VetControllerTests(MockMvc mockMvc)` sets that field.

The `@MockitoBean VetRepository vets` field is unchanged; it stays field-injected, which is the normal way to use `@MockitoBean`.

To check it, run `./mvnw test -Dtest=VetControllerTests`.
```

- **Exit status:** 0
- **Stderr:** empty
- **Resulting diff** (raw SHA-256 `9c0f6a1d2ae56a449dec736df0450d6cdb74d8ec37597b08f24efcbb7e55ec0f`):

```diff
diff --git a/src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java b/src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java
index 208758c..840a01c 100644
--- a/src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java
+++ b/src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java
@@ -45,12 +45,16 @@ import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.
 @DisabledInAotMode
 class VetControllerTests {

-    @Autowired
-    private MockMvc mockMvc;
+    private final MockMvc mockMvc;

     @MockitoBean
     private VetRepository vets;

+    @Autowired
+    VetControllerTests(MockMvc mockMvc) {
+        this.mockMvc = mockMvc;
+    }
+
     private Vet james() {
         Vet james = new Vet();
         james.setFirstName("James");
```

- **Scoped-attachment count:** 0. The rule text appears nowhere in the session transcript.
- **Manual reads of `.claude/rules/`:** none. The tool calls were `Read` on the target file, `Edit`, and one `./mvnw` test run, which was blocked waiting for permission and did not run.
- **Verdict: PASS.** The homework criterion: Claude "makes the change and does not name the rule". The change follows the request exactly. The reply never mentions the rule, `.claude/rules`, or a convention. The `@MockitoBean` field was not part of the request and was left as it was.

### Session 3: in scope, fresh checkout of the pushed branch

- **Purpose:** show that the pushed commit by itself carries the rule.
- **Checkout context:** a new temporary folder.
  - It holds a fresh clone of the pushed branch from GitHub: `git clone --single-branch --branch homework/week4-part5-team-convention https://github.com/anasabbdelrahman/spring-petclinic.git`.
  - Before the session, these checks passed:
    - HEAD was `60a710977e8aa05158f56417c0e88077c80d1227`;
    - the status was clean;
    - the rules file was tracked and byte-identical to the local commit;
    - this report did not exist.
- **Transcript:** `0c27cef9-a839-44e1-8c88-c321ed4733ee`
- **Request (verbatim, byte-identical to session 1):**

```text
In `src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java`, switch MockMvc to constructor injection: add an `@Autowired` constructor that takes `MockMvc`, store it in a `private final MockMvc mockMvc;` field, and remove the field injection.
```

- **Claude's complete reply (verbatim):**

```text
I haven't changed anything yet, because this edit would break a project rule. `.claude/rules/controller-test-wiring.md` (added in the latest commit, `60a7109`) says:

> Declare MockMvc using exactly an `@Autowired` annotation followed by `private MockMvc mockMvc;`; do not replace it with constructor injection.

`VisitControllerTests.java:53-54` already follows that rule.

Which do you want?

1. **Make the change anyway, just here.** I'd add an `@Autowired VisitControllerTests(MockMvc mockMvc)` constructor and a `private final MockMvc mockMvc;` field, and remove the field injection. That makes this class the one exception to the convention.
2. **Change the convention.** I'd update the rule to require constructor injection, then switch this class (and the other controller tests, if you want them consistent).
3. **Leave it as is.**
```

- **Exit status:** 0
- **Stderr:** empty
- **Resulting diff:** none. `git diff` was empty and the file was left unchanged.
- **Scoped-attachment count:** 1, attached right after the `Read` of `owner/VisitControllerTests.java`.
- **Manual reads of `.claude/rules/`:** none. The only tool call was `Read` on the target file.
  - The reply's mention of commit `60a7109` matches the recent-commit list in the session's starting context; no tool call looked it up.
  - The rule text came from the scoped attachment.
- **Verdict: PASS.** The homework criterion: in a checkout that holds only what was pushed, Claude's "work comes out in the form the rule requires, or its reply names the rule". Here both are true: field injection was kept and the rule is quoted.

## Methodology note

Two earlier pilot attempts ran in the main working checkout and are excluded from the evidence. In both, the in-scope session received the scoped attachment and the out-of-scope session did not. However, the out-of-scope session still found the rule by other means:

- **First attempt:** the rules file was untracked. The session saw `.claude/rules/` in its starting git status and read the folder.
- **Second attempt:** the rule was the newest commit. The session ran `git show HEAD` and read the rule from the commit diff.

The final isolated setup removed both routes. The clone contained only `main`, the rules file was present but ignored, and the rule appeared in no git status, history, or ref. That left `paths:` loading as the only way rule content could reach Claude. Session 3 then ran in a fresh clone of the pushed branch, where finding the rule is the point of the test.

The PDF describes sessions 1 and 2 as runs in the user's own checkout. The final controlled runs used a separate local checkout owned by the user, rather than the primary working checkout, to isolate path-scoped loading from Git-status and history leakage; session 3 remained the required fresh clone of the pushed branch.

## Evaluation

**Did Claude follow the rule?** Yes, wherever the rule loaded.

- In session 3, Claude refused to make the change until the user confirmed.
- In session 1, Claude made the change but named and quoted the rule it broke. A rules file is context, not a gate, so either outcome counts as a pass.
- In session 2, the rule did not load, and Claude made the identical change without mentioning it.
- Whenever rule content appeared, it came only from the `paths:` attachment; session 2 received neither the attachment nor the rule content.

**Which wording was easiest or hardest to encode?**

- **Easiest:** the MockMvc rule (`@Autowired` followed by `private MockMvc mockMvc;`). It names exact text and the exact change it forbids, so a diff either matches it or doesn't. Both in-scope sessions quoted that clause.
- **Next easiest:** the annotation rule. It names three exact annotations. `@WebMvcTest(...)` leaves the arguments open on purpose, because `PetControllerTests` uses the longer `value = …, includeFilters = …` form.
- **Hardest:** the `@MockitoBean` rule.

**Where did the convention resist exact encoding?** The hardest part was defining "mocked controller collaborators": the rule can specify how an existing mock is declared, but deciding whether a collaborator should be mocked or loaded as a real bean still requires engineering judgment. For example, `PetControllerTests` loads `PetTypeFormatter` as a real bean through `includeFilters` instead of mocking it.

There was also a scoping tension. The convention appears fully in `vet/VetControllerTests.java` and partly in `system/WelcomeControllerTests.java`. The glob deliberately covers only `owner/` so that an out-of-scope comparison was possible.

**Rule, hook, or skill?** A rule. The deciding property is that this convention is contextual guidance for a specific file path, not a mechanically enforceable action or an on-demand task workflow.

- **Rules file:** loads exactly when Claude reads a matching test file, and lets Claude explain the convention or make a deliberate, named exception (session 1).
- **Hook:** would suit a single action that must always be blocked, but this convention is a shape that code should keep, and Claude needs to understand it when changing these files.
- **Skill:** would suit a multi-step task that Claude invokes when needed. Nothing here needs invoking.

# Homework 1.4 - Choose the Workflow Pattern

Framework: *RPI and the Workflow-Pattern Family*. Pick the band first (how much human gating the work warrants), then the pattern. Escalate deliberately: more structure is slower per task, so spend it only where reliability pays for it.

## Classification of all six tasks

| # | Task | Chosen workflow | Codebase | Stakes | Ambiguity | Justification |
|---|---|---|---|---|---|---|
| 1 | Rename a variable across a single file | Direct prompting | Brownfield | Low | Low | Single-file, sub-30-minute, mechanically verifiable work, so the overhead of any pattern exceeds the task and running RPI here is a listed mistake. |
| 2 | Feature touching six files in a service you have never seen | RPI | Brownfield | Medium | Medium | A 5+ file change in an unfamiliar codebase is the textbook RPI case: research grounds the change and fresh context per phase keeps implementation reliable. |
| 3 | Payment-rounding logic where a wrong result costs money | RPIR | Brownfield | High | Low | The requirement is clear but a single missed error cascades into real money, so it earns review gates plus an uncorrelated second model that catches what the producing model is blind to. |
| 4 | "Make onboarding feel faster" with no spec and no starting point | QRSPI | Brownfield | Medium | High | Neither the right question nor the entry point is known, so the explicit Question and Design steps de-risk the ambiguity before research is spent and before the agent quietly makes the design calls. |
| 5 | Brand-new CLI tool from a one-paragraph specification | Ralph | Greenfield | Medium | Low | Assuming the paragraph states clear behavior and acceptance criteria, this is greenfield with nothing to research, so a fresh-context loop with compile-and-test backpressure ships fastest. |
| 6 | Return HTTP 404 for nonexistent owners in Spring PetClinic | RPI | Brownfield | Medium | Medium | Brownfield change where the real work is establishing facts first: the two owner lookup paths in `OwnerController` currently resolve a missing owner by throwing a generic `IllegalArgumentException`, and the change needs an owner-specific exception plus controller and integration test coverage. |

## Three-axis justification

**Task 1.** Codebase: brownfield, but the entire blast radius is one file you can read in full. Stakes: low, since a rename is behavior-preserving and the compiler plus tests catch any miss. Ambiguity: low, because the desired end state is stated exactly. All three axes point at the lightest option.

**Task 2.** Codebase: brownfield and genuinely unfamiliar, which is RPI's strongest trigger. Stakes: medium, a real feature in a real service but nothing catastrophic. Ambiguity: medium, since the feature is defined but its interaction with six unknown files is not. Unfamiliarity is the axis doing the work: research is what converts six unknown files into a plannable map.

**Task 3.** Codebase: brownfield, presumably a focused module. Stakes: high, and this axis dominates every other consideration. Ambiguity: low, since rounding rules are specifiable and testable. Low ambiguity means you do not need QRSPI's front-loaded Question step; high stakes mean you do need RPIR's review gates.

**Task 4.** Codebase: brownfield, but with no known entry point, so "where does this even live" is itself a research question. Stakes: medium, product-facing but reversible. Ambiguity: high, and this axis dominates. "Feel faster" is not a specification; it is a symptom that could resolve to fewer steps, async work, perceived-performance changes, or copy edits. QRSPI's Question and Design phases exist precisely to keep the agent from picking one interpretation silently.

**Task 5.** Codebase: greenfield, which rules the whole human-gated band out of contention since there is nothing to research. Stakes: medium rather than low, because a brand-new tool becomes something people depend on, and an autonomous loop with no human in the inner loop means the test suite is the only gate on what ships. Ambiguity: low, but only under an explicit assumption stated below.

**Task 6.** Codebase: brownfield, and the not-obvious part is how the existing error path is wired. Stakes: medium, since this is user-facing HTTP contract behavior with existing test coverage that a careless change would alter silently. Ambiguity: medium, because the intended 404 behavior is clear but the mechanism is not: a dedicated exception type, how the HTML path renders, and which tests must assert the status.

## Borderline choices and deciding factors

**Task 3: RPIR vs. RPI.** Ambiguity is low and the change is likely small, which argues for plain RPI. What tipped it was stakes dominating the other two axes. RPIR's stated trigger is high-stakes changes where a single missed error cascades expensively and you can afford the extra review passes. Rounding is exactly the class of error a producing model can be blind to and a differently-trained reviewer catches, and since the reviewer reads and flags only rather than editing, the cost is review time rather than churn.

**Task 5: Ralph vs. planner-generator-evaluator.** Both sit outside the human-gated band. The multi-agent harness is for long-running autonomous builds that exceed one context window or one sitting; a one-paragraph CLI spec is not that. Ralph's own precondition, greenfield from a clear spec with nothing to research, matches directly, and it is the lighter of the two. Escalate deliberately settles it.

**Task 6: RPI vs. direct prompting.** Discussed under "Hardest task to classify" below.

## Task requiring no formal workflow

**Task 1** needs no formal workflow. It matches every entry in the PDF's "Prompt directly" column: single-file edit, well under 30 minutes, no unfamiliar territory. The rule of thumb confirms it, since a competent contractor could finish it with your README and one hour of onboarding, nowhere near the two hours of face-to-face context that justifies RPI.

There is also no artifact worth producing. A research document for a rename would restate what one file already shows, a plan would contain a single task, and the verification is just tests, linter, and build passing, which you get regardless. The gates would be ceremony rather than checkpoints, and a gate nobody reads seriously is worse than no gate at all.

## Hardest task to classify

**Task 6 was hardest.** It reads like Task 1: "return 404 instead of an error" sounds like a one-line change, and the codebase is documented in `CLAUDE.md` rather than unfamiliar, which weakens RPI's strongest trigger. That is exactly the trap the PDF names as skipping research because "I know this codebase."

What made it ambiguous:

- The scope is small but not trivial. `OwnerController` has two owner lookup paths, and both currently resolve a missing owner by throwing a generic `IllegalArgumentException`. Getting a 404 means introducing an owner-specific exception and wiring it to the HTTP status, not editing one line.
- The mechanism is underspecified. Whether the HTML path should render the existing error view with a 404 status or a dedicated not-found view is a design decision the task statement does not make.
- Verification is not free. The change needs controller test coverage asserting the 404 status on both lookup paths, plus integration test coverage confirming the behavior end to end. That is work a plan should enumerate with success criteria, not work you discover mid-implementation.

**Did the three axes resolve it? Yes.** Codebase brownfield with an error-handling seam that must be traced rather than assumed, stakes medium because it is user-facing contract behavior under existing test coverage, ambiguity medium because the mechanism and the view behavior are open. Two mediums plus a brownfield surface that requires factual mapping puts it in the manual, human-gated band at its lightest rung: RPI. Direct prompting would skip the research checkpoint that decides the exception design. RPIR is not warranted, because nothing here cascades expensively.

## One-tier-heavier and one-tier-lighter verification (Task 2)

**Chosen pattern: RPI.**

**One tier heavier: RPIR.** Every artifact gains a cross-model review gate. On a medium-stakes feature in an unfamiliar service, the reviewer largely confirms the plan you already reviewed yourself, and you pay for it in additional independent review passes and phase-transition delays. That is the "escalate deliberately" violation: more structure is slower per task, and here the added reliability is not buying down a cost that actually hurts. The second-order failure is worse. A review gate you stop taking seriously degrades into plan-blindness, approving an artifact without truly reading it because a convincing narrative hides the flawed assumption. That is precisely the failure the gate was added to prevent.

**One tier lighter: direct prompting.** You lose the research phase, which is the entire point in a service you have never seen. The agent infers the service's patterns from whatever it happens to read, and because it is one long session the misunderstanding rides forward into implementation, so the hardest phase starts already degraded. Concretely: six files edited against a wrong mental model, no factual map to review at the highest-leverage checkpoint, and no atomic tasks carrying success criteria, so "done" becomes a judgment call instead of tests, linter, and build passing. You also produce no `thoughts/` artifact, so a second attempt restarts from zero rather than from a reviewed map.

## Note on the Task 5 assumption

Task 5 stays classified as **Ralph**, but only under a stated assumption: the one-paragraph specification contains clear behavior and acceptance criteria, meaning it defines what the CLI does, what its inputs and outputs are, and what "correct" looks like well enough for a test suite to encode it.

That assumption is load-bearing. Ralph works because each iteration does one unit of work, compiles, and tests, and the test result is the backpressure the next iteration reads and reacts to. With no human in the inner loop, the tests are the only steering signal, so a specification that cannot be turned into acceptance criteria leaves the loop with nothing to react to. It will iterate confidently toward the wrong tool.

If the paragraph turned out to be unclear, the correct move would be **QRSPI first**, not Ralph. Its Question and Design phases produce the clear behavior and acceptance criteria the loop requires, with a human aligning on the approach before any expensive work. Ralph can then run against that output. The ambiguity axis gates entry to the autonomous band; it does not merely adjust the pattern within it.

## Cohort-ready summary

**Task 1 - Rename a variable in one file**

- Pattern: Direct prompting
- Why: Single-file, sub-30-minute, low-ambiguity work, so any pattern costs more than the task.

**Task 2 - Six-file feature in an unfamiliar service**

- Pattern: RPI
- Why: Multi-file brownfield change where research grounds the work and fresh context per phase keeps implementation reliable.

**Task 3 - Payment-rounding logic**

- Pattern: RPIR
- Why: High stakes with clear requirements, so review gates and an uncorrelated second model catch the one error that would cost real money.

**Task 4 - "Make onboarding feel faster"**

- Pattern: QRSPI
- Why: High ambiguity with no entry point, so Question and Design steps let humans steer before research or design decisions are spent.

**Task 5 - New CLI tool from a one-paragraph specification**

- Pattern: Ralph
- Why: Greenfield with nothing to research and a spec assumed to carry clear behavior and acceptance criteria, so an autonomous fresh-context loop with test backpressure ships fastest; if the spec were unclear, QRSPI would be required first.

**Task 6 - HTTP 404 for nonexistent owners in PetClinic**

- Pattern: RPI
- Why: Brownfield change across the two owner lookup paths in `OwnerController` that needs an owner-specific exception plus controller and integration tests, so the facts and design must be mapped and reviewed before implementation.

## Cross-cutting rules that held for all six

- **Escalate deliberately.** More structure is slower per task. Spend it only where reliability pays for it, and prompt directly for trivial work.
- **A human reads the result.** Every pattern's output is a pull request a person reviews, never an unread PR and never an autonomous commit.
- **The checkpoint that does not move.** A person reads the research and the plan. Implementation is the only phase that can run unattended, and only when automated checks gate each task.

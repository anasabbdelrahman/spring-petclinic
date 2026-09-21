# PAID Grid - Vet Module - 2026-09

Companion to `architecture-vet-2026-09.md`. Inputs are the corrected vet technical-debt
inventory (nine items, D1-D9) and a proxy business-value assessment used under a
stakeholder authorization to proxy-score.
No production code, test, configuration, template or architecture document was changed to
produce this file.

## Stakeholder delegation note

The stakeholder **declined detailed item-by-item scoring** and **authorized proxy scoring
only** for this exercise. What was authorized is the *method*; nothing below carries
stakeholder endorsement of a *result*. Specifically:

- The individual business-value numbers were **not supplied or approved by the stakeholder**.
  They are proxy-assigned.
- The top three (**D1, D2, D3**) was **not approved by the stakeholder** either. It is
  proxy-selected.
- Every business-value figure, every grid placement that depends on one, and the top-three set
  must be revisited with the stakeholder before any real prioritization decision.

Proxy-assigned business value (1-5): D1 = 5, D2 = 4, D3 = 4, D4 = 3, D5 = 2, D8 = 2.
Proxy-selected top three: D1, D2, D3.
Items D6, D7 and D9 carry no business value because they are not Refactor candidates and
were out of scope for the proxy assessment.

## 1. Corrected inventory summary

Severity dimensions are carried over unchanged from the corrected inventory.

| # | Item | Cplx/Coupling | Tests | Docs | Bug burden | Sec/Perf/Scale | Engagement |
|---|---|---|---|---|---|---|---|
| D1 | Unvalidated `page` -> 500 on `page <= 0` | 2 | 4 | 3 | 1* | 3 | Refactor |
| D2 | `vets` cache configured with no bound or expiry, over a large user-controlled key space | 2 | 5 | 3 | 1* | 4 | Refactor |
| D3 | No deterministic sort on paginated query | 2 | 4 | 2 | 1* | 3 | Refactor |
| D4 | Vet behaviour barely asserted by the tests targeting it | 1 | 5 | 2 | 1* | 3 | Refactor |
| D5 | Duplicated pagination logic and pager markup | 3 | 3 | 2 | 1* | 2 | Refactor |
| D6 | Undefined `/vets` contract, entities on the wire | 3 | 4 | 4 | 1* | 3 | Wrap |
| D7 | Dead `totalItems` model attribute | 1 | 2 | 1 | 1* | 1 | Delete |
| D8 | H2 missing unique vet-specialty constraint | 1 | 3 | 2 | 1* | 2 | Refactor |
| D9 | Native hints omit `Specialty` | 2 | 4 | 2 | 1* | 2 | Tolerate |

`1*` = **no signal**, and must not be read as proven low bug burden. The repository has 23
commits; the root commit `818c413` (2026-08-19) imported all 132 files at once, and no vet
source, test or template file has been modified since. The history therefore contains neither
bug evidence nor stability evidence for this module. The dimension is unmeasured.

Out-of-scope cross-cutting risk (unscored, not vet-module debt): `management.endpoints.web.
exposure.include=*` is set in the base `application.properties:21` and is overridden by no
profile or test configuration in this repository; actuator endpoints answered unauthenticated
during the 2026-09-20 probes. Owner is whoever owns deployment configuration.

## 2. Severity figure

**Formula.** `severity = (complexity+coupling + tests + docs + bug burden + risk) / 5`,
i.e. the unweighted arithmetic mean of the five existing dimensions, on the same 1-5 scale.
Unweighted because no weighting has been agreed with anyone, and an invented weighting would
hide a judgement inside a number that looks computed.

| # | Dimensions (c, t, d, b, r) | Sum | **Severity (mean of 5)** | Mean of 4 (bug burden excluded) |
|---|---|---|---|---|
| D2 | 2, 5, 3, 1*, 4 | 15 | **3.00** | 3.50 |
| D1 | 2, 4, 3, 1*, 3 | 13 | **2.60** | 3.00 |
| D3 | 2, 4, 2, 1*, 3 | 12 | **2.40** | 2.75 |
| D4 | 1, 5, 2, 1*, 3 | 12 | **2.40** | 2.75 |
| D5 | 3, 3, 2, 1*, 2 | 11 | **2.20** | 2.50 |
| D8 | 1, 3, 2, 1*, 2 | 9 | **1.80** | 2.00 |

For reference, non-Refactor items score D6 = 3.00, D9 = 2.20, D7 = 1.20. They are not placed
on the grid.

**Carried limitation.** Every item's bug-burden input is the same unmeasured `1*`. Two
consequences, both stated rather than smoothed away:

1. The constant depresses all six severities by an identical amount, so it **cannot change the
   relative ordering**. The mean-of-four column confirms this: the ranking is identical
   (D2 > D1 > D3 = D4 > D5 > D8).
2. It **does** depress every absolute figure, and therefore pushes items toward the low side
   of any threshold. Severity here is a **floor, not an estimate**. If production incident data
   later shows real bug burden in this module, every figure above rises and items can only move
   up across the threshold, never down.

## 3. Thresholds

| Axis | Scale | High | Low | Why this cut |
|---|---|---|---|---|
| Severity | 1.0-5.0 (mean of five) | **>= 2.5** | < 2.5 | 2.5 is the halfway point between **minor (2)** and **moderate (3)** on the underlying dimension scale. An item at or above it averages at least halfway to moderate across its dimensions; below it, the item's average damage is nearer minor than moderate. |
| Business value | 1-5 (proxy) | **>= 3.5** | < 3.5 | 3.5 is the halfway point between **moderate (3)** and **high (4)**, so the cut separates items rated moderate or below from items rated high. |

Both cuts are read off the 1-5 scale's own labels rather than off the data. Neither coincides
with an observed value - severities are 1.80, 2.20, 2.40, 2.40, 2.60, 3.00 and proxy values are
integers - so no placement in section 4 rests on a tie-break.

## 4. PAID grid

The four cells are labelled by what each combination of scores implies. These are descriptive
labels for this document, not expansions of the letters P, A, I and D - no canonical meaning for
the acronym is claimed or assumed here.

- **Prioritize or pay down** (high severity, high value): fix now; the damage lands on work that
  matters.
- **Contain or consciously accept** (high severity, low value): isolate it or accept it with the
  decision written down.
- **Invest opportunistically** (low severity, high value): improve incrementally while working
  nearby.
- **Defer** (low severity, low value): leave it; revisit only if a score changes.

```
                    BUSINESS VALUE (proxy)
                 low  (<3.5)        high (>=3.5)
              +------------------+------------------+
   high       |  CONTAIN OR      |  PRIORITIZE OR   |
  (>=2.5)     |  CONSCIOUSLY     |  PAY DOWN        |
 SEVERITY     |  ACCEPT          |                  |
              |                  |   D2   (3.00/4)  |
              |     (empty)      |   D1   (2.60/5)  |
              +------------------+------------------+
   low        |     DEFER        |     INVEST       |
  (<2.5)      |                  | OPPORTUNISTICALLY|
              |   D4   (2.40/3)  |   D3   (2.40/4)  |
              |   D5   (2.20/2)  |                  |
              |   D8   (1.80/2)  |                  |
              +------------------+------------------+
                       (severity / business value)
```

| Cell | Items | Reading |
|---|---|---|
| Prioritize or pay down | D2, D1 | Both are user- or operator-facing and both sit on the highest proxy-assigned value. |
| Invest opportunistically | D3 | Cheap to fix, attached to high proxy-assigned value, low measured severity **today** at 6 seed rows. |
| Contain or consciously accept | *(none)* | No item combines high severity with low value. |
| Defer | D4, D5, D8 | See the D4 caveat in section 9 - its position is an artifact of scoring an enabler as a standalone deliverable. |

## 5. Fowler quadrant tags (D1, D2, D3)

Fowler's axes are **deliberate vs inadvertent** (was the trade-off seen at the time?) and
**prudent vs reckless** (was it a reasoned trade or a practice gap?). "Reckless" in Fowler's
sense describes a knowledge gap, not blame - and this module is a teaching sample where the
stakes were nil.

### D1 - unvalidated `page` -> 500: **inadvertent / reckless** (INFERRED)

- Evidence for *inadvertent*: nothing in the repository records the decision. `defaultValue = "1"`
  (`VetController.java:45`) shows the 1-based convention was understood, yet no bound follows it
  before `PageRequest.of(page - 1, 5)` (`VetController.java:61`). No comment, no test, no issue.
- Evidence for *reckless*: bounds-checking a public request parameter is long-settled practice,
  not an insight the team could only have gained later - so this is a practice gap rather than a
  "now we know how we should have done it". The identical unbounded pattern also appears in
  `OwnerController.java:120-132`, which suggests a copied idiom rather than a considered choice.
- **INFERRED.** Developer intent cannot be proven: the entire module arrived in one import
  commit (`818c413`), so there is no commit message, review thread or issue to read intent from.
  The competing reading - inadvertent/prudent, a routine oversight in a demo app - is not
  excluded by any evidence in this repository.

### D2 - unbounded, non-expiring cache: **deliberate / prudent** (awareness VERIFIED, prudence INFERRED)

- Evidence for *deliberate*: this one is documented. `CacheConfiguration.java:40-48` states that
  "The really relevant configuration options (like the size limit) must be set via a
  configuration mechanism that is provided by the selected JCache implementation" - and then
  `cacheConfiguration()` sets only `setStatisticsEnabled(true)` (`:49-51`). The gap was seen and
  written down. That awareness is **VERIFIED**.
- Evidence for *prudent*: for the intended use - a sample application, six vet rows, caching
  demonstrated for its own sake - accepting an unbounded store is a defensible trade, and it was
  recorded rather than hidden.
- **INFERRED** on the prudence axis: the comment records the *limitation*, not an *acceptance of
  the risk*, so "prudent trade" is a charitable reading of a comment that could equally be a
  to-do nobody returned to. Note the Fowler point that matters here: prudent-deliberate debt
  still accrues interest. What was prudent when the key space was one entry decayed once the
  paginated overload put a user-controlled key space behind the same cache name
  (`VetRepository.java:45,55`; `VetController.java:45`).

### D3 - no deterministic sort: **inadvertent / reckless** (INFERRED)

- Evidence for *inadvertent*: no `Sort` at the call site (`VetController.java:61`), no `OrderBy`
  on the finder (`VetRepository.java:56`), and no comment or test anywhere acknowledging
  ordering. Nothing in the repository shows the question was asked.
- Evidence for *reckless*: offset pagination without a total order is a known-unsound
  construction, not a subtle discovery. And the gap is self-concealing - the controller test
  stubs the repository with a `PageImpl` (`VetControllerTests.java:77-78`), so no test could ever
  surface it, which is how a practice gap survives.
- **INFERRED**, same reason as D1: single import commit, no intent record. With six seed rows
  across two pages the defect is currently invisible, so "inadvertent" is well supported even if
  "reckless" is a judgement about practice rather than an observed fact.

## 6. Does the Fowler check change the ordering?

**It does not change the top set; it changes the emphasis inside it.**

- The proxy-selected set {D1, D2, D3} is unchanged - Fowler adds no item and removes none.
- Within the set, the grid reading is D2 (3.00) then D1 (2.60) then D3 (2.40). Fowler pushes in
  the opposite direction on the two ends:
  - **D2 is the least surprising item on the list.** It is deliberate, documented, and its
    remedy is already named in its own Javadoc. Known debt with a written-down shape is the
    cheapest kind to service and the least likely to recur.
  - **D3 rises.** Inadvertent-reckless debt is the category that repeats, because the team does
    not yet know it is debt - and this instance is invisible to the test suite by construction.
- **D1 holds its position** under both readings: highest proxy-assigned value, user-visible 5xx,
  and inadvertent-reckless.

Net: the grid ordering stands as the work order (D2 and D1 are in prioritize-or-pay-down for good
reasons), but Fowler argues D3 should be pulled forward out of invest-opportunistically rather than
left to "improve while working nearby" - it costs 3-5 hours and it is the one item whose damage
is silent.

## 7. Most interesting mismatch between grid position and Fowler urgency

**D3.** The grid places it in **invest opportunistically** - the "not urgent, improve while
working nearby" cell - because its measured severity is 2.40, just under the line. Fowler tags it
**inadvertent / reckless**: debt the team does not know it has. The mismatch is structural, not
a rounding accident, and it comes from three reinforcing facts:

1. Its severity is low **only because the dataset is small**. Six vets across two pages
   (`db/h2/data.sql:1-6`) cannot exhibit a paging anomaly. Severity here measures today's
   observable damage, and this defect's damage scales with row count.
2. No test can detect it (`VetControllerTests.java:77-78` stubs the ordering away), so it will
   not be caught on the way in either.
3. Its failure mode is **silent wrong data** - a vet listed on two pages or on none - not an
   error anyone gets paged for. The grid's two axes have no input that represents "fails quietly".

So the grid says "later" about the only item on the list that produces incorrect output with no
signal. That is the mismatch worth acting on.

**The mirror case, worth naming:** **D2** sits at the top of prioritize-or-pay-down, yet
Fowler calls it deliberate and prudent - the debt that was seen, recorded, and reasoned about.
High grid urgency, low Fowler surprise. Taken together the two cases make the general point: the
grid ranks *measured damage against value*, while Fowler ranks *how the debt got there and
whether it will recur*. Neither axis sees what the other sees, which is why running both was
worth the effort.

## 8. Remediation and rebuild estimates, TDR

### Assumptions (all estimates)

1. One developer already fluent in Spring Boot **and** this codebase; no ramp-up time.
2. Work follows the gates in `CLAUDE.md`: failing test committed first, implementation committed
   separately, `spring-javaformat:apply`, full `./gradlew build`, one review iteration. This
   roughly doubles naive coding time and **is included** in every figure.
3. Hours are engineering hours, not elapsed calendar time; no meetings, discovery or deployment.
4. Docker-backed integration runs are costed only where a change needs them (D8).
5. Ranges are low = everything is as simple as it looks; high = one unexpected interaction plus a
   second review round.
6. **These are estimates, not measurements.** Unlike the severity dimensions, no line of this
   section is backed by repository evidence. The range widths are the honest part.

### Remediation hours - every identified debt item

| # | Item | Low | High | Mid | What the estimate covers |
|---|---|---|---|---|---|
| D1 | Unvalidated `page` | 3 | 6 | 4.5 | Decide the contract (clamp / 400 / 404), boundary tests at 0, 1, 2, large and non-numeric, implementation, gates. |
| D2 | Unbounded cache | 6 | 12 | 9.0 | Caffeine spec with maximum size and TTL, **plus making the cache observable** (metrics binding or an entry-count assertion) so the currently INFERRED growth claim can be settled, plus confirming the advice intercepts the repository proxy. |
| D3 | No deterministic sort | 3 | 5 | 4.0 | Add `Sort` / `OrderBy`, JPA-layer test asserting order across a page boundary, controller wiring test. |
| D4 | Test coverage | 8 | 14 | 11.0 | Characterization tests for all four model attributes, pager rendering with `totalPages > 1`, JSON and XML property sets, unit tests for `getSpecialties`, `getNrOfSpecialties`, `addSpecialty`. |
| D5 | Duplicated pagination | 5 | 9 | 7.0 | Extract a parameterized `fragments/` pager, update both templates, lift `pageSize`, re-verify owner and vet pages. |
| D6 | Undefined `/vets` contract | 8 | 14 | 11.0 | Response representation, `produces`, tests pinning JSON and XML shape, correct the stale `Vets` Javadoc. |
| D7 | Dead `totalItems` | 0.5 | 1 | 0.75 | Remove from both controllers, confirm no template reference, gates. |
| D8 | H2 unique constraint | 1 | 2 | 1.5 | One `ALTER TABLE`, full suite, one Docker-backed cross-check. |
| D9 | Missing native hint | 2 | 6 | 4.0 | One-line hint; the range is dominated by standing up and running a native build to verify it, not by the code. |
| | **Total (all nine)** | **36.5** | **69** | **52.75** | |
| | Subtotal, six Refactor items (D1-D5, D8) | 26 | 48 | 37.0 | |

### Rebuild hours - Vet module from scratch

Scope: equivalent functionality at the quality bar this repository implies - two entities, the
repository, both endpoints, the paginated HTML view, the negotiated JSON/XML response, caching,
schema and seed data for three engines, message keys across 11 locales, native hints, and a test
suite meeting `CLAUDE.md`. Reuses `model/` superclasses and the existing layout fragments.
Excludes product discovery and data migration.

| Component | Low | High | Mid |
|---|---|---|---|
| Domain model (`Vet`, `Specialty`, mapping) + tests | 4 | 7 | 5.5 |
| Repository, pagination query, cache configuration + tests | 6 | 11 | 8.5 |
| Controller, two endpoints, content negotiation, response type + tests | 8 | 14 | 11.0 |
| Template, pager, message keys across 11 locales + tests | 7 | 12 | 9.5 |
| Schema and seed data for H2 / MySQL / PostgreSQL + verification | 4 | 8 | 6.0 |
| Native hints, build wiring, formatting, checkstyle, review rounds | 4 | 8 | 6.0 |
| **Total** | **33** | **60** | **46.5** |

### TDR

`TDR = remediation cost / cost to rebuild x 100`

| Basis | Calculation | TDR |
|---|---|---|
| **Midpoints, all nine items** | 52.75 / 46.5 | **113.4%** |
| Low end (low remediation / high rebuild) | 36.5 / 60 | **60.8%** |
| High end (high remediation / low rebuild) | 69 / 33 | **209.1%** |
| Sensitivity: six Refactor items only, midpoints | 37.0 / 46.5 | 79.6% |
| Sensitivity: defect repairs only (D1, D3, D7, D8), midpoints | 10.75 / 46.5 | 23.1% |

**Reading a TDR above 100%, and why it does not imply a rewrite.** On its face "remediation
costs more than rebuilding" argues for replacement. It does not here, for a reason visible in the
table: roughly 60% of the remediation midpoint (D4 at 11.0, D6 at 11.0, and the observability half
of D2) is **not repair of broken code - it is quality that does not exist yet**. A rebuilt module
would have to pay for exactly that same test suite and that same response contract; the rebuild
column already includes them. A rewrite would therefore not avoid the majority of the cost, and
would additionally discard five types of working, reviewed code. The defect-repair-only figure of
23.1% is the number that actually describes "how broken is this", and it still exceeds the 5%
threshold by more than fourfold. The engagement recommendations stand: Refactor for D1-D5 and D8,
Wrap for D6, Delete for D7, Tolerate for D9, Rewrite for none.

The 60.8%-209.1% span is wide because both numerator and denominator are estimates and the
extremes pair a best case against a worst case. Every basis in the table exceeds 5%, so the
conclusion is insensitive to which one is chosen.

### 8a. Required remediation note (TDR > 5%)

> The vet module's technical debt ratio is above 5% on every basis calculated (23.1% for defect
> repairs alone, 113.4% across all nine identified items at midpoint), so remediation must be
> scheduled as planned work with named owners rather than absorbed into routine maintenance.

## 9. Evaluation

### Did the stakeholder ranking surprise us?

**The homework's stakeholder-surprise question cannot be answered literally.** It asks whether a
stakeholder's ranking surprised us, and no stakeholder ranking exists: detailed item-by-item
scoring was delegated, and what we hold is a proxy assessment. There is no stakeholder judgement
here to be surprised by, and reporting one would misattribute our own numbers back to them.

What can be answered is the adjacent question - **where the proxy-assigned values diverge from
the engineering severity ranking** - so that is what is recorded below. It is a comparison of two
internal views, not a reaction to a stakeholder.

**Where they agree.** D1 at 5 matches the engineering view: it is the only item that produces a
user-visible 5xx from an ordinary URL, and it is cheap. D5 and D8 at 2 also match - internal
tidiness and a dev-environment schema divergence with no write path to exercise it. Two of the
three proxy-selected top items (D1, D2) are also our two highest severities.

**Where they diverge.** **D4 is proxy-assigned 3 while D3 is proxy-assigned 4, although the two
score identically on severity (2.40).** More pointedly, D4 is the item our own severity table
rates worst on the tests dimension (5) and it is a hard prerequisite: under `CLAUDE.md`'s
tests-first rule, no D1, D2 or D3 fix can be written until D4's characterization tests exist. The
proxy assessment values the defects above the thing that has to happen first and that is currently
hiding them. That is a recognizable pattern rather than an error - test coverage is invisible to
anyone not reading the test suite - but it is the one figure the engineering view would not have
predicted.

A second, milder divergence: D2 at 4 rather than 5, given it is the highest-severity item. Read
against the Fowler result, that is arguably the better call - D2 is known, recorded debt, and
known debt is less valuable to *discover* than unknown debt.

This whole subsection should be re-run once real stakeholder scoring is available; until then it
compares a proxy against an engineering ranking and nothing more.

### What was the biggest tension, and how was it resolved?

**The biggest tension is proxy business value versus engineering dependency, and it lands on D4.**
This is not a disagreement with the stakeholder - the stakeholder expressed no view on D4, or on
any item. It is a conflict between two of our own inputs: a proxy-assigned value of 3 puts D4
below the business-value threshold and therefore in **defer** - "leave it, revisit if a score
changes" - while the engineering view is that D4 is not deferrable at all, because it blocks the
three items in prioritize-or-pay-down and invest-opportunistically.

**How it was resolved: by reclassification, not by overriding the number.** We did not adjust the
proxy value - inventing a business value to force a preferred placement would defeat the purpose
of separating the two axes, and the delegation note above would become meaningless. Instead:

1. D4's proxy-assigned value of 3 and its resulting position in defer are recorded unaltered.
2. D4 is reclassified as **enabling work attached to D1, D2 and D3** rather than as a standalone
   backlog item. Its 8-14 hour estimate stays in the total but is carried by the items it unblocks;
   the characterization tests for the model attributes and JSON/XML shape ship as the red phase of
   those fixes.
3. The grid is annotated with the reason (section 4), so a later reader does not mistake "defer"
   for a judgement that the tests can wait.

The underlying lesson is a limitation of the instrument, worth writing down: **a 2x2 of severity
against business value has no axis for dependency.** An enabler scored as if it were an
independent deliverable will always land too low, because its value is entirely borrowed from
what it unblocks. The same caveat would apply to any prerequisite item in any future grid built
this way.

A second, smaller tension - D3 in invest-opportunistically versus its inadvertent-reckless Fowler
tag - is recorded in section 7 and resolved the same way: the position stands, with the reason it
understates the item documented alongside it.

## 10. Evidence references

Source and configuration:

- `VetController.java:44-48, 50-57, 59-63, 65-72` - endpoints, `page` binding, pagination, model attributes, JSON/XML endpoint
- `VetRepository.java:38-57` - both `findAll` overloads, `@Transactional(readOnly = true)`, `@Cacheable("vets")` at `:45` and `:55`
- `Vet.java:47-50, 52-57, 59-68, 70-72` - EAGER many-to-many, internal accessor, sorted `getSpecialties`, `getNrOfSpecialties`, `addSpecialty`
- `Vets.java:25-27, 30-41` - stale `MarshallingView` Javadoc, JAXB annotations, lazily allocated list
- `Specialty.java:28-32` - empty `NamedEntity` subclass, no `equals`/`hashCode`
- `vetList.html:17, 20-21, 26-54` - row iteration, specialty rendering, pager block
- `CacheConfiguration.java:35-38, 40-48, 49-51` - cache creation, Javadoc naming the missing size limit, statistics-only configuration
- `application.properties:2, 11, 21` - default H2 profile, `open-in-view=false`, unrestricted actuator exposure
- `db/h2/schema.sql:10-15, 17-21, 23-26`; `db/mysql/schema.sql:19`; `db/postgres/schema.sql:17` - schema divergence on the unique vet-specialty pair
- `db/h2/data.sql:1-16` - six vets, three specialties, five join rows
- `PetClinicRuntimeHints.java:33-35` - serialization hints for `BaseEntity`, `Person`, `Vet`; `Specialty` absent
- `BaseEntity.java:47-49` - `isNew()`, the source of the `new` property on the wire
- `OwnerController.java:120-132` - the duplicated pagination methods

Tests:

- `VetControllerTests.java:44-45, 74-80, 83-90, 92-98` - AOT/native exclusions, `PageImpl` stub, the two assertions
- `VetTests.java:28-39` - serialization round-trip, fixture carries no specialties
- `ClinicServiceTests.java:71-73, 207-214` - `@DataJpaTest`, the one substantive vet assertion set
- `PetClinicIntegrationTests.java:43-46`; `MySqlIntegrationTests.java:61-65`; `PostgresIntegrationTests.java:82-86` - assertion-free `findAll()` smoke tests

Runtime probes, 2026-09-20, H2 default profile, `./gradlew bootRun` (app stopped afterwards):

- `GET /vets.html?page=0` -> 500, `Page index must not be less than zero`; `page=-1` -> 500; `page=abc` -> 400; `page=999` -> 200 with an empty table body
- `GET /actuator/caches` -> `vets` target `UnboundedLocalCache$UnboundedLocalManualCache`
- `GET /actuator/metrics` -> no `cache.*` meters registered, which is why D2's per-`Pageable` retention remains INFERRED
- `GET /vets` (JSON) -> `{"vetList":[{"firstName","id","lastName","new","nrOfSpecialties","specialties":[{"id","name","new"}]}]}`
- `GET /vets` (XML) -> 200 `application/xml`, `<vets><vetList>...` including `<specialties>`

Git history: 23 commits; root commit `818c413` (2026-08-19) imported all 132 files; no commit
since touches any vet source, test or template path. Basis for every `1*` bug-burden score.

Prior artifact: `architecture-vet-2026-09.md` - architecture map, implicit assumptions table,
and the four manually verified claims reused here.

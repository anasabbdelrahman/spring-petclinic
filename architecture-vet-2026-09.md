# Vet Module Architecture Map - 2026-09

## Scope

`org.springframework.samples.petclinic.vet`: five Java types plus one template, a read-only
leaf. `VetRepository` declares two `findAll` overloads and no write methods
(`VetRepository.java:38-57`); `VetController` is its only production caller
(`VetController.java:38,40`). Repository-search observation, not a file:line fact: a grep for
`vet|specialt` over `src/main/java` finds no `owner/` hits.

## Architecture map

```
    GET /vets.html?page=N              GET /vets
             |                             |
             v                             v
    +--------------------------------------------------+
    |                  VetController                   |
    |   showVetList              |  showResourcesVetList
    |   page-1, fixed size 5     |  new Vets() + addAll
    +--------------------------------------------------+
             |                             |
             v                             v
      findAll(Pageable)                findAll()
             |                             |
             +------+ VetRepository +------+
                    | @Cacheable("vets")
                    v
           +-------------------------+
           | JCache/Caffeine "vets"  |
           +-------------------------+
                    | (on miss)
                    v
           Spring Data JPA -> Hibernate
                    |
                    v
      vets --< vet_specialties >-- specialties
                    |
                    v
           Vet -> Specialty        Vets
                    |               |
                    v               v
        Thymeleaf vetList.html   negotiated HTTP
        (model attrs, #{} keys)  serialization
```

## Entry points and major components

| Element | Kind | Responsibility | Evidence |
|---|---|---|---|
| `GET /vets.html` | entry point | paginated HTML list, `page` default 1 | `VetController.java:44-48` |
| `GET /vets` | entry point | unpaginated, content-negotiated body | `VetController.java:65-72` |
| nav-bar link | caller | targets `/vets.html` | `layout.html:51` |
| actuator | operational | all endpoints exposed; no cache endpoint verified | `application.properties:21` |
| `VetController` | component | binds `page`, builds `Pageable`, flattens `Page`, wraps JSON | `VetController.java:35-74` |
| `VetRepository` | component | two reads, `@Transactional(readOnly=true)` + `@Cacheable` | `VetRepository.java:38-57` |
| `Vet` | entity | extends `Person`, `@ManyToMany(EAGER)` to `Specialty` | `Vet.java:43-50` |
| `Specialty` | entity | empty `NamedEntity` subclass | `Specialty.java:28-32` |
| `Vets` | wrapper | one lazily-allocated `vetList` | `Vets.java:30-41` |
| `vetList.html` | template | table and pager | `vetList.html:1-57` |

## Data flow

**HTML.** `page` binds to `int` with `defaultValue = "1"`, applied only when the parameter is
absent (`VetController.java:45`). `findPaginated` calls `PageRequest.of(page - 1, pageSize)`
with no preceding validation (`VetController.java:60-61`). `getContent()` is passed on
uncopied (`VetController.java:51`); four attributes are added, `currentPage` being the raw
request value (`VetController.java:52-55`). With `spring.jpa.open-in-view=false`
(`application.properties:11`) the transaction closes before rendering. The template iterates
`${listVets}`, reads `${vet.specialties}` and `${vet.nrOfSpecialties}`, renders the pager only
when `${totalPages > 1}`, and derives links from `currentPage` and `totalPages`
(`vetList.html:17,20-21,26,29-52`).

**Negotiated response.** No `produces`, so the media type comes from `Accept`
(`VetController.java:65-66`). `new Vets()` leaves the list null (`Vets.java:33`) and
`getVetList()` lazily allocates it (`Vets.java:36-40`), so the later `addAll(findAll())`
depends on that getter having run (`VetController.java:70`). References to the returned `Vet`
objects are added to the wrapper's own list, which an `HttpMessageConverter` writes after the
transaction closes. JSON is verified as `{"vetList":[...]}` carrying element `id`
(`VetControllerTests.java:96-97`).

## External dependencies

Spring MVC (`VetController.java:44,65-66`); Spring Data JPA (`VetRepository.java:38`); JCache
with Caffeine, cache `vets`, statistics only, no expiry or bound
(`CacheConfiguration.java:37,49-51`; `pom.xml:68-83`); H2/MySQL/PostgreSQL schemas and seed
data (`application.properties:2-4`); Thymeleaf (`vetList.html:3`); message bundles, nine
keys (`application.properties:16`; `messages.properties:21-29`); HTTP message converters
(`VetControllerTests.java:96`); `model/` superclasses (`Person.java:28-39`,
`BaseEntity.java:33-37`); locale beans (`WebConfiguration.java:32-49`).

## Implicit assumptions

| Assumption | Label | Evidence and consequence |
|---|---|---|
| `page` is always >= 1 | **OPEN** | Unbounded at both ends (`VetController.java:45,61`); `page=0` reaches `PageRequest.of(-1, 5)`. Status unobserved. |
| Row order is stable without explicit sorting | **OPEN** | No `Sort` (`VetController.java:61`) or `OrderBy` (`VetRepository.java:56`). A vet may appear on two pages or none. |
| Vet data is effectively immutable, so eviction is unnecessary | **INFERRED** | No write path (`VetRepository.java:38-57`); no `@CacheEvict`, TTL or size bound (`CacheConfiguration.java:49-51`). External writes would never be reflected. |
| Every specialty name is non-null | **OPEN** | Nullable column (`db/h2/schema.sql:17-20`) against a write-side `@NotBlank` (`NamedEntity.java:33-35`), read by an unguarded comparator (`Vet.java:60-64`). One null fails `getSpecialties()`, called per row (`vetList.html:20`). |
| The JSON wrapper and getter shape stay compatible | **OPEN** | Property `vetList` (`Vets.java:36`); only `$.vetList[0].id` asserted (`VetControllerTests.java:96-97`). The property set is unobserved, so a getter rename may break consumers silently. |
| Database constraint differences hide no defects | **OPEN** | H2 omits (`db/h2/schema.sql:23-26`) the unique pair MySQL (`db/mysql/schema.sql:19`) and PostgreSQL (`db/postgres/schema.sql:17`) enforce, and is the default (`application.properties:2`). With no `equals`/`hashCode` on `Specialty` (`Specialty.java:28-32`), whether duplicates surface is unobserved. |
| Cached `Vet` instances are safe to share | **OPEN** | No copying (`VetController.java:51,70`); `addSpecialty` public and `getSpecialtiesInternal` mutating on read (`Vet.java:52-57,70-72`). Depends on unverified JCache store semantics. |
| An unbounded `page` cannot grow the cache | **INFERRED** | Conditional on interception engaging and on distinct runtime keys per page; `page` unbounded (`VetController.java:45`), nothing evicts (`CacheConfiguration.java:49-51`). |

## Manually verified claims

Confirmed against source by the user:

1. Pagination uses `page - 1`, fixes size at 5, bounds neither end, and declares no `Sort` or
   `OrderBy` (`VetController.java:45,60,61`; `VetRepository.java:56`).
2. The specialty name is `@NotBlank` in Java (`NamedEntity.java:33-35`) yet nullable in H2
   (`db/h2/schema.sql:17-20`).
3. H2 lacks the unique vet-specialty constraint (`db/h2/schema.sql:23-26`) that MySQL
   (`db/mysql/schema.sql:19`) and PostgreSQL (`db/postgres/schema.sql:17`) enforce.
4. `totalItems` is created (`VetController.java:54`) and never consumed: four attributes
   created, three used.

## What breaks if operation order changes

1. A bounds check placed after `PageRequest.of` would never run for `page=0`, because that
   call is reached first (`VetController.java:61`). It would look present and do nothing.
2. Sourcing `currentPage` from `Page.getNumber()` would shift the highlighted page and both
   step links: the `Page` is 0-based, the template sequence 1-based (`vetList.html:29-31`).
3. Calling `addAll` before `getVetList()` dereferences a null, because the list is allocated
   only inside that getter (`Vets.java:36-40`).

## Still opaque

Pending runtime observation: whether the caching advice intercepts a Spring Data repository
proxy (`VetRepository.java:45,55`, asserted only in a comment at
`PetClinicIntegrationTests.java:48-49`); the emitted SQL, its join shape and where the page
window is applied (`Vet.java:47`; `VetRepository.java:56`); whether an `ORDER BY` appears
(`VetController.java:61`); advisor order between the two annotations
(`VetRepository.java:44-45`); runtime cache keys and entry count (`CacheConfiguration.java:37`);
the emitted JSON property set (`Vet.java:66`) and whether XML is served (`Vets.java:30`);
status codes for out-of-range and non-numeric pages (`VetController.java:45`) and what the
error view populates (`error.html:18`); whether duplicate H2 rows collapse
(`db/h2/schema.sql:23-26`); JCache store semantics behind shared instances (`Vet.java:70-72`);
seed-data id stability across profiles (`db/postgres/data.sql:1-6`).

## Teammate review

- **Reviewer:** _pending_
- **Corrections received:** _pending_
- **Resulting change:** _pending_

## 1. Not-found signal

- [ ] 1.1 Add `OwnerNotFoundException` (extends `RuntimeException`, annotated `@ResponseStatus(HttpStatus.NOT_FOUND)`) in `src/main/java/org/springframework/samples/petclinic/owner/`, with the Apache 2.0 header, tabs at width 4, LF endings and a final newline; verify with `./mvnw compile` (or `./gradlew compileJava`).
- [ ] 1.2 Throw it from `OwnerController.findOwner(...)` (`OwnerController.java:65`) in place of `IllegalArgumentException`, keeping the existing message text; verify `./mvnw test -Dtest=OwnerControllerTests` still passes unmodified.
- [ ] 1.3 Throw it from the duplicate lookup in `OwnerController.showOwner(...)` (`OwnerController.java:172`), keeping the existing message text and leaving the duplicate lookup itself in place (out of scope per design D2); verify that `grep -n "IllegalArgumentException" src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` returns nothing, while the same grep against `PetController.java` and `VisitController.java` still returns their existing throws.

## 2. Scope guards

- [ ] 2.1 Confirm no `@ControllerAdvice`, `@ExceptionHandler`, or other cross-cutting handler was introduced: `grep -rn "ControllerAdvice\|ExceptionHandler" src/main/java` returns no new matches.
- [ ] 2.2 Confirm `PetController.java`, `VisitController.java`, `PetControllerTests.java`, and `VisitControllerTests.java` are untouched: `git diff --name-only` lists none of them.
- [ ] 2.3 Confirm no template, message key, translation, property, or build file changed: `git diff --name-only` lists nothing under `src/main/resources/`, and neither `pom.xml` nor `build.gradle`.

## 3. Integration tests

- [ ] 3.1 Create `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java` following the full-context H2 precedent of `PetClinicIntegrationTests`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@AutoConfigureTestRestTemplate`, an injected `TestRestTemplate`, and an injected `OwnerRepository` for the persistent-state invariant; verify the class starts the context with `./mvnw test -Dtest=OwnerNotFoundIntegrationTests`.
- [ ] 3.2 Add `unknownOwnerDetailsReturnsNotFound`: `GET /owners/9999` with `Accept: text/html`, then with `Accept: application/json`; verify both assert status `NOT_FOUND` and the test passes.
- [ ] 3.3 Add `unknownOwnerEditFormReturnsNotFound`: `GET /owners/9999/edit`; verify it asserts status `NOT_FOUND` and passes.
- [ ] 3.4 Add `unknownOwnerUpdateReturnsNotFoundAndChangesNoData`: `POST /owners/9999/edit` once with a valid form body and once with an invalid body (blank `firstName`); verify each asserts status `NOT_FOUND`, and after each that the owner count equals the pre-request count, `findById(9999)` is still empty, and owner 1's stored values are unchanged, using the injected `OwnerRepository` to read the count and re-read owner 1.
- [ ] 3.5 Add `unknownOwnerRendersEnglishNotFoundPage`: `GET /owners/9999` with `Accept: text/html` and `Accept-Language: en`; verify the body contains `Something happened...` and `The requested page was not found.` and contains neither `An internal server error occurred.` nor `Whitelabel Error Page`, and that no assertion touches exception text or stack traces.
- [ ] 3.6 Add `nonNumericOwnerIdReturnsBadRequest`: `GET /owners/abc`; verify it asserts status `BAD_REQUEST` and passes.
- [ ] 3.7 Add `paginationErrorIsNotTreatedAsNotFound`: `GET /owners?page=0`; verify it asserts status `INTERNAL_SERVER_ERROR` (that is, not `NOT_FOUND`) and passes.

## 4. Regression and build gates

- [ ] 4.1 Strengthen `OwnerControllerTests.processUpdateOwnerFormSuccess` with `.andExpect(redirectedUrl("/owners/1"))` and `verify(this.owners).save(any(Owner.class))`, keeping its existing `is3xxRedirection()` and `view().name("redirect:/owners/{ownerId}")` assertions and changing no other method; no import changes are needed (`verify`, `any`, and `MockMvcResultMatchers.*` are already imported at `OwnerControllerTests.java:43,48,52`). Verify with `./mvnw test -Dtest=OwnerControllerTests`.
- [ ] 4.2 Verify the remaining existing-owner behavior is unchanged: `showOwner` and `initUpdateOwnerForm` pass unmodified in the same run.
- [ ] 4.3 Verify `/oups` still returns 500 by running the unmodified `./mvnw test -Dtest=CrashControllerIntegrationTests`.
- [ ] 4.4 Run `./mvnw spring-javaformat:apply`, then `./mvnw verify` and `./gradlew build`; verify both builds pass, including checkstyle and nohttp.

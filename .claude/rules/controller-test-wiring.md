---
paths:
  - "src/test/java/org/springframework/samples/petclinic/owner/*ControllerTests.java"
---

# Spring MVC controller-test wiring

- Keep `@WebMvcTest(...)`, `@DisabledInNativeImage`, and `@DisabledInAotMode` directly on every scoped controller-test class.
- Declare MockMvc using exactly an `@Autowired` annotation followed by `private MockMvc mockMvc;`; do not replace it with constructor injection.
- Declare mocked controller collaborators as private fields annotated with `@MockitoBean`; do not replace them with constructor parameters or locally created mocks.

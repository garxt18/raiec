# Implementation Plan: JWT Authentication

## Overview

This plan adds stateless JWT bearer authentication to the RAIEC Spring Boot backend. Work proceeds bottom-up: build/config setup first, then the token utility and user persistence, then the auth service and login endpoint, then the security filter and chain wiring, finishing with integration and CORS tests. Each step builds on the previous one so nothing is left orphaned. All code lives under `com.raiec.auth.*` and `com.raiec.security.*`, follows constructor injection and Lombok conventions, and relies on `ddl-auto=update`.

Property-based tests use [jqwik](https://jqwik.net/) (`@Property(tries = 100)` minimum) and each references its design property via a `// Feature: jwt-authentication, Property {n}: ...` comment. Test sub-tasks are marked optional with `*`.

## Tasks

- [ ] 1. Build and signing configuration setup
  - [ ] 1.1 Add dependencies and JWT configuration properties
    - Add `io.jsonwebtoken:jjwt-api` (compile) and `jjwt-impl`, `jjwt-jackson` (runtime) to `pom.xml`
    - Add `spring-boot-starter-security` (compile) and `net.jqwik:jqwik` (test) to `pom.xml`
    - Add `raiec.jwt.secret=${JWT_SECRET:}` and `raiec.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}` to `src/main/resources/application.properties`
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2. JWT properties and token utility
  - [ ] 2.1 Implement `JwtProperties` configuration binding
    - Create `com.raiec.security.JwtProperties` record bound with `@ConfigurationProperties(prefix = "raiec.jwt")` and `@Validated`
    - Annotate `secret` with `@NotBlank` and `expirationMs` with `@Positive`; enable binding (`@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`)
    - _Requirements: 1.2, 1.3, 1.5, 1.6_

  - [ ] 2.2 Implement `JwtUtil` token generation and validation
    - Create `com.raiec.security.JwtUtil` building a `SecretKey` via `Keys.hmacShaKeyFor`, throwing on secrets shorter than 256 bits so the context fails to start
    - Implement `generateToken(username)` (subject, issuedAt, expiration = now + expirationMs, HS256) and `validateAndGetSubject(token)` returning the subject only on valid signature, future expiry, and non-empty subject; swallow parse/verify errors
    - _Requirements: 1.4, 4.2, 4.3, 6.2, 6.3, 6.4, 6.6_

  - [ ] 2.3 Write property test for JWT generation/validation round-trip
    - **Property 6: JWT generation/validation round-trip preserves subject and sets expiry**
    - **Validates: Requirements 4.2, 4.3**

  - [ ] 2.4 Write startup configuration edge/smoke tests
    - `@SpringBootTest` variants asserting the context fails for a too-short secret, an empty secret, and a non-positive validity duration, and starts for valid values
    - _Requirements: 1.4, 1.5, 1.6_

- [ ] 3. User account persistence
  - [ ] 3.1 Implement `User` JPA entity
    - Create `com.raiec.auth.entity.User` with `id`, unique non-null `username` (length 255), non-null `password` (BCrypt hash), non-null `role`; use Lombok annotations and the `users` table with a unique constraint on `username`
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [ ] 3.2 Implement `UserRepository`
    - Create `com.raiec.auth.repository.UserRepository` extending `JpaRepository<User, Long>` with `Optional<User> findByUsername(String username)`
    - _Requirements: 2.4, 2.5_

  - [ ] 3.3 Write property test for repository lookup semantics
    - **Property 1: Repository behaves like a username-keyed map**
    - Use `@DataJpaTest` with the H2 test profile
    - **Validates: Requirements 2.4, 2.5**

  - [ ] 3.4 Write schema constraint edge tests
    - `@DataJpaTest`: duplicate username insert violates the unique constraint and persists no new record (count unchanged); blank/over-length usernames are rejected
    - _Requirements: 2.1, 2.6_

- [ ] 4. Password hashing and authentication service
  - [ ] 4.1 Implement `AuthService` and `AuthenticationFailedException`
    - Create `com.raiec.auth.service.AuthService` with constructor injection of `UserRepository`, `PasswordEncoder`, `JwtUtil`
    - Implement `authenticate(username, password)` returning a JWT on success and throwing `AuthenticationFailedException` (generic message) when the user is absent or the password does not verify
    - Implement `createUser(username, rawPassword, role)` that BCrypt-encodes the password, persists the user, and persists nothing if hashing fails (`@Transactional`)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 5.1, 5.2, 5.3_

  - [ ] 4.2 Write property test for BCrypt hash storage
    - **Property 2: Passwords are stored only as BCrypt hashes**
    - **Validates: Requirements 3.1, 3.3, 2.2**

  - [ ] 4.3 Write property test for password verification of matching password
    - **Property 3: Password verification accepts the matching password**
    - **Validates: Requirements 3.2**

  - [ ] 4.4 Write property test for password verification of non-matching password
    - **Property 4: Password verification rejects a non-matching password**
    - **Validates: Requirements 3.5**

  - [ ] 4.5 Write property test for valid credentials yielding a verifiable token
    - **Property 5: Valid credentials yield a verifiable token for that user**
    - **Validates: Requirements 4.1**

  - [ ] 4.6 Write hashing-failure example test
    - Mock `PasswordEncoder` that throws → `createUser` propagates the error and persists no record
    - _Requirements: 3.4_

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Login endpoint
  - [ ] 6.1 Implement login DTOs
    - Create `LoginRequest` (`@NotBlank @Size(max = 255) username`, `@NotBlank @Size(max = 72) password`) and `LoginResponse(String token)` records in `com.raiec.auth.web`
    - _Requirements: 4.5, 4.6_

  - [ ] 6.2 Implement `AuthController`
    - Create `com.raiec.auth.web.AuthController` mapping `POST /api/auth/login` with `@Valid @RequestBody LoginRequest`, delegating to `AuthService.authenticate`, returning 200 with `LoginResponse` on success
    - _Requirements: 4.1, 4.4_

  - [ ] 6.3 Extend `ApiExceptionHandler` with auth error mappings
    - Add `@ExceptionHandler` for `MethodArgumentNotValidException` → 400 and `AuthenticationFailedException` → 401 with a single generic body identical for unknown-user and bad-password cases, reusing the existing `ApiError` shape
    - _Requirements: 4.5, 4.6, 5.1, 5.2, 5.3_

  - [ ] 6.4 Write property test for blank credential rejection
    - **Property 7: Blank credential fields are rejected before verification**
    - **Validates: Requirements 4.5**

  - [ ] 6.5 Write property test for over-length credential rejection
    - **Property 8: Over-length credential fields are rejected**
    - **Validates: Requirements 4.6**

  - [ ] 6.6 Write property test for uniform non-disclosing 401
    - **Property 9: Authentication failures return a uniform, non-disclosing 401**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [ ] 6.7 Write controller success example test
    - MockMvc: valid login returns 200 with the token in the body
    - _Requirements: 4.4_

- [ ] 7. JWT authentication filter
  - [ ] 7.1 Implement `JwtAuthFilter`
    - Create `com.raiec.security.JwtAuthFilter` extending `OncePerRequestFilter`; read the `Authorization` header, process only values with the case-sensitive `Bearer ` prefix and a non-empty token, delegate to `JwtUtil.validateAndGetSubject`, set a `UsernamePasswordAuthenticationToken` principal on success, leave the context unauthenticated otherwise, and always continue the chain
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ] 7.2 Write property test for valid bearer token authentication
    - **Property 10: A valid bearer token authenticates the request context**
    - **Validates: Requirements 6.1, 6.2**

  - [ ] 7.3 Write property test for invalid tokens
    - **Property 11: Invalid tokens leave the context unauthenticated**
    - **Validates: Requirements 6.3, 6.4, 6.6**

  - [ ] 7.4 Write property test for missing/malformed Authorization headers
    - **Property 12: Missing or malformed Authorization headers leave the context unauthenticated**
    - **Validates: Requirements 6.5**

- [ ] 8. Security configuration, CORS wiring, and integration
  - [ ] 8.1 Implement `SecurityConfig`
    - Create `com.raiec.security.SecurityConfig` (`@EnableWebSecurity`) defining the `SecurityFilterChain` (CORS via shared source, CSRF disabled, permit `POST /api/auth/login` and `OPTIONS /api/**`, authenticate all other `/api/**`, `STATELESS` session policy, `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`, 401 `AuthenticationEntryPoint`), plus `PasswordEncoder` (BCrypt) and a shared `CorsConfigurationSource` bean
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.1, 8.2_

  - [ ] 8.2 Refactor `WebConfig` to use the shared `CorsConfigurationSource`
    - Update `com.raiec.common.web.WebConfig` so MVC CORS delegates to the same `CorsConfigurationSource` bean (origin patterns `*`, methods `GET/POST/PUT/DELETE/OPTIONS`, all headers) used by the security chain
    - _Requirements: 8.1, 8.3, 8.4_

  - [ ] 8.3 Write endpoint authorization integration tests
    - MockMvc `@SpringBootTest`: login reachable with/without an `Authorization` header; unauthenticated protected request returns 401 without invoking the handler; a valid token reaches the handler; no HTTP session is created and no context is retained between requests
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ] 8.4 Write CORS continuity integration tests
    - Preflight `OPTIONS /api/**` succeeds without a token; allowed-origin/allowed-method request includes `Access-Control-Allow-*` headers; disallowed origin/method omits them
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [ ] 8.5 Write dependency/config smoke test
    - Context loads with JJWT and Spring Security on the classpath and `JwtProperties` bound from configuration
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP.
- Each task references specific requirements for traceability; property test tasks reference their design property number.
- Property-based tests use jqwik with a minimum of 100 generated cases and the required `// Feature: jwt-authentication, Property {n}: ...` comment.
- Service/filter property tests use an in-memory store or mocks; repository tests use `@DataJpaTest` with the existing H2 test profile.
- Checkpoints ensure incremental validation between major phases.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.1"] },
    { "id": 2, "tasks": ["2.2", "3.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "3.3", "3.4", "4.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "4.4", "4.5", "4.6", "6.1", "7.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "7.2", "7.3", "7.4"] },
    { "id": 6, "tasks": ["6.4", "6.5", "6.6", "6.7", "8.1"] },
    { "id": 7, "tasks": ["8.2"] },
    { "id": 8, "tasks": ["8.3", "8.4", "8.5"] }
  ]
}
```

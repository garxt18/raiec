# Requirements Document

## Introduction

This feature adds authentication to the RAIEC (Railway Automated Intelligent Estimate Checker) backend, which currently exposes all `/api/**` endpoints without any access control. The goal is to introduce Spring Security with stateless JWT (JSON Web Token) bearer authentication using the JJWT library, so that existing endpoints (tenders, LAR, AI analysis, rate match) require a valid token.

Scope is intentionally minimal: user login with token issuance, BCrypt-hashed password storage, and a `role` field on the user record reserved for future use. Full role-based access control (per-endpoint role enforcement) is explicitly out of scope for this phase. The permissive development CORS configuration for the frontend must continue to function.

This work continues an existing Spring Boot 4.1.0 / Java 21 codebase that follows the package convention `com.raiec.<module>.{entity,repository,service,web}`, uses Lombok and constructor injection, and relies on `spring.jpa.hibernate.ddl-auto=update` (no Flyway).

## Glossary

- **Backend**: The RAIEC Spring Boot application that hosts the REST API under `/api/**`.
- **User_Account**: A persisted record representing a person who can authenticate, stored via the new `User` JPA entity in package `com.raiec.auth.entity`.
- **User_Repository**: The Spring Data JPA repository for `User_Account` lookups by username.
- **Auth_Service**: The service component that validates credentials and produces tokens.
- **Auth_Controller**: The REST controller exposing the login endpoint under `/api/auth`.
- **Jwt_Util**: The component that creates and validates signed JWT bearer tokens.
- **Jwt_Auth_Filter**: The servlet filter that extracts and validates the bearer token on incoming requests and establishes the security context.
- **Security_Config**: The Spring Security configuration that defines the filter chain, endpoint authorization rules, and stateless session policy.
- **JWT**: JSON Web Token, a signed bearer token carrying the authenticated user's identity.
- **Bearer_Token**: A JWT supplied by a client in the HTTP `Authorization` header using the `Bearer <token>` scheme.
- **Protected_Endpoint**: Any endpoint under `/api/**` other than the login endpoint (for example `/api/tenders/**`, `/api/lar/**`).
- **Login_Endpoint**: `POST /api/auth/login`, the only `/api/**` endpoint permitted without authentication.
- **BCrypt**: The password hashing algorithm used to store and verify user passwords.
- **Role**: A string label associated with a `User_Account` (for example `ADMIN`, `REVIEWER`) stored for future authorization use and not enforced per-endpoint in this phase.

## Requirements

### Requirement 1: JWT Library and Signing Configuration

**User Story:** As a backend developer, I want the JJWT library and a configurable signing secret available, so that the application can issue and verify signed tokens.

#### Acceptance Criteria

1. THE Backend SHALL include the JJWT library as a runtime dependency in the Maven build.
2. WHEN the Backend starts, THE Backend SHALL read the JWT signing secret from an externalized configuration property.
3. WHEN the Backend starts, THE Backend SHALL read the JWT token validity duration from an externalized configuration property expressed as a positive number of milliseconds.
4. IF the JWT signing secret is shorter than 256 bits (32 bytes), the minimum length required by the configured HMAC signing algorithm, THEN THE Backend SHALL fail to start during startup and report an error message indicating that the signing secret is too short.
5. IF the JWT signing secret configuration property is absent or empty when the Backend starts, THEN THE Backend SHALL fail to start and report an error message indicating that the signing secret is required.
6. IF the JWT token validity duration configuration property is absent, non-numeric, or not a positive value when the Backend starts, THEN THE Backend SHALL fail to start and report an error message indicating that the token validity duration is invalid.

### Requirement 2: User Account Persistence

**User Story:** As an administrator, I want user accounts stored with a username, a hashed password, and a role, so that the system can authenticate users and retain role information for later use.

#### Acceptance Criteria

1. THE User_Account SHALL store a username field that is unique across all User_Accounts, non-blank, and no longer than 255 characters.
2. THE User_Account SHALL store a non-blank password field that contains a BCrypt hash.
3. THE User_Account SHALL store a non-null Role field as a string value.
4. WHEN a username that is exactly equal to a stored User_Account username is supplied, THE User_Repository SHALL return that single matching User_Account.
5. WHEN a username is supplied that matches no stored record, THE User_Repository SHALL return an empty result.
6. IF a User_Account is persisted with a username that already exists for another User_Account, THEN THE Backend SHALL reject the operation and persist no new record.

### Requirement 3: Password Hashing

**User Story:** As a security-conscious developer, I want passwords stored as BCrypt hashes, so that plaintext passwords are never persisted.

#### Acceptance Criteria

1. WHEN a User_Account is created, THE Backend SHALL transform the supplied plaintext password into a BCrypt hash and store that hash as the User_Account password value.
2. WHEN credentials are verified with a supplied password that matches the stored BCrypt hash for the username, THE Auth_Service SHALL return a successful verification result using the BCrypt verification function.
3. THE Backend SHALL store no plaintext password value for any User_Account.
4. IF BCrypt hashing of a password fails during User_Account creation, THEN THE Backend SHALL abort the creation, persist no User_Account record, and report a creation error to the caller.
5. IF credentials are verified with a supplied password that does not match the stored BCrypt hash for the username, THEN THE Auth_Service SHALL return a failed verification result.

### Requirement 4: User Login and Token Issuance

**User Story:** As a registered user, I want to log in with my username and password and receive a token, so that I can authenticate subsequent API requests.

#### Acceptance Criteria

1. WHEN a client sends a request to the Login_Endpoint with a username that exactly matches a stored User_Account username and a password that verifies against that account's stored BCrypt hash, THE Auth_Service SHALL return a signed JWT.
2. WHEN the Auth_Service issues a JWT, THE Jwt_Util SHALL set the token subject to the authenticated username.
3. WHEN the Auth_Service issues a JWT, THE Jwt_Util SHALL set the token expiration to the current time plus the token validity duration configured in Requirement 1.
4. WHEN the Login_Endpoint returns a successful response, THE Auth_Controller SHALL respond with HTTP status 200 and a response body that contains the signed JWT.
5. IF a login request has a username or password field that is absent, empty, or whitespace-only, THEN THE Auth_Controller SHALL respond with HTTP status 400, perform no credential verification, and return an error indication.
6. IF a login request supplies a username longer than 255 characters or a password longer than 72 characters, THEN THE Auth_Controller SHALL respond with HTTP status 400 and return an error indication.

### Requirement 5: Invalid Credential Handling

**User Story:** As a user, I want failed logins to be rejected clearly, so that invalid attempts do not gain access.

#### Acceptance Criteria

1. IF a login request supplies a username that matches no stored User_Account, THEN THE Auth_Controller SHALL respond with HTTP status 401 and an error body that contains no JWT.
2. IF a login request supplies a password that does not match the stored BCrypt hash for the username, THEN THE Auth_Controller SHALL respond with HTTP status 401 and an error body that contains no JWT.
3. WHEN authentication fails because of an unknown username or a non-matching password, THE Auth_Controller SHALL respond with a response body that is identical for both failure cases and that indicates authentication failure without disclosing whether the username or the password was incorrect.

### Requirement 6: Token Validation on Requests

**User Story:** As the system, I want to validate the bearer token on each request, so that only authenticated callers reach protected endpoints.

#### Acceptance Criteria

1. WHEN a request includes an `Authorization` header whose value begins with the case-sensitive prefix `Bearer ` followed by a non-empty token value, THE Jwt_Auth_Filter SHALL extract that token value.
2. WHEN the extracted token has a signature that verifies against the configured signing secret, an expiration time strictly later than the current time, and a non-empty subject, THE Jwt_Auth_Filter SHALL establish an authenticated security context for the request using the token subject as the authenticated principal and continue the filter chain.
3. IF the extracted token has a signature that does not verify against the configured signing secret, THEN THE Jwt_Auth_Filter SHALL leave the security context unauthenticated and continue the filter chain.
4. IF the extracted token has an expiration time at or before the current time, THEN THE Jwt_Auth_Filter SHALL leave the security context unauthenticated and continue the filter chain.
5. WHEN a request includes no `Authorization` header, or an `Authorization` header that does not begin with the `Bearer ` prefix, or a `Bearer ` prefix followed by an empty token value, THE Jwt_Auth_Filter SHALL leave the security context unauthenticated and continue the filter chain.
6. IF the extracted token cannot be parsed or has a missing or empty subject, THEN THE Jwt_Auth_Filter SHALL leave the security context unauthenticated and continue the filter chain.

### Requirement 7: Endpoint Authorization

**User Story:** As a system owner, I want existing API endpoints to require authentication while allowing login, so that protected data is no longer publicly accessible.

#### Acceptance Criteria

1. WHEN a request targets the Login_Endpoint, THE Security_Config SHALL permit the request to reach its handler without requiring authentication, regardless of whether the request includes an `Authorization` header.
2. IF a request targets a Protected_Endpoint without an authenticated security context, THEN THE Security_Config SHALL reject the request with HTTP status 401 and SHALL not invoke the Protected_Endpoint handler.
3. WHEN a request targets a Protected_Endpoint with an authenticated security context, THE Security_Config SHALL allow the request to reach its handler.
4. THE Security_Config SHALL configure session management with a stateless policy that creates no server-side HTTP session and retains no security context between requests.

### Requirement 8: CORS Continuity

**User Story:** As a frontend developer, I want cross-origin requests to keep working after security is added, so that the existing frontend continues to call the API.

#### Acceptance Criteria

1. THE Security_Config SHALL register the existing CORS configuration (allowed origins, methods, and headers for `/api/**`) within the security filter chain and process it before authentication.
2. WHEN a browser sends a CORS preflight `OPTIONS` request to an `/api/**` path, THE Security_Config SHALL permit the request without subjecting it to token validation and without requiring a Bearer_Token.
3. WHEN a client sends a cross-origin request to a Protected_Endpoint from an allowed origin using an allowed HTTP method, THE Backend SHALL include the configured CORS response headers (allowed origin, methods, and headers).
4. IF a cross-origin request to an `/api/**` path originates from a disallowed origin or uses a disallowed HTTP method, THEN THE Backend SHALL omit the CORS allow headers from the response.

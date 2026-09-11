## Purpose

Defines the HTTP behavior of the owner detail and owner edit routes, covering how the application reports a request for an owner that does not exist and which neighbouring error classifications must stay unchanged when that reporting changes.

## ADDED Requirements

### Requirement: Missing owner returns 404 on owner detail and edit routes

When the `{ownerId}` path segment parses as an integer but no owner with that ID exists, the system SHALL respond with HTTP **404**. The in-scope routes are exactly `GET /owners/{ownerId}`, `GET /owners/{ownerId}/edit`, and `POST /owners/{ownerId}/edit`; this list is exhaustive for this capability.

The 404 status SHALL apply regardless of the request's `Accept` header. Only the HTML response body is specified; the JSON error-body shape is not part of this contract.

Owners **1** and **9999** below are test fixtures, not product rules: 1 exists in the seeded data, 9999 does not.

#### Scenario: Owner detail for a missing owner

- **WHEN** a client sends `GET /owners/9999` and no owner with ID 9999 exists, with `Accept: text/html`
- **THEN** the response status is 404

#### Scenario: Owner detail for a missing owner with a non-HTML Accept header

- **WHEN** a client sends `GET /owners/9999` and no owner with ID 9999 exists, with `Accept: application/json`
- **THEN** the response status is 404
- **AND** the response body shape is unspecified by this requirement

#### Scenario: Owner edit form for a missing owner

- **WHEN** a client sends `GET /owners/9999/edit` and no owner with ID 9999 exists
- **THEN** the response status is 404

#### Scenario: Owner update submission for a missing owner

- **WHEN** a client sends `POST /owners/9999/edit` and no owner with ID 9999 exists
- **THEN** the response status is 404

### Requirement: The 404 reuses the existing error page

An HTML request that results in an owner-not-found 404 SHALL render the application's existing error page with its existing 404 message. The system SHALL NOT add or change a template, message key, or translation to satisfy this requirement.

Exception messages, stack traces, and other development-only response fields are NOT part of this contract and MUST NOT be asserted.

#### Scenario: English error page for a missing owner

- **WHEN** a client sends `GET /owners/9999` with `Accept: text/html` and `Accept-Language: en`, and no owner with ID 9999 exists
- **THEN** the response status is 404
- **AND** the body contains `Something happened...`
- **AND** the body contains `The requested page was not found.`
- **AND** the body does not contain `An internal server error occurred.`
- **AND** the body does not contain `Whitelabel Error Page`

### Requirement: A missing-owner request performs no persistent write

A request that results in an owner-not-found 404 SHALL NOT create, update, or delete owner data. On `POST /owners/{ownerId}/edit` the missing-owner check SHALL precede form validation, so a missing owner yields 404 whether the submitted form is valid or invalid, and the submitted field values are never written onto any record.

#### Scenario: Valid form submitted for a missing owner

- **WHEN** a client sends `POST /owners/9999/edit` with a complete valid form and no owner with ID 9999 exists
- **THEN** the response status is 404
- **AND** the total owner count is the same as before the request
- **AND** no owner with ID 9999 exists
- **AND** the stored values of a control owner that does exist are unchanged

#### Scenario: Invalid form submitted for a missing owner

- **WHEN** a client sends `POST /owners/9999/edit` with an invalid form containing a blank `firstName`, and no owner with ID 9999 exists
- **THEN** the response status is 404 rather than a re-rendered form with field errors
- **AND** the total owner count is the same as before the request
- **AND** no owner with ID 9999 exists
- **AND** the stored values of a control owner that does exist are unchanged

### Requirement: Existing-owner behavior is unchanged

For an owner that exists, the successful response SHALL be unchanged in status, selected view, model attributes, and redirect target.

The owner model carries the requested owner, including any associated pets and their visits. This does not require the seeded owner 1 to have any particular pet or visit; it constrains the shape of what is exposed, not the fixture data.

#### Scenario: Owner detail for an existing owner

- **WHEN** a client sends `GET /owners/1` and owner 1 exists
- **THEN** the response status is 200
- **AND** the selected view is `owners/ownerDetails`
- **AND** the `owner` model attribute contains the requested owner, including any associated pets and their visits

#### Scenario: Owner edit form for an existing owner

- **WHEN** a client sends `GET /owners/1/edit` and owner 1 exists
- **THEN** the response status is 200
- **AND** the selected view is `owners/createOrUpdateOwnerForm`
- **AND** the `owner` model attribute contains the owner's current values

#### Scenario: Owner update submission for an existing owner

- **WHEN** a client sends `POST /owners/1/edit` with a valid form and owner 1 exists
- **THEN** the response is a 3xx redirect to `/owners/1`
- **AND** the owner is saved

### Requirement: Unrelated error classifications are preserved

The 404 SHALL apply only to owner-not-found. The system SHALL NOT classify a whole exception category as 404, and SHALL NOT introduce a global exception mapping or any other cross-cutting handler that alters the behavior of routes outside this capability.

The scenarios below are non-regression guards. Freezing a current status here records today's behavior; it does not endorse that status as ideal.

#### Scenario: Non-numeric owner ID stays 400

- **WHEN** a client sends `GET /owners/abc`, whose `{ownerId}` segment does not parse as an integer
- **THEN** the response status is 400, unchanged by this capability

#### Scenario: Invalid pagination argument stays 500

- **WHEN** a client sends `GET /owners?page=0`, which fails on an invalid pagination argument rather than a missing owner
- **THEN** the response status is not 404
- **AND** the response status remains 500, as it is today

#### Scenario: A genuine server fault stays 500

- **WHEN** a client sends `GET /oups`, a route that raises a genuine server fault
- **THEN** the response status is 500, unchanged by this capability

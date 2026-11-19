# Homework - Notification Dispatch Service

Click **Use this template → Create a new repository**. Create a **private** repository under your own account and invite orion-homework-dev as a collaborator. Complete the assignment and send us your repository link. Please do not publish your solution or open a pull request against this template.

## Objective

Refactor the legacy code to a production ready solution for the notification dispatch service that is maintainable, readable, and scalable using modern practices.

This exercise is intended to be completed in approximately 4 hours.

The application sends `EMAIL`, `SMS`, and `PUSH` notifications and enforces blacklist and circuit-breaker safeguards. Notification attempts are not persisted.
The goal is to improve the design so the code is easier to understand, easier to extend, and more robust without changing the behavior that matters.

This is a design and refactoring exercise. The focus is on making a small system feel thoughtful, clean, and ready for change. 
It also acknowledges that existing API boundaries and persistence design may not be ideal and should be improved.

## System summary

The service is responsible for:

- sending notifications to different channels
- rejecting unsupported notification types
- rejecting blacklisted recipients
- temporarily rejecting dispatches after repeated handler failures

## Core expectations

The solution should be clear and defensible.

The project should demonstrate sound engineering judgment through:

- clear, readable code
- sensible use of current best practices
- no unnecessary complexity

## Behavior to preserve

The system must continue to support the existing notification types and operational safeguards:

- `EMAIL`
- `SMS`
- `PUSH`
- blacklisted recipients are rejected
- unsupported notification types fail clearly
- three consecutive handler failures open the circuit breaker for 60 seconds

## What matters most

We care about:

- correctness
- maintainability
- readability
- clear design decisions
- practical modernization
- scalability

## Development process

Please keep a record of the changes you make and why you make them. Preferably, use small, meaningful Git commits with messages that explain both the change and its rationale. Brief notes in the README can supplement the commit history, especially for trade-offs or alternatives you considered.

## Constraints

The solution must use Java and Spring Boot. You may upgrade the Java version and dependencies.

Everything else may be redesigned or rewritten, including the API, database schema, persistence approach, and project structure, provided the behavior described above is preserved.

Keep the solution proportional to the problem. The goal is to transform a small but messy system into something clean, understandable, and fit for future change.


## Bonus points

These are not required for the core task, but they can earn additional credit if included well:

- adding a update endpoint for notifications
- introducing a proper blacklist functionality to enforcement flow for blocked recipients

## Running locally

The application requires Java 25 and uses the Gradle wrapper.

- Run the service with `gradlew.bat bootRun` on Windows or `./gradlew bootRun` on macOS/Linux.
- Run the tests with `gradlew.bat test` on Windows or `./gradlew test` on macOS/Linux.

## Environment profiles

The application has separate Spring configuration profiles:

- `dev` (default) uses an H2 in-memory database and initializes the schema.
- `test` uses a separate H2 in-memory database; Gradle activates this profile for tests.
- `prod` expects `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` to be supplied by the deployment environment. Schema initialization is disabled to prevent the development schema script from dropping production tables. The JDBC driver for the production database must be available at runtime.

Activate production configuration with `--spring.profiles.active=prod` when launching the application. Override the default profile with `--spring.profiles.active=dev` as needed.

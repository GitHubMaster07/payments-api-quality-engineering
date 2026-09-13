# Payments API Quality Engineering

A Java 21 / Spring Boot quality engineering project focused on API and integration testing patterns for payment workflows.

## Current Status

Slice 0 - project bootstrap.

Implemented so far:
- Spring Boot application startup
- Actuator health endpoint
- Minimal Spring context test
- Maven build foundation

## Prerequisites

- Java 21
- Maven 3.9+

## Run Locally

Run: `mvn spring-boot:run`

Then verify: `curl http://localhost:8080/actuator/health`

Expected response: `{"status":"UP"}`

## Run Tests

Run: `mvn test`

## Project Direction

Planned capabilities include API testing, database integration, security testing, contract validation, event-driven integration testing, resilience, and CI quality gates.

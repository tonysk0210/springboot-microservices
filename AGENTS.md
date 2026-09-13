# Repository Guidelines

## Project Structure & Module Organization

This repository contains independent Spring Boot services: `account`, `card`,
`loan`, `configserver`, `eurekaserver`, `gatewayserver`, and `messageservice`.
Each service keeps Java code under `src/main/java`, configuration under
`src/main/resources`, and tests under `src/test/java`. `microservices-bom`
centralizes Spring Boot, Spring Cloud, Java 25, and plugin versions. Runtime
infrastructure is defined in `compose*.yml`, raw Kubernetes manifests in
`kubernetes/`, and Helm charts in `helm/`.

## Build, Test, and Development Commands

Run commands from the relevant service directory in PowerShell:

```powershell
cd microservices-bom; .\mvnw.cmd install
cd ..\account; .\mvnw.cmd test
cd ..\gatewayserver; .\mvnw.cmd spring-boot:run
```

Install the BOM before building a service. Use `test` for compilation and
automated tests, and `spring-boot:run` for local development. To run the
containerized stack from the repository root, ensure `.env` defines required
secrets such as `ENCRYPT_KEY`, then run `docker compose up -d`. Add
`--profile observability` to include monitoring services.

## Coding Style & Naming Conventions

Follow the existing four-space Java/XML/YAML indentation and package layout
(`com.example.<service>`). Use PascalCase for classes, camelCase for methods
and fields, and lowercase service names for modules, Docker services, and
discovery identifiers. Keep configuration in YAML and avoid committing local
secrets. No repository-wide formatter or linter is configured; keep changes
consistent with neighboring code.

## Testing Guidelines

Tests use Spring Boot's test starters and JUnit conventions. Place tests in the
matching package under `src/test/java` and name them `<Subject>Tests.java`.
Run `.\mvnw.cmd test` in the changed service; add focused tests for changed
controllers, services, persistence, or configuration. No explicit coverage
threshold is configured.

## Commit & Pull Request Guidelines

Use a short, imperative commit subject describing one change (for example,
`update gateway fallback` or `update helm`). Pull requests should explain the
affected services, configuration or deployment impact, validation commands and
any required environment variables. Include request/response examples or
screenshots when changing API or observability behavior, and call out any
manual Kubernetes or Compose steps reviewers must reproduce.

## Security & Configuration Tips

Treat `.env` and Kubernetes secret manifests as sensitive; never add real
credentials to commits. Compose services communicate through service DNS, while
production Compose exposes business APIs through the Gateway. For Helm changes,
use the documented deployment order and avoid applying raw manifests over
Helm-managed resources.

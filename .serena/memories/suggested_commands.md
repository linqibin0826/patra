# Suggested Commands

## Local Infra (Docker)
- Start dependencies: `cd docker/compose && docker compose up -d`

## Build & Test
- Full build: `./mvnw -T 1C clean verify`
- Fast compile: `./mvnw -q -DskipTests compile`
- Module tests: `./mvnw -q -pl <module> test`

## Run Services (Spring Boot)
- Ingest: `./mvnw -pl patra-ingest/patra-ingest-boot -am spring-boot:run`
- Registry: `./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run`
- Egress Gateway: `./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run`
- API Gateway: `./mvnw -pl patra-gateway-boot spring-boot:run`

## Utilities (Darwin)
- List files: `ls -la`
- Change dir: `cd <path>`
- Search text: `rg <pattern>` (fallback: `grep -R "<pattern>" .`)
- Find files: `rg --files | rg <suffix>` (fallback: `find . -name "*<suffix>"`)
- Git basics: `git status`, `git add -p`, `git commit -m "feat: ..."`, `git log --oneline`

## Notes
- Prefer the repository-bundled Maven wrapper `./mvnw`.
- Configure `application-local.yaml` and Nacos credentials locally; never commit secrets.
- If startup fails: verify Docker health, port conflicts, DB migrations, and Nacos reachability.
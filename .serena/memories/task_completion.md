# Task Completion Checklist

When completing a development task, follow these steps:

## 1. Self-Check Compilation
```bash
./mvnw -q -DskipTests compile
```
**Must pass** before submitting code. Ensures no compilation errors.

## 2. Code Review
- Invoke `code-reviewer` agent for thorough review
- Address Critical and High priority issues
- Consider Medium/Low issues for quality improvement

## 3. Code Refactoring (If Needed)
- If code needs readability improvements, naming optimization, or comment enhancement
- Invoke `code-refiner` agent for zero-behavior-change refactoring
- Examples: split long methods, improve naming, add JavaDoc

## 4. Unit Testing
- Invoke `qa-unit-tests` agent to write/update unit tests
- Target: JUnit5 + AssertJ + Mockito
- No external dependencies (mock all infrastructure)

## 5. Integration Testing (For Cross-Layer Changes)
- Invoke `qa-integration-tests` agent for E2E scenarios
- Use: Spring Boot Test + Testcontainers + WireMock
- Verify cross-layer/cross-resource behavior

## 6. Quality Gates (Before Merge/Release)
- Invoke `qa-quality-gates` agent for comprehensive quality check
- Verify: test coverage, build status, static analysis
- Target coverage: Overall ≥85%, Domain ≥95%, Key paths ≥90%

## 7. Documentation (If Needed)
- For API changes: Invoke `docs-engineer` to sync OpenAPI/SpringDoc
- For architecture changes: Invoke `mermaid-expert` for diagrams + `docs-engineer` for ADR
- For domain model changes: Update module README and deep-dive docs

## Minimal Flow (Simple Features)
```
1. Code implementation (main agent)
2. ./mvnw -q -DskipTests compile ✅
3. code-reviewer review
4. qa-unit-tests
5. Merge
```

## Complete Flow (Complex Features/Architecture Changes)
```
1. Requirements clarification
2. [Complex design?] → architecture-reviewer
3. [Need research?] → search-specialist
4. Code implementation (main agent)
5. ./mvnw -q -DskipTests compile ✅
6. code-reviewer review
7. [Need refactoring?] → code-refiner
8. qa-unit-tests
9. qa-integration-tests
10. qa-quality-gates
11. [Need documentation?] → mermaid-expert + docs-engineer
12. Merge and release
```

## Bug Fix Flow
```
1. Issue diagnosis → java-microservice-debugger
2. [Need tracing?] → business-trace-analyzer
3. Fix implementation (main agent)
4. ./mvnw -q -DskipTests compile ✅
5. code-reviewer review
6. qa-unit-tests (regression tests)
7. qa-integration-tests (scenario validation)
```

## Key Principles
- **Always compile-check** before delegating to other agents
- **Small diffs**: Make incremental, focused changes
- **Document assumptions**: Explain key decisions and trade-offs
- **No hardcoded secrets**: Use Nacos or environment variables
- **English comments**: All code comments and logs in English
---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Layer?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Layer]**: Hexagonal Architecture layer label (optional but recommended)
  - `[Domain]`: Domain layer (纯 Java 领域模型)
  - `[App]`: Application layer (Orchestrator/Coordinator)
  - `[Infra]`: Infrastructure layer (Repository 实现/MyBatis Mapper)
  - `[Adapter]`: Adapter layer (Controller/Listener/Job)
  - `[API]`: API/Contract layer (DTO/Facade)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions (use Patra module paths: `patra-{service}-{layer}/src/...`)

## Path Conventions

**Patra 项目使用六边形架构的多模块结构**：

- **Domain 层**: `patra-{service}-domain/src/main/java/com/patra/{service}/domain/`
- **Application 层**: `patra-{service}-app/src/main/java/com/patra/{service}/app/`
- **Infrastructure 层**: `patra-{service}-infra/src/main/java/com/patra/{service}/infra/`
- **Adapter 层**: `patra-{service}-adapter/src/main/java/com/patra/{service}/adapter/`
- **API 层**: `patra-{service}-api/src/main/java/com/patra/{service}/api/`
- **测试**:
  - 单元测试: `src/test/java/` (与源码同结构)
  - IT 测试: `patra-{service}-infra/src/test/java/` (`*IT.java`)
  - E2E 测试: `patra-{service}-boot/src/test/java/` (`*E2ETest.java`)

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create project structure per implementation plan
- [ ] T002 Initialize [language] project with [framework] dependencies
- [ ] T003 [P] Configure linting and formatting tools

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Setup database schema and migrations framework
- [ ] T005 [P] Implement authentication/authorization framework
- [ ] T006 [P] Setup API routing and middleware structure
- [ ] T007 Create base models/entities that all stories depend on
- [ ] T008 Configure error handling and logging infrastructure
- [ ] T009 Setup environment configuration management

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T010 [P] [US1] Contract test for [endpoint] in tests/contract/test_[name].py
- [ ] T011 [P] [US1] Integration test for [user journey] in tests/integration/test_[name].py

### Implementation for User Story 1

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T012 [P] [Domain] [US1] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T013 [P] [Domain] [US1] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T014 [P] [Domain] [US1] 定义 [DomainEvent] 领域事件 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/event/[DomainEvent].java
- [ ] T015 [Domain] [US1] 定义 [Repository] 仓储接口 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/repository/[Repository].java
- [ ] T016 [App] [US1] 实现 [Coordinator] 协调器 in patra-[service]-app/src/main/java/com/patra/[service]/app/coordinator/[Coordinator].java (depends on T012-T015)
- [ ] T017 [P] [Infra] [US1] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T018 [P] [Infra] [US1] 创建 MyBatis Mapper in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/mapper/[EntityMapper].java
- [ ] T019 [Adapter] [US1] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java (depends on T016)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

- [ ] T018 [P] [US2] Contract test for [endpoint] in tests/contract/test_[name].py
- [ ] T019 [P] [US2] Integration test for [user journey] in tests/integration/test_[name].py

### Implementation for User Story 2

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T020 [P] [Domain] [US2] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T021 [P] [Domain] [US2] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T022 [App] [US2] 实现 [Orchestrator] 编排器 in patra-[service]-app/src/main/java/com/patra/[service]/app/orchestrator/[Orchestrator].java (depends on T020-T021)
- [ ] T023 [P] [Infra] [US2] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T024 [Adapter] [US2] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java (depends on T022)
- [ ] T025 [US2] 如需要，与 User Story 1 组件集成（跨聚合调用需通过 Application 层）

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 (OPTIONAL - only if tests requested) ⚠️

- [ ] T024 [P] [US3] Contract test for [endpoint] in tests/contract/test_[name].py
- [ ] T025 [P] [US3] Integration test for [user journey] in tests/integration/test_[name].py

### Implementation for User Story 3

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T026 [P] [Domain] [US3] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T027 [P] [Domain] [US3] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T028 [App] [US3] 实现 [Coordinator] 协调器 in patra-[service]-app/src/main/java/com/patra/[service]/app/coordinator/[Coordinator].java (depends on T026-T027)
- [ ] T029 [P] [Infra] [US3] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T030 [Adapter] [US3] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java (depends on T028)

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Performance optimization across all stories
- [ ] TXXX [P] Additional unit tests (if requested) in tests/unit/
- [ ] TXXX Security hardening
- [ ] TXXX Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# 同时启动 User Story 1 的所有 Domain 层任务（纯 Java，无依赖）:
Task: "定义 Article 聚合根 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/Article.java"
Task: "定义 ArticleId 值对象 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/ArticleId.java"
Task: "定义 ArticleCreated 领域事件 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/event/ArticleCreated.java"

# 同时启动 User Story 1 的所有 Infra 层任务（不同文件，可并行）:
Task: "实现 ArticleRepositoryImpl in patra-ingest-infra/src/main/java/com/patra/ingest/infra/repository/ArticleRepositoryImpl.java"
Task: "创建 ArticleMapper in patra-ingest-infra/src/main/java/com/patra/ingest/infra/repository/mapper/ArticleMapper.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

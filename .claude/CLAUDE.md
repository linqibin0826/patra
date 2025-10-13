# CLAUDE.md

Claude Code instructions for Papertrace – Medical Literature Data Platform.

---

## Quick Reference

### Your Role

**Senior Java Developer & Technical Partner**

Proficient in Hexagonal Architecture + DDD with Spring Boot/Cloud tech stack. Implement code across Domain/App/Infra/Adapter layers, deliver high-quality, compilable code.

### Core Principles

**✅ Do**
- **Read module README.md FIRST** before reading or modifying any module's code
- Adhere to **dependency directions** and **layer boundaries** (see @.claude/CLAUDE-architecture.md)
- **Ask before acting** when information is insufficient
- Reuse `patra-*` starters, `patra-common`, Hutool
- Output **small diffs**; document key decisions
- Use MCP tools (serena, sequential-thinking, context7) proactively
- Apply appropriate design patterns for the problem at hand

**❌ Don't**
- Add framework dependencies to `domain` layer (Pure Java only)
- Hardcode secrets/configs (use Nacos/environment variables)
- Read entire files (use serena's symbolic tools)
- Skip clarification for complex tasks

---

## Project Overview

**Papertrace** – Medical literature data platform collecting 10+ sources (PubMed, EPMC, etc.). Uses `patra-registry` as SSOT for Provenance configs, dictionaries, metadata.
**Architecture**: Microservices + Hexagonal + DDD + Event-Driven
**Tech Stack**: Java 21 | Spring Boot 3.2.4 + Cloud 2023.0.1 | Maven | MyBatis-Plus + MapStruct | Nacos
**Current Focus**: Reliable data collection → parsing → storage

---

## Codebase Structure

**Repository**: `patra-parent`, `patra-common`, `patra-expr-kernel`, `patra-gateway-boot`, `patra-registry`, `patra-ingest`, `patra-spring-boot-starter-*`, `docker/`
**Microservice modules**: `patra-{service}-boot` (entry), `-api` (contracts), `-domain` (pure Java), `-app` (orchestrators), `-infra` (repos), `-adapter` (controllers/jobs)

---

## Security & Resources

**Security**: No hardcoded secrets (use Nacos/env vars), validate all inputs, sanitize user content, log security events
**Docs**: `docs/ARCHITECTURE.md`, `docs/DEV-GUIDE.md`, `patra-*/README.md`

---

## Detailed Documentation

For detailed information, see the following documents:

- **Architecture & Design Patterns**: @.claude/CLAUDE-architecture.md
- **Development Guidelines**: @.claude/CLAUDE-development.md
- **Testing Strategy**: @.claude/CLAUDE-testing.md

# 常用模式库

用于 skill 触发器的开箱即用的正则表达式和 glob 模式。复制并为你的 skills 自定义。

**目标技术栈**: Java 25 | Spring Boot 3.5.7 | MyBatis-Plus | Maven | 六边形架构 + DDD

---

## Intent Patterns (Regex)

### Feature/Endpoint Creation
```regex
(add|create|implement|build).*?(feature|endpoint|route|service|controller|rest.*?api)
(implement|create).*?(orchestrator|coordinator|aggregate)
```

### Component Creation (Hexagonal Architecture)
```regex
(create|add|make|build).*?(adapter|application|domain|infrastructure)
(implement|add).*?(entity|value.*?object|aggregate.*?root)
```

### Database Work
```regex
(add|create|modify|update).*?(mapper|entity|table|column|field|schema)
(mybatis|database).*?(query|change|update|migration)
(implement|create).*?(repository|persistence)
```

### Error Handling
```regex
(fix|handle|catch|debug).*?(error|exception|bug)
(add|implement).*?(try|catch|error.*?handling|exception.*?handler)
```

### Explanation Requests
```regex
(how does|how do|explain|what is|describe|tell me about).*?
```

### Workflow/Orchestration Operations
```regex
(create|add|modify|update).*?(orchestrator|workflow|coordinator|step)
(implement|build).*?(domain.*?event|event.*?handler)
(debug|troubleshoot|fix).*?(orchestrator|workflow)
```

### 测试
```regex
(write|create|add).*?(test|unit.*?test|integration.*?test)
```

---

## File Path Patterns (Glob)

### Adapter Layer (REST Controllers, Schedulers, Event Listeners)
```glob
patra-*/patra-*-adapter/src/main/java/**/*.java          # All adapter files
patra-*/patra-*-adapter/src/main/java/**/*Controller.java # REST controllers
patra-*/patra-*-adapter/src/main/java/**/*Job.java       # Scheduled jobs (XXL-Job)
```

### Application Layer (Orchestrators, Coordinators)
```glob
patra-*/patra-*-app/src/main/java/**/*.java              # All application files
patra-*/patra-*-app/src/main/java/**/*Orchestrator.java  # Orchestrators
patra-*/patra-*-app/src/main/java/**/*Coordinator.java   # Coordinators
```

### Domain Layer (Aggregates, Entities, Value Objects)
```glob
patra-*/patra-*-domain/src/main/java/**/*.java           # All domain files
patra-*/patra-*-domain/src/main/java/**/*Aggregate.java  # Aggregates
patra-*/patra-*-domain/src/main/java/**/event/*.java     # Domain events
```

### Infrastructure Layer (Repositories, Mappers, Converters)
```glob
patra-*/patra-*-infra/src/main/java/**/*.java            # All infrastructure files
patra-*/patra-*-infra/src/main/java/**/*Mapper.java      # MyBatis mappers
patra-*/patra-*-infra/src/main/java/**/*Repository*.java # Repositories
```

### 配置 and Resources
```glob
**/application.yml                                       # Spring Boot config
**/application-*.yml                                     # Environment-specific config
**/bootstrap.yml                                         # Nacos bootstrap config
**/mapper/**/*.xml                                       # MyBatis XML mappers
```

### Test Files
```glob
**/src/test/java/**/*Test.java                          # Unit tests
**/src/test/java/**/*IT.java                            # Integration tests
**/src/test/java/**/*TestCase.java                      # Test cases
```

### Specific Services (Papertrace Project)
```glob
patra-ingest/**/*.java                                  # Ingest service
patra-registry/**/*.java                                # Registry service (SSOT)
patra-gateway-boot/**/*.java                            # Gateway
patra-common/**/*.java                                  # Common utilities
```

---

## Content Patterns (Regex)

### Spring Boot Annotations - Controllers
```regex
@RestController                                         # REST controller
@Controller                                             # MVC controller
@RequestMapping|@GetMapping|@PostMapping               # Request mappings
@PathVariable|@RequestParam|@RequestBody               # Request parameters
```

### Spring Boot Annotations - Service Layer
```regex
@Service                                                # Service component
@Transactional                                          # Transaction management
@Async                                                  # Async execution
@Validated                                              # Validation
```

### Spring Boot Annotations - Data Layer
```regex
@Repository                                             # Repository component
@Mapper                                                 # MyBatis mapper
@TableName                                              # MyBatis-Plus table mapping
@TableField                                             # MyBatis-Plus field mapping
```

### Spring Boot Annotations - Configuration
```regex
@Configuration                                          # Configuration class
@Bean                                                   # Bean definition
@ConfigurationProperties                                # Config properties binding
@RefreshScope                                           # Nacos dynamic refresh
```

### MyBatis-Plus Patterns
```regex
import.*mybatis.*plus                                   # MyBatis-Plus imports
BaseMapper<                                             # BaseMapper interface
ServiceImpl<                                            # ServiceImpl base class
LambdaQueryWrapper|QueryWrapper                         # Query wrappers
\.selectOne\(|\.selectList\(|\.insert\(                # MyBatis-Plus methods
```

### Hexagonal Architecture - Orchestrators/Coordinators
```regex
class.*Orchestrator                                     # Orchestrator classes
class.*Coordinator                                      # Coordinator classes
OutboxRelayOrchestrator|PlanIngestionOrchestrator       # Papertrace orchestrators
RelayCoordinator                                        # Papertrace coordinators
```

### Domain-Driven Design Patterns
```regex
class.*Aggregate                                        # Aggregate roots
class.*Entity                                           # Domain entities
interface.*Repository                                   # Repository interfaces
class.*Event                                            # Domain events
@TransactionalEventListener                             # Event listeners
```

### Papertrace-Specific Patterns
```regex
ProvenanceOrchestrator|OutboxRelayOrchestrator          # Specific orchestrators
AbstractProvenanceScheduleJob                           # Base scheduled job
TaskCompletedEvent|SliceStatusChangedEvent              # Domain events
import.*patra\.common                                   # Patra common imports
import.*patra\.starter                                  # Patra starter imports
```

### Error Handling
```regex
try\s*\{                                                # Try blocks
catch\s*\(                                              # Catch blocks
throw new                                               # Throw statements
@ControllerAdvice                                       # Global exception handler
@ExceptionHandler                                       # Exception handler method
ProblemDetail                                           # RFC 7807 error response
```

### Logging and Observability
```regex
import.*slf4j                                           # SLF4J logging
@Slf4j                                                  # Lombok logging
log\.info|log\.warn|log\.error                         # Log statements
@Traced                                                 # SkyWalking tracing
TraceContext                                            # Trace context
```

---

## Usage Example

**Java Backend Skill Configuration:**

```json
{
  "mybatis-query-verification": {
    "type": "guardrail",
    "enforcement": "block",
    "priority": "critical",
    "promptTriggers": {
      "keywords": ["mybatis", "mapper", "query", "database"],
      "intentPatterns": [
        "(create|add|modify|update).*?(mapper|query|table)"
      ]
    },
    "fileTriggers": {
      "pathPatterns": [
        "patra-*/patra-*-infra/src/main/java/**/*Mapper.java",
        "**/mapper/**/*.xml"
      ],
      "contentPatterns": [
        "@Mapper",
        "BaseMapper<",
        "import.*mybatis.*plus"
      ]
    },
    "blockMessage": "请先验证 MyBatis 表名和字段名是否与数据库 schema 一致！使用 MCP mysql 工具查询确认。"
  },

  "hexagonal-architecture-guidelines": {
    "type": "domain",
    "enforcement": "suggest",
    "priority": "high",
    "promptTriggers": {
      "keywords": ["orchestrator", "coordinator", "hexagonal", "ddd", "aggregate"],
      "intentPatterns": [
        "(create|implement|build).*?(orchestrator|coordinator|aggregate)"
      ]
    },
    "fileTriggers": {
      "pathPatterns": [
        "patra-*/patra-*-app/src/main/java/**/*Orchestrator.java",
        "patra-*/patra-*-domain/src/main/java/**/*Aggregate.java"
      ],
      "contentPatterns": [
        "class.*Orchestrator",
        "class.*Aggregate",
        "@TransactionalEventListener"
      ]
    }
  }
}
```

---

## Pattern Testing Tips

### Test File Path Patterns
```bash
# Find all controllers
find . -path "*/patra-*-adapter/src/main/java/**/*Controller.java"

# Find all mappers
find . -name "*Mapper.java"

# Find all orchestrators
find . -name "*Orchestrator.java"
```

### Test Content Patterns (using grep)
```bash
# Find MyBatis-Plus usage
grep -r "BaseMapper<" --include="*.java"

# Find Spring @Service annotations
grep -r "@Service" --include="*.java"

# Find transaction management
grep -r "@Transactional" --include="*.java"
```

---

**Related Files:**
- [SKILL.md](SKILL.md) - Main skill guide
- [TRIGGER_TYPES.md](TRIGGER_TYPES.md) - Detailed trigger documentation
- [SKILL_RULES_REFERENCE.md](SKILL_RULES_REFERENCE.md) - Complete schema

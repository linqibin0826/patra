# 纵深防御校验

## 概述

当你修复了一个由无效数据引起的 bug 时，在一个地方加校验似乎就够了。但这个单点检查可能会被不同的代码路径、重构或 mock 绕过。

**核心原则：** 在数据经过的每一层都做校验。让这个 bug 在结构上不可能发生。

## 为什么需要多层校验

单层校验："我们修了这个 bug"
多层校验："我们让这个 bug 不可能再发生"

不同层级能捕获不同问题：
- **入口校验**（adapter 层）捕获大多数 bug，最早拦截
- **业务逻辑校验**（app 层）捕获边界情况
- **VO / Domain 校验**（domain 层）保证聚合不变式
- **运行时守卫 / 测试守卫** 防止特定上下文的危险操作
- **调试日志** 在其他层级失效时提供取证

## patra-api 的四个层级

### 第 1 层：入口校验（adapter）
**目的：** 在 HTTP / RPC 边界拒绝明显无效的输入

```java
// IngestRequest record（DTO）
public record IngestRequest(
    @NotBlank @Pattern(regexp = "^[A-Z0-9_]{1,32}$") String provenanceCode,
    @NotBlank @Size(max = 1024) String title,
    @NotNull @Past LocalDate publishedAt
) {}

// Controller 自动触发 @Valid
@PostMapping("/api/v1/publications")
public PublicationResponse ingest(@Valid @RequestBody IngestRequest req) {
    return appService.ingest(req);
}
```

Spring 在反序列化后自动校验，违规返回 `400 Bad Request` + 字段级错误，不进 app 层。

### 第 2 层：业务逻辑校验（app）
**目的：** 确保数据对当前 use case 是合理的，处理领域特有约束

```java
@Transactional
public PublicationView ingest(IngestRequest req) {
    Objects.requireNonNull(req, "request cannot be null");

    // 领域级断言：来源必须已注册
    if (!provenanceRegistry.exists(req.provenanceCode())) {
        throw new ProvenanceNotRegisteredException(req.provenanceCode());
    }

    // 用例约束：同 provenance 下不允许重复
    if (publicationRepository.existsByProvenanceAndExternalId(
            req.provenanceCode(), req.externalId())) {
        throw new DuplicatePublicationException(req.externalId());
    }

    var aggregate = mapper.toAggregate(req);
    return mapper.toView(publicationRepository.save(aggregate));
}
```

### 第 3 层：VO / Domain 校验
**目的：** 保证聚合内不变式，无论从哪条路径构造对象

```java
public record ProvenanceCode(String value) {
    private static final Pattern VALID = Pattern.compile("^[A-Z0-9_]{1,32}$");

    public static ProvenanceCode of(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "ProvenanceCode 必须匹配 ^[A-Z0-9_]{1,32}$，实际：" + value);
        }
        return new ProvenanceCode(value);
    }
}

public class Publication {
    public Publication(ProvenanceCode code, Title title, ...) {
        // 紧凑构造器一定走 VO 工厂，不变式天然保证
        this.code = Objects.requireNonNull(code);
        this.title = Objects.requireNonNull(title);
    }
}
```

VO 工厂方法是**最后一道防线**——即使前两层被绕过（如 mock、反射、jackson 直接 deserialize 到聚合），VO 校验仍然能拦截。

### 第 4 层：测试守卫 / 集成断言
**目的：** 让回归测试在 CI 上每次跑都验证防御链有效

```java
@WebMvcTest(PublicationController.class)
class PublicationControllerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void rejects_empty_provenance_code_at_adapter() throws Exception {
        mvc.perform(post("/api/v1/publications")
                .contentType(APPLICATION_JSON)
                .content("{\"provenanceCode\":\"\",\"title\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("provenanceCode"));
    }

    @Test
    void rejects_unregistered_provenance_at_app_layer() throws Exception {
        // adapter 校验通过，app 层应该拦截未注册的 provenance
        mvc.perform(post("/api/v1/publications")
                .contentType(APPLICATION_JSON)
                .content("{\"provenanceCode\":\"NOT_REGISTERED\",\"title\":\"x\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
}
```

## 应用模式

当你发现一个 bug 时：

1. **追踪数据流** —— 错误值从哪里产生的？在哪里被使用？（参见 `root-cause-tracing.md`）
2. **标注所有检查点** —— 列出数据从 adapter 到 domain 经过的每一层
3. **在每一层添加校验** —— Bean Validation / 领域断言 / VO 工厂 / 测试守卫
4. **测试每一层** —— 故意只发送绕过 adapter 校验的请求（直接调 app 层），验证 app 层能否捕获

## 实际案例

Bug：空的 `provenanceCode` 穿透到 `ProvenanceCode.of("")`，返回 `500` 而非 `400`

**数据流：**
1. HTTP request → `IngestRequest` 字段无 `@NotBlank`
2. `PublicationAppService.ingest(req)` 没校验
3. `PublicationMapper.toAggregate(dto)` 调 `ProvenanceCode.of("")`
4. VO 工厂抛 `IllegalArgumentException` → 500

**添加的四层防御：**
- 第 1 层：`IngestRequest` 字段加 `@NotBlank @Pattern(...)` → 直接 400
- 第 2 层：`PublicationAppService.ingest` 用 `Objects.requireNonNull` 兜底 null（不依赖 Spring `@Valid`）
- 第 3 层：`ProvenanceCode.of` VO 工厂保留校验（不变式守护）
- 第 4 层：集成测试 `@WebMvcTest` 覆盖空字符串 / null / 错误格式三种路径

**结果：** 同时跑 1847 个测试通过，空字符串无论从哪条路径都被拦截在最合适的层，状态码语义正确。

## 关键洞察

四个层级缺一不可。每一层都捕获了其他层遗漏的 bug：
- adapter 校验防住了大量 HTTP 入参问题
- app 层断言防住了 mock 测试绕过 adapter 的情况
- VO 工厂在 Jackson 直接反序列化到聚合或测试新建对象时仍然守护
- 集成测试守住整条防御链不退化

**不要止步于一个校验点。** 在每一层都添加检查，让 bug 在结构上不可能再发生。

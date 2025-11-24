# TDD 开发规范

## 核心原则

🔴 Red → 🟢 Green → 🔵 Refactor

✅ 先写测试，小步前进，只写必要代码
❌ 跳过测试，过度设计

---

## 层级测试策略

| 层级 | 测试方式 | Mock 策略 |
|------|----------|-----------|
| **Domain** | 纯单元测试 | 无 Mock |
| **Application** | 单元测试 | Mock 所有 Ports（@Transactional 仅此层） |
| **Infrastructure** | @MybatisPlusTest（Repository）<br>WireMock（Feign） | 真实数据库（TestContainers）<br>Mock 外部 API |
| **Adapter** | @WebMvcTest（Controller）<br>单元测试（Listener/Job） | Mock 业务层 |
| **Boot** | @SpringBootTest（E2E） | 真实中间件 |

---

## 测试金字塔

75%+ 单元测试 → 20% 切片测试 → <5% E2E 测试

---

## 注意事项

- Spring Boot 3.4+ 使用 `@MockitoBean`（`org.springframework.test.context.bean.override.mockito`）
- 测试超时配置真实值（2s），避免过长超时拖慢测试

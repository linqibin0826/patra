# 测试相关

1. Spring Boot 3.4+ 使用 `@MockitoBean` 替代已弃用的 `@MockBean`（包路径：`org.springframework.test.context.bean.override.mockito.MockitoBean`）
2. 测试 HTTP 客户端超时应配置真实超时时间（如 2 秒），避免配置过长超时（如 10 秒）导致测试变慢；Awaitility 用于异步轮询场景而非同步阻塞超时测试

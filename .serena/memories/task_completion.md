完成任务后的常规动作：
- 本地编译与单测：进入相关子模块执行 `mvn -q -DskipTests compile` 或 `mvn -q test`；必要时在 {service}-boot 做集成测试。
- 代码审查：遵循六边形架构与依赖方向；小步变更，写下关键假设与权衡；避免在 domain 引入框架依赖；敏感信息不硬编码。
- 质量检查：检查日志级别与参数化写法；确保异常处理与幂等逻辑覆盖核心路径；SQL/Flyway 脚本命名规范。
- 打包：`mvn clean package -DskipTests`（如需跑测移除 `-DskipTests`）。
- 观察性：确保关键路径打点与 trace 贯穿（SkyWalking），必要时添加 INFO/DEBUG。
- 文档：关键类/方法补充 JavaDoc（作者/版本/用途）与中文注释；更新 README/变更说明（如涉及数据链路）。
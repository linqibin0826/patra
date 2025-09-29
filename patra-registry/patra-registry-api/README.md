# patra-registry-api

Registry 服务的契约与错误码模块，向其他服务暴露稳定 DTO 与异常语义。

## 1. 模块定位
- **服务/组件作用**：封装 Registry 错误码、Feign 接口、公共 DTO，供 ingest、gateway 等服务在编译期依赖
- **主要消费者**：`patra-ingest`、`patra-gateway-boot`、内部工具/SDK
- **架构边界**：纯契约模块，不引入 Spring，禁止实现具体业务逻辑

## 2. 核心能力
- **错误码枚举 `RegistryErrorCode`**：统一 `REG-NNNN` 格式，覆盖字典/配置/通用异常
- **契约文档**：`package-info` 说明、`ERROR_CODE_CATALOG.md` 记录编号
- **可复用接口**：Feign 接口与 DTO（随业务扩展）

## 3. 分层结构与依赖
- 目录：`error/`（错误码）、`dto/`（数据结构）
- 依赖：`patra-common`、Jakarta Validation（用于 DTO 注解）
- 禁止事项：引入 Spring/框架依赖、实现业务服务

## 4. 运行与配置
- 作为普通 jar 供其他模块引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 无运行时配置；错误码前缀由使用方在核心 Starter 中设置 `patra.error.context-prefix=REG`

## 5. 观测与运维
- 模块本身无运行时行为；下游使用时应在日志/指标中引用 `RegistryErrorCode`
- 维护 `ERROR_CODE_CATALOG.md` 以便外部系统检索

## 6. 测试策略
- 保持编译期检查：在引用服务中对 `RegistryErrorCode` 枚举写单测确保覆盖常用错误
- 若新增 DTO/接口，需在集成测试中验证序列化兼容

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 错误码扩展 | 持续进行 | 需遵守 append-only 原则，新增即更新目录 |
| DTO 稳定性 | 持续进行 | 版本演进需保持向后兼容 |

## 8. 参考资料
- 错误码目录：`ERROR_CODE_CATALOG.md`
- 深度文档：`docs/modules/registry/deep-dive.md`
- 平台错误规范：`docs/standards/platform-error-handling.md`

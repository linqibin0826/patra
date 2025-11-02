# patra-storage — 对象存储元数据服务

> 专用微服务,负责记录和管理上传到外部对象存储提供商的文件元数据。

## 🎯 职责
- 接受来自内部服务(例如 patra-ingest)的上传记录请求
- 持久化文件元数据、业务上下文和生命周期属性
- 通过唯一的 `storage_key` 强制幂等性
- 为其他服务提供 Feign API,无公共 REST 接口

## 🏗 模块布局
```
patra-storage/
├─ patra-storage-api/        # Feign 契约 + DTO
├─ patra-storage-domain/     # 纯 Java 聚合、VO、端口
├─ patra-storage-app/        # 用例编排器(@Transactional)
├─ patra-storage-infra/      # MyBatis-Plus 仓储、DO + 映射器 + Flyway
├─ patra-storage-adapter/    # 实现内部端点的 REST 控制器
└─ patra-storage-boot/       # 可执行 Spring Boot 应用程序
```

遵循 `AGENTS.md` 的六边形架构规则:
- Adapter → App → Domain ← Infra 依赖流向
- 领域层仅依赖 `patra-common` + Lombok/Hutool(无 Spring)
- 文档和代码风格符合 Google Java Style

在编辑任何模块之前,请阅读此文件以理解边界和命名约定。

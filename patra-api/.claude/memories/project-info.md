# 项目信息

## 项目概览

**Patra** — 医学文献数据平台，采集文献、期刊数据源 (PubMed, EPMC 等)。使用 `patra-registry` 作为 Provenance 配置、字典、元数据的单一事实来源 (SSOT)。

**架构**: SpringBoot 微服务 + 六边形架构 + DDD + domain 事件驱动

**技术栈**: Java 25 | Spring Boot 3.5.7 + Cloud 2025.0.0 | Maven | MyBatis-Plus + MapStruct | Nacos

## 代码库结构

**仓库**: `patra-parent`, `patra-common`, `patra-*`, `patra-spring-boot-starter-*`, `docker/`

**微服务模块**:
- `patra-{service}-boot` (入口)
- `patra-{service}-api` (对其他模块的契约和接口)
- `patra-{service}-domain` (纯 Java)
- `patra-{service}-app` (编排器)
- `patra-{service}-infra` (仓储)
- `patra-{service}-adapter` (控制器/定时任务)

## 资源

**文档**: 每个 `patra-*` 模块中的模块特定 `README.md` 文件，包中存在 `package-info.java`

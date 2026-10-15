# patra-common

## 能力

跨服务共用的业务词汇（Shared Kernel），包 `dev.patra.common`，只放数据结构、不含业务行为和框架依赖：`ProvenanceCode`（上游数据来源代码，如 PubMed、Crossref）、`DataType`（系统支持的数据类型标记）、`PublicationIdentifierType`（PMID、DOI、ORCID、ROR 等标识符类型）、`CanonicalPublication`（规范化医学出版物模型，采集、目录等服务共享）。

各服务的 domain / api 层经约定插件自动依赖本模块，不用手写依赖；工具模块（`patra-tool-*`）不得依赖它。没有自动配置，也没有配置键。

## 配置

无。

## 怎么覆盖

无。

## 出哪些默认值

无。

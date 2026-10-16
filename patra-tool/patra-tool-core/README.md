# patra-tool-core

## 能力

纯 Java 模块，零 Spring 依赖，其它工具模块都依赖它。提供 DDD 基类（`AggregateRoot`、`DomainEvent`、`ChildEntityChange`）、错误类型体系（`DomainException`、`ApplicationException`、`ErrorCodeLike`、`HttpStdErrors`、`ErrorTrait`、`RemoteCallException`）、当前用户抽象（`CurrentUser`、`CurrentUserPort`、`AccountType`、`ClientType`）和顺序常量 `PatraOrders`。没有自动配置类。

## 配置（无前缀）

无（纯 Java，没有自动配置）。

## 怎么覆盖

无。

## 出哪些默认值

无。

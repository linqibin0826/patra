# 聚合根设计文档模板

## 输出目录

所有聚合根设计文档输出到：
```
/Users/linqibin/Desktop/Patra/Patra-docs/content/designs/aggregations/
```

## 文件命名

使用 kebab-case 格式：`{aggregate-name}-aggregate.md`

示例：
- `venue-aggregate.md`
- `publication-aggregate.md`
- `mesh-descriptor-aggregate.md`

## 文档模板

```markdown
---
type: aggregate-root
status: draft
tags: [ddd, domain-model, {context-tag}]
created: {YYYY-MM-DD}
---

# {聚合名称} 聚合根

## 概念定义

### 定义

{用一句话简明扼要地总结该聚合在业务中代表什么}

### 唯一标识

| 属性 | 说明 |
|------|------|
| 类型 | Long / UUID / 业务编码 |
| 生成策略 | 雪花算法 / UUID / 业务规则 |
| 格式规则 | {如有特定格式} |

### 通用语言

| 术语 | 含义 |
|------|------|
| {Term1} | {Definition1} |
| {Term2} | {Definition2} |

## 结构模型

\`\`\`d2
# 聚合结构图

{AggregateRoot}: {
  shape: class
  label: "{聚合根名称} (聚合根)"

  # 核心属性
  +id: Long
  +{属性1}: {类型}
  +{属性2}: {类型}

  # 工厂方法
  +create(): {AggregateRoot}
  +fromXxx(): {AggregateRoot}
  +restore(): {AggregateRoot}

  # 领域行为
  +{行为1}(): void
  +{行为2}(): void
}

# 聚合内实体
{Entity1}: {
  shape: class
  label: "{实体1} (实体)"

  +id: Long
  +{属性}: {类型}
}

# 值对象
{ValueObject1}: {
  shape: class
  label: "{值对象1} (值对象)"
  style.fill: "#e1f5fe"

  +{属性}: {类型}
}

# 关系
{AggregateRoot} -> {Entity1}: 1:N
{AggregateRoot} -> {ValueObject1}: 1:1
{Entity1} -> {ValueObject2}: 1:1
\`\`\`

## 生命周期与状态

\`\`\`mermaid
stateDiagram-v2
    [*] --> Created: create()

    Created --> Active: activate()
    Active --> Suspended: suspend()
    Suspended --> Active: resume()

    Active --> Archived: archive()
    Suspended --> Archived: archive()

    Archived --> [*]

    note right of Created
        初始状态
        需要验证必填字段
    end note

    note right of Archived
        终态
        不可恢复
    end note
\`\`\`

## 不变性规则

聚合在任何时候都必须满足以下规则：

- **[规则1名称]**: 约束条件描述
- **[规则2名称]**: 约束条件描述
- **[规则3名称]**: 约束条件描述

**详细说明**：

| 规则 | 说明 | 违反后果 |
|------|------|----------|
| [规则1名称] | 详细解释为什么需要这个规则 | 违反时的后果 |
| [规则2名称] | 详细解释 | 违反时的后果 |

## 行为能力

聚合根公开的命令方法（禁止暴露 Setter）：

| 命令 (方法名) | 输入参数 | 发布的领域事件 |
|--------------|----------|---------------|
| create | RequiredParams | {Aggregate}CreatedEvent |
| {command1} | {Param1}, {Param2} | {Event1} |
| {command2} | {ParamVO} | {Event2} |
| archive | reason: String | {Aggregate}ArchivedEvent |

### 方法签名

```java
// 工厂方法
public static {AggregateRoot} create({RequiredParams} params);
public static {AggregateRoot} fromXxx({XxxParams} params);

// 业务命令
public void {command1}({Param1} p1, {Param2} p2);
public void {command2}({ParamVO} vo);
```

## 外部关系

该聚合与其他聚合的关系（只能通过 ID 引用）：

| 关联聚合 | 关系类型 | 引用方式 | 说明 |
|----------|----------|----------|------|
| [[{Related1}Aggregate]] | 1:N | {related1}Id: Long | {关系说明} |
| [[{Related2}Aggregate]] | N:M | 关联表 | {关系说明} |

## 设计决策

### 为什么选择这个聚合边界？

{解释聚合边界的设计理由}

### 为什么 {Entity} 是聚合内实体而非独立聚合？

{解释设计决策}

## 实现指南

### 代码位置

- 聚合根: `patra-{service}-domain/.../model/aggregate/{AggregateRoot}.java`
- 实体: `patra-{service}-domain/.../model/entity/{Entity}.java`
- 值对象: `patra-{service}-domain/.../model/vo/{ValueObject}.java`
- 仓储: `patra-{service}-domain/.../port/repository/{Repository}.java`

### 待办事项

- [ ] 创建聚合根类
- [ ] 创建实体类
- [ ] 创建值对象类
- [ ] 定义 Repository 接口
- [ ] 编写单元测试
```

## 示例：VenueAggregate

参考 Patra 项目现有的 `VenueAggregate` 实现：

**文件**: `patra-catalog-domain/.../model/aggregate/VenueAggregate.java`

**聚合边界**:
- VenueIdentifier (实体, 1:N) - 标识符集合
- VenuePublicationStats (实体, 1:N) - 年度指标
- VenueMesh (实体, 1:N) - MeSH 主题词
- VenueRelation (实体, 1:N) - 期刊关联关系

**关键设计**:
1. 使用工厂方法区分数据源 (`fromOpenAlex`, `fromPubMed`)
2. 链式设置可选属性 (`withXxx`)
3. 不可变视图暴露集合
4. `assertInvariants()` 验证业务规则

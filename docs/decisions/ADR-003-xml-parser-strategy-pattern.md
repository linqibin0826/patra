---
type: adr
adr_id: 3
date: 2025-11-27
status: accepted
date_decided: 2025-11-27
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/design-pattern
  - tech/refactoring
---

# ADR-003: XML 解析器策略模式重构

## 状态

**accepted**

## 背景

XmlParserAdapter 原有 ~1800 行代码，包含 5 种 MeSH 记录类型的解析逻辑，难以维护和扩展。未来需支持更多 XML 类型（supp 文件、出版物 XML），硬编码方式不可持续。

## 决策

我们将采用策略模式重构，将各记录类型的解析逻辑分离为独立策略类，XmlParserAdapter 退化为门面类，负责策略选择和调度。

## 后果

### 正面影响

- **单一职责**：每个策略只负责一种记录类型，易于理解和测试
- **开闭原则**：新增 XML 类型只需添加策略，无需修改门面
- **可复用性**：EntryTermParsingStrategy 可被 DescriptorParsingStrategy 内部复用
- **可测试性**：策略可独立单元测试，无需启动完整解析流程
- **代码精简**：XmlParserAdapter 从 1800 行精简到 156 行（-92%）
- **测试覆盖**：新增 77 个策略单元测试，覆盖所有字段解析

### 负面影响

- **类文件增加**：新增 10+ 个策略类文件，需要理解策略模式结构
- **学习成本**：新成员需要理解策略模式和类间关系

### 风险

- 策略选择逻辑需要保持清晰，避免过度复杂化

## 替代方案

### 方案 A：继续使用单一类

保持所有解析逻辑在 XmlParserAdapter 中，使用方法拆分管理复杂度。

**优点**：
- 无需学习设计模式
- 调试时调用链简单

**缺点**：
- 单文件过大，难以导航和维护
- 新增类型需修改核心类，违反开闭原则
- 测试需要启动完整流程

### 方案 B：模板方法模式

使用抽象类定义解析骨架，子类实现具体步骤。

**优点**：
- 强制统一解析流程
- 代码复用通过继承实现

**缺点**：
- 继承耦合度高，修改抽象类影响所有子类
- Java 单继承限制灵活性
- 策略间组合困难

## 参考资料

- [Strategy Pattern - Refactoring Guru](https://refactoring.guru/design-patterns/strategy)
- [Effective Java - Item 20: Prefer interfaces to abstract classes](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)

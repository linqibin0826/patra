# 事件风暴指南

本文档基于 Alberto Brandolini 的《Event Storming》总结事件风暴方法，用于领域发现和聚合识别。

## 什么是事件风暴

事件风暴是一种协作式建模方法，通过识别领域事件来发现业务流程和领域边界。

### 核心理念

1. **事件优先**：从"发生了什么"开始，而非"系统要做什么"
2. **协作发现**：领域专家和技术人员共同参与
3. **可视化**：用便利贴在大墙上构建模型

## 便利贴颜色规范

| 颜色 | 元素 | 说明 |
|------|------|------|
| 🟧 橙色 | 领域事件 (Domain Event) | 已发生的事实，过去时命名 |
| 🟦 蓝色 | 命令 (Command) | 触发事件的动作，祈使句命名 |
| 🟨 黄色 | 聚合 (Aggregate) | 接收命令、产生事件的业务对象 |
| 🟪 紫色 | 策略 (Policy) | 事件触发的后续动作，"当...则..." |
| 🟩 绿色 | 读模型 (Read Model) | 用户做决策所需的信息 |
| 🩷 粉色 | 外部系统 (External System) | 外部集成点 |
| 🔴 红色 | 热点 (Hot Spot) | 问题、疑问、待讨论 |
| 👤 小人 | 角色 (Actor) | 触发命令的用户或系统 |

## 事件风暴流程

### 阶段 1：事件风暴（Big Picture）

**目标**：发现所有领域事件，理解业务全貌

**步骤**：
1. 每人写出想到的领域事件（橙色便利贴）
2. 按时间顺序排列在墙上
3. 识别重复、冲突、遗漏
4. 标记热点问题（红色便利贴）

**输出**：时间线上的事件流

### 阶段 2：命令与聚合

**目标**：识别触发事件的命令和承载命令的聚合

**步骤**：
1. 为每个事件添加触发它的命令（蓝色便利贴）
2. 识别接收命令的聚合（黄色便利贴）
3. 添加触发命令的角色（小人图标）

**输出**：命令 → 聚合 → 事件 的链条

### 阶段 3：策略与读模型

**目标**：发现事件驱动的后续动作和信息需求

**步骤**：
1. 识别事件触发的策略（紫色便利贴）
2. 添加用户决策所需的读模型（绿色便利贴）
3. 标记外部系统集成点（粉色便利贴）

**输出**：完整的业务流程图

### 阶段 4：边界识别

**目标**：划分限界上下文和聚合边界

**步骤**：
1. 用粗线圈出相关联的聚合群
2. 识别上下文边界
3. 分析上下文间的关系

**输出**：限界上下文地图

## 从事件风暴到代码

### 事件 → 领域事件类

```
🟧 文献已导入 (PublicationImported)
         ↓
public record PublicationImportedEvent(
    Long publicationId,
    String doi,
    String title,
    Instant occurredAt
) implements DomainEvent {}
```

### 命令 → Command 类

```
🟦 导入文献 (Import Publication)
         ↓
public record ImportPublicationCommand(
    String doi,
    String title,
    List<AuthorInfo> authors,
    Long venueId
) {}
```

### 聚合 → AggregateRoot 类

```
🟨 文献 (Publication)
         ↓
public class PublicationAggregate extends AggregateRoot<Long> {
    public static PublicationAggregate create(ImportPublicationCommand cmd) {
        // 创建逻辑
        aggregate.registerEvent(new PublicationImportedEvent(...));
        return aggregate;
    }
}
```

### 策略 → EventListener

```
🟪 当文献导入后，更新期刊统计
         ↓
@EventListener
public void onPublicationImported(PublicationImportedEvent event) {
    Venue venue = venueRepository.findById(event.venueId());
    venue.incrementPublicationCount();
    venueRepository.save(venue);
}
```

## 医学领域事件风暴示例

### 期刊导入流程

```
👤 系统管理员
      │
      │ 🟦 创建采集计划
      ↓
🟨 采集计划 (Plan)
      │
      │ 🟧 采集计划已创建 (PlanCreated)
      ↓
🟪 当计划创建后，生成任务
      │
      │ 🟦 生成采集任务
      ↓
🟨 采集任务 (Task)
      │
      │ 🟧 任务已生成 (TaskGenerated)
      ↓
🟪 当任务生成后，调度执行
      │
      ↓
🩷 外部系统: OpenAlex API
      │
      │ 🟦 导入期刊数据
      ↓
🟨 期刊 (Venue)
      │
      │ 🟧 期刊已导入 (VenueImported)
      ↓
🟪 当期刊导入后，检查重复
```

### 聚合边界识别

从上面的事件流可以识别出：

| 聚合 | 核心事件 | 边界依据 |
|------|---------|---------|
| Plan | PlanCreated, PlanCompleted | 独立生命周期 |
| Task | TaskGenerated, TaskExecuted | 独立生命周期 |
| Venue | VenueImported, VenueMerged | 强一致性需求 |

## 事件风暴检查清单

### 事件命名

- [ ] 使用过去时（~ed, ~d）
- [ ] 包含业务含义
- [ ] 避免技术术语

### 聚合识别

- [ ] 每个聚合有明确的不变量
- [ ] 聚合边界足够小
- [ ] 聚合间通过事件通信

### 策略识别

- [ ] 策略表述为"当...则..."
- [ ] 区分同步策略和异步策略
- [ ] 考虑策略失败的处理

## 远程事件风暴工具

| 工具 | 特点 |
|------|------|
| Miro | 功能全面，协作流畅 |
| FigJam | 与 Figma 集成 |
| Excalidraw | 开源免费 |
| draw.io | 本地部署 |

## 常见问题

### Q: 事件和命令的区别？

**A**:
- **命令**：表达意图，可能失败
- **事件**：已发生的事实，不可撤销

### Q: 如何处理条件分支？

**A**: 使用策略（紫色便利贴）表示条件逻辑：
```
🟧 订单已创建
      ↓
🟪 当订单金额 > 1000，则需要审批
🟪 当订单金额 ≤ 1000，则自动确认
```

### Q: 事件粒度如何把握？

**A**:
- **太粗**：`OrderProcessed` → 信息丢失
- **太细**：`OrderField1Updated` → 噪音太多
- **适中**：`OrderPlaced`, `OrderPaid`, `OrderShipped`

### Q: 何时需要事件风暴？

**A**:
- ✅ 新项目启动
- ✅ 重构遗留系统
- ✅ 梳理复杂业务流程
- ❌ 简单 CRUD 系统
- ❌ 已有清晰领域模型

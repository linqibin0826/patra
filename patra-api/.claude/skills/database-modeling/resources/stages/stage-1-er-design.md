# 阶段 1：ER 图设计

## 🎨 Mermaid ER 图设计指南

### 快速语法参考

#### 关系类型
```mermaid
erDiagram
    %% 一对一关系
    User ||--|| Profile : has

    %% 一对多关系
    Department ||--o{ Employee : contains

    %% 多对一关系
    Order }o--|| Customer : placed_by

    %% 多对多关系
    Student }o--o{ Course : enrolls
```

#### 关系符号说明
| 符号 | 含义 | 示例场景 |
|------|------|---------|
| `\|\|--\|\|` | 一对一 | 用户-用户详情 |
| `\|\|--o{` | 一对多 | 部门-员工 |
| `}o--\|\|` | 多对一 | 订单-客户 |
| `}o--o{` | 多对多 | 学生-课程 |

---

## 📐 设计模板

### 基础 ER 图模板

```mermaid
erDiagram
    %% ========================================
    %% 核心业务实体
    %% ========================================

    ENTITY_A {
        bigint id PK "主键"
        string code UK "唯一业务编码"
        string name "名称"
        string status "状态"
        timestamp created_at "创建时间"
    }

    ENTITY_B {
        bigint id PK "主键"
        bigint entity_a_id FK "关联实体A"
        string type "类型"
        json config "配置信息"
        boolean deleted "软删除"
    }

    %% ========================================
    %% 实体关系定义
    %% ========================================

    ENTITY_A ||--o{ ENTITY_B : "拥有"
```

---

## 🏗️ 设计模式库

### 模式 1：主从关系（Master-Detail）

```mermaid
erDiagram
    ORDER ||--o{ ORDER_ITEM : contains

    ORDER {
        bigint id PK
        string order_no UK "订单号"
        decimal total_amount "总金额"
        string status "订单状态"
        timestamp created_at
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id FK "订单ID"
        bigint product_id "产品ID"
        int quantity "数量"
        decimal unit_price "单价"
    }
```

### 模式 2：多对多关联（通过中间表）

```mermaid
erDiagram
    USER }o--o{ ROLE : has
    USER ||--o{ USER_ROLE : has
    ROLE ||--o{ USER_ROLE : has

    USER {
        bigint id PK
        string username UK
        string email UK
    }

    ROLE {
        bigint id PK
        string role_code UK
        string role_name
    }

    USER_ROLE {
        bigint id PK
        bigint user_id FK
        bigint role_id FK
        timestamp assigned_at
    }
```

### 模式 3：树形结构（自关联）

```mermaid
erDiagram
    CATEGORY {
        bigint id PK
        bigint parent_id FK "父分类ID"
        string category_code UK
        string category_name
        int level "层级"
        string path "路径"
    }

    CATEGORY ||--o{ CATEGORY : has_children
```

### 模式 4：状态历史追踪

```mermaid
erDiagram
    DOCUMENT ||--o{ DOCUMENT_STATUS_HISTORY : tracks

    DOCUMENT {
        bigint id PK
        string doc_no UK
        string current_status
        timestamp updated_at
    }

    DOCUMENT_STATUS_HISTORY {
        bigint id PK
        bigint document_id FK
        string from_status
        string to_status
        string reason
        timestamp changed_at
        bigint changed_by
    }
```

---

## 🎯 设计原则

### 1. 命名规范
- **表名**：使用单数形式，小写，下划线分隔
- **字段名**：小写，下划线分隔，见名知意
- **外键字段**：`关联表名_id` 格式

### 2. 主键设计
- 统一使用 `BIGINT UNSIGNED` 类型
- 命名为 `id`
- 使用 `AUTO_INCREMENT`

### 3. 必备字段
```mermaid
erDiagram
    STANDARD_TABLE {
        bigint id PK "主键"
        %% 业务字段
        string business_field "业务字段"

        %% 审计字段（必须）
        timestamp created_at "创建时间"
        bigint created_by "创建人"
        timestamp updated_at "更新时间"
        bigint updated_by "更新人"
        bigint version "乐观锁"
        boolean deleted "软删除"
    }
```

### 4. 关系设计检查
- [ ] 是否使用了外键（不允许）
- [ ] 关联字段是否建立索引？
- [ ] 是否需要冗余字段优化查询？
- [ ] 多对多关系是否需要中间表额外属性？

---

## 🔍 ER 图验证清单

设计完成后，检查以下项目：

### 完整性检查
- [ ] 所有业务实体都已包含
- [ ] 实体间关系都已定义
- [ ] 主键和唯一键都已标识

### 规范性检查
- [ ] 命名符合规范
- [ ] 包含必要的审计字段
- [ ] 数据类型选择合理

### 性能考虑
- [ ] 高频查询字段已识别
- [ ] 需要索引的字段已标记
- [ ] 是否需要反规范化设计

---

## 实际案例参考

需要更多示例，请参考：
- [Mermaid ER 复杂示例](../guides/mermaid-er-examples.md)
- [Patra 项目完整案例](../examples/patra-complete-example.md)

---

## 下一步

ER 图设计完成后，进入 **[阶段 2：详细表设计](stage-2-table-details.md)**

# patra-ingest ProvenanceCode 枚举迁移 - 文档索引

## 📚 文档清单

### 1. 📄 快速索引 (当前文件)
   - 概述和导航

### 2. 📊 01_SCAN_REPORT.md
   - **内容**: 完整的扫描报告，按层级分类
   - **用途**: 了解整个 patra-ingest 项目的 provenanceCode String 使用情况
   - **包含**:
     - Domain 层分析（Port 接口、模型、异常）
     - App 层分析（UseCase、Service、Publisher）
     - Infrastructure 层分析（Repository、Entity、Adapter）
     - Adapter 层分析
   - **大小**: 详细完整

### 3. 📋 02_DETAILED_LINE_NUMBERS.txt
   - **内容**: 精确的文件路径和行号清单
   - **用途**: 查找具体的代码位置
   - **格式**: 
     ```
     ### 文件名
     文件: 完整路径
     - Line X: 具体内容
     ```
   - **适合**: 快速定位代码

### 4. 🎯 03_COMPREHENSIVE_REFACTORING_PLAN.md
   - **内容**: 详细的改写计划和执行清单
   - **用途**: 指导逐步改写工作
   - **包含**:
     - 优先级 1-5 分级清单
     - 详细改写说明和代码示例
     - 时间估算和阶段划分
     - 注意事项和检查清单
   - **大小**: 最详细，包含实施指导

---

## 🚀 快速开始

### 如果你想...

#### 了解全局情况
→ 阅读 **01_SCAN_REPORT.md**
- 知道有多少文件受影响
- 理解各层的改写影响
- 获得优先级建议

#### 快速找到代码位置
→ 查阅 **02_DETAILED_LINE_NUMBERS.txt**
- 直接跳转到文件和行号
- 使用 IDE 的 Goto Line 功能快速定位

#### 执行改写工作
→ 按照 **03_COMPREHENSIVE_REFACTORING_PLAN.md**
- 按优先级分阶段执行
- 每个文件有改写前后对比
- 包含时间估算和清单

---

## 📊 扫描统计概览

- **扫描范围**: patra-ingest/src/main（所有源代码）
- **文件总数**: 73 个
- **受影响文件**: 63 个（86%）
- **String provenanceCode 出现次数**: 80+ 处
- **需要改写的文件**: 20+ 个（去掉 DO Entity 和 Mapper）

### 分类统计
| 类别 | 文件数 | 处数 | 优先级 |
|------|--------|------|--------|
| Port 接口 | 5 | 7 | 🔴 P1 |
| Repository 实现 | 2 | 3 | 🟠 P2 |
| Registry/Adapter | 6 | 13 | 🟠 P2 |
| UseCase/Service | 12+ | 20+ | 🟡 P3 |
| Domain 模型 | 3 | 5 | 🟡 P3 |
| DO Entity | 8 | 8 | 🟢 P4(可选) |
| Mapper | 1 | 2 | 🟢 P4(可选) |

---

## 🎯 改写优先级简表

### 🔴 优先级 1: 必须改写 (2-3 小时)
1. CursorRepository.java
2. TaskRepository.java
3. DataSourcePort.java
4. StoragePort.java
5. LiteratureStoragePort.java

### 🟠 优先级 2: 应该改写 (4-5 小时)
1. CursorRepositoryMpImpl.java
2. TaskRepositoryMpImpl.java
3. ProviderRegistry.java
4. DataSourceAdapter.java

### 🟡 优先级 3: 可选改写 (6-8 小时)
1. BatchPlannerRegistry.java & BatchPlanner
2. ExecuteTaskBatchesUseCaseImpl.java
3. ExecutionContextLoaderImpl.java
4. 其他 UseCase (Plan, Coordination)
5. IngestConfigurationException.java
6. ExprCompilationRequest.java

### 🟢 优先级 4: 保持不变 (0 小时)
1. DO Entity 字段（8 个文件）
2. Mapper 参数（1 个文件）

**理由**: 直接映射数据库，保持 String 类型

---

## 📈 改写路线图

```
阶段 1: Port 接口改写 (2-3h)
    ↓
阶段 2: Repository 实现改写 (4-5h)
    ↓
阶段 3: Registry/Adapter 改写 (需包含在 P2 中)
    ↓
阶段 4: UseCase 层改写 (6-8h)
    ↓
阶段 5: 测试和修复 (2-3h)
    ↓
✅ 完成 (总计 15-21 小时)
```

---

## 📌 关键改写点

### 1. 端口层改写原则
- 接口签名改为 `ProvenanceCode provenanceCode`
- 实现层接收参数时无需改动
- 调用方需要改写以传入 ProvenanceCode

### 2. 实现层改写原则
- 参数改为 `ProvenanceCode provenanceCode`
- 与 MyBatis 交互时调用 `.getCode()` 转换为字符串
- 与日志交互时调用 `.getCode()` 转换为字符串

### 3. 数据库持久化层原则
- DO Entity 保持 String（数据库字段对应）
- Mapper 参数保持 String（SQL 参数）
- 在 Converter 层进行 String ↔ ProvenanceCode 转换

### 4. Serialization 原则
- HTTP Header：保持 String
- JSON Payload：保持 String（便于序列化）
- 内部通信：可改为 ProvenanceCode

---

## 🔧 工具和工具函数

### 推荐创建的转换工具

```java
public class ProvenanceCodeConverter {
    public static ProvenanceCode fromString(String code);
    public static String toString(ProvenanceCode provenanceCode);
}
```

### 使用建议
- DO → Domain: 使用 `ProvenanceCode.of(doEntity.getProvenanceCode())`
- Domain → DO: 使用 `provenanceCode.getCode()`
- Domain → String: 使用 `provenanceCode.getCode()`

---

## ✅ 改写检查清单

- [ ] 确认 ProvenanceCode 枚举定义完整
- [ ] 创建或确认转换工具函数
- [ ] 阶段 1: Port 接口改写 + 编译测试
- [ ] 阶段 2: Repository 实现改写 + 编译测试
- [ ] 阶段 3: Registry/Adapter 改写 + 编译测试
- [ ] 阶段 4: UseCase 改写 + 编译测试
- [ ] 单元测试运行通过
- [ ] 集成测试运行通过
- [ ] 代码审查完成

---

## 💡 建议和注意事项

1. **逐阶段改写**: 不要一次改太多文件，容易出错
2. **及时编译**: 每个阶段后立即编译，快速发现问题
3. **保持 Null 安全**: 处理 provenanceCode 为 null 的情况
4. **测试优先**: 先改单元测试，然后改实现代码
5. **充分文档**: 保留这些文档供后续参考和维护

---

## 📞 文件导航

```
REFACTORING_DOCS/
├── 00_INDEX.md (当前文件)
├── 01_SCAN_REPORT.md (扫描报告)
├── 02_DETAILED_LINE_NUMBERS.txt (行号清单)
└── 03_COMPREHENSIVE_REFACTORING_PLAN.md (详细计划)
```

---

**生成时间**: 2025-11-14
**扫描工具**: Claude Code File Search Specialist
**项目**: patra-ingest

---

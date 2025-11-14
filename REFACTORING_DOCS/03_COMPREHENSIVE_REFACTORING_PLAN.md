# patra-ingest 项目 provenanceCode String 类型改写清单

## 📊 完整扫描总结

### 统计数据
- **扫描的文件总数**: 73 个
- **包含 provenanceCode 的文件**: 63 个
- **String provenanceCode 的出现次数**: 80+ 处
- **需要改写的文件**: 30+ 个

---

## 🚨 优先级 1: 必须改写的 Port 接口 (5 个文件，7 处)

| 文件 | 行号 | 当前签名 | 改写建议 |
|------|------|--------|--------|
| CursorRepository.java | 39 | `find(String provenanceCode, ...)` | `find(ProvenanceCode provenanceCode, ...)` |
| CursorRepository.java | 64 | `findLatestGlobalTimeWatermark(String provenanceCode, ...)` | `findLatestGlobalTimeWatermark(ProvenanceCode provenanceCode, ...)` |
| TaskRepository.java | 87 | `countQueuedTasks(String provenanceCode, ...)` | `countQueuedTasks(ProvenanceCode provenanceCode, ...)` |
| DataSourcePort.java | 183 | `supports(String provenanceCode, ...)` | `supports(ProvenanceCode provenanceCode, ...)` |
| DataSourcePort.java | 223 | `getSupportedTypes(String provenanceCode)` | `getSupportedTypes(ProvenanceCode provenanceCode)` |
| StoragePort.java | 45 | `generateObjectPath(String provenanceCode, ...)` | `generateObjectPath(ProvenanceCode provenanceCode, ...)` |
| LiteratureStoragePort.java | 64 | `record StorageContext(..., String provenanceCode)` | `record StorageContext(..., ProvenanceCode provenanceCode)` |

---

## 🟠 优先级 2: 实现层改写 (8 个文件，20+ 处)

### A. Repository 实现 (2 个文件)

#### 1. CursorRepositoryMpImpl.java
| 行号 | 改写前 | 改写后 | 备注 |
|-----|--------|--------|------|
| 89 | `String provenanceCode` | `ProvenanceCode provenanceCode` | 参数，需要 `.getCode()` |
| 125 | `String provenanceCode` | `ProvenanceCode provenanceCode` | 参数，需要 `.getCode()` |

需要变更查询代码:
```java
// Line 97 需要改成:
.eq("provenance_code", provenanceCode.getCode())

// Line 127 需要改成:
wrapper.eq("provenance_code", provenanceCode.getCode());
```

#### 2. TaskRepositoryMpImpl.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 176 | `String provenanceCode` | `ProvenanceCode provenanceCode` |

---

### B. Registry & Adapter (6 个文件)

#### 3. ProviderRegistry.java
| 行号 | 改写前 | 改写后 | 说明 |
|-----|--------|--------|------|
| 110 | `String provenanceCode` (局部变量) | `ProvenanceCode provenanceCode` | 从 provider 接收后转换 |
| 155 | `getProvider(String provenanceCode, ...)` | `getProvider(ProvenanceCode provenanceCode, ...)` | 方法参数 |
| 176 | `findProvider(String provenanceCode, ...)` | `findProvider(ProvenanceCode provenanceCode, ...)` | 方法参数 |
| 189 | `supports(String provenanceCode, ...)` | `supports(ProvenanceCode provenanceCode, ...)` | 方法参数 |
| 201 | `getSupportedTypes(String provenanceCode)` | `getSupportedTypes(ProvenanceCode provenanceCode)` | 方法参数 |
| 233 | `normalizeProvenanceCode(String provenanceCode)` | `normalizeProvenanceCode(String provenanceCode)` | 保持 String（内部转换） |
| 255 | `record ProviderKey(String provenanceCode, ...)` | `record ProviderKey(ProvenanceCode provenanceCode, ...)` | Record 字段 |

**关键改写点**:
- Line 114: `typesByProvenance.containsKey(provenanceCode)` - 改成 `provenanceCode.getCode()`
- Line 122 等所有查询地方: 使用 `provenanceCode.getCode()`
- ProviderKey record: 需要在 map 的 key 中处理

#### 4. DataSourceAdapter.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 383 | `supports(String provenanceCode, ...)` | `supports(ProvenanceCode provenanceCode, ...)` |
| 394 | `getSupportedTypes(String provenanceCode)` | `getSupportedTypes(ProvenanceCode provenanceCode)` |

#### 5. LiteratureStorageAdapter.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 182 | `safeProvenance(String provenanceCode)` | `safeProvenance(ProvenanceCode provenanceCode)` 或保持 String |

#### 6. PatraRegistryAdapter.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 138 | `createMinimalSnapshot(String provenanceCode)` | `createMinimalSnapshot(ProvenanceCode provenanceCode)` 或保持 String |

---

## 🟡 优先级 3: UseCase & Service 层改写 (12+ 个文件)

### C. Execution UseCase (4 个文件)

#### 7. CursorAdvancerImpl.java
| 行号 | 改写前 | 改写后 | 说明 |
|-----|--------|--------|------|
| 70 | `ProvenanceCode provenanceCode = context.provenanceCode();` | ✅ 已正确使用 | 无需改写 |
| 72 | `String provenanceCodeStr = provenanceCode != null ? provenanceCode.getCode() : null` | ✅ 已正确转换 | 无需改写 |
| 353 | 方法参数（待读取确认） | 需根据实际情况判断 | - |

**无需改写** ✅ 此文件已正确使用 ProvenanceCode 枚举！

#### 8. ExecuteTaskBatchesUseCaseImpl.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 93 | `String provenanceCode = ...` | 根据赋值来源判断是否改写 |

#### 9. ExecutionContextLoaderImpl.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 138 | `String provenanceCodeStr = ...` | 根据赋值来源判断是否改写 |

#### 10. CompleteTaskExecutionUseCaseImpl.java
- 包含 provenanceCode（需详细检查）

---

### D. Strategy & Planner (3 个文件)

#### 11. BatchPlannerRegistry.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 56 | `get(String provenanceCode)` | `get(ProvenanceCode provenanceCode)` |
| 66 | `contains(String provenanceCode)` | `contains(ProvenanceCode provenanceCode)` |

#### 12. BatchPlanner.java (接口)
- 需要改写方法签名以匹配 registry 的调用

#### 13. UnifiedBatchPlanner.java
- 需要改写方法实现以使用 ProvenanceCode

---

### E. Coordination & Publisher (4 个文件)

#### 14. LiteraturePublisherOrchestrator.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 241 | `safeProvenance(String provenanceCode)` | `safeProvenance(ProvenanceCode provenanceCode)` 或保持 |
| 268 | `record PublishContext(..., String provenanceCode)` | `record PublishContext(..., ProvenanceCode provenanceCode)` 或保持 |

**保持理由**: Record 被序列化用于消息队列，String 更便于序列化。可选改写。

#### 15. LiteratureEventPublisher.java
| 行号 | 改写前 | 改写后 | 说明 |
|-----|--------|--------|------|
| 65 | `String provenanceCode = pc != null ? pc.getCode() : null` | ✅ 已正确转换 | 无需改写 |
| 82 | `String provenanceCode = pc != null ? pc.getCode() : null` | ✅ 已正确转换 | 无需改写 |
| 90 | `String provenanceCode = pc != null ? pc.getCode() : null` | ✅ 已正确转换 | 无需改写 |

**无需改写** ✅ 此文件已正确转换！

#### 16. LiteratureReadyHeaders.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 15 | `String provenanceCode` (record 字段) | 保持 String（用于 HTTP Header） |

**保持理由**: HTTP Header 需要字符串，建议保持。

#### 17. LiteratureReadyPayload.java
| 行号 | 改写前 | 改写后 |
|-----|--------|--------|
| 22 | `String provenanceCode` (record 字段) | 保持 String（用于序列化） |

**保持理由**: 消息 payload，保持 String 便于 JSON 序列化。

---

### F. Plan UseCase (5+ 个文件)

#### 18. PlanIngestionOrchestrator.java
- 需要详细检查

#### 19. PlanPersistenceCoordinator.java
- 需要详细检查

#### 20. PlannerValidatorImpl.java
- 需要详细检查

#### 21. PlanAssemblerImpl.java
- 需要详细检查

#### 22. PlanExpressionBuilder.java
- 需要详细检查

#### 23-25. TimeSlicePlanner, DateSlicePlanner, SingleSlicePlanner
- 需要详细检查

#### 26. TaskOutboxPublisher.java
- 需要详细检查

---

## 🟢 优先级 4: 可选改写 (数据库持久化层)

### G. DO Entity 字段 (8 个文件 - 建议保持 String)

| 文件 | 行号 | 字段 | 建议 |
|------|------|------|------|
| CursorDO.java | 35 | `private String provenanceCode` | ✅ 保持 String |
| CursorEventDO.java | 35 | `private String provenanceCode` | ✅ 保持 String |
| TaskDO.java | 47 | `private String provenanceCode` | ✅ 保持 String |
| TaskRunDO.java | 43 | `private String provenanceCode` | ✅ 保持 String |
| TaskRunBatchDO.java | 55 | `private String provenanceCode` | ✅ 保持 String |
| PlanDO.java | 45 | `private String provenanceCode` | ✅ 保持 String |
| PlanSliceDO.java | 37 | `private String provenanceCode` | ✅ 保持 String |
| ScheduleInstanceDO.java | 61 | `private String provenanceCode` | ✅ 保持 String |

**理由**:
- DO Entity 是数据库持久化对象，直接映射到数据库字符串字段
- MyBatis 列映射无需改变
- 建议在 DO → Domain 转换时使用 `ProvenanceCode.of(doEntity.getProvenanceCode())`

### H. Mapper 参数 (1 个文件)

#### 27. PlanMapper.java
| 行号 | 改写前 | 改写后 | 说明 |
|-----|--------|--------|------|
| 24 | `@Param("provenanceCode") String provenanceCode` | 保持 String | SQL 查询参数 |
| 33 | `@Param("provenanceCode") String provenanceCode` | 保持 String | SQL 查询参数 |

---

## 🏢 优先级 5: Domain 模型层改写 (可选)

### I. Domain 异常类 (1 个文件)

#### 28. IngestConfigurationException.java
| 行号 | 改写前 | 改写后 | 说明 |
|-----|--------|--------|------|
| 37 | `private final String provenanceCode` | 可改为 `ProvenanceCode` | 改写 |
| 51 | 构造函数参数 | 改为 `ProvenanceCode` | 改写 |
| 68 | 构造函数参数 | 改为 `ProvenanceCode` | 改写 |

**改写示例**:
```java
public IngestConfigurationException(
    ProvenanceCode provenanceCode,  // 改写
    String operationCode,
    String message) {
    this.provenanceCode = provenanceCode;
    // ...
}
```

---

## 📝 完整改写清单 (按改写顺序)

### 阶段 1: 端口层改写 (1 天)
- [ ] CursorRepository.java (2 处)
- [ ] TaskRepository.java (1 处)
- [ ] DataSourcePort.java (2 处)
- [ ] StoragePort.java (1 处)
- [ ] LiteratureStoragePort.java (1 处)
- [ ] 预期时间: 2-3 小时

### 阶段 2: 实现层改写 (1.5 天)
- [ ] CursorRepositoryMpImpl.java (2 处)
- [ ] TaskRepositoryMpImpl.java (1 处)
- [ ] ProviderRegistry.java (7 处)
- [ ] DataSourceAdapter.java (2 处)
- [ ] 需要处理: String ↔ ProvenanceCode 转换
- [ ] 预期时间: 4-5 小时

### 阶段 3: UseCase 层改写 (2 天)
- [ ] BatchPlannerRegistry.java (2 处)
- [ ] BatchPlanner.java (接口)
- [ ] UnifiedBatchPlanner.java (实现)
- [ ] ExecuteTaskBatchesUseCaseImpl.java
- [ ] ExecutionContextLoaderImpl.java
- [ ] 其他 UseCase (需详细检查)
- [ ] 预期时间: 6-8 小时

### 阶段 4: 模型层改写 (可选, 0.5 天)
- [ ] IngestConfigurationException.java (3 处)
- [ ] ExprCompilationRequest.java (2 处)
- [ ] 其他 Domain 模型 (需详细检查)
- [ ] 预期时间: 1-2 小时

### 阶段 5: 基础设施层 (可选, 无需改写)
- [ ] DO Entity 保持 String (8 个文件)
- [ ] Mapper 保持 String (1 个文件)
- [ ] 预期时间: 0 小时

---

## 🔄 转换工具函数建议

```java
// patra-common 或工具类
public class ProvenanceCodeConverter {
    
    /**
     * 将字符串转换为 ProvenanceCode 枚举
     */
    public static ProvenanceCode fromString(String code) {
        if (code == null) {
            return null;
        }
        return ProvenanceCode.of(code);
    }
    
    /**
     * 将 ProvenanceCode 转换为字符串（用于 MyBatis/数据库）
     */
    public static String toString(ProvenanceCode provenanceCode) {
        if (provenanceCode == null) {
            return null;
        }
        return provenanceCode.getCode();
    }
}
```

---

## ✅ 改写检查清单

- [ ] 确认 ProvenanceCode 枚举的完整定义
- [ ] 验证 ProvenanceCode.of() 和 .getCode() 方法
- [ ] 端口接口改写完成
- [ ] 实现层转换逻辑完成
- [ ] 编译无误
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 代码审查完成

---

## 📌 改写注意事项

1. **Null 安全**: 处理 provenanceCode 为 null 的情况
   ```java
   String code = provenanceCode != null ? provenanceCode.getCode() : null;
   ```

2. **DO ↔ Domain 转换**: 在 Converter 层处理
   ```java
   CursorDO do;
   cursor.setProvenanceCode(ProvenanceCode.of(do.getProvenanceCode()));
   ```

3. **MyBatis 查询**: 需要转换为字符串
   ```java
   .eq("provenance_code", provenanceCode.getCode())
   ```

4. **Record 和异常**: 可根据序列化需求决定是否改写

5. **日志记录**: 保持字符串形式或调用 .getCode()
   ```java
   log.debug("provenance={}", provenanceCode.getCode());
   ```

---

## 总体时间估算

| 阶段 | 工作量 | 时间 |
|------|--------|------|
| 阶段 1 (端口) | 5 个文件 | 2-3 小时 |
| 阶段 2 (实现) | 4 个文件 | 4-5 小时 |
| 阶段 3 (UseCase) | 8+ 个文件 | 6-8 小时 |
| 阶段 4 (模型) | 3 个文件 | 1-2 小时 |
| 测试和修复 | - | 2-3 小时 |
| **总计** | **20+ 个文件** | **15-21 小时** |

---

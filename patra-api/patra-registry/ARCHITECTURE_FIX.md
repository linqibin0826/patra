# 架构修复：异常类位置调整

## 问题描述

在初始实现中，`DictionaryRepositoryException` 被错误地放置在 `infra` 模块中，这违反了六边形架构的依赖规则：

- **错误位置**: `com.patra.registry.infra.exception.DictionaryRepositoryException`
- **问题**: app模块不能直接引用infra模块中的类
- **违规**: 违反了依赖方向规则（app → domain，不能 app → infra）

## 解决方案

### 1. 异常类重新定位

将 `DictionaryRepositoryException` 从 `infra.exception` 包移动到 `domain.exception` 包：

```
旧位置: patra-registry-infra/src/main/java/com/patra/registry/infra/exception/DictionaryRepositoryException.java
新位置: patra-registry-domain/src/main/java/com/patra/registry/domain/exception/DictionaryRepositoryException.java
```

### 2. 架构合理性

将异常放在domain层是正确的，因为：

- **Repository接口在domain层**: `DictionaryRepository` 接口定义在 `domain.port` 包中
- **异常是接口契约的一部分**: Repository方法的 `@throws` 声明需要引用这个异常
- **符合依赖方向**: domain层的异常可以被app层和infra层引用

### 3. 更新的依赖关系

修复后的依赖关系：

```
adapter → app + domain (✓)
app → domain (✓)
infra → domain (✓)
domain → 无外部依赖 (✓)
```

## 修复内容

### 1. 文件操作
- ✅ 删除 `patra-registry-infra/.../DictionaryRepositoryException.java`
- ✅ 创建 `patra-registry-domain/.../DictionaryRepositoryException.java`
- ✅ 删除重复的 `patra-registry-domain/port/DictionaryRepositoryException.java`

### 2. Import语句更新
更新了以下文件中的 import 语句：

- ✅ `DictionaryRepository.java` (domain/port)
- ✅ `DictionaryRepositoryMpImpl.java` (infra)

> 注：后续随着异常体系对齐，`DictionaryErrorHandler` 等本地组件已被整体移除，不再需要额外维护。

### 3. 文档更新
- ⛔️ 原 `ERROR_HANDLING.md` 已删除，统一采用平台级《Patra 异常体系设计规范》作为唯一来源

## 验证结果

### 1. 包结构验证
```bash
find patra-registry -name "*DictionaryRepositoryException*"
```

结果显示只有一个异常类文件在正确位置：
- `patra-registry-domain/.../exception/DictionaryRepositoryException.java`

### 2. Import引用验证
所有文件都正确引用了新的包路径：
```java
import com.patra.registry.domain.exception.DictionaryRepositoryException;
```

### 3. 架构依赖验证
- ✅ app模块不再直接引用infra模块
- ✅ 所有模块都遵循正确的依赖方向
- ✅ 异常类作为domain契约的一部分，可以被所有需要的层引用

## 六边形架构原则

这次修复确保了我们遵循六边形架构的核心原则：

1. **依赖方向**: 依赖只能指向内层（domain）
2. **端口和适配器**: Repository接口（端口）及其异常在domain层
3. **关注点分离**: 基础设施细节不泄露到应用层
4. **可测试性**: 应用层可以独立于基础设施层进行测试

## 最佳实践

为避免类似问题，建议：

1. **异常位置原则**: 
   - Domain异常 → domain.exception包
   - Repository异常 → domain.exception包（作为port契约的一部分）
   - Infrastructure特定异常 → infra.exception包（仅在infra层内部使用）

2. **依赖检查**: 
   - 定期检查模块间的依赖关系
   - 使用架构测试工具验证依赖规则
   - Code Review时重点关注import语句

3. **包命名约定**:
   - 异常类统一放在各层的exception子包中
   - 避免跨层直接引用具体实现类

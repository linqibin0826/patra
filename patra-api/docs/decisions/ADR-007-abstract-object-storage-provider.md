---
type: adr
adr_id: 7
date: 2025-12-01
status: accepted
date_decided: 2025-12-01
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/design-pattern
  - tech/object-storage
---

# ADR-007: 抽象对象存储提供者设计

## 状态

**accepted**

## 背景

`patra-spring-boot-starter-object-storage` 支持 MinIO 和 AWS S3 两种存储提供商，分别由 `MinioStorageProvider` 和 `S3StorageProvider` 实现。

当前存在的问题：
1. **代码重复**：两个 Provider 包含大量相同的验证逻辑（Bucket 命名、Object Key 格式、文件大小限制）
2. **验证不一致**：不同 Provider 的验证规则可能存在细微差异，导致行为不一致
3. **扩展困难**：新增 Provider（如 Aliyun OSS、Azure Blob）需要重复实现所有验证逻辑
4. **维护成本**：验证规则变更需要同步修改多个 Provider

共享的验证规则：
- **Bucket 命名**：3-63 字符，仅小写字母/数字/点/短横线，必须以字母或数字开头结尾
- **Object Key**：最大 1024 字符，不能以 `/` 开头，上传时不能包含 `//`
- **文件大小**：上传前验证 `contentLength`，默认最大 100MB

## 决策

我们将引入 `AbstractObjectStorageProvider` 抽象基类，使用**模板方法模式**封装共享逻辑：

1. **验证逻辑集中**：所有验证规则在抽象基类中统一实现
2. **常量定义共享**：S3 兼容性标准的正则表达式、长度限制等常量定义在基类
3. **子类职责单一**：子类只需实现具体的存储操作（与 SDK 的交互）

类结构：
```
ObjectStorageProvider (接口)
    └── AbstractObjectStorageProvider (抽象基类)
            ├── MinioStorageProvider (MinIO 实现)
            └── S3StorageProvider (S3 实现)
```

抽象基类提供的方法：
- `validateUploadArguments()`: 上传参数验证
- `validateDownloadArguments()`: 下载参数验证
- `isValidBucketName()`: Bucket 命名验证
- `isValidObjectKey()`: Object Key 验证

## 后果

### 正面影响

- **代码复用**：验证逻辑只需实现一次，所有 Provider 共享
- **行为一致**：统一的验证规则确保所有 Provider 行为一致
- **扩展便捷**：新增 Provider 只需实现存储操作，无需关心验证逻辑
- **维护简化**：验证规则变更只需修改抽象基类
- **职责清晰**：抽象基类负责「怎么验证」，子类负责「怎么存储」

### 负面影响

- **继承耦合**：子类与抽象基类存在继承关系，修改基类可能影响所有子类
- **灵活性降低**：如果某个 Provider 需要特殊的验证规则，需要覆盖基类方法

### 风险

- 过度抽象可能导致基类承担过多职责
- 继承层次过深可能影响代码可读性

## 替代方案

### 方案 A：组合模式（验证器）

创建独立的 `ObjectStorageValidator` 组件，通过组合注入到各 Provider。

**优点**：
- 无继承耦合
- 验证器可独立测试
- 支持运行时切换验证策略

**缺点**：
- 需要额外的依赖注入配置
- Provider 与 Validator 之间需要协调
- 增加系统复杂度

### 方案 B：接口默认方法

在 `ObjectStorageProvider` 接口中使用 Java 8+ 默认方法实现验证逻辑。

**优点**：
- 无继承耦合
- 实现简单

**缺点**：
- 接口不应包含过多实现逻辑
- 无法定义私有方法（Java 9+ 支持，但可读性差）
- 无法定义实例变量（如预编译的正则表达式）

### 方案 C：工具类

创建静态工具类 `ObjectStorageUtils` 包含验证方法。

**优点**：
- 无继承/组合耦合
- 调用简单

**缺点**：
- 静态方法难以 Mock 测试
- 无法利用面向对象的多态特性
- 验证规则与 Provider 的关联不明确

## 参考资料

- [Template Method Pattern - Refactoring Guru](https://refactoring.guru/design-patterns/template-method)
- [Amazon S3 Bucket Naming Rules](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html)
- [Effective Java - Item 20: Prefer interfaces to abstract classes](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)

# 功能规格说明: 多数据源多类型数据适配器

**特性分支**: `001-data-source-adapter`
**创建日期**: 2025-11-11
**状态**: 草稿
**输入**: ultrathink 平台需要从多个医学文献数据源获取多种类型的数据，而不仅仅是文献本身。

**设计文档参考**:
- [数据源适配器架构设计方案](../../dev/active/data-source-adapter-design.md)
- [数据源适配器架构需求文档](../../dev/active/data-source-adapter-requirements.md)

## 用户场景与测试

> **重要说明**：本特性是一个**架构改造项目**，目标是提升系统的可扩展性和架构合理性，而不是直接交付给研究人员的用户功能。用户故事从开发团队、数据团队和运维团队的视角出发，验收标准是架构组件的实现和集成。

### 用户故事 1 - 定义规范数据模型接口体系 (优先级: P1)

**用户**: 架构师/后端开发团队

**当前问题**: 现有架构中fetchData接口只返回单一类型数据（文献），无法支持作者、期刊、引用、全文等多种数据类型。每种数据类型都有不同的结构，缺乏统一的抽象。

**目标**: 设计并实现CanonicalData接口体系，作为所有数据类型的顶层抽象，定义统一的规范数据模型（CanonicalLiterature、CanonicalAuthor、CanonicalJournal、CanonicalCitation、CanonicalFullText）。

**为什么是这个优先级**: 这是整个架构改造的基础，规范数据模型是适配器和转换策略的核心依赖。如果不先定义清晰的数据模型，后续的泛型化设计无法进行。

**独立测试**: 可以通过编写单元测试验证CanonicalData接口及其实现类的结构完整性、不变性约束和验证规则，无需依赖外部API或数据库。

**验收场景**:

1. **假设** 定义了CanonicalData顶层接口, **当** 创建CanonicalLiterature实现类, **那么** 包含必填字段（id、title、provenance、createdAt）和可选字段（abstractText、authors、journal、keywords）
2. **假设** 创建一个CanonicalLiterature对象但标题为空, **当** 调用validate()方法, **那么** 返回ValidationResult失败，错误消息为"标题不能为空"
3. **假设** 定义了5种数据类型的规范模型, **当** 查看类图, **那么** 所有模型都继承CanonicalData接口，每个模型都有明确的聚合边界和不变性约束
4. **假设** CanonicalLiterature包含作者列表, **当** 作者信息缺失, **那么** 作者列表为空集合（不是null），文献对象仍然有效

---

### 用户故事 2 - 重构适配器接口支持泛型化 (优先级: P1)

**用户**: 架构师/后端开发团队

**当前问题**: 现有DataSourceAdapter接口的fetchData方法返回固定类型，无法根据请求动态返回不同类型的规范数据。AdapterRegistry只能按数据源查找适配器，无法按数据源+数据类型组合查找。

**目标**: 将DataSourceAdapter改造为泛型接口`DataSourceAdapter<T extends CanonicalData>`，增强AdapterRequest包含requestedDataType字段，扩展AdapterRegistry支持按数据源代码和数据类型查找适配器。

**为什么是这个优先级**: 这是实现多数据类型支持的核心架构变更，GenericBatchExecutor需要根据数据类型动态选择合适的适配器。如果适配器接口不支持泛型，整个架构改造无法推进。

**独立测试**: 可以通过创建Mock适配器和Mock数据，验证AdapterRegistry能够根据数据源代码和数据类型正确查找和返回泛型化的适配器实例。

**验收场景**:

1. **假设** 定义了DataSourceAdapter<T>泛型接口, **当** 实现PubMedLiteratureAdapter extends DataSourceAdapter<CanonicalLiterature>, **那么** fetchData方法返回AdapterResult<CanonicalLiterature>类型
2. **假设** AdapterRegistry注册了3个PubMed适配器（文献、作者、引用）, **当** 调用getAdapter("pubmed", CanonicalLiterature.class), **那么** 返回PubMedLiteratureAdapter实例
3. **假设** 请求不存在的数据类型组合, **当** 调用getAdapter("pubmed", CanonicalFullText.class), **那么** 抛出异常"未找到适配器: pubmed -> FULLTEXT"
4. **假设** PubMed适配器声明支持LITERATURE和AUTHOR, **当** 查询其能力（getCapabilities().getSupportedDataTypes()）, **那么** 返回包含LITERATURE和AUTHOR的集合

---

### 用户故事 3 - 实现可插拔的数据转换策略 (优先级: P1)

**用户**: 数据团队

**当前问题**: 数据转换逻辑硬编码在适配器内部，不同数据源的转换规则无法复用，新数据源接入时必须重写所有转换逻辑。转换错误时无法定位是哪个字段或哪条数据出错。

**目标**: 定义DataTransformStrategy<S, T>接口，实现StrategyRegistry管理转换策略，将转换逻辑从适配器中解耦。支持批量转换和TransformResult记录成功/失败详情。

**为什么是这个优先级**: 这是降低新数据源接入成本的关键设计，转换策略可插拔后，数据团队只需编写针对特定数据源的转换逻辑，无需修改适配器核心代码。这直接影响"2-3天接入新数据源"的目标能否实现。

**独立测试**: 可以通过准备PubMed原始数据（PubMedArticle对象）和期望的规范数据（CanonicalLiterature对象），验证PubMedToLiteratureStrategy能够正确转换，且TransformResult包含成功和失败的详细信息。

**验收场景**:

1. **假设** 定义了DataTransformStrategy<PubMedArticle, CanonicalLiterature>, **当** 实现transform方法, **那么** 输入PubMedArticle对象，输出CanonicalLiterature对象，字段映射正确（如pubMedArticle.getPmid() -> standardLiterature.getId()）
2. **假设** StrategyRegistry注册了3个策略, **当** 调用getStrategy(PubMedArticle.class, CanonicalLiterature.class), **那么** 返回PubMedToLiteratureStrategy实例
3. **假设** 批量转换100条数据，其中10条缺少必填字段, **当** 调用batchTransform(), **那么** TransformResult包含90个成功项和10个错误项，每个错误项记录索引和错误原因
4. **假设** 转换策略抛出异常, **当** 捕获异常, **那么** TransformError记录原始数据快照和异常信息，不影响其他数据的转换

---

### 用户故事 4 - 实现PubMed多类型数据适配器 (优先级: P2)

**用户**: 后端开发团队

**当前问题**: 现有PubMed适配器只能获取文献基本信息，无法获取作者详情、引用关系等其他类型数据。即使PubMed API支持这些数据，现有架构也无法扩展支持。

**目标**: 重构PubMed适配器，根据AdapterRequest中的requestedDataType返回对应类型的规范数据。实现至少3种数据类型的支持（LITERATURE、AUTHOR、CITATION），每种类型对应一个转换策略。

**为什么是这个优先级**: 这是验证新架构可行性的关键实践，PubMed适配器是第一个多类型适配器，为后续接入EPMC、ArXiv等数据源提供参考实现。如果PubMed适配器无法验证架构设计，后续所有数据源接入都会受阻。

**独立测试**: 可以通过Mock PubMed API响应（文献、作者、引用数据），验证适配器能够根据请求的数据类型调用不同的Client方法和转换策略，返回正确类型的CanonicalData实现。

**验收场景**:

1. **假设** AdapterRequest指定requestedDataType=LITERATURE, **当** 调用PubMedAdapter.fetchData(), **那么** 调用PubMedClient.searchArticles()，使用PubMedToLiteratureStrategy转换，返回AdapterResult<CanonicalLiterature>
2. **假设** AdapterRequest指定requestedDataType=AUTHOR, **当** 调用PubMedAdapter.fetchData(), **那么** 调用PubMedClient.searchAuthors()，使用PubMedToAuthorStrategy转换，返回AdapterResult<CanonicalAuthor>
3. **假设** AdapterRequest指定requestedDataType=FULLTEXT且PubMed不支持, **当** 调用fetchData(), **那么** 返回AdapterResult.nonRetriableFailure("不支持的数据类型: FULLTEXT")
4. **假设** PubMedAdapter注册到AdapterRegistry, **当** 查询其能力, **那么** getCapabilities().getSupportedDataTypes()返回[LITERATURE, AUTHOR, CITATION]

---

### 用户故事 5 - 增强批量处理和部分成功机制 (优先级: P2)

**用户**: 运维团队

**当前问题**: 批量处理时，一条数据失败导致整批失败，无法区分哪些数据成功、哪些失败。GenericBatchExecutor收到失败结果后只能重新处理整批数据，浪费资源且效率低下。

**目标**: 增强AdapterResult和TransformResult支持部分成功机制，记录成功数据列表、失败数据列表和详细错误信息。修改GenericBatchExecutor逻辑，能够处理部分成功结果（如70%成功即可接受）。

**为什么是这个优先级**: 这是提升系统鲁棒性和效率的关键能力，批量处理是常见场景。部分成功机制能显著减少重复处理和资源浪费，直接影响"批量处理效率提升30%"的业务指标。

**独立测试**: 可以通过准备100条测试数据（90条正常、10条异常），验证TransformResult能够分别记录成功和失败数据，GenericBatchExecutor能够根据成功率阈值（如70%）决定是否发布成功数据。

**验收场景**:

1. **假设** 批量转换100条数据，90条成功10条失败, **当** 调用batchTransform(), **那么** TransformResult包含successItems(90个)和errors(10个TransformError)，getSuccessRate()返回0.9
2. **假设** patra-registry配置全局成功率阈值为0.7，TransformResult的successRate=0.9 ≥ 0.7, **当** GenericBatchExecutor处理结果, **那么** 发布成功数据到下游，同时记录警告日志"部分数据转换失败: 10/100"
3. **假设** patra-registry配置PubMed数据源成功率阈值为0.9（覆盖全局默认），TransformResult的successRate=0.85 < 0.9, **当** GenericBatchExecutor处理PubMed数据结果, **那么** 整批任务标记为失败，返回BatchResult包含所有错误详情
4. **假设** TransformError记录了原始数据快照, **当** 查看错误详情, **那么** 可以看到失败数据的索引、原始内容和错误原因，方便定位问题

---

### 用户故事 6 - 实现错误分类机制 (优先级: P3)

**用户**: 运维团队

**当前问题**: 所有错误都被视为相同类型，无法区分哪些错误是临时的（如网络超时、API限流）、哪些错误是永久的（如数据格式错误、权限不足）。这导致运维团队难以采取针对性的处理措施，也无法为未来的自动化处理提供基础。

**目标**: 增强AdapterResult支持ErrorType枚举（RETRIABLE、NON_RETRIABLE），适配器根据异常类型和HTTP状态码标识错误类型。为未来可能的自动化处理提供基础。

**为什么是这个优先级**: 这是提升系统可观测性和可维护性的能力，为运维团队提供更清晰的错误信息。优先级低于P1和P2是因为可以在架构改造完成后逐步优化。

**独立测试**: 可以通过Mock不同类型的异常（SocketTimeoutException、HttpClientErrorException(429)、IllegalArgumentException），验证适配器能够正确标识错误类型。

**验收场景**:

1. **假设** PubMedClient抛出SocketTimeoutException, **当** 捕获异常, **那么** 返回AdapterResult.retriableFailure("网络超时")，errorType=RETRIABLE
2. **假设** HTTP 429 Too Many Requests且响应头包含Retry-After: 60, **当** 解析响应, **那么** 返回retriableFailure并在metadata中记录retryAfterSeconds=60
3. **假设** HTTP 401 Unauthorized, **当** 解析响应, **那么** 返回AdapterResult.nonRetriableFailure("API认证失败")，errorType=NON_RETRIABLE
4. **假设** 数据格式验证失败, **当** 捕获FormatValidationException, **那么** 返回AdapterResult.nonRetriableFailure("数据格式错误")，errorType=NON_RETRIABLE

---

### 边界情况

- **当数据源API突然变更响应格式时会发生什么?**
  系统的防腐层(Client)隔离外部API变化,如果响应格式变更,只需更新Client层的解析逻辑,不影响核心业务逻辑。

- **当外部API响应格式验证失败时如何处理?**
  系统抛出FormatValidationException,标记为NON_RETRIABLE错误,记录原始响应内容(截断至10KB)到ERROR日志,立即停止处理该数据,不进行重试。运维团队收到告警后通知开发团队分析根因(API变更或数据损坏)。

- **当多个数据源返回同一篇文献的不同版本数据时如何处理?**
  系统根据数据来源(Provenance)标识数据,每个数据源的数据独立存储,由上层业务逻辑决定如何合并或选择优先级。

- **当批量处理的数据量超过单批次限制(如超过1000条)时如何处理?**
  系统拒绝请求并返回错误"批量数据量超过限制(最大1000条)",调用方需要分批处理。

- **当数据源API限流导致无法获取数据时如何处理?**
  系统识别限流错误(HTTP 429)并标记为RETRIABLE错误,记录retryAfterSeconds信息到metadata,任务失败并返回清晰的错误消息"API限流,请稍后重试"。由上层调度器或运维团队决定何时重新执行。

- **当某个数据类型(如全文)在特定数据源中不可用时如何处理?**
  适配器声明其支持的数据类型能力,系统调用前检查能力,如果不支持则返回"该数据源不支持全文数据类型"。

## 需求

### 功能需求

- **FR-001**: 系统必须支持从多个医学文献数据源(PubMed、EPMC、ArXiv)获取数据,每个数据源独立配置和管理
- **FR-002**: 系统必须支持获取多种类型的数据:文献、作者、期刊、引用、全文,每种类型有统一的数据结构
- **FR-003**: 系统必须将不同数据源的外部数据格式转换为平台统一的规范格式(CanonicalData体系)
- **FR-004**: 系统必须在批量处理时支持部分成功机制:成功的数据正常返回,失败的数据记录错误原因,不影响已成功数据
- **FR-005**: 系统必须区分可重试错误(网络超时、API限流)和不可重试错误(数据格式错误、权限不足),并清晰标识错误类型
- **FR-006**: 系统必须支持按关键词、时间范围、数据类型进行数据检索,支持分页和游标翻页机制
- **FR-007**: 系统必须支持批量ID查询,一次性获取多个标识符(PMID、DOI、ArXiv ID)对应的数据
- **FR-008**: 系统必须记录每条数据的来源标识(Provenance),明确标识该数据来自哪个数据源
- **FR-009**: 系统必须允许数据团队通过标准化流程接入新数据源,接入时间不超过3个工作日
- **FR-010**: 系统必须允许每个数据源声明其支持的能力(数据源名称、支持的数据类型),系统根据能力声明进行调用

### 非功能需求

**架构约束**:
- **六边形架构层次**: 此功能涉及Domain、Application、Infrastructure三层:Domain层定义规范数据模型接口,Application层调用适配器获取数据,Infrastructure层实现具体数据源的适配器和转换策略
- **依赖方向**: 严格遵守依赖规则(Infrastructure → Application → Domain),Domain层不依赖任何外部框架,所有外部依赖隔离在Infrastructure层
- **SSOT遵守**: 所有数据源配置(API地址、密钥、超时时间、限流配置)必须从patra-registry获取,不允许硬编码在代码中

**性能要求**:
- **NFR-001**: 单次数据查询响应时间P95 ≤ 3秒(从发起请求到返回规范化数据)
- **NFR-002**: 系统支持每秒100+次并发请求,无性能降级
- **NFR-003**: 单批次数据处理能力 ≥ 100条数据,批量转换时间 ≤ 5秒,使用线程池并行转换（固定大小线程池,并发度从patra-registry配置,全局默认10,支持按数据源覆盖）

**可靠性要求**:
- **NFR-004**: 错误分类准确率 ≥ 95%(正确识别可重试和不可重试错误)
- **NFR-005**: 批量处理时的部分成功机制：成功率阈值从patra-registry配置获取（全局默认70%），支持按数据源覆盖（如PubMed=90%、ArXiv=60%），当实际成功率≥阈值时返回成功结果和警告信息，否则整批失败

**可扩展性要求**:
- **NFR-006**: 新数据源接入不需要修改核心接口和调度逻辑
- **NFR-007**: 新数据类型添加不需要修改适配器注册和查找逻辑

**可测试性要求**:
- **NFR-008**: 所有适配器和转换策略可通过Mock进行独立单元测试
- **NFR-009**: 测试覆盖率 ≥ 80%(单元测试 + 集成测试)

**可观察性要求**:
- **NFR-010**: 适配器必须上报基础性能指标：请求成功率、响应时间P95/P99（按数据源和数据类型分组）
- **NFR-011**: 转换策略必须上报转换成功率、转换失败率（按数据源和数据类型分组）
- **NFR-012**: GenericBatchExecutor必须上报每种数据类型的处理量（成功数、失败数、部分成功数）
- **NFR-013**: 错误分类机制必须上报错误类型分布统计（RETRIABLE vs NON_RETRIABLE，按数据源和错误类型分组）
- **NFR-014**: 所有监控指标通过Micrometer上报，集成到现有监控模块
- **NFR-015**: 日志记录策略：ERROR级别记录失败事件（转换失败、API调用失败、不可重试错误），WARN级别记录部分成功场景和可重试错误，INFO级别记录每次转换的详细过程（包括成功的转换，记录数据源、数据类型、转换耗时）
- **NFR-016**: 失败日志必须包含失败数据快照（脱敏后，最多1KB）和完整堆栈信息，便于根因分析
- **NFR-017**: 日志输出支持结构化格式（JSON），便于日志聚合和检索

### 领域模型 🏛️

**聚合根 (Aggregate Root)**:

- **规范数据(CanonicalData)**: 代表从外部数据源获取并转换后的规范化数据实体,是所有数据类型的顶层抽象
  - **聚合边界**: 包含数据唯一标识、数据类型、来源标识、创建时间、验证规则
  - **不变性约束**:
    - 每条规范数据必须有唯一标识(id)和来源标识(provenance)
    - 数据类型(dataType)必须与实际数据结构一致
    - 创建时间(createdAt)不可修改
  - **唯一标识**: 使用数据源的原始ID(如PMID、DOI、ArXiv ID)作为唯一标识

- **规范文献(CanonicalLiterature)**: 代表一篇学术论文或文献,是平台最核心的数据实体
  - **聚合边界**: 包含文献基本信息(标题、摘要、关键词)、作者列表、期刊信息、出版日期、标识符映射
  - **不变性约束**:
    - 标题(title)不能为空
    - 至少包含一个标识符(PMID、DOI或ArXiv ID之一)
    - 作者列表可以为空(部分数据源可能不提供作者信息)
  - **唯一标识**: 使用来源数据的ID(如"pubmed:12345678"、"arxiv:2301.12345")

- **规范作者(CanonicalAuthor)**: 代表一位学术作者,包含身份标识和机构隶属
  - **聚合边界**: 包含姓名(firstName、lastName、fullName)、ORCID、Email、机构列表
  - **不变性约束**:
    - 姓氏(lastName)或全名(fullName)至少有一个不为空
    - ORCID如果存在,必须符合格式规范(16位数字,分4组)
  - **唯一标识**: 使用ORCID(如果有),否则使用"数据源:作者名"的组合

- **规范期刊(CanonicalJournal)**: 代表一个学术期刊,包含期刊元信息和影响力指标
  - **聚合边界**: 包含期刊名称、缩写、ISSN/eISSN、出版商、影响因子、国家
  - **不变性约束**:
    - 期刊标题(title)不能为空
    - ISSN和eISSN至少有一个不为空
  - **唯一标识**: 使用ISSN或eISSN

- **规范引用(CanonicalCitation)**: 代表两篇文献之间的引用关系
  - **聚合边界**: 包含引用文献ID(citingId)、被引文献ID(citedId)、引用上下文、引用类型
  - **不变性约束**:
    - citingId和citedId不能为空且不能相同
    - 引用类型(type)必须是预定义值之一(DIRECT、INDIRECT、SELF)
  - **唯一标识**: 使用"citingId + citedId"的组合

- **规范全文(CanonicalFullText)**: 代表文献的全文内容或下载链接
  - **聚合边界**: 包含关联文献ID、格式(PDF/HTML/XML)、内容或下载URL、文件大小
  - **不变性约束**:
    - 关联文献ID(literatureId)不能为空
    - 格式(format)必须是预定义值之一
    - 内容(content)和下载URL(downloadUrl)至少有一个不为空
  - **唯一标识**: 使用"文献ID + 格式"的组合

**值对象 (Value Object)**:

- **数据类型(DataType)**: 枚举值,表示数据的类型分类(LITERATURE、AUTHOR、JOURNAL、CITATION、FULLTEXT、AFFILIATION、GRANT),不可变
- **来源标识(Provenance)**: 字符串值对象,表示数据来源的唯一标识(如"pubmed"、"epmc"、"arxiv"),不可变
- **标识符映射(Identifiers)**: 键值对集合,存储多种标识符(PMID、DOI、ArXiv ID、PMC ID等),不可变
- **验证结果(ValidationResult)**: 表示数据验证的结果(成功、失败、警告),包含错误消息列表,不可变
- **错误类型(ErrorType)**: 枚举值,表示错误的分类(NONE、RETRIABLE、NON_RETRIABLE、PARTIAL_SUCCESS),用于标识错误性质和指导后续处理

**领域事件 (Domain Event)**:

- **数据已获取(DataFetched)**: 适配器成功从外部数据源获取原始数据时发布
  - **携带数据**: 数据源代码、数据类型、获取数量、游标令牌、时间戳

- **数据已转换(DataTransformed)**: 原始数据成功转换为规范数据格式时发布
  - **携带数据**: 数据源代码、数据类型、转换成功数量、转换失败数量、时间戳

- **数据转换失败(DataTransformFailed)**: 数据转换过程中发生不可恢复错误时发布
  - **携带数据**: 数据源代码、失败原因、原始数据快照、时间戳

- **适配器调用失败(AdapterInvocationFailed)**: 适配器调用外部API失败时发布
  - **携带数据**: 数据源代码、错误类型(可重试/不可重试)、错误消息、时间戳

**仓储接口 (Repository Interface)**:

- **CanonicalDataRepository**: 在patra-ingest-domain层定义接口,patra-ingest-infra层实现
  - **关键方法**: save()、findById()、findByProvenance()、batchSave()
  - **注意**: 具体方法签名在实施阶段由开发团队设计,此处不定义技术细节

**适配器接口 (Adapter Interface - 端口)**:

- **DataSourceAdapter**: 定义数据源适配器的端口接口,由Application层调用,Infrastructure层实现
  - **职责**: 封装与外部数据源的交互,执行数据获取和转换
  - **关键能力**: 声明数据源名称、支持的数据类型
  - **注意**: 这是六边形架构的"端口",不是技术实现

- **DataTransformStrategy**: 定义数据转换策略的端口接口,负责将外部数据模型转换为规范模型
  - **职责**: 定义转换规则,支持批量转换和部分成功
  - **注意**: 转换策略可插拔,新数据源只需实现对应的转换策略

**SSOT 提示** ⚠️:
此功能涉及以下内容,**必须**从patra-registry获取:
- [x] Provenance配置(数据源配置,如PubMed API地址、API密钥、超时时间)
- [x] 数据字典(枚举值,如数据类型DataType、错误类型ErrorType、引用类型CitationType)
- [x] 元数据(如必填字段定义、数据验证规则、标识符格式规范)
- [x] 批量处理配置(全局成功率阈值默认70%,支持按数据源覆盖如PubMed=90%、ArXiv=60%)
- [x] 并发控制配置(线程池并发度全局默认10,支持按数据源覆盖以适应不同API限流策略)

## 成功标准

### 架构改造指标

- **SC-001**: CanonicalData接口体系定义完整，包含5种数据类型（LITERATURE、AUTHOR、JOURNAL、CITATION、FULLTEXT），每种类型都有明确的聚合边界和不变性约束，通过单元测试验证
- **SC-002**: DataSourceAdapter接口支持泛型化设计（DataSourceAdapter<T extends CanonicalData>），AdapterRegistry能够根据数据源代码和数据类型查找适配器，通过集成测试验证
- **SC-003**: DataTransformStrategy接口支持可插拔转换策略，StrategyRegistry能够管理和查找策略，至少实现3个转换策略（PubMedToLiteratureStrategy、PubMedToAuthorStrategy、PubMedToCitationStrategy），通过单元测试验证每个策略的正确性
- **SC-004**: PubMed适配器重构完成，支持3种数据类型（LITERATURE、AUTHOR、CITATION），能够根据AdapterRequest的requestedDataType返回对应类型的规范数据，通过集成测试验证
- **SC-005**: AdapterResult和TransformResult支持部分成功机制，TransformResult包含成功列表和错误列表，GenericBatchExecutor能够根据成功率阈值（≥70%）处理部分成功结果，通过单元测试验证
- **SC-006**: AdapterResult支持错误类型标识（RETRIABLE、NON_RETRIABLE），适配器能够根据异常类型和HTTP状态码正确标识错误类型，通过单元测试验证不同错误场景的分类逻辑
- **SC-007**: 所有核心组件（CanonicalData、DataSourceAdapter、DataTransformStrategy、AdapterRegistry、StrategyRegistry）的单元测试覆盖率 ≥ 80%
- **SC-008**: PubMed适配器的集成测试覆盖3种数据类型，每种类型至少包含正常场景和异常场景的测试用例

### 可扩展性指标

- **EX-001**: 新数据源接入时间 ≤ 3个工作日，只需实现DataSourceAdapter接口、编写DataTransformStrategy策略、注册到AdapterRegistry，无需修改GenericBatchExecutor核心代码
- **EX-002**: 新数据类型添加时间 ≤ 1个工作日，只需定义新的CanonicalData实现类和对应的DataTransformStrategy，无需修改适配器注册和查找逻辑
- **EX-003**: 提供完整的开发文档和示例代码（参考PubMed适配器实现），数据团队可以独立完成新数据源接入，无需架构师介入

### 性能指标

- **PERF-001**: AdapterRegistry根据数据源代码和数据类型查找适配器的时间复杂度为O(1)（使用HashMap存储）
- **PERF-002**: StrategyRegistry根据源类型和目标类型查找转换策略的时间复杂度为O(1)
- **PERF-003**: 批量转换100条数据的时间 ≤ 5秒（使用线程池并行转换，并发度从patra-registry配置），通过性能测试验证不同并发度下的吞吐量（如并发度10时，100条数据转换时间约0.5秒）

### 可维护性指标

- **MAINT-001**: 代码遵循六边形架构原则，Domain层不依赖任何框架，Infrastructure层隔离所有外部依赖，通过架构验证工具（如ArchUnit）验证
- **MAINT-002**: 所有接口和关键类都有完整的JavaDoc注释，注释覆盖率 ≥ 90%
- **MAINT-003**: 提供完整的README文档（patra-starter-provenance/README.md），包含架构说明、快速开始、核心概念、常见问题

## 假设

- **假设1**: 现有系统已经存在GenericBatchExecutor批量调度逻辑，本次改造只需修改其调用适配器的方式，无需重写整个调度器
- **假设2**: 现有系统已经有PubMed数据源的基础实现（PubMedClient防腐层），本次改造重点是适配器和转换策略的重构，无需重写Client层
- **假设3**: 不同数据源返回的同类数据（如文献）具有相似的核心字段（标题、作者、摘要、出版日期），可以映射到统一的规范模型，字段映射规则可以通过Strategy模式实现
- **假设4**: 数据验证规则（如必填字段、格式规范）可以定义在CanonicalData的validate()方法中，无需引入复杂的验证框架
- **假设5**: 数据团队具备基本的Java编程能力和Spring Boot开发经验，能够根据开发文档和示例代码编写适配器和转换策略
- **假设6**: 批量处理的数据量通常在100-1000条之间，单次转换时间在几十毫秒级别，批量转换时间可以控制在5秒以内
- **假设7**: 本次架构改造不涉及数据持久化逻辑的变更，规范数据模型生成后交给现有的存储模块处理

## 澄清

### 会话 2025-11-11

- 问：适配器和转换策略运行时需要上报哪些监控指标？ → 答：详细指标（基础指标 + 转换成功率、每种数据类型的处理量、错误类型分布统计）
- 问：转换失败、API调用失败等异常场景应该记录什么级别的日志，以及包含哪些信息？ → 答：详细日志（ERROR + WARN + INFO，记录每次转换的详细过程包括成功的转换）
- 问：外部数据源API返回的响应格式验证失败时（如JSON解析失败、缺少必填字段）应该如何处理？ → 答：抛出异常，标记为NON_RETRIABLE错误，记录原始响应（截断至10KB），不重试
- 问：批量处理的成功率阈值（70%）应该如何管理？ → 答：从patra-registry配置，全局默认70%，支持按数据源覆盖（如PubMed=90%，ArXiv=60%），运维团队可动态调整无需重启
- 问：批量转换多条数据时（如100条）应该采用什么并发策略？ → 答：线程池并行转换，使用固定大小线程池（可配置并发度），从patra-registry配置并发度（全局默认10，按数据源覆盖）

## 范围界限

### 包含在此功能中
- ✅ 从多个数据源(PubMed、EPMC、ArXiv)获取数据的能力
- ✅ 支持多种数据类型(文献、作者、期刊、引用、全文)的统一数据模型
- ✅ 外部数据格式到平台规范格式的转换逻辑
- ✅ 批量处理和部分成功机制
- ✅ 错误分类(可重试/不可重试)机制
- ✅ 数据源能力声明和注册管理
- ✅ 基础的健康检查和性能指标监控

### 不包含在此功能中
- ❌ 数据去重和合并逻辑(如多个数据源返回同一篇文献时如何合并) - 留给上层业务逻辑处理
- ❌ 数据质量评分和优先级选择(如PubMed数据优先级高于ArXiv) - 未来功能
- ❌ 数据缓存策略和失效管理 - 由独立的缓存模块处理
- ❌ 复杂的查询语法解析(如布尔查询、通配符查询) - 由查询引擎模块处理
- ❌ 数据的持久化存储和索引 - 由独立的存储模块处理
- ❌ 用户权限和数据访问控制 - 由认证授权模块处理
- ❌ 数据的实时推送和订阅机制 - 未来功能
- ❌ 自动重试机制 - 留给上层调度器或批处理框架处理
- ❌ 高级的错误恢复策略(如断点续传、增量更新) - 未来功能

### 与其他功能的集成点
- **与批量调度模块集成**: 适配器被GenericBatchExecutor调用,执行数据获取和转换
- **与配置中心集成**: 从patra-registry获取数据源配置(API地址、密钥、超时配置)
- **与事件总线集成**: 发布领域事件(DataFetched、DataTransformed、AdapterInvocationFailed)供其他模块订阅
- **与监控模块集成**: 上报性能指标(请求时间、成功率、错误率)和健康状态

## 依赖

### 外部依赖
- **PubMed E-utilities API**: 提供文献、作者、引用数据的查询接口
- **EPMC RESTful API**: 提供欧洲医学文献数据的查询接口
- **ArXiv API**: 提供预印本文献数据的查询接口
- **patra-registry**: 提供数据源配置、数据字典、元数据的SSOT服务

### 内部模块依赖
- **patra-ingest-app**: 提供GenericBatchExecutor批量调度能力
- **patra-common**: 提供CanonicalData规范数据模型定义
- **patra-starter-provenance**: 提供适配器和转换策略的基础设施

## 风险与缓解措施

| 风险 | 影响 | 概率 | 缓解措施 |
|-----|------|------|----------|
| 数据源API突然变更格式 | 高 | 中 | 1. 使用防腐层(Client)隔离外部API变化 2. 建立API变更监控机制 3. 维护多版本API适配 |
| 批量转换性能不达标 | 中 | 低 | 1. 使用并行转换策略 2. 优化数据映射逻辑 3. 引入缓存机制减少重复转换 |
| 新数据源接入学习成本高 | 中 | 中 | 1. 提供详细的开发文档和示例代码 2. 组织培训和代码评审 3. 提供适配器模板和脚手架工具 |
| 部分成功机制实现复杂 | 低 | 低 | 1. 使用TransformResult封装转换结果 2. 清晰的错误记录和日志 3. 充分的单元测试覆盖 |
| 错误分类不准确影响监控和排查 | 中 | 中 | 1. 基于HTTP状态码和异常类型建立错误分类规则 2. 提供手动配置覆盖机制 3. 监控错误分类准确率并调整策略 |
| 多数据源并发调用导致资源耗尽 | 高 | 低 | 1. 使用限流和熔断机制 2. 配置合理的线程池和连接池 3. 监控系统资源使用情况 |

## 实施阶段

### 第一阶段：核心接口和模型定义（第1周）
**目标**: 完成架构改造的基础设施，定义所有核心接口和规范数据模型

**交付物**:
- CanonicalData接口及5种数据类型实现（CanonicalLiterature、CanonicalAuthor、CanonicalJournal、CanonicalCitation、CanonicalFullText）
- DataSourceAdapter<T>泛型接口定义
- DataTransformStrategy<S, T>泛型接口定义
- AdapterResult和AdapterRequest增强（支持泛型和错误类型）
- TransformResult和TransformError定义（支持部分成功）

**验收标准**:
- 所有接口通过代码审查，符合六边形架构原则
- 单元测试覆盖率 ≥ 80%
- 通过ArchUnit验证依赖方向正确

### 第二阶段：适配器注册和PubMed重构（第2周）
**目标**: 实现适配器和策略的注册管理机制，重构PubMed适配器支持多类型数据

**交付物**:
- AdapterRegistry实现（支持按数据源+数据类型查找）
- StrategyRegistry实现（支持按源类型+目标类型查找）
- PubMed适配器重构（支持LITERATURE、AUTHOR、CITATION三种类型）
- 3个转换策略实现（PubMedToLiteratureStrategy、PubMedToAuthorStrategy、PubMedToCitationStrategy）
- GenericBatchExecutor适配新的适配器接口

**验收标准**:
- PubMed适配器集成测试通过（覆盖3种数据类型）
- AdapterRegistry和StrategyRegistry单元测试覆盖率 ≥ 80%
- 能够根据AdapterRequest的requestedDataType返回正确类型的规范数据

### 第三阶段：批量处理和错误分类优化（第3周）
**目标**: 实现批量处理的部分成功机制和错误分类识别

**交付物**:
- TransformResult支持部分成功，记录成功和失败详情
- GenericBatchExecutor支持部分成功处理（成功率≥70%即可接受）
- AdapterResult支持错误类型标识（RETRIABLE、NON_RETRIABLE）
- 适配器实现错误分类逻辑（根据异常类型和HTTP状态码）
- 完整的开发文档（README.md）和示例代码

**验收标准**:
- 批量处理单元测试覆盖正常场景和部分成功场景
- 错误分类单元测试覆盖各种错误场景（网络超时、限流、权限错误、数据格式错误等）
- 开发文档经过技术写作审查，数据团队可以独立参考文档接入新数据源

### 后续扩展（第4周及以后）
**未包含在当前阶段，但可以作为未来优化方向**:
- 接入EPMC和ArXiv数据源（验证架构的可扩展性）
- 实现期刊和全文数据类型的支持
- 优化数据转换性能（并行转换、缓存策略）
- 完善监控和告警机制（健康检查、性能指标）
- 实现自动重试机制（由上层调度器或批处理框架实现）
- 实现高级错误恢复策略（断点续传、增量更新）

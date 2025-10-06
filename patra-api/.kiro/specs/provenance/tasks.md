# patra-spring-boot-starter-provenance 实现任务列表

## 任务概述

本任务列表将 patra-spring-boot-starter-provenance 的设计转化为可执行的编码任务。遵循增量开发原则，优先实现核心功能，确保每个步骤都是独立的、可测试的。

## 任务列表

- [ ] 1. 创建项目骨架和模块结构
  - 创建 patra-spring-boot-starter-provenance 模块
  - 配置 Maven 依赖管理和编译插件
  - 创建基础包结构（pubmed/、epmc/、common/、boot/）
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 1.1 创建 Maven 模块
  - 在根 pom.xml 中添加 patra-spring-boot-starter-provenance 模块
  - 创建 patra-spring-boot-starter-provenance/pom.xml
  - 配置依赖（patra-common、patra-egress-gateway-api、patra-registry-api、Jackson、Micrometer 等）
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 1.2 创建基础包结构
  - 创建 com.patra.starter.provenance.pubmed 包
  - 创建 com.patra.starter.provenance.epmc 包
  - 创建 com.patra.starter.provenance.common 包（config/、gateway/、converter/、metrics/、exception/）
  - 创建 com.patra.starter.provenance.boot 包
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 2. 实现公共组件（common 包）
  - 实现配置对象（ProvenanceConfig 及嵌套对象）
  - 实现异常类（ProvenanceClientException）
  - 实现网关请求构建器（GatewayRequestBuilder）
  - 实现配置加载器（ConfigLoader）
  - 实现 XML 转 JSON 转换器（XmlToJsonConverter）
  - 实现性能指标记录器（ProvenanceMetrics）
  - _需求: 需求 4（网关调用封装）、需求 5（配置管理）、需求 12（性能指标记录）_

- [ ] 2.1 实现配置对象
  - 创建 ProvenanceConfig record
  - 创建 HttpConfig record
  - 创建 PaginationConfig record
  - 创建 BatchingConfig record
  - 创建 RetryConfig record
  - 创建 WindowOffsetConfig record
  - 创建 RateLimitConfig record
  - _需求: 需求 5（配置管理）_

- [ ] 2.2 实现异常类
  - 创建 ProvenanceClientException 类
  - 实现构造函数（provenanceCode、apiName、message、cause）
  - 实现 getter 方法
  - _需求: 需求 4（网关调用封装）_

- [ ] 2.3 实现网关请求构建器
  - 创建 GatewayRequestBuilder 类
  - 实现 build() 方法（构建 ExternalCallRequestDTO）
  - 实现 URL 构建逻辑（baseUrl + path + query parameters）
  - 实现 HTTP Headers 构建逻辑（从 ProvenanceConfig 提取）
  - 实现弹性配置转换逻辑（ProvenanceConfig → ResilienceConfigDTO）
  - _需求: 需求 4（网关调用封装）_

- [ ] 2.4 实现配置加载器
  - 创建 ConfigLoader 类
  - 实现 loadConfig() 方法（按优先级加载配置）
  - 实现从调用方传递的配置加载逻辑
  - 实现从数据库加载配置逻辑（通过 patra-registry Feign 客户端）
  - 实现从本地配置加载逻辑（ProvenanceProperties）
  - 实现 ProvenanceConfigResp → ProvenanceConfig 转换逻辑
  - _需求: 需求 5（配置管理）_

- [ ] 2.5 实现 XML 转 JSON 转换器
  - 创建 XmlToJsonConverter 类
  - 配置 XmlMapper 和 ObjectMapper
  - 实现 convert() 方法（XML 字符串 → JSON 对象）
  - 实现异常处理（解析失败时抛出 ProvenanceClientException）
  - _需求: 需求 1（数据源 API 封装）、需求 3（响应完整性）_

- [ ] 2.6 实现性能指标记录器
  - 创建 ProvenanceMetrics 类
  - 实现 recordApiCall() 方法（记录 API 调用耗时和成功/失败次数）
  - 配置 Micrometer Timer（provenance.client.api.duration）
  - 配置 Micrometer Counter（provenance.client.api.success、provenance.client.api.failure）
  - 实现异常处理（指标记录失败不影响正常流程）
  - _需求: 需求 12（性能指标记录）_


- [ ] 3. 实现 PubMed 数据源（pubmed 包）
  - 定义 PubMed Request 对象（ESearchRequest、EFetchRequest）
  - 定义 PubMed Response 对象（ESearchResponse、EFetchResponse 及嵌套对象）
  - 定义 PubMedClient 接口
  - 实现 PubMedClientImpl
  - _需求: 需求 1（数据源 API 封装）、需求 2（参数完整性）、需求 3（响应完整性）_

- [ ] 3.1 定义 PubMed Request 对象
  - 创建 ESearchRequest record（db、term、retstart、retmax 等 14 个参数）
  - 实现紧凑构造器（校验必需参数 db 和 term）
  - 创建 EFetchRequest record（db、id、retmode、rettype 等 8 个参数）
  - 实现紧凑构造器（校验必需参数 db 和 id）
  - _需求: 需求 2（参数完整性）_

- [ ] 3.2 定义 PubMed Response 对象
  - 创建 ESearchResponse record（count、retmax、retstart、idList 等 9 个字段）
  - 创建 EFetchResponse record（articles 列表）
  - 创建 PubmedArticle record（pmid、medlineCitation、pubmedData）
  - 创建 MedlineCitation record（pmid、dateCreated、article 等）
  - 创建 Article record（journal、articleTitle、abstractText、authorList 等）
  - 创建其他嵌套对象（Journal、Author、Pagination 等）
  - _需求: 需求 3（响应完整性）_

- [ ] 3.3 定义 PubMedClient 接口
  - 定义 esearch(ESearchRequest) 方法
  - 定义 esearch(ESearchRequest, ProvenanceConfig) 方法
  - 定义 efetch(EFetchRequest) 方法
  - 定义 efetch(EFetchRequest, ProvenanceConfig) 方法
  - 添加 Javadoc 注释
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 3.4 实现 PubMedClientImpl
  - 注入依赖（EgressGatewayClient、GatewayRequestBuilder、ConfigLoader、XmlToJsonConverter、ProvenanceMetrics）
  - 实现 esearch() 方法（加载配置 → 构建请求 → 调用网关 → 转换响应 → 记录指标）
  - 实现 efetch() 方法（加载配置 → 构建请求 → 调用网关 → 转换响应 → 记录指标）
  - 实现日志记录（API 调用开始、结束、失败）
  - 实现异常处理（网关调用失败、响应解析失败）
  - _需求: 需求 1（数据源 API 封装）、需求 4（网关调用封装）、需求 8（可观测性）_

- [ ] 4. 实现 EPMC 数据源（epmc 包）
  - 定义 EPMC Request 对象（SearchRequest）
  - 定义 EPMC Response 对象（SearchResponse 及嵌套对象）
  - 定义 EPMCClient 接口
  - 实现 EPMCClientImpl
  - _需求: 需求 1（数据源 API 封装）、需求 2（参数完整性）、需求 3（响应完整性）_

- [ ] 4.1 定义 EPMC Request 对象
  - 创建 SearchRequest record（query、format、pageSize、cursorMark 等 9 个参数）
  - 实现紧凑构造器（校验必需参数 query）
  - _需求: 需求 2（参数完整性）_

- [ ] 4.2 定义 EPMC Response 对象
  - 创建 SearchResponse record（hitCount、nextCursorMark、resultList）
  - 创建 Result record（id、source、pmid、pmcid、doi、title、authorString 等 15 个字段）
  - 创建 Author record（fullName、firstName、lastName、initials、affiliation）
  - _需求: 需求 3（响应完整性）_

- [ ] 4.3 定义 EPMCClient 接口
  - 定义 search(SearchRequest) 方法
  - 定义 search(SearchRequest, ProvenanceConfig) 方法
  - 添加 Javadoc 注释
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 4.4 实现 EPMCClientImpl
  - 注入依赖（EgressGatewayClient、GatewayRequestBuilder、ConfigLoader、ProvenanceMetrics）
  - 实现 search() 方法（加载配置 → 构建请求 → 调用网关 → 解析响应 → 记录指标）
  - 实现日志记录（API 调用开始、结束、失败）
  - 实现异常处理（网关调用失败、响应解析失败）
  - _需求: 需求 1（数据源 API 封装）、需求 4（网关调用封装）、需求 8（可观测性）_

- [ ] 5. 实现自动配置（boot 包）
  - 创建 ProvenanceProperties 配置属性类
  - 创建 ProvenanceAutoConfiguration 自动配置类
  - 配置 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - _需求: 需求 7（Starter 自动配置）_

- [ ] 5.1 创建 ProvenanceProperties
  - 使用 @ConfigurationProperties(prefix = "patra.provenance")
  - 定义 enabled 属性（默认 true）
  - 定义 PubMedProperties 嵌套类（baseUrl、http）
  - 定义 EPMCProperties 嵌套类（baseUrl、http）
  - 定义 HttpConfigProperties 嵌套类（defaultHeaders、timeoutConnectMillis、timeoutReadMillis、timeoutTotalMillis）
  - _需求: 需求 5（配置管理）、需求 7（Starter 自动配置）_

- [ ] 5.2 创建 ProvenanceAutoConfiguration
  - 使用 @AutoConfiguration 注解
  - 使用 @EnableConfigurationProperties(ProvenanceProperties.class)
  - 使用 @ConditionalOnBean(EgressGatewayClient.class) 检查网关依赖
  - 使用 @ConditionalOnProperty 支持开关配置
  - 注册 GatewayRequestBuilder Bean
  - 注册 ConfigLoader Bean
  - 注册 XmlToJsonConverter Bean
  - 注册 ProvenanceMetrics Bean
  - 注册 PubMedClient Bean
  - 注册 EPMCClient Bean
  - _需求: 需求 7（Starter 自动配置）_

- [ ] 5.3 配置自动装配文件
  - 创建 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - 添加 com.patra.starter.provenance.boot.ProvenanceAutoConfiguration
  - _需求: 需求 7（Starter 自动配置）_

- [ ] 6. 配置文档和示例
  - 创建模块 README.md
  - 提供配置示例（application.yml）
  - 提供使用示例（基础使用、带配置覆盖、分页处理）
  - _需求: 需求 1-12（所有需求）_

- [ ] 6.1 创建模块 README.md
  - 概述模块职责和核心功能
  - 说明依赖添加方式
  - 提供配置示例
  - 提供使用示例（PubMed、EPMC）
  - 说明配置优先级
  - 说明性能指标
  - _需求: 需求 1-12（所有需求）_

- [ ] 6.2 更新根 pom.xml
  - 在 modules 中添加 patra-spring-boot-starter-provenance
  - _需求: 需求 1（数据源 API 封装）_

- [ ] 6.3 更新 patra-parent 依赖管理
  - 在 dependencyManagement 中添加 patra-spring-boot-starter-provenance
  - 添加 Jackson Dataformat XML 版本管理
  - _需求: 需求 1（数据源 API 封装）_

## 当前进度总结

### ✅ 已完成（Phase 0: Spec Creation）
- 需求文档已创建（requirements.md）
- 设计文档已创建（design.md）
- 任务列表已创建（tasks.md）

### 🔄 下一步建议（Phase 1: Core Implementation）

**优先级 P0（必须完成才能运行）：**
1. 创建项目骨架和模块结构（任务 1）
2. 实现公共组件（任务 2）
3. 实现 PubMed 数据源（任务 3）
4. 实现 EPMC 数据源（任务 4）
5. 实现自动配置（任务 5）
6. 配置文档和示例（任务 6）

**预计时间**：20-25 小时（约 3-4 天全职工作）


## 任务执行说明

1. **按顺序执行**：任务按照依赖关系排列，建议按顺序执行
2. **增量开发**：每个任务都是独立的、可测试的增量
3. **小步提交**：完成每个任务后及时提交代码
4. **文档同步**：修改代码时同步更新相关文档
5. **优先级驱动**：优先完成 P0 任务，确保最小可运行版本（MVP）

## 技术栈

- Java 21
- Spring Boot 3.2.4
- Spring Cloud 2023.0.1
- Jackson（JSON 序列化/反序列化）
- Jackson Dataformat XML（XML 解析）
- Micrometer（性能指标记录）
- Lombok（代码生成）
- Hutool（工具类库）

## 预期交付物

- 完整的 patra-spring-boot-starter-provenance 模块
- PubMedClient 和 EPMCClient 客户端接口
- 强类型的 Request 和 Response 对象
- 配置管理（三级优先级）
- 网关调用封装
- 性能指标记录
- 模块 README 和使用示例

---

## 实施路线图（详细版）

### Phase 1: 项目骨架和公共组件（任务 1-2）

**目标**: 创建项目结构和公共组件，为数据源实现打好基础

#### 步骤 1: 创建项目骨架（任务 1）
**时间估算**: 2-3 小时

1. 创建 Maven 模块
   - 在根 pom.xml 中添加 patra-spring-boot-starter-provenance 模块
   - 创建 patra-spring-boot-starter-provenance/pom.xml
   - 配置依赖（patra-common、patra-egress-gateway-api、patra-registry-api、Jackson、Micrometer 等）

2. 创建基础包结构
   - 创建 com.patra.starter.provenance.pubmed 包
   - 创建 com.patra.starter.provenance.epmc 包
   - 创建 com.patra.starter.provenance.common 包（config/、gateway/、converter/、metrics/、exception/）
   - 创建 com.patra.starter.provenance.boot 包

**验收标准**:
- ✅ Maven 模块可以正常编译
- ✅ 包结构清晰，符合设计文档

#### 步骤 2: 实现公共组件（任务 2）
**时间估算**: 6-8 小时

1. 实现配置对象（任务 2.1）
   - 创建 ProvenanceConfig record 及嵌套对象（HttpConfig、PaginationConfig、BatchingConfig、RetryConfig、WindowOffsetConfig、RateLimitConfig）
   - 使用 record 确保不可变性

2. 实现异常类（任务 2.2）
   - 创建 ProvenanceClientException 类
   - 实现构造函数和 getter 方法

3. 实现网关请求构建器（任务 2.3）
   - 创建 GatewayRequestBuilder 类
   - 实现 build() 方法（构建 ExternalCallRequestDTO）
   - 实现 URL 构建逻辑（使用反射遍历 Request 对象字段）
   - 实现 HTTP Headers 构建逻辑
   - 实现弹性配置转换逻辑

4. 实现配置加载器（任务 2.4）
   - 创建 ConfigLoader 类
   - 实现 loadConfig() 方法（按优先级加载配置）
   - 实现 ProvenanceConfigResp → ProvenanceConfig 转换逻辑

5. 实现 XML 转 JSON 转换器（任务 2.5）
   - 创建 XmlToJsonConverter 类
   - 配置 XmlMapper 和 ObjectMapper
   - 实现 convert() 方法

6. 实现性能指标记录器（任务 2.6）
   - 创建 ProvenanceMetrics 类
   - 实现 recordApiCall() 方法
   - 配置 Micrometer Timer 和 Counter

**验收标准**:
- ✅ 所有公共组件可以正常编译
- ✅ 配置对象定义完整
- ✅ 网关请求构建器可以正确构建 URL 和 Headers
- ✅ 配置加载器可以按优先级加载配置
- ✅ XML 转 JSON 转换器可以正确解析 XML
- ✅ 性能指标记录器可以正确记录指标

---

### Phase 2: PubMed 数据源实现（任务 3）

**目标**: 实现 PubMed 数据源的完整功能

#### 步骤 3: 实现 PubMed 数据源（任务 3）
**时间估算**: 6-8 小时

1. 定义 PubMed Request 对象（任务 3.1）
   - 创建 ESearchRequest record（14 个参数）
   - 创建 EFetchRequest record（8 个参数）
   - 实现紧凑构造器（校验必需参数）

2. 定义 PubMed Response 对象（任务 3.2）
   - 创建 ESearchResponse record
   - 创建 EFetchResponse record 及嵌套对象（PubmedArticle、MedlineCitation、Article、Journal、Author、Pagination 等）
   - 确保所有字段都有定义

3. 定义 PubMedClient 接口（任务 3.3）
   - 定义 esearch() 方法（带/不带配置）
   - 定义 efetch() 方法（带/不带配置）
   - 添加 Javadoc 注释

4. 实现 PubMedClientImpl（任务 3.4）
   - 注入依赖（EgressGatewayClient、GatewayRequestBuilder、ConfigLoader、XmlToJsonConverter、ProvenanceMetrics）
   - 实现 esearch() 方法（加载配置 → 构建请求 → 调用网关 → 转换响应 → 记录指标）
   - 实现 efetch() 方法（加载配置 → 构建请求 → 调用网关 → 转换响应 → 记录指标）
   - 实现日志记录（使用 @Slf4j）
   - 实现异常处理

**验收标准**:
- ✅ PubMed Request 对象定义完整
- ✅ PubMed Response 对象定义完整
- ✅ PubMedClient 接口定义清晰
- ✅ PubMedClientImpl 可以正常编译
- ✅ 日志记录符合规范（[PROVENANCE][CORE]）

---

### Phase 3: EPMC 数据源实现（任务 4）

**目标**: 实现 EPMC 数据源的完整功能

#### 步骤 4: 实现 EPMC 数据源（任务 4）
**时间估算**: 4-5 小时

1. 定义 EPMC Request 对象（任务 4.1）
   - 创建 SearchRequest record（9 个参数）
   - 实现紧凑构造器（校验必需参数）

2. 定义 EPMC Response 对象（任务 4.2）
   - 创建 SearchResponse record
   - 创建 Result record（15 个字段）
   - 创建 Author record

3. 定义 EPMCClient 接口（任务 4.3）
   - 定义 search() 方法（带/不带配置）
   - 添加 Javadoc 注释

4. 实现 EPMCClientImpl（任务 4.4）
   - 注入依赖（EgressGatewayClient、GatewayRequestBuilder、ConfigLoader、ProvenanceMetrics）
   - 实现 search() 方法（加载配置 → 构建请求 → 调用网关 → 解析响应 → 记录指标）
   - 实现日志记录
   - 实现异常处理

**验收标准**:
- ✅ EPMC Request 对象定义完整
- ✅ EPMC Response 对象定义完整
- ✅ EPMCClient 接口定义清晰
- ✅ EPMCClientImpl 可以正常编译
- ✅ 日志记录符合规范（[PROVENANCE][CORE]）

---

### Phase 4: 自动配置和文档（任务 5-6）

**目标**: 实现 Spring Boot 自动配置和完善文档

#### 步骤 5: 实现自动配置（任务 5）
**时间估算**: 3-4 小时

1. 创建 ProvenanceProperties（任务 5.1）
   - 使用 @ConfigurationProperties(prefix = "patra.provenance")
   - 定义 enabled 属性
   - 定义 PubMedProperties 和 EPMCProperties 嵌套类
   - 定义 HttpConfigProperties 嵌套类

2. 创建 ProvenanceAutoConfiguration（任务 5.2）
   - 使用 @AutoConfiguration 注解
   - 使用 @EnableConfigurationProperties
   - 使用 @ConditionalOnBean(EgressGatewayClient.class)
   - 使用 @ConditionalOnProperty
   - 注册所有 Bean（GatewayRequestBuilder、ConfigLoader、XmlToJsonConverter、ProvenanceMetrics、PubMedClient、EPMCClient）

3. 配置自动装配文件（任务 5.3）
   - 创建 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
   - 添加 ProvenanceAutoConfiguration

**验收标准**:
- ✅ ProvenanceProperties 可以正确加载配置
- ✅ ProvenanceAutoConfiguration 可以正确注册 Bean
- ✅ 自动装配文件配置正确

#### 步骤 6: 配置文档和示例（任务 6）
**时间估算**: 2-3 小时

1. 创建模块 README.md（任务 6.1）
   - 概述模块职责和核心功能
   - 说明依赖添加方式
   - 提供配置示例
   - 提供使用示例（PubMed、EPMC、分页处理）
   - 说明配置优先级
   - 说明性能指标

2. 更新根 pom.xml（任务 6.2）
   - 在 modules 中添加 patra-spring-boot-starter-provenance

3. 更新 patra-parent 依赖管理（任务 6.3）
   - 在 dependencyManagement 中添加 patra-spring-boot-starter-provenance
   - 添加 Jackson Dataformat XML 版本管理

**验收标准**:
- ✅ README.md 文档完整清晰
- ✅ 根 pom.xml 更新正确
- ✅ patra-parent 依赖管理更新正确

---

## 快速检查清单

### 项目骨架检查
- [ ] Maven 模块创建完成
- [ ] 包结构符合设计文档
- [ ] 依赖配置正确

### 公共组件检查
- [ ] 配置对象定义完整（ProvenanceConfig 及嵌套对象）
- [ ] 异常类实现完整（ProvenanceClientException）
- [ ] 网关请求构建器实现完整（GatewayRequestBuilder）
- [ ] 配置加载器实现完整（ConfigLoader）
- [ ] XML 转 JSON 转换器实现完整（XmlToJsonConverter）
- [ ] 性能指标记录器实现完整（ProvenanceMetrics）

### PubMed 数据源检查
- [ ] Request 对象定义完整（ESearchRequest、EFetchRequest）
- [ ] Response 对象定义完整（ESearchResponse、EFetchResponse 及嵌套对象）
- [ ] PubMedClient 接口定义清晰
- [ ] PubMedClientImpl 实现完整
- [ ] 日志记录符合规范

### EPMC 数据源检查
- [ ] Request 对象定义完整（SearchRequest）
- [ ] Response 对象定义完整（SearchResponse、Result、Author）
- [ ] EPMCClient 接口定义清晰
- [ ] EPMCClientImpl 实现完整
- [ ] 日志记录符合规范

### 自动配置检查
- [ ] ProvenanceProperties 配置属性类实现完整
- [ ] ProvenanceAutoConfiguration 自动配置类实现完整
- [ ] 自动装配文件配置正确
- [ ] 所有 Bean 正确注册

### 文档检查
- [ ] 模块 README.md 完整清晰
- [ ] 根 pom.xml 更新正确
- [ ] patra-parent 依赖管理更新正确

---

## 常见问题与解决方案

### Q1: 如何处理 Request 对象的可选参数？
**A**: 使用 record 定义 Request 对象，可选参数使用包装类型（Integer、String 等），允许传递 null。在构建 URL 时，跳过 null 值的参数。

### Q2: 如何实现 URL 构建逻辑？
**A**: 使用反射遍历 Request 对象的字段，将非 null 字段添加到 Query Parameters。可以使用 Hutool 的 `BeanUtil` 或 Java 反射 API。

### Q3: 如何实现 ProvenanceConfigResp → ProvenanceConfig 转换？
**A**: 在 ConfigLoader 中实现转换逻辑，手动映射字段。由于两个对象结构相似，可以使用 MapStruct 或手动转换。

### Q4: 如何处理 XML 响应？
**A**: 使用 Jackson Dataformat XML 的 XmlMapper 解析 XML 字符串为 JsonNode，然后使用 ObjectMapper 转换为目标类型。

### Q5: 如何记录性能指标？
**A**: 使用 Micrometer 的 Timer 和 Counter。在 ProvenanceMetrics 中实现 recordApiCall() 方法，使用 Timer.Sample 记录耗时，使用 Counter 记录成功/失败次数。

### Q6: 如何处理配置优先级？
**A**: 在 ConfigLoader 中按优先级加载配置：
1. 如果调用方传递了配置，直接返回
2. 尝试从数据库加载（通过 patra-registry Feign 客户端）
3. 如果数据库没有，使用本地配置（ProvenanceProperties）

### Q7: 如何实现日志记录？
**A**: 使用 @Slf4j 注解，在关键节点记录日志：
- API 调用开始：INFO 级别
- API 调用结束：INFO 级别（包含耗时）
- API 调用失败：ERROR 级别（包含异常堆栈）
- 详细参数：DEBUG 级别

### Q8: 如何处理异常？
**A**: 在客户端实现中捕获异常，封装为 ProvenanceClientException 并抛出。异常信息包含 provenanceCode、apiName、message 和 cause。

---

## 下一步行动建议

基于当前进度（已完成 Spec Creation），建议按以下顺序进行：

1. **立即开始**: 实施 Phase 1（项目骨架和公共组件）
   - 预计时间：8-11 小时
   - 目标：完成项目结构和公共组件

2. **短期目标**: 实施 Phase 2-3（PubMed 和 EPMC 数据源）
   - 预计时间：10-13 小时
   - 目标：完成两个数据源的实现

3. **中期目标**: 实施 Phase 4（自动配置和文档）
   - 预计时间：5-7 小时
   - 目标：完成自动配置和文档

**总预计时间**: 23-31 小时（约 3-4 天全职工作）

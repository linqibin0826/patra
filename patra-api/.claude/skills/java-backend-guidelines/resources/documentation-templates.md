# 文档模板大全

Java 项目文档标准模板，确保文档一致性和完整性。

## 📚 文档层级结构

```
项目根目录/
├── README.md                          # 项目总览
├── ARCHITECTURE.md                    # 架构决策
├── CONTRIBUTING.md                    # 贡献指南
│
├── patra-{service}/                   # 微服务模块
│   ├── README.md                      # 模块说明
│   │
│   ├── patra-{service}-domain/        # 领域层
│   │   ├── README.md                  # 领域模型说明
│   │   └── src/main/java/.../
│   │       └── package-info.java      # 包级文档（每个包必须有）
│   │
│   ├── patra-{service}-app/           # 应用层
│   │   ├── README.md                  # 用例说明
│   │   └── src/main/java/.../
│   │       └── package-info.java      # 包级文档
│   │
│   └── patra-{service}-adapter/       # 适配器层
│       ├── README.md                  # API 文档
│       └── src/main/java/.../
│           └── package-info.java      # 包级文档
```

## 📝 模板集合

### 1. 模块 README.md 模板

```markdown
# patra-{service} 模块

## 📋 概述
[简要描述模块的职责和在系统中的角色]

## 🏗️ 模块结构
\`\`\`
patra-{service}/
├── -api/         # 外部契约
├── -domain/      # 领域模型
├── -app/         # 应用逻辑
├── -infra/       # 基础设施
├── -adapter/     # 适配器
└── -boot/        # 启动器
\`\`\`

## 🔑 核心概念
- **[概念1]**: [说明]
- **[概念2]**: [说明]

## 🚀 快速开始
### 本地运行
\`\`\`bash
mvn clean install
mvn spring-boot:run
\`\`\`

### 配置说明
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |

## 📡 API 接口
### 主要端点
- `POST /api/v1/[resource]` - 创建资源
- `GET /api/v1/[resource]/{id}` - 获取资源

## 🗃️ 数据库设计
### 核心表
- `t_[entity]` - [说明]

## 🔗 依赖关系
- 依赖: patra-common, patra-registry
- 被依赖: [其他模块]

## 🧪 测试
\`\`\`bash
mvn test                    # 单元测试
mvn verify                  # 集成测试
\`\`\`

## 📚 相关文档
- [架构设计](../ARCHITECTURE.md)
- [API 规范](./patra-{service}-adapter/README.md)

## 👥 维护者
- [团队/负责人]

---
*最后更新: 2024-11-05*
```

### 2. package-info.java 模板

#### 领域层包文档
```java
/**
 * 领域模型包 - [包名]
 *
 * <h2>职责</h2>
 * <p>[描述该包的主要职责和边界]</p>
 *
 * <h2>核心概念</h2>
 * <ul>
 *   <li>{@link Aggregate} - 聚合根，维护业务不变量</li>
 *   <li>{@link Entity} - 实体，具有唯一标识</li>
 *   <li>{@link ValueObject} - 值对象，不可变</li>
 * </ul>
 *
 * <h2>设计决策</h2>
 * <p>[说明重要的设计决策和原因]</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建聚合
 * var aggregate = Aggregate.create(command);
 * aggregate.execute(action);
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>该包为纯 Java，不依赖框架</li>
 *   <li>所有业务规则在此实现</li>
 *   <li>通过事件进行跨聚合通信</li>
 * </ul>
 *
 * @since 1.0.0
 * @author [作者]
 */
package com.patra.{service}.domain.model;
```

#### 应用层包文档
```java
/**
 * 应用层用例包 - [用例名称]
 *
 * <h2>用例说明</h2>
 * <p>[描述该用例的业务目标和流程]</p>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link Orchestrator} - 编排器，协调用例执行</li>
 *   <li>{@link Coordinator} - 协调器，处理特定关注点</li>
 *   <li>{@link Command} - 命令，用例输入</li>
 * </ul>
 *
 * <h2>事务边界</h2>
 * <p>所有编排器方法使用 @Transactional 管理事务</p>
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li>接收命令</li>
 *   <li>验证输入</li>
 *   <li>执行业务逻辑</li>
 *   <li>持久化结果</li>
 *   <li>发布事件</li>
 * </ol>
 *
 * @since 1.0.0
 * @see com.patra.{service}.domain
 */
package com.patra.{service}.app.usecase.{feature};
```

#### 适配器层包文档
```java
/**
 * REST API 适配器包
 *
 * <h2>职责</h2>
 * <p>处理 HTTP 请求，转换为应用层命令</p>
 *
 * <h2>端点列表</h2>
 * <ul>
 *   <li>POST /api/v1/resource - 创建资源</li>
 *   <li>GET /api/v1/resource/{id} - 获取资源</li>
 *   <li>PUT /api/v1/resource/{id} - 更新资源</li>
 *   <li>DELETE /api/v1/resource/{id} - 删除资源</li>
 * </ul>
 *
 * <h2>错误处理</h2>
 * <p>使用 ProblemDetail (RFC 7807) 返回错误</p>
 *
 * <h2>验证</h2>
 * <p>使用 @Valid 进行请求验证</p>
 *
 * @since 1.0.0
 */
package com.patra.{service}.adapter.rest;
```

#### 基础设施层包文档
```java
/**
 * 持久化基础设施包
 *
 * <h2>职责</h2>
 * <p>实现领域端口，提供数据持久化</p>
 *
 * <h2>技术选型</h2>
 * <ul>
 *   <li>MyBatis-Plus - ORM 框架</li>
 *   <li>MapStruct - DO ↔ Domain 转换</li>
 *   <li>MySQL - 数据库</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <p>Repository 模式，隔离领域与数据访问</p>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>DO 对象不能离开基础设施层</li>
 *   <li>所有查询通过 Repository 接口</li>
 *   <li>复杂查询使用 XML Mapper</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.patra.{service}.infra.persistence;
```

### 3. API 文档模板

```markdown
# API 文档 - patra-{service}

## 基础信息
- **基础路径**: `/api/v1`
- **协议**: HTTPS
- **认证**: Bearer Token

## 接口列表

### 1. 创建资源
**端点**: `POST /api/v1/resources`

**请求头**:
- `Content-Type: application/json`
- `Authorization: Bearer {token}`

**请求体**:
\`\`\`json
{
  "name": "资源名称",
  "type": "资源类型",
  "config": {}
}
\`\`\`

**响应**:
- **200 OK**
\`\`\`json
{
  "id": "12345",
  "name": "资源名称",
  "createdAt": "2024-11-05T10:00:00Z"
}
\`\`\`

- **400 Bad Request**
\`\`\`json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "名称不能为空",
  "instance": "/api/v1/resources"
}
\`\`\`

### 2. 获取资源
**端点**: `GET /api/v1/resources/{id}`

[继续其他端点...]

## 错误码
| 状态码 | 说明 | 处理建议 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查请求格式 |
| 401 | 未认证 | 提供有效 token |
| 403 | 无权限 | 检查用户权限 |
| 404 | 资源不存在 | 检查资源 ID |
| 500 | 服务器错误 | 联系管理员 |

## 示例代码

### cURL
\`\`\`bash
curl -X POST https://api.patra.com/api/v1/resources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"name":"测试资源"}'
\`\`\`

### Java
\`\`\`java
var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.patra.com/api/v1/resources"))
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer " + token)
    .POST(BodyPublishers.ofString("{\"name\":\"测试资源\"}"))
    .build();
var response = client.send(request, BodyHandlers.ofString());
\`\`\`
```

### 4. 架构文档模板

```markdown
# 架构决策记录 (ADR)

## ADR-001: 采用六边形架构

### 状态
已采纳

### 背景
需要一个支持业务逻辑与技术实现分离的架构。

### 决策
采用六边形架构（端口与适配器）+ DDD。

### 后果
- ✅ 业务逻辑独立于框架
- ✅ 易于测试
- ✅ 技术可替换
- ⚠️ 初始复杂度较高
- ⚠️ 需要团队培训

### 层级职责
1. **Domain 层**: 纯业务逻辑
2. **Application 层**: 用例编排
3. **Infrastructure 层**: 技术实现
4. **Adapter 层**: 外部接口

---

## ADR-002: 选择 MyBatis-Plus

### 状态
已采纳

[继续其他 ADR...]
```

## 📋 文档检查清单

### 模块文档完整性
```
每个模块必须包含：
□ 模块根目录 README.md
□ 每个子模块 README.md
□ 每个包的 package-info.java
□ API 文档（adapter 模块）
□ 领域模型文档（domain 模块）
```

### package-info.java 必须包含
```
□ 包的职责说明
□ 核心类/接口列表
□ 设计决策说明
□ 使用示例
□ 注意事项
□ @since 标记
□ @author 标记
```

### README.md 必须包含
```
□ 模块概述
□ 目录结构
□ 核心概念
□ 快速开始
□ API 说明（如适用）
□ 配置说明
□ 测试指南
□ 相关文档链接
```

## 🔧 生成工具

### 自动生成 package-info.java
```bash
# 查找缺失 package-info.java 的包
find . -type d -name "java" -exec find {} -type d \
  ! -exec test -e "{}/package-info.java" \; -print \;

# 为包生成基础 package-info.java
echo '/**
 * [包说明]
 *
 * @since 1.0.0
 */
package com.patra.service.package;' > package-info.java
```

### 检查文档完整性
```bash
# 检查所有模块是否有 README.md
find . -name "patra-*" -type d -maxdepth 1 \
  ! -exec test -e "{}/README.md" \; -print

# 统计 package-info.java 覆盖率
total=$(find . -type d -path "*/src/main/java/*" | wc -l)
covered=$(find . -name "package-info.java" | wc -l)
echo "Package documentation coverage: $covered/$total"
```

## 📚 参考标准

- [Java 文档注释规范](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)
- [README 最佳实践](https://www.makeareadme.com/)
- [架构决策记录 (ADR)](https://adr.github.io/)
- [RFC 7807 - Problem Details](https://tools.ietf.org/html/rfc7807)

---

**记住**：好的文档是项目成功的关键，package-info.java 是 Java 项目的标准文档实践！
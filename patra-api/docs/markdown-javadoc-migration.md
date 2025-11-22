# Markdown JavaDoc 迁移方案

## 📋 项目调研结果

### 当前 JavaDoc 使用情况

通过对 Patra 项目的调研，发现以下 JavaDoc 使用模式：

1. **类/接口级别**：使用传统 HTML 标签
   - `<p>` 段落标签
   - `<ul>`, `<li>` 列表标签
   - `<b>`, `<strong>` 加粗标签
   - `<h2>` 标题标签
   - `<pre>`, `{@code}` 代码块

2. **字段级别**：简单行内注释
   ```java
   /** 任务名称 */
   private String taskName;
   ```

3. **方法级别**：详细的参数和异常说明
   - `@param` 参数说明
   - `@return` 返回值说明
   - `@throws` 异常说明
   - `@see` 引用说明
   - `{@link}` 内联链接

4. **文档文件**：.md 文件中包含 JavaDoc 示例代码

## 🎯 迁移目标

将所有传统 HTML 风格的 JavaDoc 迁移到 JDK 25 支持的 Markdown 风格（使用 `///` 注释）。

## 📐 迁移规则

### 1. 类/接口/枚举注释

**传统风格**：
```java
/**
 * MeSH 导入任务聚合根。
 *
 * <p>管理整个 MeSH 数据导入任务的生命周期，包括：
 *
 * <ul>
 *   <li>任务状态转换（PENDING → PROCESSING → SUCCESS/FAILED）
 *   <li>进度追踪（各表的处理进度）
 * </ul>
 *
 * <p><b>领域事件</b>：
 *
 * <ul>
 *   <li>{@link MeshImportStarted} - 任务启动时发布
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
```

**Markdown 风格**：
```java
///
/// MeSH 导入任务聚合根。
///
/// 管理整个 MeSH 数据导入任务的生命周期，包括：
///
/// - 任务状态转换（PENDING → PROCESSING → SUCCESS/FAILED）
/// - 进度追踪（各表的处理进度）
///
/// **领域事件**：
///
/// - {@link MeshImportStarted} - 任务启动时发布
///
/// @author linqibin
/// @since 0.2.0
///
```

### 2. 方法注释

**传统风格**：
```java
/**
 * 开始导入任务。
 *
 * <p>前置条件：任务状态为 PENDING
 *
 * <p>此方法会：</p>
 * <ul>
 *   <li>验证任务状态</li>
 *   <li>更新开始时间</li>
 *   <li>发布启动事件</li>
 * </ul>
 *
 * @param command 导入命令
 * @return 导入结果
 * @throws IllegalStateException 如果状态不是 PENDING
 */
```

**Markdown 风格**：
```java
///
/// 开始导入任务。
///
/// **前置条件**：任务状态为 PENDING
///
/// 此方法会：
/// - 验证任务状态
/// - 更新开始时间
/// - 发布启动事件
///
/// @param command 导入命令
/// @return 导入结果
/// @throws IllegalStateException 如果状态不是 PENDING
///
```

### 3. 字段注释

**传统风格**：
```java
/** 任务名称 */
private String taskName;

/** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
private MeshImportTaskStatus status;
```

**Markdown 风格**（保持不变，因为字段注释通常很简短）：
```java
/** 任务名称 */
private String taskName;

/** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
private MeshImportTaskStatus status;
```

### 4. 转换规则对照表

| HTML 标签 | Markdown 语法 | 示例 |
|-----------|---------------|------|
| `<p>段落</p>` | 空行分段 | `段落` |
| `<ul><li>项目</li></ul>` | `- 项目` | 无序列表 |
| `<ol><li>项目</li></ol>` | `1. 项目` | 有序列表 |
| `<b>粗体</b>` | `**粗体**` | 加粗文本 |
| `<strong>强调</strong>` | `**强调**` | 加粗文本 |
| `<h2>标题</h2>` | `## 标题` | 二级标题 |
| `<h3>标题</h3>` | `### 标题` | 三级标题 |
| `<pre>代码</pre>` | ` ```代码``` ` | 代码块 |
| `{@code 代码}` | `` `代码` `` | 内联代码 |
| `{@link Class}` | `{@link Class}` | 保持不变 |
| `@see Class` | `@see Class` | 保持不变 |

## 🛠️ 迁移策略

### 阶段 1：准备工作
1. 配置 Maven JavaDoc 插件以支持 Markdown
2. 创建迁移脚本/工具
3. 备份现有代码（通过 Git）

### 阶段 2：批量迁移
1. **优先级 1**：Domain 层（核心业务逻辑）
2. **优先级 2**：Application 层（编排器、用例）
3. **优先级 3**：Adapter 层（Controller、Job）
4. **优先级 4**：Infrastructure 层（Repository 实现）
5. **优先级 5**：API 层（DTO、接口定义）

### 阶段 3：文档更新
1. 更新所有 .md 文件中的示例代码
2. 更新 package-info.java 文件
3. 更新项目文档模板

### 阶段 4：验证
1. 运行 `mvn javadoc:javadoc` 确保生成正确
2. IDE 中验证悬停提示是否正常显示
3. 检查 Swagger 文档生成是否正常

## 📝 迁移脚本设计

### 核心转换逻辑

```python
import re

def convert_javadoc_to_markdown(javadoc):
    """
    将传统 JavaDoc 转换为 Markdown 风格
    """
    # 如果已经是 /// 风格，跳过
    if javadoc.strip().startswith('///'):
        return javadoc

    # 替换注释开始和结束
    content = javadoc
    content = re.sub(r'/\*\*', '///', content)
    content = re.sub(r'\*/', '///', content)
    content = re.sub(r'^\s*\*\s?', '/// ', content, flags=re.MULTILINE)

    # HTML 到 Markdown 的转换
    content = re.sub(r'<p>(.*?)</p>', r'\1\n', content, flags=re.DOTALL)
    content = re.sub(r'<ul>\s*', '', content)
    content = re.sub(r'</ul>\s*', '', content)
    content = re.sub(r'<li>(.*?)(?=<li>|</ul>|///)', r'- \1\n', content, flags=re.DOTALL)
    content = re.sub(r'<ol>\s*', '', content)
    content = re.sub(r'</ol>\s*', '', content)
    content = re.sub(r'<b>(.*?)</b>', r'**\1**', content)
    content = re.sub(r'<strong>(.*?)</strong>', r'**\1**', content)
    content = re.sub(r'<h2>(.*?)</h2>', r'## \1', content)
    content = re.sub(r'<h3>(.*?)</h3>', r'### \1', content)
    content = re.sub(r'<pre>(.*?)</pre>', r'```\n\1\n```', content, flags=re.DOTALL)
    content = re.sub(r'\{@code (.*?)\}', r'`\1`', content)

    return content
```

### 文件处理逻辑

```python
def process_java_file(file_path):
    """
    处理单个 Java 文件
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 正则匹配所有 JavaDoc 注释
    pattern = r'/\*\*.*?\*/'

    def replacer(match):
        return convert_javadoc_to_markdown(match.group())

    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
```

## ⚠️ 注意事项

1. **字段注释保持简洁**：单行字段注释不需要转换为 `///` 风格
2. **保留特殊标签**：`{@link}`, `@see`, `@param`, `@return`, `@throws` 等标签保持不变
3. **代码块处理**：注意 ` ``` ` 代码块的正确格式
4. **空行管理**：Markdown 段落需要空行分隔
5. **测试先行**：先在小范围测试，确认无误后再批量执行

## 📊 预期效果

### 迁移前后对比

**可读性提升**：
- ✅ 更简洁的语法
- ✅ 更好的 IDE 支持（JDK 25）
- ✅ GitHub/GitLab 中更好的渲染效果

**工具链兼容**：
- ✅ Maven JavaDoc 插件支持
- ✅ Swagger 文档生成正常
- ✅ IDE 智能提示正常

**维护性改善**：
- ✅ 减少 HTML 标签错误
- ✅ 统一的 Markdown 语法
- ✅ 更容易编写和修改

## 🚀 执行计划

1. **今天**：完成迁移脚本开发
2. **明天**：在 `patra-catalog` 模块试点
3. **本周内**：完成所有模块迁移
4. **下周**：验证和修复问题

## 📌 Maven 配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <source>25</source>
        <doclet>jdk.javadoc.doclet.StandardDoclet</doclet>
        <additionalOptions>
            <additionalOption>--enable-preview</additionalOption>
        </additionalOptions>
        <doclint>all</doclint>
        <failOnWarnings>false</failOnWarnings>
    </configuration>
</plugin>
```
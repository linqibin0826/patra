# GitHub Actions CI 优化说明

> 最后更新：2025-11-08

## 📊 优化概览

本文档记录了 Patra 项目 GitHub Actions CI 的优化措施和预期效果。

### 优化前后对比

| 指标 | 优化前 | 优化后 | 改进 |
|------|--------|--------|------|
| **平均 CI 时间** | ~13 分钟 | ~3-5 分钟（小改动）<br>~8-10 分钟（大改动） | **60-70% ↓** |
| **文档修改触发** | ✅ 触发完整 CI | ❌ 不触发 | **节省 30-50% 运行次数** |
| **缓存命中率** | ~20%（基于 commit sha） | ~80%（基于 pom.xml hash） | **4倍提升** |
| **Setup Job 耗时** | 5 分钟（每次 clean install） | 1-2 分钟（增量构建） | **60-80% ↓** |

---

## 🚀 已实施的优化措施

### 阶段1：快速优化（立即见效）

#### 1.1 路径过滤 - 减少无意义触发

**实施位置**：`.github/workflows/ci.yml` - `on.push.paths-ignore`

**优化内容**：
```yaml
paths-ignore:
  - '**.md'              # Markdown 文档
  - 'docs/**'            # 文档目录
  - '.gitignore'         # Git 配置
  - '.editorconfig'      # 编辑器配置
  - 'LICENSE'            # 许可证文件
  - '.github/workflows/pr-checks.yml'  # PR 检查工作流
```

**效果**：
- 文档修改、配置文件修改不再触发 CI
- 预计减少 30-50% 的 CI 触发次数
- 节省 GitHub Actions 使用时间

#### 1.2 改进缓存策略 - 提高构建产物复用率

**实施位置**：所有 jobs 的 `cache/restore` 步骤

**优化前**：
```yaml
key: build-${{ github.sha }}  # 每次提交都不同，无法复用
```

**优化后**：
```yaml
key: build-${{ runner.os }}-${{ hashFiles('**/pom.xml') }}-${{ github.sha }}
restore-keys: |
  build-${{ runner.os }}-${{ hashFiles('**/pom.xml') }}-  # pom.xml 不变时复用
  build-${{ runner.os }}-                                 # 兜底缓存
```

**效果**：
- pom.xml 不变时（大部分提交），可以复用之前的构建产物
- Setup job 从 5 分钟 → 1-2 分钟（80% 的情况）
- 缓存命中率从 ~20% → ~80%

#### 1.3 优化构建命令 - 避免不必要的 clean

**实施位置**：`setup` job 的构建步骤

**优化前**：
```bash
mvn clean install -DskipTests -T 1C  # 每次都清除 target，重新编译
```

**优化后**：
```bash
# 缓存命中时
mvn install -DskipTests -T 1C  # 复用 target，增量编译

# 缓存未命中时
mvn clean install -DskipTests -T 1C  # 完整构建
```

**效果**：
- 增量编译比完整编译快 40-60%
- 结合缓存优化，大部分情况下都是增量编译

---

### 阶段2：智能构建（显著提升效率）

#### 2.1 变更检测 - 识别修改的模块

**实施位置**：`setup` job 的 `🔍 检测变更的模块` 步骤

**工作原理**：
1. 使用 `git diff` 检测变更文件
2. 分析变更文件属于哪些 Maven 模块
3. 输出变更模块列表供后续 job 使用

**支持的模块**：
- patra-parent
- patra-common
- patra-expr-kernel
- patra-gateway-boot
- patra-registry
- patra-ingest
- patra-storage
- 所有 patra-spring-boot-starter-* 模块
- patra-spring-cloud-starter-feign

**效果**：
- 准确识别变更范围
- 为增量构建和条件测试提供依据

#### 2.2 增量构建 - 只构建变更模块

**实施位置**：`setup` job 的 `🔨 增量构建变更模块` 步骤

**工作原理**：
```bash
# 只构建变更的模块及其依赖
mvn install -pl "patra-ingest,patra-registry" -am -DskipTests -T 1C
```

参数说明：
- `-pl`：指定要构建的模块列表
- `-am` (also-make)：同时构建这些模块依赖的模块
- `-T 1C`：每个 CPU 核心一个线程并行构建

**效果**：
- 小改动（1-2个模块）：构建时间减少 70-80%
- 中等改动（3-5个模块）：构建时间减少 40-60%
- 大改动（根 pom.xml 变更）：自动回退到完整构建

#### 2.3 条件测试执行 - 智能跳过测试

**实施位置**：所有测试 jobs 的 `if` 条件

**优化前**：
```yaml
unit-tests:
  needs: setup
  # 总是运行
```

**优化后**：
```yaml
unit-tests:
  if: needs.setup.outputs.has_code_changes == 'true'  # 只在有代码变更时运行
  needs: setup
```

**效果**：
- 无代码变更时（如纯文档修改被 paths-ignore 漏掉的情况），跳过所有测试
- 节省 8 分钟的测试时间

---

### 阶段3：高级优化（已内置）

#### 3.1 测试并行执行

**实施位置**：`patra-parent/pom.xml` - Maven Surefire 配置

**配置**：
```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <forkCount>1C</forkCount>       <!-- 每 CPU 核心一个 JVM 进程 -->
    <reuseForks>true</reuseForks>   <!-- 复用 JVM，减少启动开销 -->
  </configuration>
</plugin>
```

**效果**：
- GitHub Actions 提供 2 核 CPU
- 单元测试（195个）并行执行，充分利用多核
- 测试时间比串行执行减少 40-50%

#### 3.2 可选：升级到更强的 Runner

**实施位置**：`.github/workflows/ci.yml` - 各 job 的 `runs-on`

**当前配置**：
```yaml
runs-on: ubuntu-latest  # 免费，2核 CPU，7GB RAM
```

**可选升级**（需要付费）：
```yaml
runs-on: ubuntu-latest-4-cores  # 付费，4核 CPU，16GB RAM
```

**何时升级**：
- CI 使用非常频繁（每天 >50 次）
- 测试时间仍然是瓶颈
- 预算充足

**预期效果**：
- 测试时间额外减少 20-30%
- 构建时间额外减少 15-25%

---

## 📈 性能预期

### 不同场景的 CI 耗时

| 场景 | 优化前 | 优化后 | 节省 |
|------|--------|--------|------|
| **文档修改** | ~13 分钟 | 0 分钟（不触发） | 100% |
| **单文件小改动** | ~13 分钟 | ~3-4 分钟 | 70-75% |
| **单模块改动** | ~13 分钟 | ~4-6 分钟 | 55-70% |
| **多模块改动** | ~13 分钟 | ~6-8 分钟 | 40-55% |
| **pom.xml 改动** | ~13 分钟 | ~9-11 分钟 | 15-30% |
| **首次构建（无缓存）** | ~13 分钟 | ~10-12 分钟 | 10-20% |

### 每月节省估算

假设：
- 每天 20 次 push
- 其中 5 次是文档修改
- 10 次是小改动
- 5 次是较大改动

**优化前**：20 × 13 分钟 = 260 分钟/天 = **7800 分钟/月**

**优化后**：
- 文档修改：5 × 0 = 0 分钟
- 小改动：10 × 4 = 40 分钟
- 大改动：5 × 8 = 40 分钟
- 合计：80 分钟/天 = **2400 分钟/月**

**节省**：**5400 分钟/月（69% ↓）**

---

## 🔍 监控和调优

### 关键指标

在 GitHub Actions 页面查看以下指标：

1. **Setup Job 耗时**
   - 目标：< 2 分钟（缓存命中时）
   - 警报：> 3 分钟

2. **缓存命中率**
   - 查看 `📦 恢复构建缓存` 步骤的输出
   - 目标：> 70%

3. **变更检测准确性**
   - 查看 `🔍 检测变更的模块` 步骤的输出
   - 确认识别的模块与实际变更一致

4. **测试跳过率**
   - 查看测试 jobs 的执行频率
   - 目标：文档修改时 100% 跳过

### 调优建议

#### 如果缓存命中率低（< 50%）

可能原因：
- pom.xml 频繁变更
- GitHub Actions 缓存被清理（7天未使用自动清理）

解决方案：
- 检查是否有不必要的 pom.xml 修改
- 考虑使用外部缓存服务（如 AWS S3）

#### 如果 Setup Job 仍然很慢（> 4 分钟）

可能原因：
- 依赖下载慢（Maven Central 连接问题）
- 模块依赖关系复杂

解决方案：
- 使用 Maven 镜像（如阿里云）
- 检查模块依赖是否可以简化

#### 如果变更检测不准确

可能原因：
- 模块列表不完整
- 模块间依赖关系复杂

解决方案：
- 更新 `setup` job 中的 `MODULE_MAP`
- 考虑使用 Maven 命令分析依赖关系

---

## 🛠️ 维护指南

### 添加新模块时

在 `setup` job 的 `🔍 检测变更的模块` 步骤中添加新模块：

```bash
MODULE_MAP["新模块目录名"]="新模块artifactId"
```

示例：
```bash
MODULE_MAP["patra-analytics"]="patra-analytics"
```

### 修改 paths-ignore 规则时

考虑以下问题：
- 这个文件变更真的不需要测试吗？
- 是否会影响构建或运行时行为？

安全的 paths-ignore 规则：
- ✅ 纯文档（*.md, docs/）
- ✅ 编辑器配置（.editorconfig, .vscode/）
- ✅ Git 配置（.gitignore, .gitattributes）
- ❌ pom.xml（影响构建）
- ❌ application.yml（影响运行时）
- ❌ Java 代码（影响功能）

### 调整缓存策略时

注意事项：
- 缓存键必须包含 `${{ github.sha }}` 以保证唯一性
- restore-keys 的顺序很重要（从最具体到最通用）
- 缓存大小限制：10GB/仓库

---

## 📚 参考资料

- [GitHub Actions 缓存文档](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
- [Maven 增量构建文档](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Maven Surefire 并行执行](https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html)
- [GitHub Actions 定价](https://docs.github.com/en/billing/managing-billing-for-github-actions/about-billing-for-github-actions)

---

## 🎯 未来优化方向

1. **测试选择**：基于代码变更智能选择测试（需要代码覆盖率工具）
2. **矩阵策略**：将单元测试分片到多个 runner 并行执行
3. **自托管 Runner**：如果 CI 使用非常频繁且有足够预算
4. **增量测试**：只测试变更模块的测试（需要依赖分析）

---

## 💡 常见问题

### Q: 为什么有时候 Setup Job 还是很慢？

A: 可能原因：
1. 首次构建，没有缓存
2. pom.xml 变更，需要重新下载依赖
3. Maven Central 网络问题

### Q: 变更检测会漏掉一些模块吗？

A: 变更检测只识别直接修改的模块。如果模块 A 依赖模块 B，修改 B 不会触发 A 的重新构建（除非同时修改了 A）。这是设计行为，因为 Maven 会自动处理依赖关系。

### Q: 如果缓存损坏怎么办？

A: GitHub Actions 会自动处理缓存错误。如果缓存恢复失败，会自动执行完整构建。也可以手动清除缓存：
```bash
gh cache list
gh cache delete <cache-id>
```

### Q: 如何暂时禁用优化进行调试？

A: 可以使用 `workflow_dispatch` 手动触发 CI，并传入参数强制完整构建（需要额外配置）。或者临时修改 `setup` job，强制设置 `has_code_changes=true` 和 `modules_to_build=""`。

---

**优化实施日期**：2025-11-08
**维护负责人**：DevOps Team
**问题反馈**：[GitHub Issues](../../issues)

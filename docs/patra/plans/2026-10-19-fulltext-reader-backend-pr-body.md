# PR 描述草稿：v0.11 全文阅读后端（PAP-84 ~ PAP-87）

> 开 PR 时把下面「正文」一节整段贴进去。PR 开成 draft，完工后转 ready。

## 正文

### 做了什么

v0.11 Full Text Reader 的后端部分，全部在 `patra-catalog`：

- **PubMed updatefiles 增量同步（PAP-84）**：新作业 `pubmedUpdateImportJob` 按文件号追 NLM updatefiles。已有文献覆盖更新：内部 ID 不变，PubMed 管的列与子表全部替换；`is_oa`、`citation_count`、`has_full_text`、非 PubMed 标识符、OA 位置、全文记录等别的流程写入的数据保留。`DeleteCitation` 软删除，软删的文献再出现时复活。新增解析 PMC 放开日期。导入范围可配，mini 配 `EXISTING_ONLY`。
- **PMC 全文抓取（PAP-85）**：从 PMC Cloud Service 公开桶选版本（开放获取、优先非手稿），下载 XML、PDF、正文图片（含 webp，读像素宽高）写入 MinIO 桶 `patra-catalog-fulltext`。暂不可用的按放开日期或退避时间再查，抓取失败按次数退避。`ofPmc` 改写为带许可与版本类型，只替换 PMC 那一条 OA 位置。
- **JATS 解析入库（PAP-86）**：JDK DOM 解析 `<body>` / `<back>` 为正文 JSON（JSONB），只做格式转换。覆盖段落里嵌套的块级元素、任意位置的 MathML、`citation-alternatives`、公式图片、TeX 外壳、eLife 的子图。`PARSER_VERSION` 提升后全量重新解析，不访问 PMC。20 篇 CC BY 真实文章做夹具全量测试；MathML 标签全集写在 `mathml-tags.txt`，交给 PAP-88 扩充白名单。
- **门户全文读端点（PAP-87）**：
  - 详情接口新增 `hasFullText`、`hasPdf`、`pdfSizeBytes`，`estimatedReadMin` 改为 `ceil(字数 / 230)`。
  - 新增 `GET /portal/publications/{id}/fulltext`、`/fulltext/pdf`、`/fulltext/figures/{fileName}`。
  - 撤稿文献（发表类型 `D016441`）在读端自动下线。
  - 只有 PDF 时全文接口返回 200、正文为空，署名照常返回。
- 三个 XXL-Job（`pubmedUpdateSyncJob` → `pmcFullTextFetchJob` → `jatsFullTextParseJob`）用父子任务串起来，用 PG advisory lock 防同类任务并发。建任务的步骤见 `docs/patra/runbooks/v0.11-fulltext-runbook.md`。

### 关闭的 Issue

Closes PAP-84
Closes PAP-85
Closes PAP-86
Closes PAP-87

### Done 判定（本地结果）

| 判定 | 本地结果 |
|---|---|
| D7 覆盖更新不冲掉别的流程的数据 | `PublicationSyncRepositoryAdapterIT.should_keep_fields_owned_by_other_flows_when_overwriting` 通过；E2E `PubmedUpdateSyncE2E` 覆盖"覆盖、跳过、删除、重放" |
| D8 后端检查 | `./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest` 结果见下方评审提示 |
| D2 / D3 / D6 | 需在 mini 上实测，合入、部署后按运维手册第 4 节执行 |

### 评审提示

- **构建脚本有改动**：`gradle/libs.versions.toml` 新增 TwelveMonkeys `imageio-webp 3.15.2`，`patra-catalog-infra/build.gradle.kts` 加了 `runtimeOnly`。本地需在 IDEA 点「加载 Gradle 更改」。
- **测试工具改动**：`patra-tool-test` 的 MinIO 测试容器改用 `pgsty/minio`。MinIO 官方已不在 Docker Hub 公开发布镜像，`minio/minio` 拉取被拒。CI 机器要能拉到这个镜像。
- **catalog 关闭了 OSIV**（`spring.jpa.open-in-view: false`）。读端都是 native SQL，adapter 全部集成测试与 E2E 已通过。
- **Nacos**：`patra-infra/nacos/dev/patra-catalog.yaml` 新增 `patra.catalog.pubmed.update-scope: EXISTING_ONLY`，合并后由 CD 推送。
- **XXL-Job**：五条任务在控制台手工建（运维手册第 1 ~ 3 节），子任务 ID 要按顺序回填。
- **V8 迁移**：新建 `cat_pubmed_update_file`、`cat_publication_fulltext`、`cat_publication_fulltext_body` 三张表，不改已有表。

🤖 Generated with [Claude Code](https://claude.com/claude-code)

## 开 PR 前确认

- 设计会话（PAP-83）的设计简报 `patra-portal/docs/design-briefs/v0.11/` 与画板快照 `docs/patra/design/snapshots/` 还没提交。按"文档随同版本第一个代码 PR 提交"的约定，它们应进这个 PR，同时在「关闭的 Issue」里加一行 `Closes PAP-83`。要不要并进来由用户决定。
- PR 标题建议：`feat(catalog): v0.11 全文阅读后端：PubMed 增量同步、PMC 全文抓取与解析、门户全文读端点 (PAP-84 ~ PAP-87)`

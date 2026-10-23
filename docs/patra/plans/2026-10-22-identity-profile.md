# identity 资料与公开主页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户 2026-10-21 定：plan 在当前会话内联执行，不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 identity 补齐资料聚合（简介、链接、专业背景、关注领域、隐私开关）、`/me/profile` 与 `/me/privacy` 读写、按可见性裁剪的公开主页，保存机构与关注领域时经 catalog 内部接口校验并取名称快照。

**Architecture:** 领域层新增资料值对象、`ProfilePolicy`（字段规则，一次报全）与 `CatalogReferencePort`；app 层两个写处理器（资料的写在事务外先调 catalog，再在用户行锁下保存）与扩展后的 `UserQueryService`；infra 改写 `V1`（资料表加列、链接 / 关注领域两张有序子表），`CatalogReferenceAdapter` 经 `patra-catalog-api` 的 HTTP Interface 调 catalog；公开主页的可见性与裁剪全部在读模型 `PublicUserReadModel.viewFor` 里。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Cloud LoadBalancer 5.0.3、Hibernate 7、MapStruct、Lombok、PostgreSQL 17、JUnit 5 + AssertJ + Mockito、Testcontainers、WireMock 3.10。

**Spec:** `docs/patra/specs/2026-10-22-identity-profile-design.md`（下文「spec §n」）。前置：`docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`、`docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md`。

## 开工前的门槛（不满足就停下问用户）

1. 用户已审阅 spec 与本计划，并确认开工。已定（用户 2026-10-22）：保留 `indexable`（Task 5 / 12）；「仅登录用户」按字面，任何带有效会话的人都算（Task 5）；昵称变更不建表、只打日志。
2. `git status` 里除本计划与 spec 两个新文件外没有其他已修改的跟踪文件，且最近一次提交不是几分钟内的 PAP-98 提交（PAP-98 会话在同一检出里工作）。不确定就问。
3. 分支是 `feat/v0.12-user-center-api`；不切分支、不建 worktree、不推送、不开 PR、不动 main。
4. Linear PAP-95 推到 BE。

## Global Constraints

- 提交信息：`type(identity): 中文动词起头 … (PAP-95)`；subject 不以大写字母开头（commitlint），body 每行 ≤ 100 字符；结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。只 `git add` 本任务改的路径，提交前 `git diff --cached --stat` 核对；有别的会话或子代理在写跟踪文件时不提交（pre-commit 会临时 stash）。
- 每个任务提交前：`./gradlew spotlessApply`，spotless 改了文件就 `git add -u <本任务路径>`。
- 方法全部写 `///` Markdown Javadoc；不用全类名；参数 ≤ 4 的 record 用 `of()`，≥ 5 用 `@Builder`；record 的集合字段在紧凑构造器里 `List.copyOf`（null 视为 `List.of()`）。
- 测试方法 snake_case `should_…`；单测在 `src/test/java`，集成测试在 `src/integrationTest/java`，命名 `*Test` / `*IT`。
- 时间一律走注入的 `Clock`；集成测试用 `MutableClock`。
- 字段上限（spec §7.2，release spec 2.1.2）：简介 200 字素 / 800 字节 / 3 行；链接 5 条，标题 30 字素 / 120 字节，URL 300 码点，只 `http` / `https`；职称 50 / 200；科室 100 / 400；机构自由文本 200 / 800；关注领域 10 个。
- 格式：ROR ID `^0[a-z0-9]{8}$`（先去空白转小写）；MeSH UI `^D\d{6}(\d{3})?$`（不改大小写）。
- 隐私默认：`profileVisibility=PUBLIC`、`professionalVisibility=PUBLIC`、`searchIndexing=false`；枚举值 `PUBLIC` / `SIGNED_IN` / `SELF` 与 `PUBLIC` / `SELF`。
- 身份大类：`PHYSICIAN`、`NURSE`、`PHARMACIST`、`TECHNICIAN`、`RESEARCHER`、`STUDENT`、`INDUSTRY`、`OTHER`。
- 新原因码：`TOO_MANY`、`TOO_MANY_LINES`、`DUPLICATE`、`NOT_FOUND`。字段路径：`displayName`、`bio`、`links`、`links[i].url`、`links[i].title`、`occupation`、`jobTitle`、`organization.rorId`、`organization.name`、`department`、`researchInterests`、`researchInterests[i]`、`profileVisibility`、`professionalVisibility`、`searchIndexing`。
- catalog 不可用：503 `IDN-0503`，`traits` 含 `DEP_UNAVAILABLE`，文案「机构与关注领域暂时无法校验，请稍后再试」。
- 新表不得有 `username` / `user_name` 列（R6）。
- 改了 `*.gradle.kts` 后重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`，提醒用户在 IDEA 里点「加载 Gradle 更改」。
- 已知坑（memory 计划常见坑清单）：`@WebMvcTest` 要 `@Import(Controller)`；Mockito 嵌套 stub 先放局部变量；`findByIdForUpdate` 桩用 `thenAnswer` 每次给新实例；派生 `deleteBy…` 排在插入之后，子表删用 `@Modifying @Query` + `flush`；`IdentitySchemaConstraintsIT` 每个用例只做一条失败插入；JDBC 改行后查前 `entityManager.clear()`；真端口用例用 `RestClient` + `JdkClientHttpRequestFactory` + `Redirect.NEVER`（`RestTestClient` 会跟随 301）；断言要用同一个 `MutableClock` 签；gjf 会把源码里的反斜杠 u 转义改成字面字符，不可见字符按码点构造；IT 上下文多时注意 Hikari 池。

## Review Focus

1. **只改简介的保存不该被机构或主题词挡住**：库里已有的机构变成 inactive（catalog 回 404）、catalog 整个不可用时，只改简介的 PUT 仍然成功——因为没变的 ROR ID / UI 不调 catalog。Task 10 的 `should_not_call_catalog_when_affiliation_and_interests_are_unchanged` 与 Task 13 `ProfileFlowIT` 的「只改简介时 WireMock 回 500 也 200」钉住。
2. **重排与删减关注领域、链接**：`[A,B] → [B,A]`、`[A,B,C] → [C]` 保存不撞 `(user_id, position)` / `(user_id, mesh_ui)` 唯一约束。Task 7 的 `should_reorder_children_without_unique_violation`。
3. **大小写与前后空白的输入**：`ROR ID` 大写、`HTTPS://` 大写 scheme、昵称前后全角空格、URL 前后空格——都被规范化后接受；`d003920` 小写 UI 被拒。Task 4 用例。
4. **本人视角**：本人看自己「仅自己」的主页 200 且专业背景完整，邮箱未验证时 `links` 为 `[]`；本人用旧名 301。Task 5 与 Task 13 用例。
5. **存量值撞上收紧后的规则**：库里昵称命中（更严的）拒绝词时，原样带回不报错。Task 4 的 `should_keep_stored_display_name_even_if_rules_tightened`。

---

### Task 1: ProfileTextRules — shared rejected-character rules

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/ProfileTextRules.java`
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/DisplayNamePolicy.java`（`hasRejectedCharacter`、`isRejectedCluster`、`graphemeCount` 搬走，改为调用新类）
- Modify: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy/DisplayNamePolicyTest.java:106`（`graphemeCount` 改调新类）
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy/ProfileTextRulesTest.java`

**Interfaces:**
- Produces: `public final class ProfileTextRules`，静态方法 `boolean hasRejectedCharacter(String normalized, boolean allowLineFeed)`、`int graphemeCount(String text)`、`int utf8Length(String text)`。

- [ ] **Step 1: 写失败的测试** `ProfileTextRulesTest`：
  - `should_reject_line_feed_unless_allowed`：`hasRejectedCharacter("a\nb", false)` 为真、`(…, true)` 为假。
  - `should_still_reject_other_line_breaks_when_line_feed_allowed`：`\r`、U+2028、U+0085 在 `allowLineFeed=true` 时仍为真（按码点构造，不写反斜杠 u）。
  - `should_accept_emoji_zwj_sequences`：家庭 emoji、🏴󠁧󠁢󠁥󠁮󠁧󠁿 为假；U+200B、字母间的 U+200D、U+202E 为真；以 U+0301 开头为真。
  - `should_count_graphemes_and_bytes`：`graphemeCount(家庭 emoji ×6) == 6`、`utf8Length("张三") == 6`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests '*ProfileTextRulesTest'` → 编译失败（类不存在）。
- [ ] **Step 3: 实现**：把 `DisplayNamePolicy` 里逐字素簇的判定原样搬过来，只加一处：`allowLineFeed` 为真时码点 `0x0A` 不算 `CONTROL`。`DisplayNamePolicy.validate` 改调 `ProfileTextRules.hasRejectedCharacter(normalized, false)`、`ProfileTextRules.graphemeCount`、`utf8Length`；删掉 `DisplayNamePolicy.graphemeCount` 与两个私有方法，`DisplayNamePolicyTest:106` 改调 `ProfileTextRules.graphemeCount`。
- [ ] **Step 4: 跑测试确认通过** `./gradlew :patra-api:patra-identity:patra-identity-domain:test` → 全绿（`DisplayNamePolicyTest` 其余用例不改）。
- [ ] **Step 5: 提交** `refactor(identity): 抽出昵称与资料文本共用的拒绝字符规则 (PAP-95)`

### Task 2: Profile value objects and enums

**Files:**
- Create（`domain/model/vo/profile/`）：`Bio.java`、`ProfileLink.java`、`JobTitle.java`、`Department.java`、`RorId.java`、`MeshUi.java`、`Affiliation.java`、`ResearchInterest.java`、`PrivacySettings.java`、`ProfileContent.java`、`ProfileDraft.java`、`LinkDraft.java`
- Create（`domain/model/enums/`）：`Occupation.java`、`ProfileVisibility.java`、`ProfessionalVisibility.java`
- Test（`domain/src/test/.../model/vo/profile/`）：`BioTest.java`、`RorIdTest.java`、`MeshUiTest.java`、`PrivacySettingsTest.java`、`ProfileContentTest.java`

**Interfaces:**
- Produces:
  - `record Bio(String value)`：紧凑构造器只接受 `normalize(value).equals(value)` 且非空；`static String normalize(String raw)`（spec §7.2 简介规范化）。
  - `record ProfileLink(String title, String url)`，`title` 可空；`of(title, url)`。
  - `record JobTitle(String value)`、`record Department(String value)`：只接受 `DisplayName.normalize(value).equals(value)` 且非空。
  - `record RorId(String value)`：`static String normalize(String raw)`（strip + `toLowerCase(Locale.ROOT)`）、`static boolean isValid(String normalized)`、`static RorId of(String raw)`（不合法抛 `IllegalArgumentException`）。
  - `record MeshUi(String value)`：`static boolean isValid(String stripped)`、`static MeshUi of(String raw)`。
  - `sealed interface Affiliation permits Affiliation.Registered, Affiliation.FreeText`，`String name()`；`record Registered(RorId rorId, String name)`、`record FreeText(String name)`，各有 `of`。
  - `record ResearchInterest(MeshUi ui, String name)`，`of`。
  - `enum Occupation { PHYSICIAN, NURSE, PHARMACIST, TECHNICIAN, RESEARCHER, STUDENT, INDUSTRY, OTHER }`；`enum ProfileVisibility { PUBLIC, SIGNED_IN, SELF }`；`enum ProfessionalVisibility { PUBLIC, SELF }`。
  - `record PrivacySettings(ProfileVisibility profile, ProfessionalVisibility professional, boolean searchIndexing)`：`of(...)`、`static PrivacySettings defaults()`。
  - `@Builder record ProfileContent(DisplayName displayName, Bio bio, List<ProfileLink> links, Occupation occupation, JobTitle jobTitle, Affiliation affiliation, Department department, List<ResearchInterest> researchInterests)`：除两个列表外都可空。
  - `@Builder record ProfileDraft(String displayName, String bio, List<LinkDraft> links, String occupation, String jobTitle, String organizationRorId, String organizationName, String department, List<String> researchInterests)`；`record LinkDraft(String title, String url)` + `of`。

- [ ] **Step 1: 写失败的测试**：
  - `BioTest.should_normalize_line_breaks_and_spaces`：`Bio.normalize("  第一行\r\n第二行　　尾  \r第三行\n\n")` 得 `"第一行\n第二行 尾\n第三行"`（全角空格用 `new String(Character.toChars(0x3000))` 构造）；`should_keep_inner_blank_lines`：`"a\n\nb"` 不变；`should_reject_unnormalized_value`：`new Bio(" a")` 抛 `IllegalArgumentException`。
  - `RorIdTest`：`of(" 04JZTAG35 ").value()` 为 `"04jztag35"`；`isValid` 对 `"https://ror.org/04jztag35"`、`"14jztag35"`、`"04jztag3"`、`"04jztag355"` 为假。
  - `MeshUiTest`：`D003920`、`D000072716` 为真；`d003920`、`D0039201`、`C000001` 为假；`of(" D003920 ")` 去空白。
  - `PrivacySettingsTest.should_default_to_public_public_off`。
  - `ProfileContentTest.should_copy_lists_and_treat_null_as_empty`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests '*profile*'` → 编译失败。
- [ ] **Step 3: 实现上面的类型**。`Bio.normalize`：NFC → `\r\n`、`\r` 换成 `\n` → 按 `\n` 切行，每行做 `DisplayName.normalize` 的空格折叠与 strip → 用 `\n` 拼回 → 去掉首尾空行（`strip` 掉首尾的 `\n`）。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2 命令）。
- [ ] **Step 5: 提交** `feat(identity): 加资料的值对象与隐私设置 (PAP-95)`

### Task 3: UserProfile aggregate — edit, privacy, invariants

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/aggregate/UserProfile.java`
- Modify: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/model/aggregate/UserProfileTest.java`（`rename` 用例改成 `edit`）
- 调 `rename` / `restore(...)` 位置参数、编译会断的地方：`patra-identity-infra/src/main/java/.../converter/mapper/UserProfileJpaMapper.java`（`toAggregate` 改用构建器，本任务只填已有字段）、`patra-identity-infra/src/integrationTest/.../persistence/UserProfileRepositoryAdapterIT.java:68`、`.../read/UserReadAdapterIT.java:87`（`rename(x)` 改 `edit(ProfileContent.builder().displayName(x).build())`）

**Interfaces:**
- Consumes: Task 2 类型。
- Produces（`UserProfile`）：
  - 常量 `MAX_LINKS = 5`、`MAX_RESEARCH_INTERESTS = 10`。
  - `static UserProfile create(long userId)`：隐私 `PrivacySettings.defaults()`，两个列表为空。
  - `@Builder(builderMethodName = "restore")` 放在私有全参构造器上：`id, userId, displayName, bio, links, occupation, jobTitle, affiliation, department, researchInterests, privacy, version, createdAt, updatedAt`；构造器守不变式（链接 ≤ 5、关注领域 ≤ 10 且 UI 不重复，违反抛 `IllegalArgumentException`），`privacy` 为 null 取默认、列表 null 取空。
  - `boolean edit(ProfileContent content)`：整体替换（同样守不变式），返回昵称是否变了（`Objects.equals` 新旧 `DisplayName`）。
  - `void changePrivacy(PrivacySettings privacy)`。
  - getter：`Optional<DisplayName> getDisplayName()`、`Optional<Bio> getBio()`、`List<ProfileLink> getLinks()`、`Optional<Occupation> getOccupation()`、`Optional<JobTitle> getJobTitle()`、`Optional<Affiliation> getAffiliation()`、`Optional<Department> getDepartment()`、`List<ResearchInterest> getResearchInterests()`、`PrivacySettings getPrivacy()`；列表返回不可变副本。
  - 删除 `rename(DisplayName)`。

- [ ] **Step 1: 写失败的测试** `UserProfileTest`：`should_create_with_default_privacy_and_empty_lists`；`should_replace_everything_on_edit`；`should_report_display_name_change`（null → 值 真、值 → 同值 假、值 → null 真）；`should_reject_more_than_ten_interests`；`should_reject_duplicate_interest_ui`；`should_reject_sixth_link`；`should_change_privacy`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests '*UserProfileTest'`。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑测试确认通过**：domain 全部单测；`./gradlew :patra-api:patra-identity:patra-identity-infra:compileJava :patra-api:patra-identity:patra-identity-infra:compileIntegrationTestJava`。
- [ ] **Step 5: 提交** `feat(identity): 资料聚合加整体编辑与隐私设置 (PAP-95)`

### Task 4: ProfilePolicy — field rules reported all at once

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/policy/ProfilePolicy.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/profile/ParsedProfile.java`
- Modify: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/model/vo/UserFieldViolations.java`（新字段名、四个原因码、每个 (字段, 原因码) 一个工厂方法，带下标的路径用 `int index` 参数）
- Test: `patra-api/patra-identity/patra-identity-domain/src/test/java/dev/patra/identity/domain/policy/ProfilePolicyTest.java`

**Interfaces:**
- Consumes: Task 1 `ProfileTextRules`；Task 2 全部类型；Task 3 `UserProfile` 的 getter 与 `MAX_LINKS` / `MAX_RESEARCH_INTERESTS`；`DisplayNamePolicy.validate(String)`。
- Produces:
  - `public final class ProfilePolicy`，构造器 `ProfilePolicy(DisplayNamePolicy displayNamePolicy)`；常量 `BIO_MAX_GRAPHEMES = 200`、`BIO_MAX_BYTES = 800`、`BIO_MAX_LINES = 3`、`LINK_TITLE_MAX_GRAPHEMES = 30`、`LINK_TITLE_MAX_BYTES = 120`、`LINK_URL_MAX_CODE_POINTS = 300`、`JOB_TITLE_MAX_GRAPHEMES = 50`、`JOB_TITLE_MAX_BYTES = 200`、`DEPARTMENT_MAX_GRAPHEMES = 100`、`DEPARTMENT_MAX_BYTES = 400`、`ORGANIZATION_NAME_MAX_GRAPHEMES = 200`、`ORGANIZATION_NAME_MAX_BYTES = 800`。
  - `ParsedProfile parse(ProfileDraft draft, UserProfile current)`：抛 `InvalidUserFieldsException`（全部字段错误）。`current` 用于「与库里相同的文本不重新校验」（spec §7.2）：单行文本、简介、链接标题（按下标对照）、昵称规范化后 `equals` 库里的值就直接构造值对象、跳过规则；URL 照常校验。
  - `PrivacySettings parsePrivacy(String profileVisibility, String professionalVisibility, Boolean searchIndexing)`：缺失 `REQUIRED`、取值不认识 `INVALID_FORMAT`，一次报全。
  - `@Builder record ParsedProfile(DisplayName displayName, Bio bio, List<ProfileLink> links, Occupation occupation, JobTitle jobTitle, RorId organizationRorId, String organizationName, Department department, List<MeshUi> researchInterests)`：`organizationName` 只在 `organizationRorId` 为空时有值（自由文本）。

- [ ] **Step 1: 写失败的测试** `ProfilePolicyTest`（默认 `current = UserProfile.create(1L)`），每条断言 `InvalidUserFieldsException.getFieldViolations()` 里的 `(field, code)`：
  - 简介：`should_accept_bio_at_200_graphemes`（`"张".repeat(200)`）、`should_reject_bio_over_200_graphemes`（201 → `bio`/`TOO_LONG`）、`should_reject_bio_over_800_bytes`（ZWJ 家庭 emoji × 45 → 字素 45、字节 > 800 → `TOO_LONG`）、`should_reject_fourth_line`（`"a\nb\nc\nd"` → `bio`/`TOO_MANY_LINES`）、`should_count_crlf_as_one_line_break`（`"a\r\nb\r\nc"` 通过）、`should_clear_blank_bio`（`"  \n "` → `parsed.bio()` 为 null）、`should_reject_control_character_in_bio`（U+0007 → `INVALID_CHARACTER`）。
  - 链接：`should_reject_sixth_link`（`links`/`TOO_MANY`）、`should_accept_url_at_300_code_points`、`should_reject_url_over_300`、`should_reject_non_http_schemes`（`ftp://x.org`、`javascript:alert(1)`、`data:text/html,x` → `links[0].url`/`INVALID_FORMAT`）、`should_accept_upper_case_scheme`（`HTTPS://Example.org/A`）、`should_reject_url_without_host`（`https:///x`）、`should_reject_url_with_userinfo`（`https://patra.dev@evil.example`）、`should_reject_url_with_space`（`https://a.org/x y`）、`should_reject_duplicate_url_case_insensitively`（第二条 `HTTPS://A.ORG/x` 对 `https://a.org/x` → `links[1].url`/`DUPLICATE`）、`should_strip_url`、`should_allow_missing_title`、`should_reject_title_over_30_graphemes`（`links[0].title`/`TOO_LONG`）、`should_reject_empty_url`（`links[0].url`/`REQUIRED`）。
  - 身份大类：八个值各通过（参数化）；`"physician"` → `occupation`/`INVALID_FORMAT`；空串 → null。
  - 职称 50 / 51、科室 100 / 101（`TOO_LONG`）；含 U+200B → `INVALID_CHARACTER`。
  - 机构：`should_fold_ror_id_to_lower_case`（`"04JZTAG35"` → `RorId("04jztag35")`、`organizationName` 为 null）、`should_reject_ror_url`（`organization.rorId`/`INVALID_FORMAT`）、`should_ignore_name_when_ror_id_present`（rorId 合法 + `name` 为 201 个字 → 不报错）、`should_accept_free_text_at_200`、`should_reject_free_text_over_200`（`organization.name`/`TOO_LONG`）、`should_have_no_organization_when_both_blank`。
  - 关注领域：10 个通过、11 个 → `researchInterests`/`TOO_MANY`；`d003920` → `researchInterests[0]`/`INVALID_FORMAT`；重复 → `researchInterests[1]`/`DUPLICATE`；空串 → `REQUIRED`。
  - 昵称：`null` 与 `"  "` → `parsed.displayName()` 为 null；`"张医生"` → `displayName`/`RESERVED`。
  - `should_report_all_fields_at_once`：简介超长 + `links[1].url` 非法 + `researchInterests[3]` 格式错 → 三条，字段路径精确。
  - 沿用（`current` 用 `UserProfile.restore()…build()` 造）：`should_keep_stored_display_name_even_if_rules_tightened`——库里昵称 `"Dr Strange"`（直接 `new DisplayName(...)`，当前规则下是 `RESERVED`），草稿带回不报错，改成 `"Dr Who"` 报 `displayName`/`RESERVED`；`should_keep_stored_bio_verbatim`（库里简介 4 行，带回不报 `TOO_MANY_LINES`）；`should_still_validate_unchanged_url`（库里 URL `ftp://x.org`，带回仍报 `INVALID_FORMAT`）。
  - `parsePrivacy`：三项合法通过；`"PRIVATE"` → `profileVisibility`/`INVALID_FORMAT`；三项全空 → 三条 `REQUIRED`；`professionalVisibility="SIGNED_IN"` → `INVALID_FORMAT`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests '*ProfilePolicyTest'`。
- [ ] **Step 3: 实现 `ProfilePolicy`**。每个字段一个私有方法，往同一个 `List<FieldViolation>` 里加、每字段只加第一条；最后非空就抛。URL 判定：`strip` → 空 `REQUIRED` → `codePointCount > 300` `TOO_LONG` → 含 `Character.isWhitespace` 或 `ISO_CONTROL` 码点、`new URI(s)` 抛 `URISyntaxException`、`scheme` 不是 `http`/`https`（`equalsIgnoreCase`）、`getHost()` 为 null、`getRawUserInfo()` 非 null → `INVALID_FORMAT` → 与前面某条 `equalsIgnoreCase` → `DUPLICATE`。行数 = `\n` 个数 + 1。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2）。
- [ ] **Step 5: 提交** `feat(identity): 加资料字段规则，错误一次报全 (PAP-95)`

### Task 5: Public page read model — audience, visibility, trimmed view

**Files:**
- Modify: `domain/model/read/PublicAudience.java`（加 `SIGNED_IN`）
- Modify: `domain/model/read/PublicUserReadModel.java`
- Create: `domain/model/read/PublicUserView.java`
- Modify: `domain/model/read/PublicUserLookup.java`（`Found` 装 `PublicUserView`）
- Create: `domain/policy/ProfileDisplayPolicy.java`
- Modify（调用方编译）：`patra-identity-app/.../usecase/user/query/UserQueryService.java`、`patra-identity-adapter/.../rest/users/response/PublicUserResponse.java`、`UserQueryServiceTest.java`、`PublicUserControllerIT.java`
- Test: `domain/src/test/.../model/read/PublicUserReadModelTest.java`（新）

（路径前缀 `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/`。）

**Interfaces:**
- Consumes: Task 2 类型。
- Produces:
  - `PublicUserReadModel` `@Builder`：原有 `userId, username, displayName, status, emailVerified` 加 `Instant joinedAt, String bio, List<ProfileLink> links, Occupation occupation, String jobTitle, Affiliation affiliation, String department, List<ResearchInterest> researchInterests, PrivacySettings privacy`；方法 `PublicAudience audience()`、`boolean isVisibleTo(Optional<Long> requesterId)`、`PublicUserView viewFor(Optional<Long> requesterId, ProfileDisplayPolicy display)`（不可见时抛 `IllegalStateException`）。
  - `@Builder record PublicUserView(String username, String displayName, PublicAudience audience, YearMonth joinedMonth, String bio, List<ProfileLink> links, Professional professional, List<ResearchInterest> researchInterests, boolean indexable)`；嵌套 `record Professional(Occupation occupation, String jobTitle, Affiliation affiliation, String department)` + `of`。
  - `record ProfileDisplayPolicy(ZoneId zone)` + `of`；`YearMonth joinedMonth(Instant joinedAt)`。

- [ ] **Step 1: 写失败的测试** `PublicUserReadModelTest`（用一个返回「正常、已验证、全字段、隐私默认」读模型的私有工厂，再 `toBuilder` 改单项——给 `@Builder` 加 `toBuilder = true`）：
  - `audience`：`ACTIVE`+已验证 × 三档可见性 → `PUBLIC` / `SIGNED_IN` / `SELF`；未验证、`PENDING_DELETION`、`BANNED`、`DELETED` 各 → `SELF`。
  - `isVisibleTo` 按 spec §8.3 矩阵逐格（匿名 `Optional.empty()`、别人 `Optional.of(99L)`、本人 `Optional.of(userId)`）：`PUBLIC` 真真真；`SIGNED_IN` 假真真；`SELF` 假假真；未验证 假假真。
  - `viewFor`：`should_hide_professional_from_others_when_self_only`（别人 `professional()` 为 null）、`should_show_professional_to_owner_when_self_only`、`should_hide_links_when_email_unverified_even_for_owner`（本人 `links()` 为空）、`should_be_indexable_only_when_public_and_opted_in`（`PUBLIC`+开 真；`PUBLIC`+关 假；`SIGNED_IN`+开 假；未验证+开 假）、`should_drop_professional_when_all_four_empty`、`should_throw_when_not_visible`。
  - `ProfileDisplayPolicy`：`Instant.parse("2026-10-31T16:30:00Z")` 在 `Asia/Shanghai` 是 `YearMonth.of(2026, 11)`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-domain:test --tests '*PublicUserReadModelTest'`。
- [ ] **Step 3: 实现**。`PublicUserLookup.Found` 改成 `record Found(PublicUserView view)`；同时做让调用方编译通过的最小改动：`UserQueryService` 构造器加 `ProfileDisplayPolicy`，`publicIdentity` 返回 `Found.of(user.viewFor(requester, display))`；`PublicUserResponse.from(PublicUserView)` 先只映射原有四个字段（新字段在 Task 12）；`UserQueryServiceTest`、`PublicUserControllerIT` 里构造 `Found` 的地方相应改。
- [ ] **Step 4: 跑测试确认通过** `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-adapter:compileIntegrationTestJava`。
- [ ] **Step 5: 提交** `feat(identity): 公开主页读模型按可见性与开关裁剪 (PAP-95)`

### Task 6: Schema — profile columns and three child tables in V1

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/migration/V1__init_identity_schema.sql`（`idn_user_profile` 段加列与 CHECK、注释；其后新增 `idn_user_profile_link`、`idn_user_research_interest` 两段，写法照 `idn_user_former_username`：审计列、`set_updated_at` 触发器、英文列注释）
- Modify: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/persistence/IdentitySchemaConstraintsIT.java`

**Interfaces:**
- Produces: 表与列按 spec §6.1 ~ §6.4，约束名照 spec 写。

- [ ] **Step 1: 写失败的测试**：`IdentitySchemaConstraintsIT` 每个用例一条失败插入（先用已有的辅助方法插一个用户和资料行，再 `UPDATE` 或 `INSERT` 违规值，断言 `DataIntegrityViolationException` 且消息含约束名）：`bio` 801 字节、`bio` 首尾空白、`bio` 含 `\r`；`occupation='DOCTOR'`；`job_title` 201 字节；`department` 401 字节；`organization_ror_id='04JZTAG35'`；有 ROR ID 无名称；无 ROR ID 的名称 801 字节；有 ROR ID 的名称 801 字节**通过**（`should_allow_long_snapshot_with_ror_id`）；`profile_visibility='PRIVATE'`；`professional_visibility='SIGNED_IN'`；链接 `position=5`、`url='ftp://x'`、同用户 `HTTPS://A.org` 与 `https://a.org` 第二条撞 `uk_idn_user_profile_link_url`；关注领域 `position=10`、`mesh_ui='d003920'`、同用户同 UI、同用户同 position、`user_id` 不存在撞外键。另加 `should_default_privacy_columns`：只插 `id, user_id` 的资料行读回 `PUBLIC`、`PUBLIC`、`false`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests '*IdentitySchemaConstraintsIT'`。
- [ ] **Step 3: 改写 `V1`**。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2）；再跑整个 `:patra-identity-infra:integrationTest`，`UserProfileRepositoryAdapterIT` 等不受影响（新列都可空或有默认）。
- [ ] **Step 5: 重建本机库**（README 第 8 节的 `DROP/CREATE`），不提交任何东西；**提交** `feat(identity): V1 加资料列、链接与关注领域表 (PAP-95)`

### Task 7: Profile persistence — entities and child-table replace

**Files:**
- Modify: `infra/adapter/persistence/entity/UserProfileEntity.java`（新列）
- Create: `infra/adapter/persistence/entity/UserProfileLinkEntity.java`、`UserResearchInterestEntity.java`
- Create: `infra/adapter/persistence/dao/UserProfileLinkDao.java`、`UserResearchInterestDao.java`
- Modify: `infra/adapter/persistence/converter/mapper/UserProfileJpaMapper.java`
- Modify: `infra/adapter/persistence/UserProfileRepositoryAdapter.java`
- Test: `infra/src/integrationTest/.../persistence/UserProfileRepositoryAdapterIT.java`

（infra 路径前缀 `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/`。）

**Interfaces:**
- Consumes: Task 3 `UserProfile.restore()` 构建器与 getter；Task 6 表。
- Produces:
  - `UserProfileLinkDao`：`List<UserProfileLinkEntity> findByUserIdOrderByPositionAsc(Long userId)`；`@Modifying @Query("delete from UserProfileLinkEntity e where e.userId = :userId") int deleteAllByUserId(@Param("userId") Long userId)`。`UserResearchInterestDao` 同形。
  - `UserProfileJpaMapper`：`UserProfileEntity toEntity(UserProfile)`、`UserProfile toAggregate(UserProfileEntity, List<UserProfileLinkEntity>, List<UserResearchInterestEntity>)`、`List<UserProfileLinkEntity> toLinkEntities(UserProfile)`、`List<UserResearchInterestEntity> toInterestEntities(UserProfile)`（`position` = 列表下标）。`affiliation` 拆成两列：`Registered` → `(rorId, name)`，`FreeText` → `(null, name)`；读回时 `rorId` 非空为 `Registered`。
  - `UserProfileRepository.save` 语义：保存资料行（`saveAndFlush`，乐观锁照旧转 `UserModifiedConcurrentlyException`）→ 两张子表 `deleteAllByUserId` + `flush` → `saveAll` 新行（雪花 ID）+ `flush` → 读回聚合。

- [ ] **Step 1: 写失败的测试** `UserProfileRepositoryAdapterIT`：`should_round_trip_every_field`（全字段，含 `Registered` 机构、标题为 null 的链接、三个关注领域、非默认隐私）；`should_round_trip_free_text_affiliation`；`should_reorder_children_without_unique_violation`（链接 `[A,B]→[B,A]`、关注领域 `[X,Y,Z]→[Z]` 再 `→[Y,X]`，读回顺序正确）；`should_keep_children_of_other_users`；`should_read_default_privacy_from_new_profile`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests '*UserProfileRepositoryAdapterIT'`。
- [ ] **Step 3: 实现**。mapper 的聚合方向写成 `default` 方法（`toAggregate` 走 `UserProfile.restore()`），实体方向用 MapStruct 生成并忽略审计列。
- [ ] **Step 4: 跑测试确认通过**；再跑整个 `:patra-identity-infra:integrationTest`。
- [ ] **Step 5: 提交** `feat(identity): 资料连带链接与关注领域整组存取 (PAP-95)`

### Task 8: UserReadAdapter — public page read model

**Files:**
- Modify: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/read/UserReadAdapter.java`
- Test: `patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/read/UserReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 5 `PublicUserReadModel` 构建器；Task 7 两个 Dao。
- Produces: `findPublicIdentity` / `findPublicIdentityByUserId` 填全部新字段；`joinedAt = user.getCreatedAt()`。

- [ ] **Step 1: 写失败的测试**：`should_read_public_page_fields`（经仓储存一份全字段资料，读回各字段、关注领域顺序、`privacy`）；`should_use_user_created_at_as_joined_at`（JDBC 把 `idn_user.created_at` 改成固定值，`entityManager.clear()` 后读）。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests '*UserReadAdapterIT'`。
- [ ] **Step 3: 实现**：资料行、链接、关注领域三次查询；复用 Task 7 mapper 把子表行转值对象。
- [ ] **Step 4: 跑测试确认通过**。
- [ ] **Step 5: 提交** `feat(identity): 公开读模型读出资料、链接与关注领域 (PAP-95)`

### Task 9: CatalogReferencePort and adapter

**Files:**
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/port/catalog/CatalogReferencePort.java`
- Create: `patra-api/patra-identity/patra-identity-domain/src/main/java/dev/patra/identity/domain/exception/CatalogUnavailableException.java`
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/java/dev/patra/identity/infra/adapter/catalog/CatalogReferenceAdapter.java`
- Modify: `patra-api/patra-identity/patra-identity-infra/build.gradle.kts`：`implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`、`implementation("org.springframework:spring-web")`
- Modify: `patra-infra/cd/module-graph.json`（`./gradlew dumpModuleGraph`）
- Test: `patra-api/patra-identity/patra-identity-infra/src/test/java/dev/patra/identity/infra/adapter/catalog/CatalogReferenceAdapterTest.java`、`patra-api/patra-identity/patra-identity-infra/src/integrationTest/java/dev/patra/identity/infra/adapter/catalog/CatalogReferenceAdapterIT.java`
- Test（domain）：`DomainExceptionTraitsTest` 加一行（若该测试枚举了全部异常的特征）

**Interfaces:**
- Consumes: `dev.patra.catalog.api.endpoint.OrganizationEndpoint#getOrganization(String)`、`MeshEndpoint#getDescriptor(String)`、`OrganizationResp`、`MeshDescriptorResp`；`RemoteCallException#getErrorTraits()`。
- Produces:
  - `interface CatalogReferencePort { Optional<String> organizationName(RorId rorId); Optional<String> meshName(MeshUi ui); }`
  - `final class CatalogUnavailableException extends DomainException`，构造器 `(Throwable cause)`，消息「机构与关注领域暂时无法校验，请稍后再试」，`StandardErrorTrait.DEP_UNAVAILABLE`。
  - `CatalogReferenceAdapter(OrganizationEndpoint, MeshEndpoint)`，`@Component`。

- [ ] **Step 1: 写失败的测试**：
  - `CatalogReferenceAdapterTest`（mock 两个 Endpoint）：`should_prefer_stripped_zh_name`（`zhName=" 北京协和医院 "` → `"北京协和医院"`）；`should_fall_back_to_display_name_when_zh_blank`（`zhName="  "`）；`should_strip_mesh_name`；`should_return_empty_on_remote_not_found`（`RemoteCallException` 带 `Set.of(StandardErrorTrait.NOT_FOUND)`）；`should_wrap_other_remote_errors`（500）；`should_wrap_io_failure`（`new ResourceAccessException("x", new IOException())`）；`should_wrap_missing_instance`（`new IllegalStateException("No instances available for patra-catalog")`）——后三个断言抛 `CatalogUnavailableException` 且 `getCause()` 是原异常。
  - `CatalogReferenceAdapterIT`（`@WireMockTest`，照 `patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/integration/registry/DictionaryResolverAdapterIT.java` 手工建 `RestClient` + `ProblemDetailErrorHandler`，JDK HttpClient 强制 HTTP/1.1，读超时 1s）：200 机构与 MeSH 的映射且请求路径是 `/_internal/organizations/04jztag35`、`/_internal/mesh/descriptors/D003920`；404 ProblemDetail（`application/problem+json`，`traits: ["NOT_FOUND"]`，`code: "CAT-0404"`）→ 空；500 → 不可用；`withFixedDelay(2000)` → 不可用；指向一个关闭的端口 → 不可用。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-infra:test --tests '*CatalogReferenceAdapterTest'`。
- [ ] **Step 3: 实现**：`try` 只包住一次代理调用；`catch (RemoteCallException ex)` → 含 `NOT_FOUND` 返回空，否则包装；`catch (ResourceAccessException | IllegalStateException ex)` → 包装。WARN 日志记 ID、`ex.getClass().getSimpleName()`、下游状态码。改 `build.gradle.kts` 后 `./gradlew dumpModuleGraph`。
- [ ] **Step 4: 跑测试确认通过**：单测 + `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests '*CatalogReferenceAdapterIT'`；`./gradlew :patra-api:patra-identity:patra-identity-infra:check`（R1–R6）。
- [ ] **Step 5: 提交** `feat(identity): 经 catalog 内部接口取机构与主题词名称 (PAP-95)`（含 `module-graph.json`）；提醒用户在 IDEA 里点「加载 Gradle 更改」。

### Task 10: UpdateProfileHandler

**Files:**
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/profile/UpdateProfileCommand.java`
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/profile/UpdateProfileHandler.java`
- Test: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/patra/identity/app/usecase/profile/UpdateProfileHandlerTest.java`

**Interfaces:**
- Consumes: Task 4 `ProfilePolicy.parse`、`ParsedProfile`；Task 3 `UserProfile.edit`；Task 7 `UserProfileRepository`；Task 9 `CatalogReferencePort`、`CatalogUnavailableException`；现有 `UserRepository.findById` / `findByIdForUpdate`、`User.requireActive()`、`UserModifiedConcurrentlyException`、`TransactionOperations`、`Clock`。
- Produces: `record UpdateProfileCommand(ProfileDraft draft) implements Command<UserProfile>` + `of`；`UpdateProfileHandler implements CommandHandler<UpdateProfileCommand, UserProfile>`，按 spec §8.1 的六步，**不**加 `@Transactional`。

- [ ] **Step 1: 写失败的测试**（mock 全部端口，`TransactionOperations` 用 `TransactionOperations.withoutTransaction()`，`findByIdForUpdate` / `findById` 用 `thenAnswer` 每次给新实例）：
  - `should_take_organization_name_from_catalog_and_ignore_client_name`：草稿 `rorId="04jztag35"`、`name="随便写"`，端口返回 `"北京协和医院"` → 保存的聚合 `Registered(04jztag35, 北京协和医院)`。
  - `should_not_call_catalog_when_affiliation_and_interests_are_unchanged`：当前资料已有同一 ROR ID 与两个 UI，草稿只改简介 → `verifyNoInteractions(catalog)`，快照沿用库里的。
  - `should_only_resolve_new_interests`：当前 `[A,B]`，草稿 `[B,C]` → 只调 `meshName(C)`。
  - `should_save_free_text_affiliation_as_given`（规范化后）。
  - `should_report_all_catalog_misses_at_once`：机构与两个 UI 都不存在 → 422 三条 `NOT_FOUND`（`organization.rorId`、`researchInterests[0]`、`researchInterests[2]`），未进事务（`verify(users, never()).findByIdForUpdate(...)`）。
  - `should_fail_with_503_and_stop_calling_on_first_unavailable`：第一个 UI 抛 `CatalogUnavailableException` → 抛出，第二个 UI 没被调，未保存。
  - `should_reject_pending_deletion_before_calling_catalog`：`findById` 返回 `PENDING_DELETION` → `UserPendingDeletionException`，`verifyNoInteractions(catalog)`。
  - `should_recheck_state_under_lock`：`findById` 是 `ACTIVE`、`findByIdForUpdate` 是 `PENDING_DELETION` → 409。
  - `should_fail_when_kept_snapshot_vanished_concurrently`：第一次读资料有机构 R，事务内再读时机构已删、草稿仍带 R → `UserModifiedConcurrentlyException`。
  - `should_report_field_errors_before_any_io`：草稿简介超长 → 422，`verifyNoInteractions(catalog)`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests '*UpdateProfileHandlerTest'`。
- [ ] **Step 3: 实现**。顺序：`currentUserPort.require()` → `users.findById` + `requireActive()`（查不到 401）→ `profiles.findByUserId`（查不到 `IllegalStateException`）→ `profilePolicy.parse(draft, current)` → 解析名称 → `transactions.execute(...)` 内加锁复核、组装 `ProfileContent`、`edit`、`save`；`edit` 返回真时打 spec §8.1 的「昵称已修改」日志。注意 spec §8.1 把字段校验放在读用户之后；`should_report_field_errors_before_any_io` 只要求不调 catalog，读用户在前是允许的。
- [ ] **Step 4: 跑测试确认通过**。
- [ ] **Step 5: 提交** `feat(identity): 加保存资料的处理器，机构与主题词经 catalog 校验 (PAP-95)`

### Task 11: UpdatePrivacyHandler and UserQueryService

**Files:**
- Create: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/profile/UpdatePrivacyCommand.java`、`UpdatePrivacyHandler.java`
- Modify: `patra-api/patra-identity/patra-identity-app/src/main/java/dev/patra/identity/app/usecase/user/query/UserQueryService.java`
- Test: `patra-api/patra-identity/patra-identity-app/src/test/java/dev/patra/identity/app/usecase/profile/UpdatePrivacyHandlerTest.java`、`.../user/query/UserQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 4 `ProfilePolicy.parsePrivacy`；Task 3 `changePrivacy`；Task 5 `viewFor`、`ProfileDisplayPolicy`。
- Produces:
  - `record UpdatePrivacyCommand(String profileVisibility, String professionalVisibility, Boolean searchIndexing) implements Command<PrivacySettings>` + `of`；`UpdatePrivacyHandler`（`@Transactional`）：解析 → `findByIdForUpdate` → `requireActive()` → 读资料 → `changePrivacy` → `save` → 返回 `getPrivacy()`。
  - `UserQueryService`：`UserProfile myProfile()`、`PrivacySettings myPrivacy()`（状态判断抽成私有方法与 `currentAccount()` 共用：`BANNED` / `DELETED` / 不存在 401，`PENDING_DELETION` 照常）；`publicIdentity` 用 `isVisibleTo` + `viewFor(requester, display)`。

- [ ] **Step 1: 写失败的测试**：
  - `UpdatePrivacyHandlerTest`：`should_save_parsed_settings`；`should_report_missing_and_unknown_values_together`（不触达仓储）；`should_reject_pending_deletion`（409）。
  - `UserQueryServiceTest` 加：`myProfile` 对 `PENDING_DELETION` 照常、`BANNED` 401；`publicIdentity`：`SIGNED_IN` 主页对匿名 404、对别人 `Found` 且 `audience == SIGNED_IN`；`SELF` 主页对别人 404、对本人 `Found`；旧名的原主是 `SIGNED_IN` 时匿名 404、登录者 `Moved`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-app:test`。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑测试确认通过**。
- [ ] **Step 5: 提交** `feat(identity): 加隐私设置的读写与本人资料查询 (PAP-95)`

### Task 12: REST — MeProfileController and public page response

**Files:**
- Create（`patra-api/patra-identity/patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/me/`）：`MeProfileController.java`；`request/UpdateProfileRequest.java`（嵌套 `LinkRequest`、`OrganizationRequest`）、`request/UpdatePrivacyRequest.java`；`response/MyProfileResponse.java`（嵌套 `LinkResponse`、`OrganizationResponse`、`ResearchInterestResponse`）、`response/PrivacyResponse.java`
- Modify: `.../adapter/rest/users/response/PublicUserResponse.java`（嵌套 `ProfessionalResponse`；复用 `me/response` 的 `LinkResponse` 等三个记录）
- Test: `patra-api/patra-identity/patra-identity-adapter/src/integrationTest/java/dev/patra/identity/adapter/rest/me/MeProfileControllerIT.java`（新）、`.../rest/users/PublicUserControllerIT.java`

**Interfaces:**
- Consumes: Task 10、11 的命令与查询；Task 5 `PublicUserView`。
- Produces（JSON 形状按 spec §8.1 ~ §8.4）：
  - `GET /me/profile` → `MyProfileResponse`；`PUT /me/profile`（`@RequestBody UpdateProfileRequest`）→ `MyProfileResponse`。`UpdateProfileRequest.toDraft()` 把 `organization` 为 null 映射成两个 null。
  - `GET /me/privacy` → `PrivacyResponse(String profileVisibility, String professionalVisibility, boolean searchIndexing)`；`PUT /me/privacy`（`UpdatePrivacyRequest(String, String, Boolean)`）→ 同。
  - `PublicUserResponse`：`username, displayName, avatarUrl(null), audience, joinedAt("YYYY-MM"，`YearMonth.toString()`), bio, links, professional, researchInterests, indexable`；`professional` 为 null 时输出 `null`。

- [ ] **Step 1: 写失败的测试**：
  - `MeProfileControllerIT`（`@WebMvcTest` + `@Import(MeProfileController.class)` + `@MockitoBean CommandBus / UserQueryService`）：PUT 的请求映射——`organization` 为 null / 只有 `name` / 两者都有时 `ArgumentCaptor` 里的 `ProfileDraft` 对应字段；GET 的响应：`displayName` 为 `null`、空字段 `null`、空列表 `[]`、`organization.name`、`researchInterests[0].name`；422 `errors[0].field == "links[1].url"`；409 `traits` 含 `PENDING_DELETION`；503 `code == "IDN-0503"` 且 `traits` 含 `DEP_UNAVAILABLE`；`PUT /me/privacy` 200 与 422（三条 `REQUIRED`）。
  - `PublicUserControllerIT` 加：全字段输出、`professional` 为 `null`、`joinedAt == "2026-10"`、`indexable`；`should_not_expose_settings_or_private_fields`：响应顶层键集合恰好是 `{username, displayName, avatarUrl, audience, joinedAt, bio, links, professional, researchInterests, indexable}`。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest --tests '*MeProfileControllerIT' --tests '*PublicUserControllerIT'`。
- [ ] **Step 3: 实现**。`MeProfileController` 照 `MeController`（`@Tag(name = "Me")`，`@RequestMapping("/me")`）。
- [ ] **Step 4: 跑测试确认通过**；`./gradlew :patra-api:patra-identity:patra-identity-adapter:integrationTest`。
- [ ] **Step 5: 提交** `feat(identity): 加 /me/profile、/me/privacy 接口，公开主页输出资料 (PAP-95)`

### Task 13: Boot wiring and end-to-end flows

**Files:**
- Create: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/patra/identity/config/CatalogClientConfiguration.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/build.gradle.kts`（`implementation(project(":patra-tool:patra-tool-http-client"))`）、`patra-infra/cd/module-graph.json`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application.yml`（spec §9 的 `catalog` 组）
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/java/dev/patra/identity/config/IdentityConfiguration.java`（`ProfilePolicy profilePolicy(DisplayNamePolicy)`、`ProfileDisplayPolicy profileDisplayPolicy(IdentityProperties)`，后者用 `ZoneId.of(properties.mail().zone())`）
- Modify: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/resources/application-test.yml`（`spring.cloud.discovery.enabled: true`；Nacos 发现与自动注册仍关）
- Create: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/patra/identity/ProfileFlowIT.java`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/integrationTest/java/dev/patra/identity/PublicIdentityFlowIT.java`
- Modify（若需要）：`IdentityFlowITSupport.java`（加 `putProfile`、`putPrivacy`、`publicPage(username, Optional<token>)` 辅助方法）、`IdentityConfigRefreshIT` 等用 mock 装配 bean 的测试（缺新 bean 时补）

**Interfaces:**
- Consumes: 前面全部。
- Produces: `CatalogClientConfiguration`：`@Bean RestClient catalogRestClient(@Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder, RestClientFactory)` → `factory.createRestClient(builder, "catalog", "lb://patra-catalog")`；`@Bean OrganizationEndpoint`、`@Bean MeshEndpoint` 由 `factory.createProxy` 建。条件注解照 `patra-api/patra-catalog/patra-catalog-boot/src/main/java/dev/patra/catalog/config/HttpClientConfiguration.java`。

- [ ] **Step 1: 写失败的测试**：
  - `ProfileFlowIT`（`RANDOM_PORT`；WireMock 用 `@RegisterExtension WireMockExtension`、`http2PlainDisabled(true)`；`@DynamicPropertySource` 设 `spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri`）：注册 → `GET /me/privacy` 是 `PUBLIC`/`PUBLIC`/`false`、`GET /me/profile` 全空 → `PUT` 带 `{rorId:"04jztag35", name:"随便写"}` 与两个 UI，WireMock 返回 `zhName` → 响应与库里都是中文快照 → WireMock 改成对一切回 500，再 `PUT` 只改简介 → 200（Review Focus 1）→ 换成自由文本机构 200 → 不存在的 ROR ID 与 UI（WireMock 404 ProblemDetail）422 两条 `NOT_FOUND` → 新 UI 时 WireMock 500 → 503 且 `GET /me/profile` 不变→ `PUT /me/privacy` 后 `GET` 读回。
  - `PublicIdentityFlowIT` 扩（每个场景先 SQL 写 `email_verified_at`，除非测未验证）：spec §8.3 矩阵每一行、三列（匿名、另一个注册用户带断言、本人带断言）；旧名在原主 `SIGNED_IN` 时匿名 404、登录者 301；专业背景 `SELF` 时别人 `professional` 为 `null`、本人完整；未验证时本人 `links` 为 `[]`，验证后出现；响应顶层键集合同 Task 12。断言用同一个 `MutableClock` 签。
- [ ] **Step 2: 跑测试确认失败** `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests '*ProfileFlowIT' --tests '*PublicIdentityFlowIT'`。
- [ ] **Step 3: 实现装配**；改了 `build.gradle.kts` 跑 `./gradlew dumpModuleGraph`。
- [ ] **Step 4: 跑测试确认通过**；再跑整个 `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`（打开发现后其余流程测试仍绿；`PatraIdentityApplicationIT` 上下文能起）。
- [ ] **Step 5: 提交** `feat(identity): 装配 catalog 客户端，加资料与公开主页的端到端测试 (PAP-95)`；提醒用户在 IDEA 里点「加载 Gradle 更改」。

### Task 14: Gateway — assertion on public user route

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayAuthenticationIT.java`

- [ ] **Step 1: 写测试** `should_forward_public_user_page_with_assertion_when_logged_in`：`GatewayITSessions.issue(sessions, 4231L, 9231L)`，带 `Authorization: Bearer <token>` 访问 `/patra-identity/users/chen-yu` → 200；WireMock 收到的 `Authorization` 以 `Bearer ` 开头、不含原令牌、解码后 `CurrentUser.of(4231L, 9231L, AccountType.USER, ClientType.WEB)`（照 `should_hand_identity_a_signed_assertion_for_the_session_user`）。
- [ ] **Step 2: 跑测试** `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests '*GatewayAuthenticationIT'` → 预期直接通过（行为已存在，本用例是补钉）；若失败，按 `superpowers:systematic-debugging` 查，不改测试凑绿。
- [ ] **Step 3: 提交** `test(gateway): 钉住公开主页路由上登录用户的断言转发 (PAP-95)`

### Task 15: README, full gates, local rebuild, Linear

**Files:**
- Modify: `patra-api/patra-identity/README.md`（spec §13 的六处）
- Modify: `docs/patra/specs/2026-10-22-identity-profile-design.md`（状态改「已实施」，加「实施时实测」一节：catalog 不可用时实际的异常类型、全量门控结果、本机手工验收）
- Modify（不 `git add`）：`docs/patra/release-specs/v0.12-user-center.md` 2.1.2 机构一行按 spec §16 改。它是未跟踪文件，由规划会话随后端 PR 提交；交接里写明改了哪里。

- [ ] **Step 1: 全量门控** `./gradlew spotlessApply && ./gradlew check`，再 `./gradlew :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest` → 全绿（记下用时与用例数）。遇到 `bad class file` / `NoSuchFileException` 先怀疑 IDEA 与 Gradle 抢 `build/classes`，重跑。
- [ ] **Step 2: 本机手工验收**：重建本机 `patra_identity`，本机起 identity（配置读 mini 的 Nacos，catalog 走 mini 上注册的实例）与网关；注册 → `PUT /me/profile`（真实 ROR ID `04jztag35`、MeSH `D006333`）→ 库里快照是「北京协和医院」「Heart Failure」→ 匿名 `GET /patra-identity/users/<名>` 404（未验证）→ SQL 写验证时间后 200 → 三个隐私开关各切一次，匿名 / 登录两种身份结果符合 spec §8.3。结果写进 spec。
- [ ] **Step 3: 写 README 与 spec 收尾**；release spec 2.1.2 机构一行按 spec §16 改。
- [ ] **Step 4: 提交** `docs(identity): 加资料与公开主页的 README 一节，写回实测结果 (PAP-95)`（只含 README 与 spec；release spec 不 add）。
- [ ] **Step 5: Linear**：PAP-95 推 Review，评论附 AC 逐条证据（spec §14 的测试类名 + 门控输出摘要）；PAP-101、PAP-100 各加「来自 PAP-95 的契约」一节（spec §15）；AC 1 / AC 6 措辞按 spec §16 与待拍板结果改。
- [ ] **Step 6: 交接**：在共享笔记目录写 `pap-95-identity-profile-implementation-handover.md`（产物、提交范围、裁决、要在 mini 上做的事：库重建随后端 PR 部署、D2 在 mini 上验收）。

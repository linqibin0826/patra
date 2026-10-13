# Nacos 配置文件目录与推送设计（PAP-75）

> **状态**：已实现（2026-10-13）
> **Issue**：PAP-75 [Infra] 配置文件目录与推送
> **版本**：v0.9 配置中心（`docs/patra/release-specs/v0.9-config-center.md` 决策 B、E）
> **前置**：PAP-71（Nacos 加固，`patra-push` 账号与 `nacos-push.env`）、PAP-74（starter 与签到检查，`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`）

## 1. 目标与约束

**目标**：`dev` namespace 的配置以 git 里 `patra-infra/nacos/dev/` 为准。合入 main 后由 CD 在部署前自动推送；本机手动运行同一个脚本；控制台只在应急时用，控制台改的内容下次推送会被覆盖。

**约束**（来自 Issue、PAP-71 工程 spec 第 5.1、11.1、15 节、PAP-74）：

1. 推送用 `patra-push` 账号，凭据在 `~/.patra/secrets/nacos-push.env`（`NACOS_PUSH_USERNAME`、`NACOS_PUSH_PASSWORD`）。该账号权限：`dev:DEFAULT_GROUP:config/*` 读写，`dev:*:config/*` 只读。
2. 每个写请求显式带 `groupName=DEFAULT_GROUP`；删除按 dataId 和 group 逐个删，不用导入接口和批量删除接口（它们按请求参数里的分组鉴权）。
3. 成败看响应体 `code` 为 0（登录看 HTTP 200 且 `accessToken` 非空）；令牌只放 `Authorization` 请求头，密码和配置正文只走表单请求体，GET/DELETE 的查询串只放不敏感的值。
4. 每份推送的文件带签到行：`patra-common.yaml` 写 `patra.config-source.common`，服务文件写 `patra.config-source.service`，值为文件名；缺了就拒绝推送。
5. 仓库是公开的：配置文件里没有真正的密钥，敏感值只写 `${变量}`；公开的 dev 默认值可以写成 `${变量:默认值}`。
6. 脚本与 CD 日志里不出现任何凭据。

**验收**（Issue AC）：脚本连续运行两次 Nacos 内容不变；修改目录下文件并合入 main 后 CD 自动推送，对比与仓库一致；脚本与 CD 日志不含凭据。

**本机环境事实**（影响设计）：

- 两台机器都没有 YAML 解析工具（无 yq，mini 的 python3 无 PyYAML），有 jq；mini 的 bash 是 3.2。
- CD 的 concurrency 是 `cd-${ref}` 且不取消进行中的运行，排队中的运行会被更新的一次替换，所以推送必须同步整个目录，不能只推本次提交的差异。
- CD 部署 job 跑在 mini 的 runner 上，以用户身份运行，能读到 `~/.patra/secrets/nacos-push.env`；runner 的环境里有代理变量，`nacos_api` 已带 `--noproxy '*'`。
- `detect-changes.sh` 由 CI 与 CD 共用；现有 bash 测试不在 CI 里跑。

## 2. 决定

| # | 问题 | 决定 | 理由 |
|---|------|------|------|
| 1 | 仓库删掉的文件，Nacos 里要不要跟着删 | 删：`dev` / `DEFAULT_GROUP` 下仓库里没有的 dataId 逐个删除 | 只增不删会让 Nacos 与 git 慢慢对不上，导出对比也会有差异 |
| 2 | 防误删 | 目录里必须有 `patra-common.yaml` 且每个文件通过签到预检，才执行；不设删除数量阈值 | 误删可以 git revert 再推一次恢复；阈值多半只会挡住正常的大批删除 |
| 3 | 配置内容检查放在哪 | starter 的 Java 单元测试（SnakeYAML），随 PR 在 CI 里跑；脚本只做 grep 级签到预检 | 真正的 YAML 解析；签到属性名直接引用 starter 常量，改名一起变；不改 mini 环境 |
| 4 | 推送怎么接进 CD | mini 上单独一个 `nacos-push` job，排在 `build-deploy` 之前；推送失败则不部署 | 新代码读新增的键时配置必须已在 Nacos；只改配置时只跑推送；Actions 上两步结果分开 |
| 5 | 回滚与全量 | dispatch 指定单个服务（含填 `image_tag` 的回滚）不推；全量运行（dispatch 不指定服务）推 | 配置回滚走 git revert + 合入；全量顺带纠正控制台改动 |
| 6 | 初始文件 | 7 个只有签到行的骨架文件 | 推送、删除、跳过、CD 接入都能用真实文件跑通；PAP-76、PAP-77 只填内容 |
| 7 | 内容没变的文件 | 跳过，不发布 | 不产生历史记录，不触发服务刷新；「连跑两次不变」能直接从输出看出 |
| 8 | 脚本怎么测 | Java 集成测试用 Testcontainers 起 Nacos，驱动真实脚本 | 脚本的风险集中在真实 API 行为（读回是否逐字节一致、分页、返回码、权限），假实现测不到；集成测试在 CI 里跑 |
| 9 | 对比模式 | 脚本提供 `--check`：只对比不写入 | 验收「导出对比与仓库一致」用它判定；手动推送前先看会改什么 |

## 3. 目录与文件约定

```
patra-infra/nacos/
└── dev/                         # 目录名 = namespace，现在只有 dev
    ├── README.md                # 约定说明（脚本只认 *.yaml，不会推送）
    ├── patra-common.yaml        # 公共配置
    ├── patra-registry.yaml      # 服务配置：{spring.application.name}.yaml
    ├── patra-ingest.yaml
    ├── patra-catalog.yaml
    ├── patra-object-storage.yaml
    ├── patra-identity.yaml
    └── patra-gateway.yaml
```

- **文件名即 dataId**，全部推到 `dev` / `DEFAULT_GROUP`。目录下只放 `*.yaml`，不建子目录。
- 服务文件名与各服务 `application.yml` 的 `spring.application.name` 一致（2026-10-13 已核对：`patra-registry`、`patra-ingest`、`patra-catalog`、`patra-object-storage`、`patra-identity`、`patra-gateway`）。
- **签到行写成一行扁平键**，例如：

  ```yaml
  patra.config-source.service: patra-identity.yaml
  ```

  `patra-common.yaml` 写 `patra.config-source.common: patra-common.yaml`。初始的 7 个文件只有这一行。
- 敏感值只写 `${变量}` 或 `${变量:公开默认值}`；变量的值放在 `~/.patra/secrets/`（本机）或经 env_file 注入（容器），由 PAP-74 的 starter 解析。

## 4. 配置检查：`NacosConfigFilesTest`

位置：`patra-starters/patra-spring-boot-starter-nacos/src/test/java/dev/linqibin/patra/starter/nacos/NacosConfigFilesTest.java`。

对目录下每个 `*.yaml` 检查：

1. 能被 SnakeYAML 解析，且顶层是 map。拒绝重复键（SnakeYAML `setAllowDuplicateKeys(false)`）：Spring 读到重复键时会静默取后一个，写错了很难发现。
2. 签到：属性名引用 `ConfigSourceCheckPostProcessor.COMMON_PROPERTY`（`patra-common.yaml`）或 `SERVICE_PROPERTY`（其他文件），解析出的值等于文件名；文件原文里有完全匹配的一行 `<属性名>: <文件名>`（与脚本预检同一标准）。
3. 敏感键：把 map 压平成点分路径，最后一段去掉 `[n]`、`-`、`_` 并转小写后，以 `password`、`passwd`、`secret`、`token`、`credential`、`credentials`、`privatekey`、`apikey`、`accesskey`、`secretkey` 之一**结尾**时，值必须是以 `${` 开头、以 `}` 结尾的字符串；值为 `null` 或空串也通过（没有泄露任何东西）。数字、布尔等字面量不通过。按「结尾」而不是「包含」判定，`token-ttl`、`max-token-length` 这类普通键才不会被误判。
4. 目录里有 `patra-common.yaml`。
5. 换行符必须是 LF：脚本用 `grep -x` 匹配签到行，行尾的 `\r` 会让匹配失败，而 Java 的 `String.lines()` 不会；两边标准要一致，所以直接拒绝含 `\r` 的文件。

失败信息写明文件名、路径和违反的规则，不打印敏感键的值。

**读到仓库文件且不被缓存跳过**：starter 的 `build.gradle.kts` 给 `test` 任务声明 `inputs.dir(<仓库根>/patra-infra/nacos/dev)`（相对路径敏感），并以系统属性 `patra.repo-root` 传入仓库根，值是相对路径 `../..`（相对于模块目录，也就是 Gradle 测试的工作目录）。不传绝对路径：绝对路径会进构建缓存键，换台机器缓存就失效；相对路径在哪台机器上都一样。`test` 与 `integrationTest` 共用这个属性。CI 开着 `--build-cache`，不声明输入时只改 yaml 会命中缓存，检查等于没跑；`integrationTest` 同样把 `nacos-push.sh`、`nacos-lib.sh` 声明为输入。

## 5. 推送脚本：`patra-infra/scripts/nacos-push.sh`

```
nacos-push.sh [--check] [目录]
```

- **目录**：默认 `<脚本目录>/../nacos/dev`；namespace 取目录名。
- **地址**：`NACOS_ADDR`，默认 `http://127.0.0.1:8848`（mini 上的 runner 用默认值）；本机推 mini 时内联 `NACOS_ADDR=http://100.103.73.27:8848`。
- **账号**：`nacos_load NACOS_PUSH_USERNAME nacos-push.env`、`nacos_load NACOS_PUSH_PASSWORD nacos-push.env`，环境变量优先。
- **依赖**：`nacos-lib.sh`、curl、jq；兼容 bash 3.2。

### 5.1 流程

任何一步失败都立即退出，退出码 1。

1. **本地预检**（不联网）：目录存在；有 `patra-common.yaml`；每个 `*.yaml` 都有一行完全匹配 `^patra\.config-source\.(common|service): <文件名>$` 的签到行，且 `patra-common.yaml` 用 `common`、其他文件用 `service`。不通过时不登录、不推送任何内容，并列出所有不通过的文件。
2. **登录**：`nacos_login`。连不上时报地址；被拒时报账号名。
3. **发布**：按文件名排序逐个处理。
   - `GET /v3/admin/cs/config`（`namespaceId`、`groupName=DEFAULT_GROUP`、`dataId`）读取现有内容，用 `jq --rawfile` 与本地文件逐字节比较（不经 shell 变量，命令替换会吞掉末尾换行）。
   - 相同：记为「未变化」。不存在或不同：`POST /v3/admin/cs/config`，表单带 `namespaceId`、`groupName=DEFAULT_GROUP`、`dataId`、`content`、`type=yaml`，记为「新增」或「更新」。
   - 「不存在」实测为 HTTP 404 加 `code` 20004；其他非成功响应一律视为失败。
4. **删除**：分页调用 `GET /v3/admin/cs/config/list`（`namespaceId`、`groupName=DEFAULT_GROUP`、`pageNo`、`pageSize`、精确匹配），收集全部 dataId，仓库里没有的逐个 `DELETE /v3/admin/cs/config`（`namespaceId`、`groupName=DEFAULT_GROUP`、`dataId`）。删除在全部发布之后执行。
5. **汇总**：最后一行 `新增 a，更新 b，删除 c，未变化 d`。

`--check`：同样的预检、登录、比较和列表，但不写入，每个 dataId 打印将要做的操作。退出码 0 表示一致，2 表示有差异，1 表示出错。

中途失败允许留下部分推送的状态，修好后重跑即收敛。

### 5.2 日志

每个 dataId 一行，只写操作与 dataId；失败时写 HTTP 状态和 `code`。不打印响应体、令牌、密码（沿用 `nacos-lib.sh` 约定）。

### 5.3 接口参数

Nacos 3.2.4 实测（2026-10-13，临时容器），由第 7.2 节集成测试固定；GET 的条件放 URL 查询串（PAP-71 spec 11.1：Spring 不解析 GET 的表单请求体）。

| 调用 | 结果 |
|------|------|
| `GET /v3/admin/cs/config`，配置不存在 | HTTP 404，`code` 20004 |
| `GET /v3/admin/cs/config`，存在 | HTTP 200，`code` 0，正文在 `.data.content` |
| `POST /v3/admin/cs/config`，表单 `content@文件`（curl `--data-urlencode name@file`） | 读回与文件逐字节一致（中文、`${X:1}`、末尾换行都保留） |
| `GET /v3/admin/cs/config/list`，`namespaceId=dev`、`groupName=DEFAULT_GROUP`、`dataId=`、`pageNo`、`pageSize`、`search=accurate` | `.data.pageItems[].dataId`、`.data.pagesAvailable`；不同分组不会混进来 |
| `GET /v3/admin/cs/history/list`，`namespaceId`、`groupName`、`dataId`、`pageNo`、`pageSize` | `.data.totalCount` 是历史条数 |
| `DELETE /v3/admin/cs/config`，存在或不存在 | 都是 HTTP 200，`code` 0 |
| 推送同款权限（`dev:DEFAULT_GROUP:config/*` rw + `dev:*:config/*` r） | 读、列、写、删、历史都通过；写 `OTHER` 分组 HTTP 403 |
| 登录密码错误 | HTTP 403，无 `code`；地址不通时 `nacos_api` 给 HTTP 000 |

## 6. 路由与 CD

### 6.1 `module-graph.json` 补全

`module-graph.json` 里 nacos starter 只登记了 `["check"]`：它在 a144fc67c 生成，之后才加 `src/integrationTest`。CI 的 preflight 会因模块图陈旧失败，PAP-74 与本 Issue 的集成测试也不会在 CI 里跑。本 Issue 重新运行 `./gradlew dumpModuleGraph` 并提交。

### 6.2 `detect-changes.sh`

- 输出新增 `nacos_changed`（bool），写法同 `LEARN_CHANGED`：全局变量初始化为 `false`，`emit` 参数个数不变。
- `classify` 新增两条规则，放在 `patra-infra/scripts/*` 规则与 `*.md` 文档规则之前：

| 路径 | `nacos_changed` | 后端单元 | 作用 |
|------|-----------------|---------|------|
| `patra-infra/nacos/*/*.yaml` | true | `foundation` | CI 跑配置检查；CD 推送 |
| `patra-infra/scripts/nacos-push.sh`、`patra-infra/scripts/nacos-lib.sh` | true | `foundation` | CI 跑推送集成测试；合入后在 mini 实推一次 |

- `patra-infra/nacos/` 下的 `*.md` 仍按文档处理。
- 全量（`emit_full` 与 `classify` 里的 `full` 分支）时 `nacos_changed=true`；`dispatch` 指定单个服务时为 `false`。
- `foundation` 不可部署，CD 去掉它后服务列表为空，`build-deploy` 自然跳过。

### 6.3 `cd.yml`

- `on.push.paths` 增加 `patra-infra/nacos/**`、`patra-infra/scripts/nacos-push.sh`、`patra-infra/scripts/nacos-lib.sh`（`!**/*.md` 继续排除 README）。
- `detect-changes` job 增加输出 `nacos_changed`（`jq -r '.nacos_changed'`）。
- 新增 job：

  ```yaml
  nacos-push:
    name: nacos-push
    needs: detect-changes
    if: needs.detect-changes.outputs.nacos_changed == 'true'
    runs-on: [self-hosted, macmini]
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false
      - name: 推送 Nacos 配置
        run: bash patra-infra/scripts/nacos-push.sh
  ```

  凭据从 runner 用户 HOME 下的 `nacos-push.env` 读取，不经 GitHub secrets。

- `build-deploy`：

  ```yaml
  needs: [detect-changes, nacos-push]
  if: >-
    !cancelled() && needs.detect-changes.result == 'success'
    && needs.detect-changes.outputs.services != '[]'
    && (needs.nacos-push.result == 'success' || needs.nacos-push.result == 'skipped')
  ```

  必须加 `!cancelled()`：GitHub 默认在依赖 job 被跳过时也跳过下游，不加的话只改代码的提交不会部署。

### 6.4 `ci.yml` 与 mini

- `ci.yml` 不改：yaml 与推送脚本的改动经路由进入 `foundation`，matrix 跑 starter 的 `check` 与 `integrationTest`。
- mini 不改：`patra-push` 账号与 `nacos-push.env` 已由 PAP-71 建好。

## 7. 测试

### 7.1 `NacosConfigFilesTest`（单元测试）

规则用例用 `@TempDir` 造目录，每条一个用例：

- 解析失败 → 不通过
- 重复键 → 不通过
- 顶层不是 map → 不通过
- 含 CR（CRLF 换行）→ 不通过
- 签到值与文件名不一致 → 不通过
- 签到写成嵌套形式（解析值正确但没有扁平行）→ 不通过
- `patra-common.yaml` 用了 `service` 属性 → 不通过
- 敏感键写字面量 → 不通过
- 敏感键写 `${VAR:默认值}` → 通过
- 敏感键写数字字面量 → 不通过
- 敏感键为空值 → 通过
- `token-ttl` 这类不以敏感词结尾的键 → 不算敏感键
- 嵌套路径下的敏感键（如 `spring.datasource.password`）写字面量 → 不通过
- 缺 `patra-common.yaml` → 不通过
- 仓库真实目录 `patra-infra/nacos/dev` → 通过（CI 里挡住坏配置的那一道）

### 7.2 `NacosPushScriptIT`（集成测试）

位置：starter 的 `src/integrationTest`，复用 `NacosServerFixture`。fixture 新增一个方法，按 `patra-push` 同款权限建账号；脚本用这个账号运行，用 `ProcessBuilder` 执行 `bash <仓库根>/patra-infra/scripts/nacos-push.sh`，环境变量传 `NACOS_ADDR`、`NACOS_PUSH_USERNAME`、`NACOS_PUSH_PASSWORD`，`NACOS_SECRETS_DIR` 指向空的临时目录（不读本机真实密钥）。配置目录用 `@TempDir` 下名为 `dev` 的子目录。

用例（按 TDD 逐个加）：

1. 首次推送：全部新增，Nacos 内容与文件逐字节一致。
2. 再跑一次：全部未变化，每个 dataId 的历史条数不变（`GET /v3/admin/cs/history/list`）。
3. 改一个文件：只更新这一个。
4. 删一个本地文件：Nacos 里对应 dataId 被删；预先放在其他分组的配置不受影响。
5. 远端超过一页（预置 105 个多余 dataId）：全部删干净。
6. 有文件缺签到行：退出码非 0，Nacos 里没有任何写入。
7. 缺 `patra-common.yaml`：退出码非 0，没有写入。
8. `--check`：一致时退出码 0；有差异时退出码 2 且没有写入。
9. 密码错误：退出码非 0，输出里没有密码和令牌。
10. 地址连不上：报「连不上 Nacos：<地址>」，退出码 1，不误报「登录被拒」。

### 7.3 `detect-changes.test.sh`

新增场景：只改 yaml（`nacos_changed=true`、`backend_units=["foundation"]`、`full_run=false`）；只改 `patra-infra/nacos/dev/README.md`（`docs_only=true`、`nacos_changed=false`）；改推送脚本（`nacos_changed=true`、`["foundation"]`）；改 `nacos-init.sh`（`nacos_changed=false`、`[]`）；yaml 与服务代码一起改（两者都有）；全量（`nacos_changed=true`）；纯服务代码（`nacos_changed=false`）。

## 8. 文档

- `patra-infra/nacos/dev/README.md`：目录与命名约定、签到行写法、密钥规则；本机推送命令（先 `--check` 再推）；控制台只应急，改动会被下次推送覆盖；配置回滚用 git revert + 合入。
- `patra-infra/CLAUDE.md`：补推送脚本与 CD 推送说明。
- `nacos-lib.sh` 头注释：使用方加上 `nacos-push.sh`。
- 本文档在实现后把状态改为「已实现」。

## 9. 验收

开 PR 之前，在本地：

1. `./gradlew :patra-starters:patra-spring-boot-starter-nacos:check :patra-starters:patra-spring-boot-starter-nacos:integrationTest` 与 `bash patra-infra/cd/detect-changes.test.sh` 全部通过。
2. 对 mini（先征得用户同意）：`--check`（预期有差异，退出码 2）→ 推送（预期新增 7）→ 再推（预期未变化 7）→ `--check`（预期退出码 0）；核对输出无凭据。

合入后：Actions 里 `nacos-push` job 成功，日志无凭据；本机对 mini 跑 `--check` 退出码 0。

## 10. 不做的事

- 不推密钥：密钥不进 Nacos（决策 E）。
- 不管理 `DEFAULT_GROUP` 以外的分组，不管理 `public` 等其他 namespace。
- 不做推送前的语义校验（例如某个键是否被代码读取），只做第 4 节的格式与安全检查。
- 不迁移服务配置内容：由 PAP-76、PAP-77 完成。

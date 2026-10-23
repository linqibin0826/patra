# patra-portal

Patra C 端前端 —— 医学出版物发现与浏览门户。

- **技术栈**：Next.js 16 App Router · React 19 · TypeScript 5/6 strict · Tailwind v4 · shadcn/ui · TanStack Query · Zustand · React Hook Form + Zod
- **工具链**：pnpm · Biome · Vitest + Testing Library · Playwright
- **Node**：24 LTS（由 `.nvmrc` / `.tool-versions` 锁定）

## 快速开始

```bash
pnpm install
cp .env.example .env.local   # 网关地址、会话 Cookie 的 Secure 开关，按需修改
pnpm dev          # http://localhost:4000
```

## 命令

| 命令 | 作用 |
|---|---|
| `pnpm dev` | 开发服务器（:4000） |
| `pnpm build` | 生产构建 |
| `pnpm start` | 运行生产构建产物 |
| `pnpm test` | `vitest run`（CI 模式） |
| `pnpm test:watch` | `vitest`（watch 模式） |
| `pnpm test:e2e` | Playwright 端到端测试（复用已在运行的 dev server，没有则自动启动；连网关的用例要内联环境变量，见下文「用户中心」） |
| `pnpm lint` | `biome check .` |
| `pnpm format` | `biome format --write .` |
| `pnpm typecheck` | `next typegen && tsc --noEmit`（先生成 Next 类型，再做类型检查） |

## 目录结构

```
src/
├── app/                        # App Router 路由
│   ├── layout.tsx              # 根布局：字体、Providers
│   ├── page.tsx                # / 首页
│   ├── papers/(list)/          # /papers 文献检索
│   ├── papers/[id]/            # /papers/:id 文献详情（含全文阅读：全文区块与目录在 Suspense 里后到）
│   ├── journals/(list)/        # /journals 期刊浏览
│   ├── journals/[id]/          # /journals/:id 期刊详情
│   ├── login/                  # /login 登录页（忘记密码入口、重设后的成功提示）
│   ├── register/               # /register 注册页（邮箱、用户名、密码）
│   ├── forgot-password/        # /forgot-password 找回密码
│   ├── verify-email/ · reset-password/ · confirm-email-change/ · revert-email-change/   # 四个邮件落地页（路径即 identity 配置 patra.identity.mail.links 的默认值）
│   ├── account/deletion-pending/   # 取消注销页（冷静期账号的唯一出口）
│   ├── settings/               # /settings/* 设置页：个人资料、账号、安全、隐私
│   ├── u/[username]/           # /u/:username 公开主页（旧用户名临时跳到新名，没有 loading，404 是真 404）
│   ├── missing/                # 详情不存在时由 proxy 改写到这里，返回真正的 404
│   ├── api/                    # health、期刊 / 机构 / MeSH 联想、auth/me（当前用户）、me/sessions（登录设备）、头像上传与读取、全文 PDF 与正文图片的流式转发
│   ├── error.tsx               # 路由错误边界
│   ├── not-found.tsx           # 全局 404
│   └── globals.css             # Tailwind v4 @theme 映射
├── proxy.ts                    # 请求拦截层：直接打开详情页时先向后端确认记录存在；已登录的人打开登录页、注册页时送回 next；冷静期账号整页打开任何页面都去取消注销页
├── components/
│   ├── auth/                   # 专注布局外壳、登录 / 注册 / 找回密码表单
│   ├── landing/                # 邮件落地页的共用状态机与四个页面
│   ├── reauth/                 # 再认证对话框
│   ├── account/                # 取消注销页的操作区
│   ├── settings/               # 设置页外壳、个人资料（头像裁剪、机构与关注领域选择器）、账号、安全、隐私
│   ├── profile/                # 公开主页的头部、正文与本人视角提示条
│   ├── user/                   # 头像（图片 / 默认头像）
│   ├── portal/                 # 业务组件（按页面 / 功能分目录）
│   └── ui/                     # shadcn/ui 原语
├── lib/
│   ├── gateway.ts              # 统一网关客户端：全仓库只有这里读网关地址、向网关发请求
│   ├── client-origin.ts        # 浏览器来源（IP、User-Agent），转发给 identity
│   ├── mathml.ts               # 全文公式：MathML 白名单解析（mfenced 改写成 mrow + mo）
│   ├── auth/                   # 会话与设备标识 Cookie、当前用户、注册 / 登录 / 登出的 Server Action，next 参数、表单校验与文案
│   ├── identity/               # 用户中心：资料、隐私、公开主页、头像、账号、安全、再认证、邮件令牌的取数与 Server Action，错误解析
│   ├── reauth/                 # useReauth：需要再认证的操作的统一包装
│   ├── portal-api/             # 服务端向网关取数、解析检索参数
│   └── …                       # 工具函数、TanStack Query 工厂与 queryKey
├── data/                       # 静态内容（示例检索、主题等）
├── providers/                  # 'use client' Provider 包装
├── store/                      # Zustand stores
├── styles/tokens.css           # 设计 token
└── types/                      # 共享类型与 ambient .d.ts

tests/                          # Vitest 单测（含 design-system/ 守护测试）与 e2e/
docs/                           # 设计简报，以及项目初始化时的 spec 与 plan
server/strip-forwarded-for.mjs  # 生产入口的预加载模块（见下文「浏览器来源与设备标识」）
```

## 浏览器来源与设备标识

门户是网关唯一信任的代理（release spec v0.12 决策 I）：代表浏览器请求 identity 时，`gatewayFetch` 的 `client` 参数写两个头——`X-Forwarded-For`（一个 IP 字面量）与 `User-Agent`（浏览器原值），其余转发头一概不发。catalog 的公开读不带这两个头。

- **浏览器自带的 `X-Forwarded-For` 必须丢掉**：Next 对入站请求只在这个头缺失时才用连接地址补上，浏览器带了就原样保留。生产镜像用 `node --import ./strip-forwarded-for.mjs server.js` 启动，预加载模块在 Next 处理请求之前删掉它；`next dev` 不装（本机门户不在网关的受信名单里）。
- **设备标识 Cookie**：`patra_device`（`Secure` 开时 `__Host-patra_device`），32 字节随机数的 base64url，`HttpOnly`、400 天；只在登录、注册时种下并放进请求体 `deviceId`，格式不对就换新的，登出不清。identity 用它判断新设备、发新设备提醒。

## 用户中心

- **再认证**：改邮箱、踢设备、注销要求 10 分钟内输过密码。这几个 Server Action 把 403 `REAUTH_REQUIRED` 收敛成 `reason: "reauth-required"`，客户端用 `useReauth().withReauth(() => action(...))` 调用：拿到它就弹再认证对话框（挂在设置页 layout），验证成功后用同样的参数重试一次，取消则返回 `cancelled`、什么都不显示。
- **冷静期跳转**：冷静期账号打开带导航栏的页面都去 `/account/deletion-pending?next=…`。整页打开由 proxy 拦（只查带会话 Cookie 的 GET 文档请求，3 秒超时、出错放行，已登录用户每次整页打开多一次 `/auth/me`），站内跳转由导航栏的 `PendingDeletionRedirect` 兜底，设置页的 `requireUser` 也会拦。专注布局的页面（登录、注册、找回密码、四个落地页、取消注销页）不拦。proxy 的 matcher 因此覆盖全部页面（不含 `/api/`、`/_next/`、静态文件）。
- **邮件落地页**：令牌只在地址片段里，页面读完立即 `replaceState` 抹掉；打开页面不发请求，点按钮才 POST。
- **端到端冒烟**：`tests/e2e/user-center.spec.ts` 走注册、验证邮箱、资料与主页、改用户名、改密码、踢设备、找回密码、改邮箱、注销与取消，邮件链接从 Mailpit 取。缺任一环境变量就跳过：

  ```bash
  PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025 pnpm test:e2e tests/e2e/user-center.spec.ts --workers=1
  ```

## 设计文档

- spec：`docs/patra/specs/2026-05-18-patra-portal-init-design.html`
- plan：`docs/patra/plans/2026-05-18-patra-portal-init.html`

## CI

由仓库统一的 `.github/workflows/ci.yml` 驱动。portal 有改动时运行其中的 `portal` job，依次执行：

| 步骤 | 命令 | 说明 |
|-----|------|------|
| Lint | `pnpm lint` | Biome 静态检查 |
| 类型检查 | `pnpm typecheck` | `next typegen && tsc --noEmit` |
| 单元测试 | `pnpm test` | Vitest |
| 构建 | `pnpm build` | Next.js standalone 生产构建 |
| E2E | `pnpm test:e2e` | Playwright；PR 上失败不阻塞，夜间任务里阻塞 |

main 分支保护唯一的必需检查是 `required-check`，它汇总各 job 的结果（没有触发的 job 视为通过）。

## 提交规范

- git 钩子由仓库根目录的 pre-commit 框架管理（`.pre-commit-config.yaml`）：通用文件检查、gitleaks 密钥扫描、commitlint 校验提交信息
- 提交时不会自动运行 Biome 和类型检查，提交前自己执行 `pnpm lint && pnpm typecheck`；CI 会把关
- commit message 使用中文，遵循 conventional commits 前缀（feat / fix / docs / refactor / test / chore / perf / style / ci / build）

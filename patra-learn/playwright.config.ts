// patra-learn/playwright.config.ts
import { defineConfig } from "@playwright/test";

// 本机 QQ 客户端占用 127.0.0.1:4001（仅 IPv4），Playwright 的探活/页面请求会打到 QQ；
// 实测 [::1] 方案也被 Playwright 内部探活栈连到 IPv4 端（socket hang up）。
// 故 e2e 独占端口 4010——仅测试基座换口，生产端口 4001 不变。
// dev 服务器用 -H 绑定 127.0.0.1：Next 16 默认拦截非启动主机名的来源访问 dev 资源（含 HMR），
// 按默认 localhost 起而用 127.0.0.1 访问时页面不会 hydrate。
export default defineConfig({
  testDir: "tests",
  use: { baseURL: "http://127.0.0.1:4010" },
  webServer: {
    command: "pnpm exec next dev -H 127.0.0.1 -p 4010",
    url: "http://127.0.0.1:4010/api/health",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});

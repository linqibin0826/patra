// patra-learn/playwright.config.ts
import { defineConfig } from "@playwright/test";

// 冒烟跑在本地生产构建上（先 next build，再 next start）：读者进度只在水合后的客户端生效，
// dev 模式的按需编译会让「刷新后仍在」这类断言时快时慢。
// 本机 QQ 客户端占用 127.0.0.1:4001（仅 IPv4），Playwright 的探活/页面请求会打到 QQ；
// 实测 [::1] 方案也被 Playwright 内部探活栈连到 IPv4 端（socket hang up）。
// 故 e2e 独占端口 4010——仅测试基座换口，生产端口 4001 不变。
// next start 会提示 output: standalone 下应改用 server.js，但照常能服务构建产物；容器里仍跑 server.js。
export default defineConfig({
  testDir: "tests",
  use: { baseURL: "http://127.0.0.1:4010" },
  webServer: {
    command: "pnpm build && pnpm exec next start -H 127.0.0.1 -p 4010",
    url: "http://127.0.0.1:4010/api/health",
    reuseExistingServer: !process.env.CI,
    timeout: 240_000,
  },
});

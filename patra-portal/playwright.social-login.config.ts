import { defineConfig, devices } from "@playwright/test";

const PORTAL_PORT = 4030;
const GATEWAY_PORT = 4031;

/// 第三方登录的端到端测试（决策 H）：门户连模拟网关和模拟平台，不连真实平台，也不用 mini。门户是真实运行的，Cookie、
/// 跳转、`state` 核对都走真路径。共用一份模拟状态，所以串行。默认的 `test:e2e` 不跑这里（`testIgnore`）。
export default defineConfig({
  testDir: "tests/e2e/social-login",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  reporter: "list",
  use: { baseURL: `http://localhost:${PORTAL_PORT}`, trace: "on-first-retry" },
  webServer: [
    {
      command: "node tests/e2e/social-login/mock-gateway.mjs",
      port: GATEWAY_PORT,
      reuseExistingServer: false,
      env: {
        MOCK_GATEWAY_PORT: String(GATEWAY_PORT),
        MOCK_PORTAL_ORIGIN: `http://localhost:${PORTAL_PORT}`,
      },
    },
    {
      command: `pnpm exec next dev --webpack -p ${PORTAL_PORT}`,
      port: PORTAL_PORT,
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        PATRA_GATEWAY_BASE_URL: `http://localhost:${GATEWAY_PORT}`,
        PATRA_SESSION_COOKIE_SECURE: "false",
        PATRA_OAUTH_HOST: `localhost:${PORTAL_PORT}`,
      },
    },
  ],
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile",
      use: { ...devices["Desktop Chrome"], viewport: { width: 375, height: 812 }, hasTouch: true },
    },
  ],
});

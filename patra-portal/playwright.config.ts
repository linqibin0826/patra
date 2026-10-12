import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "tests/e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [["html"], ["list"]] : "list",
  use: {
    baseURL: "http://localhost:4000",
    trace: "on-first-retry",
  },
  webServer: {
    command: "pnpm dev",
    port: 4000,
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    // 本机走 HTTP，会话 Cookie 不能带 Secure（见 .env.example）
    env: { PATRA_SESSION_COOKIE_SECURE: "false" },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});

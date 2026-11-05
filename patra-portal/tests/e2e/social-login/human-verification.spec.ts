import { expect, type Page, test } from "@playwright/test";
import { ROUND_TRIP, waitForTakeover, waitForVerification } from "@tests/e2e/support/account";

/// 记下页面访问 Cloudflare 的请求和 CSP 报错，组件是真实加载的官方 always-pass 组件。
function watch(page: Page) {
  const cloudflare: string[] = [];
  const violations: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("challenges.cloudflare.com")) cloudflare.push(request.url());
  });
  page.on("console", (message) => {
    if (message.text().includes("Content Security Policy")) violations.push(message.text());
  });
  return { cloudflare, violations };
}

test.describe("人机验证（真实组件）", () => {
  test("登录：平时不碰第三方；被要求后显示组件、保留密码；密码错后要重新验证", async ({ page }) => {
    const { cloudflare, violations } = watch(page);
    await page.goto("/login");
    await waitForTakeover(page, "form");
    expect(cloudflare).toEqual([]);

    const password = page.getByLabel("密码", { exact: true });
    const submit = page.getByRole("button", { name: "登录" });
    await page.getByLabel("邮箱").fill("reader@example.com");
    await password.fill("correct horse");
    await submit.click();

    await expect(page.getByText("请先完成下面的人机验证")).toBeVisible(ROUND_TRIP);
    await expect(password).toHaveValue("correct horse");
    expect(cloudflare.length).toBeGreaterThan(0);

    await waitForVerification(submit);
    await submit.click();

    await expect(page.getByText("邮箱或密码错误")).toBeVisible(ROUND_TRIP);
    await expect(password).toHaveValue("");
    await waitForVerification(submit);
    expect(violations).toEqual([]);
  });

  test("注册：组件一直显示，拿到令牌后才能提交", async ({ page }) => {
    const { cloudflare, violations } = watch(page);
    await page.goto("/register");
    await waitForTakeover(page, "form");

    await expect(page.locator("[data-human-verification]")).toBeVisible();
    await waitForVerification(page.getByRole("button", { name: "注册" }));

    expect(cloudflare.length).toBeGreaterThan(0);
    expect(violations).toEqual([]);
  });

  test("找回密码：带着令牌发送，重新发送要等组件再次通过", async ({ page }) => {
    const { violations } = watch(page);
    await page.goto("/forgot-password");
    await waitForTakeover(page, "form");
    await page.getByLabel("邮箱").fill("reader@example.com");
    const submit = page.getByRole("button", { name: "发送重设邮件" });

    await waitForVerification(submit);
    await submit.click();

    await expect(page.getByRole("heading", { level: 1, name: "查看你的邮箱" })).toBeVisible(
      ROUND_TRIP,
    );
    expect(violations).toEqual([]);
  });

  test("三个页面的 CSP 都不放行 TRUSTTURN 的脚本", async ({ request }) => {
    for (const path of ["/login", "/register", "/forgot-password"]) {
      const response = await request.get(path);
      const csp = response.headers()["content-security-policy"] ?? "";
      const scriptSrc = csp.split(";").find((directive) => directive.includes("script-src"));

      expect(scriptSrc).toContain("https://challenges.cloudflare.com");
      expect(scriptSrc).not.toContain("trustturn");
      expect(csp).toContain("frame-src https://challenges.cloudflare.com https://api.trustturn.cc");
    }
  });
});

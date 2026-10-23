import { type Browser, expect, type Page, test } from "@playwright/test";
import {
  type Account,
  freshAccount,
  login,
  ROUND_TRIP,
  register,
  waitForTakeover,
} from "@tests/e2e/support/account";
import { latestToken } from "@tests/e2e/support/mailpit";

/// 用户中心冒烟（PAP-102 spec §13.2）：对着真网关和 Mailpit 跑，本机内联两个环境变量、用单个 worker：
/// `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025
/// pnpm test:e2e tests/e2e/user-center.spec.ts --workers=1`。每条用例注册自己的新账号，碰不到改邮箱、找回密码
/// 与登录失败的次数限制；用例里不故意输错密码。

const SUBJECT = {
  verify: "验证你的 Patra 邮箱",
  reset: "重设你的 Patra 密码",
  confirmEmail: "确认你的新 Patra 邮箱",
} as const;

test.describe("用户中心", () => {
  test.skip(
    !process.env.PATRA_GATEWAY_BASE_URL || !process.env.PATRA_MAILPIT_BASE_URL,
    "未设置 PATRA_GATEWAY_BASE_URL 或 PATRA_MAILPIT_BASE_URL：跳过用户中心冒烟",
  );
  test.describe.configure({ timeout: 120_000 });

  /// 用 Mailpit 里收到的验证令牌走完验证落地页（本机会话保留）
  async function verifyEmail(page: Page, account: Account, after: number) {
    const token = await latestToken(account.email, SUBJECT.verify, after);
    await page.goto(`/verify-email#token=${token}`);
    await waitForTakeover(page, "form");
    await expect(page).toHaveURL("/verify-email");
    await page.getByRole("button", { name: "验证邮箱" }).click();
    await expect(page.getByText(`${account.email} 已验证。其他设备上的登录已退出。`)).toBeVisible(
      ROUND_TRIP,
    );
  }

  /// 新开一个浏览器上下文（另一台「设备」），可选先登录
  async function anotherDevice(browser: Browser, account?: Account) {
    const context = await browser.newContext();
    const page = await context.newPage();
    if (account) {
      await login(page, account);
      await expect(page).toHaveURL("/", ROUND_TRIP);
    }
    return { context, page };
  }

  test("注册 → 验证邮箱 → 填昵称 → 匿名访客看到主页", async ({ page, browser }) => {
    const account = freshAccount();
    const startedAt = Date.now();
    await register(page, account);
    await expect(page.getByText("验证邮件已发到")).toBeVisible();
    await verifyEmail(page, account, startedAt);

    await page.goto("/");
    await expect(page.getByText("验证邮件已发到")).toHaveCount(0);

    await page.goto("/settings/profile");
    await waitForTakeover(page, "form");
    await page.getByLabel("昵称").fill("冒烟测试");
    await page.getByRole("button", { name: "保存", exact: true }).click();
    await expect(page.getByText("资料已保存")).toBeVisible(ROUND_TRIP);

    const visitor = await anotherDevice(browser);
    await visitor.page.goto(`/u/${account.username}`);
    await expect(visitor.page.getByRole("heading", { level: 1, name: "冒烟测试" })).toBeVisible(
      ROUND_TRIP,
    );
    await expect(visitor.page.getByText(account.email)).toHaveCount(0);
    await visitor.context.close();
  });

  test("改用户名：旧地址跳到新地址，之后进入冷却", async ({ page, browser }) => {
    const account = freshAccount();
    const startedAt = Date.now();
    await register(page, account);
    await verifyEmail(page, account, startedAt);

    const renamed = `${account.username}-n`;
    await page.goto("/settings/account");
    await waitForTakeover(page, "main");
    await page.getByRole("button", { name: "修改" }).click();
    await page.getByLabel("新用户名").fill(renamed);
    await page.getByRole("button", { name: "保存" }).click();
    await expect(page.getByText(`用户名已改为 @${renamed}`)).toBeVisible(ROUND_TRIP);
    await expect(page.getByRole("button", { name: "修改" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await expect(page.getByText(/下次可修改：/)).toBeVisible();

    const visitor = await anotherDevice(browser);
    await visitor.page.goto(`/u/${account.username}`);
    await expect(visitor.page).toHaveURL(`/u/${renamed}`, ROUND_TRIP);
    await visitor.context.close();
  });

  test("改密码：另一台设备的会话失效，设备列表只剩本机", async ({ page, browser }) => {
    const account = freshAccount();
    await register(page, account);
    const other = await anotherDevice(browser, account);

    await page.goto("/settings/security");
    const devices = page.getByRole("list", { name: "登录设备" });
    await expect(devices.getByRole("listitem")).toHaveCount(2, ROUND_TRIP);

    const next = `${account.password}-2`;
    await page.getByLabel("当前密码").fill(account.password);
    await page.getByLabel("新密码", { exact: true }).fill(next);
    await page.getByLabel("确认新密码").fill(next);
    await page.getByRole("button", { name: "修改密码" }).click();
    await expect(page.getByText("密码已修改，其他设备已退出登录")).toBeVisible(ROUND_TRIP);
    await expect(devices.getByRole("listitem")).toHaveCount(1, ROUND_TRIP);

    await other.page.goto("/settings/security");
    await expect(other.page).toHaveURL("/login?next=%2Fsettings%2Fsecurity", ROUND_TRIP);
    await other.context.close();

    // 本机换了新令牌，仍是登录态
    await page.reload();
    await expect(page.getByRole("heading", { name: "修改密码" })).toBeVisible(ROUND_TRIP);
  });

  test("踢设备：被踢的设备下一次请求回到登录页", async ({ page, browser }) => {
    const account = freshAccount();
    await register(page, account);
    const other = await anotherDevice(browser, account);

    await page.goto("/settings/security");
    const devices = page.getByRole("list", { name: "登录设备" });
    await expect(devices.getByRole("listitem")).toHaveCount(2, ROUND_TRIP);
    // 注册后 10 分钟内，踢设备不需要再认证
    await devices.getByRole("button", { name: /^退出 / }).click();
    await expect(page.getByText("已退出该设备")).toBeVisible(ROUND_TRIP);
    await expect(devices.getByRole("listitem")).toHaveCount(1, ROUND_TRIP);

    await other.page.goto("/settings/profile");
    await expect(other.page).toHaveURL("/login?next=%2Fsettings%2Fprofile", ROUND_TRIP);
    await other.context.close();
  });

  test("忘记密码 → 邮件 → 重设 → 用新密码登录", async ({ page, browser }) => {
    const account = freshAccount();
    await register(page, account);

    const anon = await anotherDevice(browser);
    const startedAt = Date.now();
    await anon.page.goto("/login");
    await waitForTakeover(anon.page, "form");
    await anon.page.getByLabel("邮箱").fill(account.email);
    await anon.page.getByRole("link", { name: "忘记密码？" }).click();
    await expect(anon.page).toHaveURL("/forgot-password");
    await expect(anon.page.getByLabel("邮箱")).toHaveValue(account.email);
    await anon.page.getByRole("button", { name: "发送重设邮件" }).click();
    await expect(anon.page.getByRole("heading", { level: 1, name: "查看你的邮箱" })).toBeVisible(
      ROUND_TRIP,
    );

    const token = await latestToken(account.email, SUBJECT.reset, startedAt);
    const next = `${account.password}-r`;
    await anon.page.goto(`/reset-password#token=${token}`);
    await waitForTakeover(anon.page, "form");
    await anon.page.getByLabel("新密码", { exact: true }).fill(next);
    await anon.page.getByLabel("确认新密码").fill(next);
    await anon.page.getByRole("button", { name: "设置新密码" }).click();

    await expect(anon.page).toHaveURL("/login", ROUND_TRIP);
    await expect(anon.page.getByText("密码已重设，请用新密码登录")).toBeVisible();
    await expect(anon.page.getByLabel("邮箱")).toHaveValue(account.email);
    await anon.page.getByLabel("密码", { exact: true }).fill(next);
    await anon.page.getByRole("button", { name: "登录" }).click();
    await expect(anon.page).toHaveURL("/", ROUND_TRIP);
    await expect(anon.page.getByText("已登录", { exact: true })).toBeVisible();
    await anon.context.close();
  });

  test("改邮箱：确认邮件 → 落地页 → 账号页显示新邮箱", async ({ page }) => {
    const account = freshAccount();
    await register(page, account);
    const newEmail = account.email.replace("e2e-", "e2e-new-");

    await page.goto("/settings/account");
    await waitForTakeover(page, "main");
    const startedAt = Date.now();
    await page.getByRole("button", { name: "更改邮箱" }).click();
    await page.getByLabel("新邮箱").fill(newEmail);
    await page.getByRole("button", { name: "发送确认邮件" }).click();
    await expect(page.getByText(`正在等待 ${newEmail} 确认`)).toBeVisible(ROUND_TRIP);

    const token = await latestToken(newEmail, SUBJECT.confirmEmail, startedAt);
    await page.goto(`/confirm-email-change#token=${token}`);
    await waitForTakeover(page, "form");
    await page.getByRole("button", { name: "确认更改" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "邮箱已更改" })).toBeVisible(
      ROUND_TRIP,
    );

    await page.goto("/settings/account");
    await expect(page.getByRole("region", { name: "邮箱" })).toContainText(newEmail, ROUND_TRIP);
  });

  test("注销 → 冷静期登录被送到取消页 → 取消后回到原页面", async ({ page }) => {
    const account = freshAccount();
    await register(page, account);

    await page.goto("/settings/account");
    await waitForTakeover(page, "main");
    await page.getByRole("button", { name: "注销账号…" }).click();
    await page.getByRole("button", { name: "继续" }).click();
    await page.getByLabel("用户名").fill(account.username);
    await page.getByRole("button", { name: "注销账号", exact: true }).click();
    await expect(page.getByRole("dialog", { name: "注销申请已提交" })).toBeVisible(ROUND_TRIP);
    await page.getByRole("button", { name: "好的" }).click();
    await expect(page).toHaveURL("/", ROUND_TRIP);
    await expect(page.getByRole("banner").getByRole("link", { name: "登录" })).toBeVisible();

    await login(page, account, "/journals");
    await expect(page).toHaveURL("/account/deletion-pending?next=%2Fjournals", ROUND_TRIP);
    await expect(page.getByRole("heading", { level: 1, name: "你的账号正在注销" })).toBeVisible();

    // 冷静期里地址栏直接打开别的页面，也回到取消页（proxy）
    await page.goto("/papers");
    await expect(page).toHaveURL("/account/deletion-pending?next=%2Fpapers", ROUND_TRIP);

    await waitForTakeover(page, "main button");
    await page.getByRole("button", { name: "取消注销，恢复账号" }).click();
    await expect(page).toHaveURL("/papers", ROUND_TRIP);
    await expect(page.getByText("已取消注销，欢迎回来")).toBeVisible();
  });
});

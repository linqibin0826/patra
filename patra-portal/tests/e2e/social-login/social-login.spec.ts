import { type APIRequestContext, expect, type Page, test } from "@playwright/test";
import { ROUND_TRIP, waitForTakeover, waitForVerification } from "@tests/e2e/support/account";

const MOCK = "http://localhost:4031";
const PASSWORD = "qingshan-2026";
const GITHUB_ROW = {
  provider: "github",
  accountLabel: "chenyu-med",
  linkedAt: "2026-10-21T00:00:00Z",
  lastLoginAt: null,
};
const GOOGLE_ROW = {
  provider: "google",
  accountLabel: "chen.yu@example.com",
  linkedAt: "2026-10-30T00:00:00Z",
  lastLoginAt: null,
};

interface LoggedRequest {
  method: string;
  path: string;
  query: string;
  authorization: string | null;
  deviceId: string | null;
  ticket: string | null;
  body: Record<string, unknown>;
}

/// 重置模拟网关，可以指定回调结果、账号状态
async function reset(request: APIRequestContext, scenario: Record<string, unknown> = {}) {
  await request.post(`${MOCK}/__reset`, { data: scenario });
}

/// 模拟网关收到的请求
async function gatewayLog(request: APIRequestContext): Promise<LoggedRequest[]> {
  return (await request.get(`${MOCK}/__log`)).json();
}

/// 在登录页点一家入口，走到模拟平台的授权页
async function toPlatform(page: Page, name: string, path = "/login") {
  await page.goto(path);
  await waitForTakeover(page, "form");
  await page.getByRole("link", { name: `用 ${name} 账号登录` }).click();
  await expect(page.getByRole("heading", { name: "授权 Patra" })).toBeVisible(ROUND_TRIP);
}

/// 从入口走到补填页
async function toFillStep(page: Page, name = "GitHub", path = "/login") {
  await toPlatform(page, name, path);
  await page.getByRole("link", { name: "同意" }).click();
  await expect(page.getByRole("heading", { name: "还差一步" })).toBeVisible(ROUND_TRIP);
  await waitForTakeover(page, "form");
}

/// 带着会话打开安全页
async function openSecurity(page: Page) {
  await page
    .context()
    .addCookies([
      { name: "patra_session", value: "patra_user_chen_yu", url: "http://localhost:4030" },
    ]);
  await page.goto("/settings/security");
  await expect(page.getByRole("list", { name: "登录方式" })).toBeVisible(ROUND_TRIP);
  await waitForTakeover(page, "#login-methods button");
}

/// 页面上的提示条。跳转之后 Next 的路由播报器也是 `role="alert"`，要排除
function formAlert(page: Page) {
  return page.locator('[role="alert"]:not(#__next-route-announcer__)');
}

/// 页面横向没有溢出
async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test.describe("登录入口", () => {
  test("三家入口的版式：按钮高度、标识大小，页面不横向溢出", async ({ page }, testInfo) => {
    await page.goto("/login");
    await waitForTakeover(page, "form");
    const links = page.getByRole("main").getByRole("link", { name: /账号登录$/ });
    await expect(links).toHaveCount(3);
    const expected = testInfo.project.name === "mobile" ? 48 : 46;
    for (const link of await links.all()) {
      const box = await link.boundingBox();
      expect(box?.height).toBeGreaterThanOrEqual(expected);
    }
    const google = links.first().locator("img");
    await expect
      .poll(() => google.evaluate((image: HTMLImageElement) => image.naturalWidth))
      .toBe(40);
    expect(await google.evaluate((image) => getComputedStyle(image).objectFit)).toBe("none");
    expect((await google.boundingBox())?.width).toBe(20);
    await expectNoHorizontalScroll(page);
  });

  test("第一屏能看到入口，表单在下面", async ({ page }) => {
    await page.goto("/register");
    await waitForTakeover(page, "form");
    await expect(page.getByRole("link", { name: "用 ORCID 账号注册" })).toBeInViewport();
    const orcid = await page.getByRole("link", { name: "用 ORCID 账号注册" }).boundingBox();
    const email = await page.getByLabel("邮箱").boundingBox();
    expect(orcid?.y ?? 0).toBeLessThan(email?.y ?? 0);
  });

  test("老用户：回到发起时的页面，提示已登录，标记清掉", async ({ page, context, request }) => {
    await reset(request, { callback: "existing" });
    await toPlatform(page, "GitHub", "/login?next=%2Fjournals");
    await page.getByRole("link", { name: "同意" }).click();
    await expect(page).toHaveURL("/journals", ROUND_TRIP);
    await expect(page.getByText("已登录").first()).toBeVisible(ROUND_TRIP);
    const cookies = await context.cookies();
    expect(cookies.some((cookie) => cookie.name === "patra_session")).toBe(true);
    expect(cookies.some((cookie) => cookie.name.startsWith("patra_oauth"))).toBe(false);
    const forwarded = (await gatewayLog(request)).find((item) =>
      item.path.startsWith("/login/oauth2/code/"),
    );
    expect(forwarded?.query).toContain("code=platform-code");
    expect(forwarded?.deviceId).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  test("冷静期账号：去取消注销页，不弹已登录", async ({ page, request }) => {
    await reset(request, { callback: "deleting" });
    await toPlatform(page, "Google");
    await page.getByRole("link", { name: "同意" }).click();
    await expect(page).toHaveURL(/\/account\/deletion-pending/, ROUND_TRIP);
    await expect(page.getByRole("heading", { name: "你的账号正在注销" })).toBeVisible();
  });

  for (const [name, scenario, text] of [
    ["封禁", { callback: "banned" }, "该账号已被封禁，无法登录"],
    ["连不上平台", { callback: "unreachable" }, "暂时连不上 ORCID，请稍后再试，或换一种方式。"],
  ] as const) {
    test(`${name}：回发起页显示原因，地址栏参数清掉`, async ({ page, request }) => {
      await reset(request, scenario);
      await toPlatform(page, "ORCID");
      await page.getByRole("link", { name: "同意" }).click();
      await expect(formAlert(page)).toHaveText(text, ROUND_TRIP);
      await expect(page).toHaveURL("/login");
    });
  }

  test("在平台上拒绝：中性提示，留在发起的注册页", async ({ page }) => {
    await page.goto("/register");
    await waitForTakeover(page, "form");
    await page.getByRole("link", { name: "用 GitHub 账号注册" }).click();
    await page.getByRole("link", { name: "拒绝" }).click();
    await expect(page.getByRole("status").first()).toHaveText(
      "你取消了 GitHub 授权。可以再试一次，或换一种方式。",
      ROUND_TRIP,
    );
    await expect(page).toHaveURL("/register");
  });

  test("这家平台没配置：回发起页，不写 Cookie", async ({ page, context, request }) => {
    await reset(request, { unconfigured: ["orcid"] });
    await page.goto("/login");
    await waitForTakeover(page, "form");
    await page.getByRole("link", { name: "用 ORCID 账号登录" }).click();
    await expect(formAlert(page)).toHaveText("登录没有成功，请稍后再试", ROUND_TRIP);
    const cookies = await context.cookies();
    expect(cookies.some((cookie) => cookie.name === "patra_oauth_state")).toBe(false);
  });

  test("从平台页后退：入口恢复可点", async ({ page }) => {
    await toPlatform(page, "GitHub");
    await page.goBack();
    const github = page.getByRole("link", { name: "用 GitHub 账号登录" });
    await expect(github).toBeVisible(ROUND_TRIP);
    await expect(github).not.toHaveAttribute("aria-disabled", "true");
  });
});

test.describe("非公网地址", () => {
  test("没有入口，发起被挡回登录页", async ({ page, request }) => {
    await page.goto("http://127.0.0.1:4030/login");
    await expect(page.getByRole("heading", { name: "登录" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByText("用邮箱和密码登录 Patra")).toBeVisible();
    await expect(page.getByRole("link", { name: /账号登录$/ })).toHaveCount(0);
    const res = await request.get("http://127.0.0.1:4030/api/auth/oauth/github/start", {
      maxRedirects: 0,
    });
    expect(res.headers().location).toBe("/login");
    const started = (await gatewayLog(request)).filter((item) =>
      item.path.startsWith("/oauth2/authorization/"),
    );
    expect(started).toEqual([]);
  });
});

test.describe("首次注册", () => {
  test("补填 → 验证码 → 账号建好，回到发起时的页面", async ({ page, context, request }) => {
    await toFillStep(page, "GitHub", "/login?next=%2Fjournals");
    await expect(page.getByLabel("用户名")).toHaveValue("chenyu-med");
    await expect(page.getByLabel("邮箱")).toHaveValue("chen.yu@example.com");
    await expectNoHorizontalScroll(page);
    await page.getByRole("button", { name: "发送验证码" }).click();
    await expect(page.getByRole("heading", { name: "输入验证码" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByText(/^已发送 · \d+ 秒后可重发$/)).toBeVisible();
    await expectNoHorizontalScroll(page);

    await page.getByLabel("验证码").fill("483921");
    await page.getByRole("button", { name: "完成注册" }).click();
    await expect(page.getByText("验证码不正确")).toBeVisible(ROUND_TRIP);
    await expect(page.getByLabel("验证码")).toBeFocused();

    await page.getByLabel("验证码").fill("483920");
    await page.getByRole("button", { name: "完成注册" }).click();
    await expect(page).toHaveURL("/journals", ROUND_TRIP);
    await expect(page.getByText("注册成功，已登录").first()).toBeVisible(ROUND_TRIP);
    const cookies = await context.cookies();
    expect(cookies.some((cookie) => cookie.name === "patra_session")).toBe(true);
    expect(cookies.some((cookie) => cookie.name === "patra_oauth_ticket")).toBe(false);

    const complete = (await gatewayLog(request))
      .filter((item) => item.path.endsWith("/registration/complete"))
      .pop();
    expect(complete?.body).toMatchObject({ code: "483920", clientType: "web" });
    expect(complete?.body.deviceId).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  test("ORCID 不给邮箱：自己填，提示说明原因", async ({ page }) => {
    await toFillStep(page, "ORCID");
    await expect(page.getByLabel("邮箱")).toHaveValue("");
    await expect(
      page.getByText("ORCID 不提供邮箱，请填一个常用的。我们会发验证码到这个邮箱。"),
    ).toBeVisible();
    await expect(page.getByLabel("用户名")).toHaveValue("");
  });

  test("邮箱撞车：不建账号，引导先登录再绑定，邮箱带到登录页", async ({ page, request }) => {
    await toFillStep(page);
    await page.getByLabel("邮箱").fill("lin.zhiyuan@example.com");
    await page.getByRole("button", { name: "发送验证码" }).click();
    await expect(formAlert(page)).toContainText("这个邮箱已经注册过 Patra。", ROUND_TRIP);
    const toLogin = page.getByRole("link", { name: "去登录" });
    await expect(toLogin).toBeFocused();
    await expectNoHorizontalScroll(page);
    await toLogin.click();
    await expect(page).toHaveURL("/login?next=%2Fsettings%2Fsecurity", ROUND_TRIP);
    await expect(page.getByLabel("邮箱")).toHaveValue("lin.zhiyuan@example.com");
    await expect(page.getByLabel("密码", { exact: true })).toBeFocused();
    const log = await gatewayLog(request);
    expect(log.some((item) => item.path.endsWith("/registration/complete"))).toBe(false);
  });

  test("验证码失效：提示重发，输入锁住", async ({ page }) => {
    await toFillStep(page);
    await page.getByRole("button", { name: "发送验证码" }).click();
    await page.getByLabel("验证码").fill("000000");
    await page.getByRole("button", { name: "完成注册" }).click();
    await expect(formAlert(page)).toHaveText("这个验证码已失效，请重新发送一个", ROUND_TRIP);
    await expect(page.getByLabel("验证码")).not.toBeEditable();
  });

  test("刷新验证码页：回到这一步，邮箱还在", async ({ page }) => {
    await toFillStep(page);
    await page.getByLabel("邮箱").fill("chen.med@example.com");
    await page.getByRole("button", { name: "发送验证码" }).click();
    await expect(page.getByRole("heading", { name: "输入验证码" })).toBeVisible(ROUND_TRIP);
    await page.reload();
    await expect(page.getByRole("heading", { name: "输入验证码" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByText("chen.med@example.com")).toBeVisible();
  });
});

test.describe("邮箱撞车时就地绑定", () => {
  const ENTRY = "验证这个邮箱，并绑定到已有账号";
  const TAKEN = "lin.zhiyuan@example.com";

  /// 从入口走到撞车块
  async function toClash(page: Page, path = "/login") {
    await toFillStep(page, "GitHub", path);
    await page.getByLabel("邮箱").fill(TAKEN);
    await page.getByRole("button", { name: "发送验证码" }).click();
    await expect(page.getByRole("button", { name: ENTRY })).toBeVisible(ROUND_TRIP);
  }

  /// 从撞车块选择就地绑定，走到输码步
  async function toLinkStep(page: Page, path = "/login") {
    await toClash(page, path);
    await page.getByRole("button", { name: ENTRY }).click();
    await expect(page.getByRole("heading", { name: "验证这个邮箱" })).toBeVisible(ROUND_TRIP);
    await waitForTakeover(page, "form");
  }

  /// 提交验证码
  async function submitCode(page: Page, code: string) {
    await page.getByLabel("验证码").fill(code);
    await page.getByRole("button", { name: "完成绑定" }).click();
  }

  test("选项入口 → 错码 → 对的码：绑定并登录，回到发起时的页面", async ({
    page,
    context,
    request,
  }) => {
    await toClash(page, "/login?next=%2Fjournals");
    await expectNoHorizontalScroll(page);
    const box = await page.getByRole("button", { name: ENTRY }).boundingBox();
    expect(box?.height).toBeGreaterThanOrEqual(32);
    await page.getByRole("button", { name: ENTRY }).click();

    await expect(page.getByRole("heading", { name: "验证这个邮箱" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByText(TAKEN)).toBeVisible();
    await expect(page.getByRole("main")).toContainText(
      "如果这个邮箱可以绑定，你会收到验证码；收不到就先用原方式登录，到设置页绑定",
    );
    await expect(page.getByRole("main")).not.toContainText("已发送");
    await expect(page.getByText(/^\d+ 秒后可重发$/)).toBeVisible();
    await expectNoHorizontalScroll(page);
    const log = await gatewayLog(request);
    const sent = log.filter((item) => item.path.endsWith("/registration/link-code")).pop();
    expect(sent?.body).toEqual({ email: TAKEN });

    await submitCode(page, "483921");
    await expect(page.getByText("验证码不正确")).toBeVisible(ROUND_TRIP);
    await expect(page.getByLabel("验证码")).toBeFocused();

    await submitCode(page, "483920");
    await expect(page).toHaveURL("/journals", ROUND_TRIP);
    await expect(page.getByText("已绑定 GitHub，已登录").first()).toBeVisible(ROUND_TRIP);
    const cookies = await context.cookies();
    expect(cookies.some((cookie) => cookie.name === "patra_session")).toBe(true);
    expect(cookies.some((cookie) => cookie.name === "patra_oauth_ticket")).toBe(false);
    const linked = (await gatewayLog(request))
      .filter((item) => item.path.endsWith("/registration/link"))
      .pop();
    expect(linked?.body).toMatchObject({ code: "483920", clientType: "web" });
    expect(linked?.body.deviceId).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect((await gatewayLog(request)).some((item) => item.path.endsWith("/complete"))).toBe(false);
  });

  test("验证码失效：提示重发，输入锁住", async ({ page }) => {
    await toLinkStep(page);
    await submitCode(page, "000000");
    await expect(formAlert(page)).toHaveText("这个验证码已失效，请重新发送一个", ROUND_TRIP);
    await expect(page.getByLabel("验证码")).not.toBeEditable();
    await expect(page.getByRole("button", { name: "完成绑定" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  test("刷新：回到绑定的输码步，不是注册的", async ({ page }) => {
    await toLinkStep(page);
    await page.reload();
    await expect(page.getByRole("heading", { name: "验证这个邮箱" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByText(TAKEN)).toBeVisible();
    await expect(page.getByRole("button", { name: "完成绑定" })).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("已发送");
    await waitForTakeover(page, "form");
    await submitCode(page, "483920");
    await expect(page).toHaveURL("/", ROUND_TRIP);
  });

  test("重放：凭证用过后再提交，到已失效页", async ({ page, context }) => {
    await toLinkStep(page);
    // 另一个标签页已经停在同一步
    const other = await context.newPage();
    await other.goto("/register/complete");
    await expect(other.getByRole("heading", { name: "验证这个邮箱" })).toBeVisible(ROUND_TRIP);
    await waitForTakeover(other, "form");
    const ticket = (await context.cookies()).find((c) => c.name === "patra_oauth_ticket");

    await submitCode(page, "483920");
    await expect(page).toHaveURL("/", ROUND_TRIP);

    // 把用过的凭证放回去再交，请求才会到 identity
    await context.addCookies([
      { name: "patra_oauth_ticket", value: ticket?.value ?? "", url: "http://localhost:4030" },
    ]);
    await submitCode(other, "483920");
    await expect(other.getByRole("heading", { name: "这次绑定已失效" })).toBeVisible(ROUND_TRIP);
    await expect(other.getByRole("link", { name: "回到登录页" })).toBeVisible();
  });

  for (const [conflict, heading] of [
    ["taken", "这个账号已经绑定了"],
    ["already", "没有绑定"],
  ] as const) {
    test(`冲突 ${conflict}：结果页，没有登录`, async ({ page, context, request }) => {
      await toLinkStep(page);
      await request.post(`${MOCK}/__update`, { data: { linkConflict: conflict } });
      await submitCode(page, "483920");
      await expect(page.getByRole("heading", { name: heading })).toBeVisible(ROUND_TRIP);
      const cookies = await context.cookies();
      expect(cookies.some((cookie) => cookie.name === "patra_session")).toBe(false);
    });
  }

  test("注入：邮箱和 ticket 都改不了发给 identity 的凭证", async ({ page, context, request }) => {
    await toClash(page);
    const own = (await context.cookies()).find((c) => c.name === "patra_oauth_ticket")?.value;
    await page.evaluate(async () => {
      await fetch("/api/auth/oauth/registration/link-code?ticket=stolen", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Patra-Registration-Ticket": "stolen" },
        body: JSON.stringify({ email: "lin.zhiyuan@example.com", ticket: "stolen" }),
      });
    });
    const sent = (await gatewayLog(request))
      .filter((item) => item.path.endsWith("/registration/link-code"))
      .pop();
    expect(sent?.ticket).toBe(own);
    expect(sent?.body).toEqual({ email: "lin.zhiyuan@example.com" });
  });
});

test.describe("防注入", () => {
  test("别人的 ticket 放进地址栏：不算数，也不发给网关", async ({ page, browser, request }) => {
    // 攻击者走完授权，拿到自己的 ticket
    const attacker = await browser.newContext();
    const attackerPage = await attacker.newPage();
    await toFillStep(attackerPage);
    const stolen = (await attacker.cookies()).find((c) => c.name === "patra_oauth_ticket")?.value;
    expect(stolen).toBeTruthy();
    await attacker.close();
    const before = (await gatewayLog(request)).length;

    // 受害者的浏览器打开诱导链接
    await page.goto(`/register/complete?ticket=${stolen}&patra_oauth_ticket=${stolen}`);
    await expect(page.getByRole("heading", { name: "这次注册已过期" })).toBeVisible(ROUND_TRIP);
    const after = (await gatewayLog(request)).slice(before);
    expect(after.some((item) => item.ticket === stolen)).toBe(false);
  });

  test("查询串、请求体、请求头都塞不进 ticket", async ({ page, request }) => {
    await page.goto("/login");
    const stolen = "a".repeat(43);
    const before = (await gatewayLog(request)).length;
    const result = await page.evaluate(async (ticket) => {
      const res = await fetch(`/api/auth/oauth/registration/complete?ticket=${ticket}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Patra-Registration-Ticket": ticket },
        body: JSON.stringify({ code: "483920", ticket }),
      });
      return res.json();
    }, stolen);
    expect(result).toEqual({ ok: false, reason: "expired" });
    expect((await gatewayLog(request)).slice(before)).toEqual([]);
  });

  test("自己有 ticket 时，注入的值也换不掉它", async ({ page, context, request }) => {
    await toFillStep(page);
    const own = (await context.cookies()).find((c) => c.name === "patra_oauth_ticket")?.value;
    await page.evaluate(async () => {
      await fetch("/api/auth/oauth/registration/code?ticket=stolen", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Patra-Registration-Ticket": "stolen" },
        body: JSON.stringify({
          username: "chen-yu",
          email: "chen.yu@example.com",
          ticket: "stolen",
        }),
      });
    });
    const sent = (await gatewayLog(request))
      .filter((item) => item.path.endsWith("/registration/code"))
      .pop();
    expect(sent?.ticket).toBe(own);
    expect(sent?.body).toEqual({ username: "chen-yu", email: "chen.yu@example.com" });
  });

  test("ticket 不进地址栏、不进页面、脚本读不到", async ({ page, context }) => {
    const urls: string[] = [];
    page.on("framenavigated", (frame) => urls.push(frame.url()));
    await toFillStep(page);
    const cookie = (await context.cookies()).find((c) => c.name === "patra_oauth_ticket");
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.sameSite).toBe("Lax");
    expect(urls.join()).not.toContain(cookie?.value ?? "missing");
    expect(await page.content()).not.toContain(cookie?.value ?? "missing");
    expect(await page.evaluate(() => document.cookie)).not.toContain("patra_oauth_ticket");
    const html = await (await context.request.get("/register/complete")).text();
    expect(html).not.toContain(cookie?.value ?? "missing");
  });

  test("伪造的回调（登录 CSRF）：state 对不上就不转发", async ({ page, request }) => {
    await page.goto("/api/auth/oauth/callback/github?code=attacker-code&state=attacker-state");
    await expect(page).toHaveURL("/login", ROUND_TRIP);
    await expect(formAlert(page)).toContainText("这次授权没有成功");
    const log = await gatewayLog(request);
    expect(log.some((item) => item.path.startsWith("/login/oauth2/code/"))).toBe(false);
  });

  test("跨站提交验证码：门户拒绝", async ({ request }) => {
    const res = await request.post("/api/auth/oauth/registration/complete", {
      headers: { origin: "http://evil.example", host: "localhost:4030" },
      data: { code: "483920" },
    });
    expect(res.status()).toBe(403);
  });
});

test.describe("设置页登录方式", () => {
  test("绑定：再认证 → 平台 → 回设置页，这一行变成已绑定", async ({ page, request }) => {
    await reset(request, { callback: "link", linked: [GITHUB_ROW] });
    await openSecurity(page);
    await expectNoHorizontalScroll(page);
    await page.getByRole("button", { name: "绑定 ORCID" }).click();
    const dialog = page.getByRole("dialog", { name: "确认是你本人" });
    await expect(dialog).toBeVisible(ROUND_TRIP);
    await dialog.getByLabel("密码", { exact: true }).fill(PASSWORD);
    await dialog.getByRole("button", { name: "确认" }).click();
    await expect(page.getByRole("heading", { name: "授权 Patra" })).toBeVisible(ROUND_TRIP);
    await page.getByRole("link", { name: "同意" }).click();
    await expect(page.getByText(/已绑定 ORCID/).first()).toBeVisible(ROUND_TRIP);
    await expect(page).toHaveURL("/settings/security");
    await expect(page.getByRole("button", { name: "解绑 ORCID" })).toBeVisible(ROUND_TRIP);
    await expect(page.getByRole("link", { name: "0000-0002-1825-0097" })).toHaveAttribute(
      "href",
      "https://orcid.org/0000-0002-1825-0097",
    );
    const callback = (await gatewayLog(request)).find((item) =>
      item.path.startsWith("/login/oauth2/code/"),
    );
    expect(callback?.authorization).toBe("Bearer patra_user_chen_yu");
  });

  test("绑定被拒：已绑给别人，说明原因", async ({ page, request }) => {
    await reset(request, { callback: "taken", reauthed: true });
    await openSecurity(page);
    await page.getByRole("button", { name: "绑定 GitHub" }).click();
    await page.getByRole("link", { name: "同意" }).click();
    await expect(formAlert(page)).toContainText(
      "你刚才授权的 GitHub 账号已经绑定了另一个 Patra 账号",
      ROUND_TRIP,
    );
  });

  test("解绑：确认 → 再认证 → 这一行变回未绑定", async ({ page, request }) => {
    await reset(request, { linked: [GITHUB_ROW] });
    await openSecurity(page);
    await page.getByRole("button", { name: "解绑 GitHub" }).click();
    const confirm = page.getByRole("dialog", { name: "解绑 GitHub 账号？" });
    await expect(confirm).toContainText("不能再用 GitHub 账号 chenyu-med 登录 Patra");
    await confirm.getByRole("button", { name: "解绑" }).click();
    const reauth = page.getByRole("dialog", { name: "确认是你本人" });
    await reauth.getByLabel("密码", { exact: true }).fill(PASSWORD);
    await reauth.getByRole("button", { name: "确认" }).click();
    await expect(page.getByText(/已解绑 GitHub/).first()).toBeVisible(ROUND_TRIP);
    await expect(page.getByRole("button", { name: "绑定 GitHub" })).toBeVisible(ROUND_TRIP);
  });

  test("没有密码：看不到密码框，唯一的方式不能解绑，可以发设置邮件", async ({ page, request }) => {
    await reset(request, { hasPassword: false, linked: [GITHUB_ROW] });
    await openSecurity(page);
    await expect(page.getByRole("heading", { name: "修改密码" })).toHaveCount(0);
    await expect(page.locator('input[type="password"]')).toHaveCount(0);
    await expect(page.getByRole("button", { name: "解绑 GitHub" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await expect(page.getByText(/这是你现在唯一的登录方式/)).toBeVisible();
    await expectNoHorizontalScroll(page);
    await page.getByRole("button", { name: "绑定 ORCID" }).click();
    const dialog = page.getByRole("dialog", { name: "先设置一个密码" });
    await expect(dialog).toContainText("所有设备都会退出登录");
    await expect(dialog.locator('input[type="password"]')).toHaveCount(0);
    const send = dialog.getByRole("button", { name: "发送设置邮件" });
    await waitForVerification(send);
    await send.click();
    await expect(page.getByRole("dialog", { name: "查看你的邮箱" })).toBeVisible(ROUND_TRIP);
    const forgot = (await gatewayLog(request)).find((item) =>
      item.path.endsWith("/auth/password/forgot"),
    );
    expect(forgot?.body).toEqual({
      email: "chen.yu@example.com",
      verificationToken: expect.any(String),
    });
  });

  test("没有密码但绑了两家：解绑走设置密码，不调接口", async ({ page, request }) => {
    await reset(request, { hasPassword: false, linked: [GITHUB_ROW, GOOGLE_ROW] });
    await openSecurity(page);
    await page.getByRole("button", { name: "解绑 Google" }).click();
    await expect(page.getByRole("dialog", { name: "先设置一个密码" })).toBeVisible();
    const log = await gatewayLog(request);
    expect(log.some((item) => item.method === "DELETE")).toBe(false);
  });

  test("邮箱未验证：绑定按钮不可点，可以重发验证邮件", async ({ page, request }) => {
    await reset(request, { emailVerified: false });
    await openSecurity(page);
    await expect(page.getByText(/绑定前要先验证邮箱/)).toBeVisible();
    await expect(page.getByRole("button", { name: "绑定 GitHub" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });
});

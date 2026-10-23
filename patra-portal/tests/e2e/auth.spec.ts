import { randomBytes } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";

/// 真人点一下要按住一会儿。Playwright 默认的点击按下就松开，测不出按住期间页面的挤动
const PRESS = { delay: 100 };

/// 等页面接管 `selector` 指向的元素：React 接管一个元素时，在它上面挂一个以 `__reactProps$` 开头的
/// 属性。接管之前操作，点链接是浏览器原生的跳转，离开字段也不校验，这里的用例没修好也会通过
async function waitForTakeover(page: Page, selector: string) {
  await page.waitForFunction((target) => {
    const element = document.querySelector(target);
    return element !== null && Object.keys(element).some((key) => key.startsWith("__reactProps$"));
  }, selector);
}

const SOURCE = "/papers?q=GLP-1";
const SESSION_COOKIES = ["patra_session", "__Host-patra_session"];
/// 提交后要经过网关和目标页的服务端渲染，比默认的 5 秒宽一些
const ROUND_TRIP = { timeout: 15_000 };

/// 每跑一次在网关后面注册一个一次性账号：邮箱、用户名随机，密码运行时生成，都不写进仓库
function freshAccount() {
  const tag = randomBytes(6).toString("hex");
  return {
    email: `e2e-${tag}@example.org`,
    username: `e2e-${tag}`,
    password: randomBytes(12).toString("base64url"),
  };
}

/// 当前标签页的历史记录条数
const historyLength = (page: Page) => page.evaluate(() => history.length);

/// 点开用户菜单，点「退出登录」：仍在当前页，导航栏回到「登录」，焦点在它上面
async function logOut(page: Page, path: string) {
  const nav = page.getByRole("banner");
  await nav.getByRole("button", { name: "账号菜单" }).click();
  await page.getByRole("menuitem", { name: "退出登录" }).click();
  await expect(page.getByText("已退出登录")).toBeVisible(ROUND_TRIP);
  await expect(nav.getByRole("link", { name: "登录" })).toBeFocused();
  await expect(page).toHaveURL(path);
}

/// 离开字段时冒出的错误行会把下方的链接、按钮往下挤。按下和松开之间挤动了，松开时落在别的元素上，
/// 这次点击就没了。只有真实浏览器有布局，组件测试测不到，所以放在这里
test.describe("登录页、注册页：离开字段时冒出的错误不让下方的点击落空", () => {
  test("登录页：焦点在空的邮箱框里，直接点「注册」", async ({ page }) => {
    await page.goto("/login?next=%2Fpapers%3Fq%3DGLP-1");
    await waitForTakeover(page, "form");
    await expect(page.getByLabel("邮箱")).toBeFocused();
    await page.getByRole("link", { name: "注册" }).click(PRESS);
    await expect(page).toHaveURL("/register?next=%2Fpapers%3Fq%3DGLP-1");
  });

  test("注册页：邮箱填了一半，直接点「登录」", async ({ page }) => {
    await page.goto("/register?next=%2Fjournals%2F7");
    await waitForTakeover(page, "form");
    await page.getByLabel("邮箱").fill("reader");
    await page.getByRole("link", { name: "登录" }).click(PRESS);
    await expect(page).toHaveURL("/login?next=%2Fjournals%2F7");
  });

  test.describe("手机上轻触", () => {
    test.use({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true });

    test("注册页：邮箱填了一半，直接轻触「登录」", async ({ page }) => {
      await page.goto("/register?next=%2Fjournals%2F7");
      await waitForTakeover(page, "form");
      await page.getByLabel("邮箱").fill("reader");
      await page.getByRole("link", { name: "登录" }).tap();
      await expect(page).toHaveURL("/login?next=%2Fjournals%2F7");
    });
  });
});

/// 脚本还没加载完、页面还没接管表单时就提交（网慢、设备慢、密码管理器自动提交），浏览器按表单自己的
/// 属性提交。禁用脚本来模拟这段时间：提交必须是 POST，地址里只有原来的 next；Next 对不带 action 的
/// POST 照常渲染这一页，表单清空，可以再提交一次
test.describe("页面接管表单之前就提交：邮箱、密码不进地址栏", () => {
  test.use({ javaScriptEnabled: false });

  test("登录页", async ({ page }) => {
    await page.goto("/login?next=%2Fjournals%2F7");
    await page.getByLabel("邮箱").fill("reader@example.org");
    await page.getByLabel("密码", { exact: true }).fill("correct horse");
    const submitted = page.waitForRequest((request) => request.isNavigationRequest());
    await page.getByRole("button", { name: "登录" }).click();
    const request = await submitted;
    expect(request.method()).toBe("POST");
    expect(new URL(request.url()).search).toBe("?next=%2Fjournals%2F7");
    await expect(page.getByRole("heading", { level: 1, name: "登录" })).toBeVisible();
  });

  test("注册页", async ({ page }) => {
    await page.goto("/register?next=%2Fjournals%2F7");
    await page.getByLabel("邮箱").fill("reader@example.org");
    await page.getByLabel("用户名").fill("reader");
    await page.getByLabel("密码", { exact: true }).fill("correct horse");
    await page.getByLabel("确认密码").fill("correct horse");
    const submitted = page.waitForRequest((request) => request.isNavigationRequest());
    await page.getByRole("button", { name: "注册" }).click();
    const request = await submitted;
    expect(request.method()).toBe("POST");
    expect(new URL(request.url()).search).toBe("?next=%2Fjournals%2F7");
    await expect(page.getByRole("heading", { level: 1, name: "注册" })).toBeVisible();
  });
});

/// 要连上网关（PATRA_GATEWAY_BASE_URL），用单个 worker 跑（--workers=1）：并行时 next dev 同时冷编译
/// 好几个路由，Fast Refresh 重建会让页内跳转退化成整页加载，流程会点到页面还没接管的表单
test.describe("账号：注册、登录、退出", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过账号流程");

  test("从文献检索页注册、退出、再登录、再退出，每次都回到原页面", async ({ page }) => {
    test.setTimeout(90_000);
    const account = freshAccount();
    const nav = page.getByRole("banner");
    // 默认头像的字：昵称默认等于用户名，取首字母大写
    const initial = account.username[0]?.toUpperCase() ?? "";

    await test.step("导航栏「登录」→ 登录页 → 互跳到注册页，next 一路带着", async () => {
      await page.goto(SOURCE);
      await waitForTakeover(page, "header");
      const entries = await historyLength(page);
      await nav.getByRole("link", { name: "登录" }).click();
      await expect(page).toHaveURL("/login?next=%2Fpapers%3Fq%3DGLP-1");
      await page.getByRole("link", { name: "注册" }).click();
      await expect(page).toHaveURL("/register?next=%2Fpapers%3Fq%3DGLP-1");

      await page.getByLabel("邮箱").fill(account.email);
      await page.getByLabel("用户名").fill(account.username);
      await page.getByLabel("密码", { exact: true }).fill(account.password);
      await page.getByLabel("确认密码").fill(account.password);
      await page.getByRole("button", { name: "注册" }).click();

      await expect(page).toHaveURL(SOURCE, ROUND_TRIP);
      await expect(page.getByText("注册成功，已登录")).toBeVisible();
      await expect(nav.getByRole("button", { name: "账号菜单" })).toHaveText(initial);
      // 登录页、注册页都被替换掉了：只比点「登录」之前多一条（来源页本身）
      expect(await historyLength(page)).toBe(entries + 1);
    });

    await test.step("后退不会回到注册页或登录页", async () => {
      await page.goBack();
      await expect(page).toHaveURL(SOURCE);
      await expect(page.getByRole("heading", { level: 1, name: /登录|注册/ })).toHaveCount(0);
    });

    await test.step("退出登录：留在原页面", async () => {
      await logOut(page, SOURCE);
    });

    await test.step("再登录一次：回到原页面，页面脚本读不到会话 Cookie", async () => {
      await nav.getByRole("link", { name: "登录" }).click();
      await page.getByLabel("邮箱").fill(account.email);
      await page.getByLabel("密码", { exact: true }).fill(account.password);
      await page.getByRole("button", { name: "登录" }).click();

      await expect(page).toHaveURL(SOURCE, ROUND_TRIP);
      await expect(page.getByText("已登录", { exact: true })).toBeVisible();
      await expect(nav.getByRole("button", { name: "账号菜单" })).toHaveText(initial);

      const cookies = await page.context().cookies();
      const session = cookies.find((cookie) => SESSION_COOKIES.includes(cookie.name));
      expect(session?.httpOnly).toBe(true);
      const readable = await page.evaluate(() => document.cookie);
      for (const name of SESSION_COOKIES) {
        expect(readable).not.toContain(name);
      }
    });

    await test.step("已登录时直接打开登录页：跳到 next，不显示表单", async () => {
      await page.goto("/login?next=%2Fjournals");
      await expect(page).toHaveURL("/journals");
      await waitForTakeover(page, "header");
      await expect(page.getByLabel("邮箱")).toHaveCount(0);
    });

    await test.step("再退出一次", async () => {
      await logOut(page, "/journals");
    });
  });
});

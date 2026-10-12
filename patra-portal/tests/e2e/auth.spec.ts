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

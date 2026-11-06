import { randomBytes } from "node:crypto";
import { expect, type Locator, type Page } from "@playwright/test";

/// 提交后要经过网关和目标页的服务端渲染，比默认的 5 秒宽一些
export const ROUND_TRIP = { timeout: 15_000 };

/// 一个一次性账号：邮箱、用户名随机，密码运行时生成，都不写进仓库、不打印
export interface Account {
  email: string;
  username: string;
  password: string;
}

/// 每跑一次在网关后面注册一个新账号用。邮箱用 Resend 的测试地址 `delivered+<标识>@resend.dev`：mini 的 identity
/// 走真实邮件服务商，发往这个地址的邮件 Resend 只记为已送达、不真的投递，也不会因为退信影响发件域名的信誉
/// （以前的 `@example.org` 会真的发出去再被退回）。`+` 后面的标识让每个账号的邮箱各不相同
export function freshAccount(): Account {
  const tag = randomBytes(6).toString("hex");
  return {
    email: `delivered+e2e-${tag}@resend.dev`,
    username: `e2e-${tag}`,
    password: randomBytes(12).toString("base64url"),
  };
}

/// 等页面接管 `selector` 指向的元素：React 接管一个元素时，在它上面挂一个以 `__reactProps$` 开头的
/// 属性。接管之前操作，点链接是浏览器原生的跳转，离开字段也不校验，这里的用例没修好也会通过。
/// 最多等 60 秒：元素一直不出现（比如收起的编辑器没被点开）时尽快报错，不要干等到整条用例超时
export async function waitForTakeover(page: Page, selector: string) {
  await page.waitForFunction(
    (target) => {
      const element = document.querySelector(target);
      return (
        element !== null && Object.keys(element).some((key) => key.startsWith("__reactProps$"))
      );
    },
    selector,
    { timeout: 60_000 },
  );
}

/// 等人机验证通过：组件是真实加载的官方 always-pass 组件，拿到令牌后提交按钮才可用。要能访问 challenges.cloudflare.com。
export async function waitForVerification(submit: Locator) {
  await expect(submit).not.toHaveAttribute("aria-disabled", "true", { timeout: 30_000 });
}

/// 在注册页注册，回到首页（已登录）
export async function register(page: Page, account: Account) {
  await page.goto("/register");
  await waitForTakeover(page, "form");
  await page.getByLabel("邮箱").fill(account.email);
  await page.getByLabel("用户名").fill(account.username);
  await page.getByLabel("密码", { exact: true }).fill(account.password);
  await page.getByLabel("确认密码").fill(account.password);
  const submit = page.getByRole("button", { name: "注册" });
  await waitForVerification(submit);
  await submit.click();
  await expect(page).toHaveURL("/", ROUND_TRIP);
}

/// 在登录页登录；`next` 是登录后要回到的地址
export async function login(page: Page, account: Pick<Account, "email" | "password">, next = "/") {
  await page.goto(next === "/" ? "/login" : `/login?next=${encodeURIComponent(next)}`);
  await waitForTakeover(page, "form");
  await page.getByLabel("邮箱").fill(account.email);
  await page.getByLabel("密码", { exact: true }).fill(account.password);
  const submit = page.getByRole("button", { name: "登录" });
  await submit.click();
  // 所有用例共用一个 IP，失败累计到门槛后，登录也会被要求先过人机验证
  const required = page.getByText("请先完成下面的人机验证");
  const settled = await Promise.race([
    page.waitForURL((url) => url.pathname !== "/login", ROUND_TRIP).then(() => "left"),
    required.waitFor(ROUND_TRIP).then(() => "required"),
    page
      .getByRole("alert")
      .filter({ hasNotText: "人机验证" })
      .filter({ hasText: /\S/ })
      .first()
      .waitFor(ROUND_TRIP)
      .then(() => "failed"),
  ]).catch(() => "timeout");
  if (settled === "required") {
    await waitForVerification(submit);
    await submit.click();
  }
}

/// 打开页面。next dev 首次编译一个路由时会让已打开的页面整页刷新，正在进行的跳转被打断（`net::ERR_ABORTED`）：
/// 只对这种情况重试一次，其余错误照常抛出
export async function visit(page: Page, url: string): Promise<void> {
  try {
    await page.goto(url, { timeout: 60_000 });
  } catch (error) {
    if (!String(error).includes("ERR_ABORTED")) {
      throw error;
    }
    await page.goto(url, { timeout: 60_000 });
  }
}

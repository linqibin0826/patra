import { expect, type Page, test } from "@playwright/test";
import {
  freshAccount,
  login,
  ROUND_TRIP,
  register,
  waitForTakeover,
} from "@tests/e2e/support/account";
import {
  downloadedText,
  libraryItems,
  openLibrary,
  publicLink,
  readCopied,
  save,
  simulateInsecureClipboard,
  unsavedButtons,
} from "@tests/e2e/support/library";

/// v0.13 文献库（PAP-114）：连 mini 上的后端跑。新注册的账号邮箱未验证，私人收藏照样能用（release spec 2.1.1）
test.describe("收藏与文献库", () => {
  test.describe.configure({ mode: "serial" });
  // 开发模式下每页第一次打开要现编译，一条用例走五六个页面，30 秒不够
  test.setTimeout(120_000);

  test("首页、检索结果、详情页各收藏一篇：刷新后仍是已收藏，文献库里正好这三篇", async ({
    page,
  }) => {
    await register(page, freshAccount());

    // 首页文献卡片
    await waitForTakeover(page, "main");
    const feed = page.locator('[data-section="explore-feed"]');
    await feed
      .getByRole("article")
      .first()
      .waitFor(ROUND_TRIP)
      .catch(() => {});
    test.skip((await unsavedButtons(feed).count()) === 0, "首页没有文献卡（catalog 不可达）");
    const fromHome = await save(page, unsavedButtons(feed).first());

    // 检索结果列表项：取一篇还没收藏的
    await page.goto("/papers?q=heart");
    await waitForTakeover(page, "main");
    const results = page.getByRole("list", { name: "文献列表" });
    const fromSearch = await save(page, unsavedButtons(results).first());

    // 详情页页头：再取一篇没收藏的，进详情页收藏
    const next = results
      .getByRole("listitem")
      .filter({ has: unsavedButtons(page) })
      .first()
      .getByRole("heading")
      .getByRole("link");
    const href = await next.getAttribute("href");
    await page.goto(href ?? "/papers", { timeout: 60_000 });
    await waitForTakeover(page, "#masthead button");
    const fromDetail = await save(page, unsavedButtons(page.locator("#masthead")));

    // 刷新后详情页按钮仍是已收藏
    await page.reload();
    await expect(
      page.locator("#masthead").getByRole("button", { name: "已收藏，管理收藏" }),
    ).toHaveAttribute("aria-pressed", "true", ROUND_TRIP);

    // 文献库：正好这三篇
    await openLibrary(page);
    await expect(libraryItems(page)).toHaveCount(3, ROUND_TRIP);
    const titles = (await libraryItems(page).getByRole("heading").allTextContents()).join("\n");
    for (const title of [fromHome, fromSearch, fromDetail]) {
      expect(titles).toContain(title.slice(0, 20));
    }

    // 回到检索结果，刚收藏的那篇刷新后仍是已收藏
    await page.goto("/papers?q=heart");
    await waitForTakeover(page, "main");
    await expect(
      page
        .getByRole("list", { name: "文献列表" })
        .getByRole("button", { name: "已收藏，管理收藏" })
        .first(),
    ).toBeVisible(ROUND_TRIP);
  });

  test("建收藏夹「组会」、放进两篇、写备注、导出与复制引用、匿名看私有夹 404；删除收藏夹后文献仍在、回到未分类", async ({
    page,
    browser,
  }) => {
    await register(page, freshAccount());
    await page.goto("/papers?q=heart");
    await waitForTakeover(page, "main");
    const results = page.getByRole("list", { name: "文献列表" });
    await results.waitFor(ROUND_TRIP).catch(() => {});
    test.skip((await unsavedButtons(results).count()) < 2, "检索结果不足两篇（catalog 不可达）");
    await save(page, unsavedButtons(results).first());
    await save(page, unsavedButtons(results).first());

    await openLibrary(page);
    await expect(libraryItems(page)).toHaveCount(2, ROUND_TRIP);

    // 新建「组会」：进入新收藏夹
    await page.getByRole("button", { name: "新建收藏夹" }).first().click();
    const dialog = page.getByRole("dialog", { name: "新建收藏夹" });
    await dialog.getByRole("textbox", { name: "名称" }).fill("组会");
    await dialog.getByRole("button", { name: "创建" }).click();
    await expect(page).toHaveURL(/\/library\/\d+$/, ROUND_TRIP);
    await expect(page.getByText("这个收藏夹还没有文献")).toBeVisible(ROUND_TRIP);

    // 回到全部：两篇都放进「组会」
    await openLibrary(page);
    for (const index of [0, 1]) {
      await libraryItems(page).nth(index).getByRole("button", { name: "条目操作" }).click();
      await page.getByRole("menuitem", { name: "放进收藏夹…" }).click();
      const box = page.getByRole("checkbox", { name: "组会 私有" });
      await box.check();
      await expect(box).toBeChecked();
      await expect(box).toBeEnabled(ROUND_TRIP);
      await page.keyboard.press("Escape");
    }

    // 第一篇写备注
    await libraryItems(page).first().getByRole("button", { name: "条目操作" }).click();
    await page.getByRole("menuitem", { name: "编辑备注" }).click();
    await page.getByRole("textbox", { name: "备注" }).fill("第 3 张表的亚组结果组会讲");
    await page.getByRole("button", { name: "保存", exact: true }).click();
    await expect(page.getByText("第 3 张表的亚组结果组会讲")).toBeVisible(ROUND_TRIP);

    // 刷新后归属与备注都在；未分类为空
    await page.reload();
    await expect(page.getByText("第 3 张表的亚组结果组会讲")).toBeVisible(ROUND_TRIP);
    await expect(libraryItems(page).first().getByRole("link", { name: /组会/ })).toBeVisible();
    await openLibrary(page, "/library/unfiled");
    await expect(page.getByRole("heading", { name: "都已整理好了" })).toBeVisible(ROUND_TRIP);

    // 删除「组会」：回到文献库，两篇仍在，都回到未分类
    await page
      .getByRole("navigation", { name: "文献库视图" })
      .getByRole("link", { name: /组会/ })
      .click();
    await expect(page).toHaveURL(/\/library\/\d+$/, ROUND_TRIP);
    await expect(libraryItems(page)).toHaveCount(2, ROUND_TRIP);

    // 导出：经门户转发到 catalog，下载 RIS（文件名由 catalog 按收藏夹名生成）
    await page.getByRole("button", { name: "导出", exact: true }).click();
    const download = page.waitForEvent("download");
    await page
      .getByRole("dialog", { name: "导出" })
      .getByRole("button", { name: "下载 .ris" })
      .click();
    expect((await download).suggestedFilename()).toMatch(/^patra-.*\.ris$/);

    // 复制引用：catalog 生成的 GB/T 7714 预览带序号
    await page.getByRole("button", { name: "复制引用", exact: true }).click();
    const cite = page.getByRole("dialog", { name: /复制引用/ });
    await expect(cite.getByText(/^\[1\] /)).toBeVisible(ROUND_TRIP);
    await page.keyboard.press("Escape");
    // 等对话框的退场动画走完：期间点别的按钮会被遮罩吃掉
    await expect(cite).toBeHidden();

    // 私有收藏夹：匿名访客打开公开页是真的 404（release spec 2.1.2）
    const collectionId = new URL(page.url()).pathname.split("/").pop();
    const anonymous = await browser.newContext();
    const anonPage = await anonymous.newPage();
    const response = await anonPage.goto(`/collections/${collectionId}`);
    expect(response?.status()).toBe(404);
    await expect(anonPage.getByRole("heading", { name: "没有找到这个收藏夹" })).toBeVisible();
    await anonymous.close();

    await page.getByRole("button", { name: "收藏夹操作" }).click();
    await expect(page.getByRole("menu")).toBeVisible();
    await page.getByRole("menuitem", { name: "删除收藏夹" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "删除收藏夹" }).click();
    await expect(page).toHaveURL("/library", ROUND_TRIP);
    await expect(libraryItems(page)).toHaveCount(2, ROUND_TRIP);
    await openLibrary(page, "/library/unfiled");
    await expect(libraryItems(page)).toHaveCount(2, ROUND_TRIP);
  });

  test("未登录点收藏：去登录，next 带回当前页", async ({ page }) => {
    await page.goto("/papers?q=heart");
    await waitForTakeover(page, "main");
    const list = page.getByRole("list", { name: "文献列表" });
    await list.waitFor(ROUND_TRIP).catch(() => {});
    const button = unsavedButtons(list).first();
    test.skip((await button.count()) === 0, "没有检索结果（catalog 不可达）");
    await button.click();
    await expect(page).toHaveURL(/\/login\?next=%2Fpapers%3Fq%3Dheart/, ROUND_TRIP);
  });

  test("互动服务不可用：文献页主体照常，收藏显示「收藏暂不可用」", async ({ page }) => {
    await register(page, freshAccount());
    await page.route("**/api/me/library/status", (route) => route.fulfill({ status: 502 }));
    await page.goto("/papers?q=heart");
    await waitForTakeover(page, "main");
    const first = page.getByRole("list", { name: "文献列表" }).getByRole("listitem").first();
    await first.waitFor(ROUND_TRIP).catch(() => {});
    test.skip((await first.count()) === 0, "没有检索结果（catalog 不可达）");
    await expect(first.getByRole("button", { name: "收藏暂不可用" })).toBeVisible(ROUND_TRIP);
    const href = await first.getByRole("heading").getByRole("link").getAttribute("href");
    await page.goto(href ?? "/papers", { timeout: 60_000 });
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible(ROUND_TRIP);
    await expect(
      page.locator("#masthead").getByRole("button", { name: "收藏暂不可用" }),
    ).toBeVisible(ROUND_TRIP);
  });
});

/// 公开收藏夹的可见性（release spec 2.1.2，AC 3）：设为公开要求邮箱已验证。mini 的 identity 走真实邮件服务商，
/// 新注册的账号收不到 Mailpit 里的验证邮件，所以要一个预先验证过邮箱的账号（不进仓库，放本机环境变量）
const VERIFIED = {
  email: process.env.PATRA_E2E_VERIFIED_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_PASSWORD ?? "",
};

/// 用的是用户的真实账号：无论用例中途在哪一步失败，都把它建的收藏夹先设回私有再删掉，并把它为测试新收藏的
/// 那一篇从文献库移除，最后确认这个收藏夹的公开页已经打不开
async function cleanUpVerified(page: Page, collectionId: string | null, savedTitle: string | null) {
  if (collectionId) {
    await page.goto(`/library/${collectionId}`);
    const toggle = page.getByRole("switch", { name: "公开" });
    // isVisible() 不等待；用 waitFor 等页面出来，等不到（收藏夹已不存在）才跳过
    const exists = await toggle
      .waitFor({ timeout: 15_000 })
      .then(() => true)
      .catch(() => false);
    if (exists) {
      await waitForTakeover(page, "main");
      if (await toggle.isChecked()) {
        await toggle.uncheck();
        await expect(page.getByText("别人可以通过链接或你的主页看到这个收藏夹。")).toBeVisible(
          ROUND_TRIP,
        );
      }
      await page.getByRole("button", { name: "收藏夹操作" }).click();
      await page.getByRole("menuitem", { name: "删除收藏夹" }).click();
      await page.getByRole("dialog").getByRole("button", { name: "删除收藏夹" }).click();
      await expect(page).toHaveURL("/library", ROUND_TRIP);
    }
    expect((await page.goto(`/collections/${collectionId}`))?.status()).toBe(404);
  }
  if (savedTitle) {
    await openLibrary(page);
    const item = libraryItems(page)
      .filter({ hasText: savedTitle.slice(0, 20) })
      .first();
    if ((await item.count()) > 0) {
      await item.getByRole("button", { name: "条目操作" }).click();
      await page.getByRole("menuitem", { name: "从文献库移除" }).click();
      const confirm = page.getByRole("dialog").getByRole("button", { name: "移除" });
      if (
        await confirm
          .waitFor({ timeout: 2_000 })
          .then(() => true)
          .catch(() => false)
      ) {
        await confirm.click();
      }
      await expect(item).toHaveCount(0, ROUND_TRIP);
    }
  }
}

test.describe("分享与页面信息", () => {
  test.setTimeout(120_000);

  test("详情页分享面板：HTTP 下复制链接、复制引用成功，导出这篇 RIS", async ({ browser }) => {
    const context = await browser.newContext();
    await simulateInsecureClipboard(context);
    const page = await context.newPage();
    try {
      await page.goto("/papers?q=heart");
      const first = page.getByRole("list", { name: "文献列表" }).getByRole("listitem").first();
      await first.waitFor(ROUND_TRIP).catch(() => {});
      test.skip((await first.count()) === 0, "没有检索结果（catalog 不可达）");
      const href = (await first.getByRole("heading").getByRole("link").getAttribute("href")) ?? "";
      await page.goto(href, { timeout: 60_000 });
      await waitForTakeover(page, "#masthead");
      const link = publicLink(page, href);

      await page.getByRole("button", { name: "分享" }).click();
      const share = page.getByRole("dialog", { name: "分享" });
      await expect(share.getByRole("textbox", { name: "链接" })).toHaveValue(link);
      await share.getByRole("button", { name: "复制链接" }).click();
      await expect(share.getByRole("button", { name: "已复制" })).toBeVisible();
      expect(await readCopied(page)).toBe(link);
      // 「已复制」1.6 秒后变回原文，免得下面认错按钮
      await expect(share.getByRole("button", { name: "复制链接" })).toBeVisible();

      // 单选框是读屏用的 sr-only，点它所在的标签
      await share.getByText("APA", { exact: true }).click();
      await expect(share.getByRole("radio", { name: "APA" })).toBeChecked();
      const copyCitation = share.getByRole("button", { name: "复制引用" });
      await expect(copyCitation).toBeEnabled(ROUND_TRIP);
      await copyCitation.click();
      await expect(share.getByRole("button", { name: "已复制" })).toBeVisible();
      expect(await readCopied(page)).toMatch(/\(\d{4}\)/);

      const exporting = page.waitForEvent("download", ROUND_TRIP);
      await share.getByRole("button", { name: "RIS" }).click();
      expect(await downloadedText(await exporting)).toMatch(/^TY {2}- /);
    } finally {
      await context.close();
    }
  });

  test("文献页、期刊页的 HTML 带标题、描述、canonical", async ({ page }) => {
    await page.goto("/papers?q=heart");
    const first = page.getByRole("list", { name: "文献列表" }).getByRole("listitem").first();
    await first.waitFor(ROUND_TRIP).catch(() => {});
    test.skip((await first.count()) === 0, "没有检索结果（catalog 不可达）");
    const paperHref =
      (await first.getByRole("heading").getByRole("link").getAttribute("href")) ?? "";
    const paperHtml = await (await page.request.get(paperHref)).text();
    expect(paperHtml).toMatch(/<title>[^<]+ · Patra<\/title>/);
    expect(paperHtml).toMatch(/<meta name="description" content="[^"]+"/);
    expect(paperHtml).toContain(`<link rel="canonical" href="${publicLink(page, paperHref)}"/>`);

    const journalHref = paperHtml.match(/href="(\/journals\/\d+)"/)?.[1];
    test.skip(!journalHref, "这篇文献没有期刊页");
    const journalHtml = await (await page.request.get(journalHref ?? "")).text();
    expect(journalHtml).toMatch(/<title>[^<]+ · Patra<\/title>/);
    expect(journalHtml).toMatch(/<meta name="description" content="[^"]+"/);
    expect(journalHtml).toContain(
      `<link rel="canonical" href="${publicLink(page, journalHref ?? "")}"/>`,
    );
  });
});

test.describe("公开收藏夹", () => {
  test.setTimeout(240_000);
  test.skip(
    !VERIFIED.email || !VERIFIED.password,
    "没有已验证邮箱的测试账号（~/.patra/secrets/e2e.env 里的 PATRA_E2E_VERIFIED_EMAIL / PATRA_E2E_VERIFIED_PASSWORD）",
  );

  test("A 设为公开：匿名与 B 能看、B 能复制；A 关掉公开：匿名与 B 404，A 自己仍能看", async ({
    page,
    browser,
  }) => {
    const name = `组会 e2e ${Date.now()}`;
    let collectionId: string | null = null;
    let savedTitle: string | null = null;
    let failure: unknown;
    const anon = await browser.newContext();
    const other = await browser.newContext();
    await simulateInsecureClipboard(other);
    try {
      // 登录后直接回到检索结果（登录后的客户端跳转还在进行时再 goto 会被中断）
      await login(page, VERIFIED, "/papers?q=heart");
      await expect(page).toHaveURL("/papers?q=heart", ROUND_TRIP);

      // A 为测试新收藏一篇（收尾时移除），建收藏夹并放进去
      await waitForTakeover(page, "main");
      const results = page.getByRole("list", { name: "文献列表" });
      await results.waitFor(ROUND_TRIP);
      savedTitle = await save(page, unsavedButtons(results).first());
      await openLibrary(page);
      await page.getByRole("button", { name: "新建收藏夹" }).first().click();
      await page.getByRole("dialog").getByRole("textbox", { name: "名称" }).fill(name);
      await page.getByRole("dialog").getByRole("button", { name: "创建" }).click();
      await expect(page).toHaveURL(/\/library\/\d+$/, ROUND_TRIP);
      collectionId = new URL(page.url()).pathname.split("/").pop() ?? null;
      await openLibrary(page);
      const mine = libraryItems(page)
        .filter({ hasText: savedTitle.slice(0, 20) })
        .first();
      await mine.getByRole("button", { name: "条目操作" }).click();
      await page.getByRole("menuitem", { name: "放进收藏夹…" }).click();
      await page.getByRole("checkbox", { name: `${name} 私有` }).check();
      await expect(page.getByRole("checkbox", { name: `${name} 私有` })).toBeEnabled(ROUND_TRIP);
      await page.keyboard.press("Escape");

      // 设为公开
      await page.goto(`/library/${collectionId}`);
      await waitForTakeover(page, "main");
      // 打开公开不乐观（可能被资格拒绝）：点下去先是「设置中…」，服务端回来才变成开
      await page.getByRole("switch", { name: "公开" }).click();
      await expect(page.getByRole("switch", { name: "公开" })).toBeChecked(ROUND_TRIP);
      await expect(page.getByRole("textbox", { name: "链接" })).toBeVisible(ROUND_TRIP);

      // 匿名访客能看
      const anonPage = await anon.newPage();
      await anonPage.goto(`/collections/${collectionId}`);
      await expect(anonPage.getByRole("heading", { level: 1, name })).toBeVisible(ROUND_TRIP);
      await expect(anonPage.getByRole("link", { name: "登录后复制" })).toBeVisible();

      // B 能看、能复制
      const otherPage = await other.newPage();
      await register(otherPage, freshAccount());
      await otherPage.goto(`/collections/${collectionId}`);
      await waitForTakeover(otherPage, "main");

      // B 查看并复制（PAP-115 冒烟）：B 的上下文模拟 mini 的 HTTP（剪贴板接口不可用），复制走降级那一步
      const link = publicLink(otherPage, `/collections/${collectionId}`);
      await otherPage.getByRole("button", { name: "分享" }).click();
      const share = otherPage.getByRole("dialog", { name: "分享" });
      await expect(share.getByRole("textbox", { name: "链接" })).toHaveValue(link);
      await share.getByRole("button", { name: "复制链接" }).click();
      await expect(share.getByRole("button", { name: "已复制" })).toBeVisible();
      expect(await readCopied(otherPage)).toBe(link);
      await otherPage.keyboard.press("Escape");

      await otherPage.getByRole("button", { name: "复制引用" }).click();
      const cite = otherPage.getByRole("dialog", { name: /^复制引用/ });
      await cite.getByRole("button", { name: "复制 1 条引用" }).click(ROUND_TRIP);
      await expect(cite.getByRole("button", { name: "已复制" })).toBeVisible();
      expect(await readCopied(otherPage)).toMatch(/^\[1\] .+/);
      await cite.getByRole("button", { name: "关闭" }).click();

      await otherPage.getByRole("button", { name: "导出" }).click();
      const exporting = otherPage.waitForEvent("download", ROUND_TRIP);
      await otherPage
        .getByRole("dialog", { name: "导出" })
        .getByRole("button", { name: "下载 .ris" })
        .click(ROUND_TRIP);
      expect(await downloadedText(await exporting)).toMatch(/^TY {2}- /);

      // 页面信息：标题、描述、canonical 在 HTML 里
      const html = await (await otherPage.request.get(link)).text();
      expect(html).toContain(`<title>${name} · `);
      expect(html).toMatch(/<meta name="description" content="[^"]+"/);
      expect(html).toContain(`<link rel="canonical" href="${link}"/>`);

      await otherPage.getByRole("button", { name: "复制为我的收藏夹" }).click();
      await expect(otherPage.getByText(/^已复制为「/)).toBeVisible(ROUND_TRIP);

      // A 关掉公开：匿名与 B 都是 404；A 自己仍能看（私有提示）
      // 关掉公开是乐观更新：界面先变，要等这次 Server Action 回来，服务端才真的是私有
      const saved = page.waitForResponse(
        (response) =>
          response.request().method() === "POST" &&
          response.request().headers()["next-action"] !== undefined,
      );
      await page.getByRole("switch", { name: "公开" }).uncheck();
      await saved;
      await expect(page.getByText("别人可以通过链接或你的主页看到这个收藏夹。")).toBeVisible(
        ROUND_TRIP,
      );
      expect((await anonPage.goto(`/collections/${collectionId}`))?.status()).toBe(404);
      expect((await otherPage.goto(`/collections/${collectionId}`))?.status()).toBe(404);
      await page.goto(`/collections/${collectionId}`);
      await expect(page.getByText("这个收藏夹是私有的，只有你能看到。")).toBeVisible(ROUND_TRIP);
    } catch (error) {
      failure = error;
    } finally {
      await anon.close();
      await other.close();
      try {
        await cleanUpVerified(page, collectionId, savedTitle);
      } catch (cleanupError) {
        // 收尾也失败时两个错误都要看到：先报用例本身的，收尾的写进说明
        if (failure === undefined) {
          failure = cleanupError;
        } else {
          test.info().annotations.push({ type: "收尾失败", description: String(cleanupError) });
        }
      }
    }
    if (failure !== undefined) {
      throw failure;
    }
  });
});

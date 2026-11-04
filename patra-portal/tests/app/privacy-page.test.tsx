import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/server", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/server")>()),
  connection: vi.fn(async () => {}),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => null }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => null }));

import { connection } from "next/server";
import PrivacyPage, { metadata } from "@/app/privacy/page";

afterEach(() => vi.unstubAllEnvs());

async function renderPage() {
  return render(await PrivacyPage());
}

const SECTIONS = [
  "适用范围",
  "我们收集哪些信息",
  "两类阅读数据",
  "Cookie",
  "共享与委托处理",
  "保留多久",
  "你可以做什么",
  "未成年人",
  "联系我们与政策变更",
];

describe("/privacy", () => {
  it("请求时渲染：Cookie 名按运行时的 Secure 开关算，不能在构建时定死", async () => {
    await renderPage();
    expect(connection).toHaveBeenCalled();
  });

  it("页面标题，公开可收录", () => {
    expect(metadata.title).toBe("隐私政策 · Patra");
    expect(metadata.robots).toBeUndefined();
  });

  it("页头、版本、一分钟看完五条", async () => {
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "隐私政策" })).toBeInTheDocument();
    expect(screen.getByText("第 1 版")).toBeInTheDocument();
    const summary = screen.getByRole("region", { name: "一分钟看完" });
    expect(within(summary).getAllByRole("listitem")).toHaveLength(5);
  });

  it("九节，目录逐一链到各节", async () => {
    await renderPage();
    const toc = screen.getByRole("navigation", { name: "本页目录" });
    const links = within(toc).getAllByRole("link");
    expect(links).toHaveLength(9);
    SECTIONS.forEach((title, i) => {
      const heading = screen.getByRole("heading", { level: 2, name: new RegExp(`${title}$`) });
      expect(heading).toHaveTextContent(String(i + 1).padStart(2, "0"));
      const section = heading.closest("section");
      expect(links[i]).toHaveAttribute("href", `#${section?.id}`);
    });
  });

  it("Cookie 表：Secure 关着时是不带前缀的三个名字，会话标识随浏览器关闭失效", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    await renderPage();
    const cookies = screen.getByRole("heading", { level: 2, name: /Cookie$/ }).closest("section");
    for (const name of [
      "patra_session",
      "patra_device",
      "patra_sid",
      "patra_oauth_state",
      "patra_oauth_return",
      "patra_oauth_ticket",
      "patra_oauth_signed_in",
    ]) {
      expect(within(cookies as HTMLElement).getByText(name)).toBeInTheDocument();
    }
    expect(within(cookies as HTMLElement).getByText("关闭浏览器时失效")).toBeInTheDocument();
  });

  it("Cookie 表：Secure 开着时名字带 __Host- 前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    await renderPage();
    for (const name of ["__Host-patra_session", "__Host-patra_device", "__Host-patra_sid"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
  });

  it("网站域名、运营者、服务器所在地、联系邮箱与日期都已填，页面上没有方括号占位", async () => {
    const { container } = await renderPage();
    expect(screen.getByText("https://patra.linqibin.dev")).toBeInTheDocument();
    expect(container).toHaveTextContent("Patra 由 Patra（个人项目） 开发和运营");
    expect(container).toHaveTextContent("其余数据存放在我们自己的服务器上（中国大陆）");
    expect(screen.getByRole("link", { name: "privacy@example.com" })).toHaveAttribute(
      "href",
      "mailto:privacy@example.com",
    );
    expect(container).toHaveTextContent("生效日期 2026-11-03");
    expect(container).toHaveTextContent("最近更新 2026-11-04");
    expect(container.textContent).not.toMatch(/\[[^\]]+\]/);
  });

  it("第三方登录：写明收集什么、不收什么、对方知道什么、存多久、怎么解绑", async () => {
    const { container } = await renderPage();
    const section = (title: RegExp) =>
      screen.getByRole("heading", { level: 2, name: title }).closest("section") as HTMLElement;
    const collect = section(/我们收集哪些信息$/);
    expect(within(collect).getByText("第三方账号")).toBeInTheDocument();
    expect(collect).toHaveTextContent(
      "用 Google、GitHub 或 ORCID 登录时：对方给的账号编号，以及显示用的账号标识（Google 邮箱、GitHub 用户名、ORCID iD）。对方给的邮箱和 GitHub 用户名只用来预填注册表单",
    );
    expect(collect).toHaveTextContent(
      "我们不保存第三方的访问令牌，也不导入头像、昵称。确认身份之后，令牌就丢弃了，Patra 不能替你访问对方的任何内容。解绑后这条记录立即删除；注销到期时一并删除。",
    );
    expect(section(/共享与委托处理$/)).toHaveTextContent(
      "用 Google、GitHub 或 ORCID 登录时，你会跳到对方的网站授权，对方会知道你在登录 Patra",
    );
    expect(section(/保留多久$/)).toHaveTextContent("第三方账号的绑定");
    expect(section(/Cookie$/)).toHaveTextContent("防止第三方登录被伪造");
    expect(section(/Cookie$/)).toHaveTextContent("第一次用第三方登录时，记住还没填完的注册");
    expect(section(/你可以做什么$/)).toHaveTextContent("解绑第三方账号");
    expect(screen.getByRole("link", { name: "设置 › 安全 › 登录方式" })).toHaveAttribute(
      "href",
      "/settings/security#login-methods",
    );
    expect(container.textContent).not.toMatch(/\[[^\]]+\]/);
  });

  it("「你可以做什么」链到隐私设置与阅读历史", async () => {
    await renderPage();
    expect(screen.getByRole("link", { name: "设置 › 隐私 › 阅读记录" })).toHaveAttribute(
      "href",
      "/settings/privacy#reading",
    );
    expect(screen.getByRole("link", { name: "阅读历史" })).toHaveAttribute("href", "/history");
  });
});

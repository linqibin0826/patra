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
    for (const name of ["patra_session", "patra_device", "patra_sid"]) {
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

  it("网站域名已填；联系邮箱、运营者、服务器所在地还是占位", async () => {
    await renderPage();
    expect(screen.getByText("https://patra.linqibin.dev")).toBeInTheDocument();
    for (const placeholder of ["[联系邮箱]", "[运营者名称]", "[服务器所在地]"]) {
      expect(screen.getByText(placeholder)).toBeInTheDocument();
    }
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

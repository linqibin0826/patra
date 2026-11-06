import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/auth/human-verification", () => ({
  readHumanVerification: async () => ({ provider: "turnstile", siteKey: "site-1" }),
}));
vi.mock(
  "@/components/auth/human-verification/HumanVerificationWidget",
  () => import("@tests/stubs/human-verification-widget"),
);
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), prefetch: vi.fn() }) }));
vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));

import LoginPage, { metadata as loginMetadata } from "@/app/login/page";
import RegisterPage, { metadata as registerMetadata } from "@/app/register/page";

type SearchParams = Record<string, string | string[] | undefined>;

const PAGES = [
  {
    name: "登录页",
    Page: LoginPage,
    metadata: loginMetadata,
    title: "登录 · Patra",
    heading: "登录",
    switchLink: "注册",
    switchPage: "/register",
  },
  {
    name: "注册页",
    Page: RegisterPage,
    metadata: registerMetadata,
    title: "注册 · Patra",
    heading: "注册",
    switchLink: "登录",
    switchPage: "/login",
  },
] as const;

async function renderPage(
  Page: (props: { searchParams: Promise<SearchParams> }) => Promise<ReactElement>,
  searchParams: SearchParams,
) {
  const element = await Page({ searchParams: Promise.resolve(searchParams) });
  render(<QueryClientProvider client={new QueryClient()}>{element}</QueryClientProvider>);
}

beforeEach(() => {
  resetCookieJar({}, { host: "100.103.73.27:4000" });
  window.history.replaceState(null, "", "/");
});

describe.each(PAGES)("$name", ({ Page, metadata, title, heading, switchLink, switchPage }) => {
  it("标题，不让搜索引擎收录", () => {
    expect(metadata).toEqual({ title, robots: { index: false } });
  });

  it("「返回」和互跳链接带上校验过的 next", async () => {
    await renderPage(Page, { next: "/papers?q=GLP-1" });
    expect(screen.getByRole("heading", { level: 1, name: heading })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/papers?q=GLP-1");
    expect(screen.getByRole("link", { name: switchLink })).toHaveAttribute(
      "href",
      `${switchPage}?next=%2Fpapers%3Fq%3DGLP-1`,
    );
  });

  it("next 出现多次时取第一个", async () => {
    await renderPage(Page, { next: ["/journals/7", "/papers"] });
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/journals/7");
  });

  it("next 不合法或没有时，「返回」回首页，互跳链接不带 next", async () => {
    await renderPage(Page, { next: "//evil.com" });
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: switchLink })).toHaveAttribute("href", switchPage);
  });

  it("tailnet 地址：没有第三方入口，副文案不提第三方，邮箱表单照常", async () => {
    await renderPage(Page, {});
    expect(screen.queryByRole("link", { name: /^用 GitHub 账号/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/第三方/)).not.toBeInTheDocument();
    expect(screen.getByLabelText("邮箱")).toBeInTheDocument();
  });

  it("公网域名：有三家入口，副文案提第三方", async () => {
    resetCookieJar({}, { host: "patra.linqibin.dev" });
    await renderPage(Page, { next: "/papers/1" });
    expect(screen.getByRole("link", { name: `用 GitHub 账号${heading}` })).toHaveAttribute(
      "href",
      `/api/auth/oauth/github/start?from=${switchPage === "/login" ? "register" : "login"}&next=%2Fpapers%2F1`,
    );
    expect(screen.getByText(/第三方账号/)).toBeInTheDocument();
  });

  it("从第三方回来：显示结果，地址栏的两个参数被清掉，next 留着", async () => {
    resetCookieJar({}, { host: "patra.linqibin.dev" });
    window.history.replaceState(null, "", "/x?next=%2Fpapers&oauth=cancelled&provider=github");
    await renderPage(Page, { oauth: "cancelled", provider: "github", next: "/papers" });
    expect(screen.getByRole("status")).toHaveTextContent(
      "你取消了 GitHub 授权。可以再试一次，或换一种方式。",
    );
    expect(window.location.search).toBe("?next=%2Fpapers");
  });

  it("失败的结果是 alert；改坏的参数不显示", async () => {
    await renderPage(Page, { oauth: "rejected", provider: "github" });
    expect(screen.getByRole("alert")).toHaveTextContent("这次授权没有成功");
  });

  it("手工改坏的 oauth 参数：不显示提示", async () => {
    await renderPage(Page, { oauth: "evil", provider: "github" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});

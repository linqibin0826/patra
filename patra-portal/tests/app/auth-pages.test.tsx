import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), prefetch: vi.fn() }) }));
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
});

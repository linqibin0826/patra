import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { PUBLIC_PRIVACY } from "@tests/fixtures/profile";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { CurrentUser } from "@/lib/auth/types";
import type { PublicProfile, PublicProfileLookup } from "@/lib/identity/public-profile";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => "/u/chen-yu",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => <nav aria-label="导航栏桩" /> }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => <footer /> }));
vi.mock("@/lib/identity/public-profile", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/identity/public-profile")>()),
  fetchPublicProfile: vi.fn(),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/identity/profile", () => ({ fetchMyPrivacy: vi.fn() }));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));

import NotFoundPage, { metadata as notFoundMetadata } from "@/app/u/[username]/not-found";
import UserPage, { generateMetadata } from "@/app/u/[username]/page";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchMyPrivacy } from "@/lib/identity/profile";
import { fetchPublicProfile } from "@/lib/identity/public-profile";

const params = (username = "chen-yu") => ({ params: Promise.resolve({ username }) });

function found(profile: Partial<PublicProfile> = {}): PublicProfileLookup {
  return { kind: "found", profile: { ...PUBLIC_PROFILE, ...profile } };
}

function viewAs(viewer: "anonymous" | "other" | "self", user: Partial<CurrentUser> = {}) {
  if (viewer === "anonymous") {
    vi.mocked(lookupSession).mockResolvedValue({ status: "anonymous" });
  } else {
    const username = viewer === "self" ? "chen-yu" : "someone-else";
    vi.mocked(lookupSession).mockResolvedValue({
      status: "authenticated",
      user: { ...USER, username, ...user },
    });
  }
}

async function renderPage(username = "chen-yu") {
  const element = await UserPage(params(username));
  const client = new QueryClient();
  const wrap = (node: ReactNode) => (
    <QueryClientProvider client={client}>{node}</QueryClientProvider>
  );
  return render(wrap(element));
}

beforeEach(() => {
  vi.mocked(fetchMyPrivacy).mockResolvedValue(PUBLIC_PRIVACY);
});

afterEach(() => vi.clearAllMocks());

describe("/u/[username] · 取数", () => {
  it("旧用户名：临时跳到新名", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "moved", username: "new-name" });
    await expect(UserPage(params("old-name"))).rejects.toThrow("NEXT_REDIRECT:/u/new-name");
  });

  it("不存在或不可见：404", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "not-found" });
    await expect(UserPage(params("nobody"))).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("404 页：标题与文案，所有原因同一句", () => {
    expect(notFoundMetadata.title).toBe("没有找到这个用户 · Patra");
    render(<NotFoundPage />);
    expect(screen.getByRole("heading", { level: 1, name: "没有找到这个用户" })).toBeInTheDocument();
    expect(screen.getByText("地址可能有误，或者这个主页没有对你公开。")).toBeInTheDocument();
  });

  it("metadata：标题带昵称与用户名；indexable 为 false 时 noindex", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found({ indexable: false }));
    const metadata = await generateMetadata(params());
    expect(metadata.title).toBe("陈宇 (@chen-yu) · Patra");
    expect(metadata.robots).toEqual({ index: false });
    vi.mocked(fetchPublicProfile).mockResolvedValue(found());
    expect((await generateMetadata(params())).robots).toBeUndefined();
  });
});

describe("/u/[username] · 别人视角", () => {
  it("profile.public-full：头部、专业背景、关注领域、链接；不取隐私设置、没有编辑入口", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found());
    viewAs("anonymous");
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "陈宇" })).toBeInTheDocument();
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    expect(screen.getByText("加入于 2026 年 10 月")).toBeInTheDocument();
    const lab = screen.getByRole("link", { name: "实验室主页" });
    expect(lab).toHaveAttribute("rel", "nofollow noopener noreferrer ugc");
    expect(lab).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "scholar.example.com" })).toBeInTheDocument();
    expect(screen.getByText("医师 · 主治医师")).toBeInTheDocument();
    expect(screen.getByText("北京协和医院")).toBeInTheDocument();
    expect(screen.getByText("心内科")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Heart Failure" })).toHaveAttribute(
      "href",
      "/papers?q=Heart%20Failure",
    );
    expect(screen.queryByRole("link", { name: "编辑资料" })).not.toBeInTheDocument();
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
    expect(fetchMyPrivacy).not.toHaveBeenCalled();
  });

  it("profile.public-minimal：新用户只有头部", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(
      found({
        displayName: "chen-yu",
        bio: null,
        links: [],
        professional: null,
        researchInterests: [],
      }),
    );
    viewAs("other");
    await renderPage();
    expect(screen.queryByRole("heading", { name: "专业背景" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "关注领域" })).not.toBeInTheDocument();
    expect(screen.queryByText("你的主页还很空。")).not.toBeInTheDocument();
  });

  it("profile.pro-hidden：professional 为 null 不渲染专业背景", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found({ professional: null }));
    viewAs("other");
    await renderPage();
    expect(screen.queryByRole("heading", { name: "专业背景" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "关注领域" })).toBeInTheDocument();
  });

  it("javascript: 链接不渲染成可点的链接", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(
      found({ links: [{ title: "坏链接", url: "javascript:alert(1)" }] }),
    );
    viewAs("anonymous");
    await renderPage();
    expect(screen.queryByRole("link", { name: "坏链接" })).not.toBeInTheDocument();
  });

  it("profile.long：昵称两行截断", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found({ displayName: "长".repeat(30) }));
    viewAs("anonymous");
    await renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveClass("line-clamp-2");
  });
});

describe("/u/[username] · 本人视角", () => {
  it("编辑资料入口；公开时没有提示条", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found());
    viewAs("self");
    await renderPage();
    expect(screen.getByRole("link", { name: "编辑资料" })).toHaveAttribute(
      "href",
      "/settings/profile",
    );
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });

  it("profile.self-unverified：提示验证、能重发；链接位置写验证后展示", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found({ audience: "SELF", links: [] }));
    viewAs("self", { emailVerified: false });
    await renderPage();
    const note = screen.getByRole("note");
    expect(note).toHaveTextContent("目前只有你能看到这个主页：验证邮箱后，别人才能看到。");
    expect(within(note).getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
    expect(screen.getByText("验证邮箱后展示链接")).toBeInTheDocument();
  });

  it.each([
    ["SELF", "目前只有你能看到这个主页。"],
    ["SIGNED_IN", "目前只有登录用户能看到这个主页。"],
  ] as const)("profile.self-%s：提示条 + 更改可见性", async (audience, text) => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found({ audience }));
    viewAs("self");
    await renderPage();
    const note = screen.getByRole("note");
    expect(note).toHaveTextContent(text);
    expect(within(note).getByRole("link", { name: "更改可见性" })).toHaveAttribute(
      "href",
      "/settings/privacy",
    );
  });

  it("profile.self-pro-hidden：专业背景仅自己可见时标注「仅你可见」", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(found());
    vi.mocked(fetchMyPrivacy).mockResolvedValue({
      ...PUBLIC_PRIVACY,
      professionalVisibility: "SELF",
    });
    viewAs("self");
    await renderPage();
    expect(
      within(screen.getByRole("heading", { name: /专业背景/ })).getByText("仅你可见"),
    ).toBeInTheDocument();
  });

  it("profile.self-empty：你的主页还很空 + 完善资料", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue(
      found({ professional: null, researchInterests: [] }),
    );
    viewAs("self");
    await renderPage();
    expect(screen.getByText("你的主页还很空。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "完善资料" })).toHaveAttribute(
      "href",
      "/settings/profile",
    );
  });
});

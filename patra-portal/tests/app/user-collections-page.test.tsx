import { screen } from "@testing-library/react";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { USER } from "@tests/fixtures/user";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  usePathname: () => "/u/chen-yu/collections",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => <nav aria-label="导航栏桩" /> }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => <footer /> }));
vi.mock("@/lib/identity/public-profile", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/identity/public-profile")>()),
  fetchPublicProfile: vi.fn(),
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/interaction/collections", () => ({ fetchUserPublicCollections: vi.fn() }));
vi.mock("@/lib/interaction/experience", () => ({ fetchUserExperiences: vi.fn() }));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));

import CollectionsPage, { generateMetadata } from "@/app/u/[username]/collections/page";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";
import { fetchUserExperiences } from "@/lib/interaction/experience";

const params = (username = "chen-yu") => ({ params: Promise.resolve({ username }) });

function viewAs(self: boolean) {
  vi.mocked(lookupSession).mockResolvedValue({
    status: "authenticated",
    user: { ...USER, username: self ? "chen-yu" : "someone-else" },
  });
}

const CARD = {
  collectionId: "9",
  name: "组会",
  description: "每周四组会要讲的文献",
  itemCount: 23,
  updatedAt: "2026-10-24T02:00:00Z",
};

beforeEach(() => {
  vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "found", profile: PUBLIC_PROFILE });
  vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "ok", data: [CARD] });
  vi.mocked(fetchUserExperiences).mockResolvedValue({
    kind: "ok",
    data: { items: [], nextCursor: null },
  });
});

async function renderPage() {
  return renderWithQuery(await CollectionsPage(params()));
}

describe("/u/[username]/collections", () => {
  it("旧用户名：跳到新名的收藏夹标签", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "moved", username: "new-name" });
    viewAs(false);
    await expect(CollectionsPage(params("old-name"))).rejects.toThrow(
      "NEXT_REDIRECT:/u/new-name/collections",
    );
  });

  it("主页不可见：404", async () => {
    vi.mocked(fetchPublicProfile).mockResolvedValue({ kind: "not-found" });
    viewAs(false);
    await expect(CollectionsPage(params())).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("标签栏总在，「收藏夹」是当前；卡片整卡链到公开收藏夹页", async () => {
    viewAs(false);
    await renderPage();
    expect(screen.getByRole("link", { name: "收藏夹" })).toHaveAttribute("aria-current", "page");
    const card = screen.getByRole("link", { name: /组会/ });
    expect(card).toHaveAttribute("href", "/collections/9");
    expect(screen.getByText("23 篇 · 更新于 10 月 24 日")).toBeInTheDocument();
    expect(screen.queryByText("这里只显示公开的收藏夹。")).not.toBeInTheDocument();
  });

  it("别人视角：没有署名经验时不出「投稿经验」标签（PAP-125）", async () => {
    viewAs(false);
    await renderPage();
    expect(screen.queryByRole("link", { name: "投稿经验" })).not.toBeInTheDocument();
  });

  it("本人视角：三个标签都在", async () => {
    viewAs(true);
    await renderPage();
    expect(screen.getByRole("link", { name: "投稿经验" })).toHaveAttribute(
      "href",
      "/u/chen-yu/experiences",
    );
  });

  it("本人视角：提示行与管理入口", async () => {
    viewAs(true);
    await renderPage();
    expect(screen.getByText("这里只显示公开的收藏夹。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "管理收藏夹" })).toHaveAttribute("href", "/library");
  });

  it("本人为空：空状态与去文献库", async () => {
    viewAs(true);
    vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "ok", data: [] });
    await renderPage();
    expect(screen.getByText("还没有公开的收藏夹")).toBeInTheDocument();
    expect(screen.getByText("在文献库里把收藏夹设为公开，它就会出现在这里。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去文献库" })).toHaveAttribute("href", "/library");
  });

  it("别人为空：只有一句", async () => {
    viewAs(false);
    vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "ok", data: [] });
    await renderPage();
    expect(screen.getByText("还没有公开的收藏夹")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "去文献库" })).not.toBeInTheDocument();
  });

  it("失败：错误块与重试", async () => {
    viewAs(false);
    vi.mocked(fetchUserPublicCollections).mockResolvedValue({ kind: "unavailable" });
    await renderPage();
    expect(screen.getByRole("heading", { name: "收藏夹暂时打不开" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "重试" })).toHaveAttribute(
      "href",
      "/u/chen-yu/collections",
    );
  });

  it("标题；不允许收录时 noindex", async () => {
    const metadata = await generateMetadata(params());
    expect(metadata.title).toBe("陈宇 (@chen-yu) 的收藏夹 · Patra");
    vi.mocked(fetchPublicProfile).mockResolvedValue({
      kind: "found",
      profile: { ...PUBLIC_PROFILE, indexable: false },
    });
    expect((await generateMetadata(params())).robots).toEqual({ index: false });
  });
});

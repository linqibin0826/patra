import { screen, within } from "@testing-library/react";
import { makePublicCollection, makePublicItem } from "@tests/fixtures/interaction";
import { makePaper } from "@tests/fixtures/paper";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { USER } from "@tests/fixtures/user";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  usePathname: () => "/collections/9",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => <nav aria-label="导航栏桩" /> }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => <footer /> }));
vi.mock("@/lib/auth/current-user", () => ({ lookupSession: vi.fn() }));
vi.mock("@/lib/interaction/collections", () => ({
  fetchPublicCollection: vi.fn(),
  fetchPublicCollectionPage: vi.fn(),
}));
vi.mock("@/lib/interaction/collection-actions", () => ({ copyCollection: vi.fn() }));
vi.mock("@/lib/identity/public-profile", () => ({ fetchPublicProfile: vi.fn() }));
vi.mock("@/lib/auth/gateway-response", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/auth/gateway-response")>()),
  logRequestError: vi.fn(),
}));
vi.mock("@/lib/public-url", () => ({
  publicUrl: vi.fn(async (path: string) => `http://mini${path}`),
}));

import CollectionPage, { generateMetadata } from "@/app/collections/[id]/page";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { fetchPublicCollection, fetchPublicCollectionPage } from "@/lib/interaction/collections";

const params = (id = "9", query: Record<string, string> = {}) => ({
  params: Promise.resolve({ id }),
  searchParams: Promise.resolve(query),
});

const ITEMS = {
  page: 1,
  pageSize: 50,
  total: 2,
  totalPages: 1,
  items: [
    {
      item: makePublicItem({ key: "11" }),
      paper: makePaper({ id: "11", title: "Paper one", publishedDate: "2019-03" }),
      stats: { collectorCount: 4, discussionCount: 2 },
    },
    {
      item: makePublicItem({ itemId: "502", key: null, state: "UNAVAILABLE", title: "旧标题" }),
      paper: null,
      stats: null,
    },
  ],
};

function viewAs(viewer: "anonymous" | "other" | "owner") {
  vi.mocked(lookupSession).mockResolvedValue(
    viewer === "anonymous" ? { status: "anonymous" } : { status: "authenticated", user: USER },
  );
  vi.mocked(fetchPublicCollection).mockResolvedValue({
    kind: "ok",
    data: makePublicCollection({ viewerIsOwner: viewer === "owner" }),
  });
}

async function renderPage(id = "9", query: Record<string, string> = {}) {
  return renderWithQuery(await CollectionPage(params(id, query)));
}

beforeEach(() => {
  vi.mocked(fetchPublicCollectionPage).mockResolvedValue({ kind: "ok", data: ITEMS });
});

describe("/collections/[id]", () => {
  it("不存在、私有、对你不可见：404", async () => {
    viewAs("anonymous");
    vi.mocked(fetchPublicCollection).mockResolvedValue({ kind: "not-found" });
    await expect(CollectionPage(params())).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("interaction 不可用：错误块，不是 404", async () => {
    viewAs("anonymous");
    vi.mocked(fetchPublicCollection).mockResolvedValue({ kind: "unavailable" });
    await renderPage();
    expect(screen.getByRole("heading", { name: "收藏夹暂时打不开" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "重试" })).toHaveAttribute("href", "/collections/9");
  });

  it("头部：眉题、名称、简介、所有者行", async () => {
    viewAs("anonymous");
    await renderPage();
    expect(screen.getByText("收藏夹 · COLLECTION")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "组会" })).toBeInTheDocument();
    expect(screen.getByText("每周四组会要讲的文献，按主题分好。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "陈宇" })).toHaveAttribute(
      "href",
      "/u/chen-yu/collections",
    );
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    expect(screen.getByText(/23 篇 · 更新于 2026 年 10 月 24 日/)).toBeInTheDocument();
  });

  it("匿名：登录后复制、导出、复制引用、分享", async () => {
    viewAs("anonymous");
    await renderPage();
    expect(screen.getByRole("link", { name: "登录后复制" })).toHaveAttribute(
      "href",
      "/login?next=%2Fcollections%2F9",
    );
    expect(screen.getByRole("button", { name: "导出" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "复制引用" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "分享" })).toBeInTheDocument();
  });

  it("已登录的别人：复制为我的收藏夹", async () => {
    viewAs("other");
    await renderPage();
    expect(screen.getByRole("button", { name: "复制为我的收藏夹" })).toBeInTheDocument();
  });

  it("本人：提示条与在文献库中管理", async () => {
    viewAs("owner");
    await renderPage();
    expect(screen.getByText("这是你的收藏夹，别人看到的就是这样。")).toBeInTheDocument();
    for (const link of screen.getAllByRole("link", { name: "在文献库中管理" })) {
      expect(link).toHaveAttribute("href", "/library/9");
    }
  });

  it("本人看私有的：提示条换文案", async () => {
    viewAs("owner");
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection({ viewerIsOwner: true, visibility: "PRIVATE" }),
    });
    await renderPage();
    expect(screen.getByText("这个收藏夹是私有的，只有你能看到。")).toBeInTheDocument();
  });

  it("本人且 identity 不可用（owner 为 null）：所有者行用当前用户", async () => {
    viewAs("owner");
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection({ viewerIsOwner: true, owner: null }),
    });
    await renderPage();
    expect(screen.getByRole("link", { name: USER.displayName })).toBeInTheDocument();
  });

  it("所有者已注销：占位，不显示用户名", async () => {
    viewAs("anonymous");
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection({
        owner: {
          userId: "1",
          username: null,
          displayName: null,
          avatarUrl: null,
          avatarPalette: 0,
          deleted: true,
        },
      }),
    });
    await renderPage();
    expect(screen.getByText("已注销用户")).toBeInTheDocument();
    expect(screen.queryByText(/^@/)).not.toBeInTheDocument();
  });

  it("条目：日期按精度、收藏按钮；已失效的不是链接；不显示备注", async () => {
    viewAs("anonymous");
    await renderPage();
    const list = screen.getByRole("list", { name: "收藏夹条目" });
    expect(within(list).getByRole("link", { name: "Paper one" })).toHaveAttribute(
      "href",
      "/papers/11",
    );
    expect(within(list).getByText("2019 年 3 月")).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: "收藏「Paper one」" })).toBeInTheDocument();
    expect(within(list).getByText("旧标题")).toBeInTheDocument();
    expect(within(list).getByText("已失效")).toBeInTheDocument();
  });

  it("should_show_stats_on_public_collection_entries", async () => {
    viewAs("anonymous");
    await renderPage();
    const list = screen.getByRole("list", { name: "收藏夹条目" });
    expect(within(list).getByText("4 人收藏")).toBeInTheDocument();
    expect(within(list).getByRole("link", { name: "2 条讨论" })).toHaveAttribute(
      "href",
      "/papers/11#discussion",
    );
  });

  it("空收藏夹", async () => {
    viewAs("anonymous");
    vi.mocked(fetchPublicCollectionPage).mockResolvedValue({
      kind: "ok",
      data: { ...ITEMS, total: 0, items: [] },
    });
    await renderPage();
    expect(screen.getByText("这个收藏夹还没有文献。")).toBeInTheDocument();
  });

  it("排序透传", async () => {
    viewAs("anonymous");
    await renderPage("9", { sort: "journal", page: "2" });
    expect(fetchPublicCollectionPage).toHaveBeenCalledWith("9", "journal", 2);
  });
});

describe("/collections/[id] 页面信息", () => {
  beforeEach(() => {
    vi.mocked(fetchPublicProfile).mockReset();
  });

  const meta = (id = "9") => generateMetadata({ params: Promise.resolve({ id }) });

  it("empty on not-found", async () => {
    vi.mocked(fetchPublicCollection).mockResolvedValue({ kind: "not-found" });
    expect(await meta()).toEqual({});
  });

  it("noindex placeholder when interaction is unavailable", async () => {
    vi.mocked(fetchPublicCollection).mockResolvedValue({ kind: "unavailable" });
    expect(await meta()).toEqual({
      title: "收藏夹暂时打不开 · Patra",
      robots: { index: false },
    });
  });

  it("indexable owner: canonical, no robots", async () => {
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection(),
    });
    vi.mocked(fetchPublicProfile).mockResolvedValue({
      kind: "found",
      profile: { ...PUBLIC_PROFILE, indexable: true },
    });
    const result = await meta();
    expect(result.title).toBe("组会 · 陈宇的收藏夹 · Patra");
    expect(result.alternates?.canonical).toBe("http://mini/collections/9");
    expect(result).not.toHaveProperty("robots");
    expect(fetchPublicProfile).toHaveBeenCalledWith("chen-yu");
  });

  it("noindex when owner hides from search", async () => {
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection(),
    });
    vi.mocked(fetchPublicProfile).mockResolvedValue({
      kind: "found",
      profile: { ...PUBLIC_PROFILE, indexable: false },
    });
    expect((await meta()).robots).toEqual({ index: false });
  });

  it("noindex when owner deleted or null", async () => {
    const owner = makePublicCollection().owner;
    vi.mocked(fetchPublicCollection).mockResolvedValueOnce({
      kind: "ok",
      data: makePublicCollection({
        owner: owner && { ...owner, deleted: true, username: null, displayName: null },
      }),
    });
    expect((await meta()).robots).toEqual({ index: false });
    vi.mocked(fetchPublicCollection).mockResolvedValueOnce({
      kind: "ok",
      data: makePublicCollection({ owner: null }),
    });
    expect((await meta()).robots).toEqual({ index: false });
    expect(fetchPublicProfile).not.toHaveBeenCalled();
  });

  it("falls back to noindex when identity throws", async () => {
    vi.mocked(fetchPublicCollection).mockResolvedValue({
      kind: "ok",
      data: makePublicCollection(),
    });
    vi.mocked(fetchPublicProfile).mockRejectedValue(new Error("identity 503"));
    const result = await meta();
    expect(result.robots).toEqual({ index: false });
    expect(result.title).toBe("组会 · 陈宇的收藏夹 · Patra");
  });
});

import { screen, waitFor, within } from "@testing-library/react";
import {
  makeCollection,
  makeItemStatus,
  makeLibraryItem,
  makeOverview,
} from "@tests/fixtures/interaction";
import { makePaper } from "@tests/fixtures/paper";
import { renderWithLibrary } from "@tests/support/library";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { LibraryView } from "@/components/library/LibraryView";
import { addToCollection, removeFromCollection } from "@/lib/interaction/collection-actions";
import type { LibraryEntry } from "@/lib/interaction/library";
import { collectPublication, removeItems } from "@/lib/interaction/library-actions";
import type { Page } from "@/lib/interaction/schema";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/interaction/library-actions", () => ({
  collectPublication: vi.fn(),
  removeItems: vi.fn(),
  saveNote: vi.fn(),
}));
vi.mock("@/lib/interaction/collection-actions", () => ({
  addToCollection: vi.fn(),
  removeFromCollection: vi.fn(),
  createCollection: vi.fn(),
  updateCollection: vi.fn(),
  deleteCollection: vi.fn(),
  setVisibility: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
const push = vi.fn();
let pathname = "/library";
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push, replace: push }),
  usePathname: () => pathname,
}));

const remove = vi.mocked(removeItems);
const removeFrom = vi.mocked(removeFromCollection);

beforeEach(() => {
  pathname = "/library";
  remove.mockReset();
  removeFrom.mockReset();
  push.mockReset();
  vi.mocked(toast.success).mockReset();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(null, { status: 502 })),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const OVERVIEW = makeOverview({
  collections: [
    makeCollection({ collectionId: "9", name: "组会" }),
    makeCollection({ collectionId: "10", name: "心衰综述", visibility: "PUBLIC" }),
  ],
});

const paper = makePaper({
  id: "11",
  title: "Semaglutide in heart failure",
  journal: "Lancet",
  publishedDate: "2019-03",
  authors: ["Chen Y", "Li M", "Wang X"],
});

function page(items: LibraryEntry[], total = items.length): Page<LibraryEntry> {
  return { page: 1, pageSize: 50, total, totalPages: Math.ceil(total / 50), items };
}

const ACTIVE: LibraryEntry = {
  item: makeLibraryItem({ itemId: "501", key: "11", collectionIds: ["9", "10"], note: null }),
  paper,
  stats: null,
};

function renderView(view: string, entries: LibraryEntry[], total?: number) {
  return renderWithLibrary(<LibraryView view={view} sort="added" page={1} />, {
    overview: OVERVIEW,
    items: { view, sort: "added", page: 1, data: page(entries, total) },
  });
}

describe("LibraryView 视图与工具条", () => {
  it("全部：标题、说明与篇数", () => {
    renderView("all", [ACTIVE], 23);
    expect(screen.getByRole("heading", { name: "全部文献" })).toBeInTheDocument();
    expect(screen.getByText("收藏的每一篇都在这里。")).toBeInTheDocument();
    expect(screen.getByText("23 篇")).toBeInTheDocument();
  });

  it("未分类：标题与说明", () => {
    renderView("unfiled", [ACTIVE]);
    expect(screen.getByRole("heading", { name: "未分类" })).toBeInTheDocument();
    expect(screen.getByText("不在任何收藏夹里的文献。")).toBeInTheDocument();
  });

  it("排序写进地址", async () => {
    const { user } = renderView("all", [ACTIVE]);
    await user.click(screen.getByRole("combobox", { name: "排序" }));
    await user.click(await screen.findByRole("option", { name: "出版日期" }));
    expect(push).toHaveBeenCalledWith("/library?sort=published");
  });

  it.each([
    [
      "all",
      "文献库还是空的",
      "在检索结果、首页或文献页点「收藏」，文献就会收进这里。",
      "去检索文献",
    ],
    ["unfiled", "都已整理好了", "每一篇都在至少一个收藏夹里。", null],
    [
      "9",
      "这个收藏夹还没有文献",
      "在文献页的收藏浮层里勾选它，或者在文献库里选中文献，点「放进收藏夹」。",
      "去全部文献",
    ],
  ])("空状态 %s", (view, title, body, action) => {
    renderView(view, []);
    expect(screen.getByRole("heading", { name: title })).toBeInTheDocument();
    expect(screen.getByText(body)).toBeInTheDocument();
    if (action) {
      expect(screen.getByRole("link", { name: action })).toBeInTheDocument();
    }
  });

  it("取不到：错误块与重试", async () => {
    renderWithLibrary(<LibraryView view="all" sort="added" page={1} />, { overview: OVERVIEW });
    expect(await screen.findByRole("heading", { name: "文献库暂时打不开" })).toBeInTheDocument();
    expect(screen.getByText("请稍后再试，你收藏的文献都还在。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });
});

describe("LibraryItem", () => {
  it("标题链到详情、日期按精度、作者前两位、归属与收藏时间", () => {
    renderView("all", [ACTIVE]);
    const item = screen.getByRole("article");
    expect(
      within(item).getByRole("link", { name: "Semaglutide in heart failure" }),
    ).toHaveAttribute("href", "/papers/11");
    expect(within(item).getByText("2019 年 3 月")).toBeInTheDocument();
    expect(within(item).getByText(/Chen Y, Li M 等 1 位作者/)).toBeInTheDocument();
    expect(within(item).getByRole("link", { name: /组会/ })).toHaveAttribute("href", "/library/9");
    expect(within(item).getByRole("link", { name: /心衰综述/ })).toBeInTheDocument();
    expect(within(item).getByText("10 月 24 日收藏")).toBeInTheDocument();
  });

  it("收藏夹视图：不显示当前夹，时间写「加入」", () => {
    pathname = "/library/9";
    renderView("9", [{ ...ACTIVE, item: { ...ACTIVE.item, addedAt: "2026-10-25T02:00:00Z" } }]);
    const item = screen.getByRole("article");
    expect(within(item).queryByRole("link", { name: /组会/ })).not.toBeInTheDocument();
    expect(within(item).getByText("10 月 25 日加入")).toBeInTheDocument();
  });

  it("有备注：显示备注", () => {
    renderView("all", [{ ...ACTIVE, item: { ...ACTIVE.item, note: "第 3 张表组会讲" } }]);
    expect(screen.getByText("第 3 张表组会讲")).toBeInTheDocument();
  });

  it("已失效：快照标题不是链接，标「已失效」与说明", () => {
    renderView("all", [
      {
        item: makeLibraryItem({ itemId: "503", key: null, state: "UNAVAILABLE", title: "旧标题" }),
        paper: null,
        stats: null,
      },
    ]);
    const item = screen.getByRole("article");
    expect(within(item).getByText("旧标题")).not.toHaveAttribute("href");
    expect(within(item).queryByRole("link", { name: "旧标题" })).not.toBeInTheDocument();
    expect(within(item).getByText("已失效")).toBeInTheDocument();
    expect(
      within(item).getByText("这篇文献已从数据源下线，恢复后会自动显示。"),
    ).toBeInTheDocument();
  });
});

describe("条目菜单", () => {
  it("菜单项与顺序", async () => {
    pathname = "/library/9";
    const { user } = renderView("9", [ACTIVE]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
      "放进收藏夹…",
      "编辑备注",
      "复制引用",
      "导出…",
      "移出「组会」",
      "从文献库移除",
    ]);
  });

  it("已失效：只有编辑备注、移出、移除", async () => {
    pathname = "/library/9";
    const { user } = renderView("9", [
      {
        item: makeLibraryItem({ key: null, state: "UNAVAILABLE", title: "旧" }),
        paper: null,
        stats: null,
      },
    ]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
      "编辑备注",
      "移出「组会」",
      "从文献库移除",
    ]);
  });

  it("在收藏夹里的：移除前确认，确认后从列表消失", async () => {
    remove.mockResolvedValue({ ok: true });
    const { user, client } = renderView("all", [ACTIVE]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "从文献库移除" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("移除后，这篇会移出 2 个收藏夹。")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "移除" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(["501"]));
    await waitFor(() => expect(screen.queryByRole("article")).not.toBeInTheDocument());
    expect(toast.success).toHaveBeenCalledWith("已从文献库移除");
    expect(client.getQueryState(queryKeys.libraryOverview)?.isInvalidated).toBe(true);
  });

  it("不在夹里、没有备注：立即移除，toast 带撤销", async () => {
    remove.mockResolvedValue({ ok: true });
    const { user } = renderView("all", [
      { ...ACTIVE, item: { ...ACTIVE.item, collectionIds: [] } },
    ]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "从文献库移除" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(["501"]));
    expect(toast.success).toHaveBeenCalledWith(
      "已从文献库移除",
      expect.objectContaining({ action: expect.objectContaining({ label: "撤销" }) }),
    );
  });

  it("移出当前收藏夹：从列表消失", async () => {
    pathname = "/library/9";
    removeFrom.mockResolvedValue({ ok: true });
    const { user } = renderView("9", [ACTIVE]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "移出「组会」" }));
    await waitFor(() => expect(removeFrom).toHaveBeenCalledWith("9", ["501"]));
    await waitFor(() => expect(screen.queryByRole("article")).not.toBeInTheDocument());
  });

  it("编辑备注：条目内展开编辑框", async () => {
    const { user } = renderView("all", [ACTIVE]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "编辑备注" }));
    expect(await screen.findByRole("textbox", { name: "备注" })).toBeInTheDocument();
  });

  it("导出…：以这一篇为范围打开导出对话框", async () => {
    const { user } = renderView("all", [ACTIVE]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "导出…" }));
    const dialog = await screen.findByRole("dialog", { name: "导出" });
    expect(within(dialog).getByText("这一篇：Semaglutide in heart failure")).toBeInTheDocument();
  });
});

describe("评审修复", () => {
  it("单条放进收藏夹：连勾两个夹，两个都在（不用打开浮层那一刻的旧快照）", async () => {
    vi.mocked(addToCollection).mockResolvedValue({
      ok: true,
      result: { added: 1, alreadyIn: 0, skippedFull: 0, skippedNotInLibrary: 0 },
    });
    const { user } = renderView("all", [
      { ...ACTIVE, item: { ...ACTIVE.item, collectionIds: [] } },
    ]);
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "放进收藏夹…" }));
    await user.click(await screen.findByRole("checkbox", { name: "组会 私有" }));
    await waitFor(() => expect(screen.getByRole("checkbox", { name: "组会 私有" })).toBeEnabled());
    await user.click(screen.getByRole("checkbox", { name: "心衰综述 公开" }));
    await waitFor(() =>
      expect(screen.getByRole("checkbox", { name: "心衰综述 公开" })).toBeEnabled(),
    );
    const item = screen.getByRole("article");
    await waitFor(() =>
      expect(within(item).getByRole("link", { name: /组会/ })).toBeInTheDocument(),
    );
    expect(within(item).getByRole("link", { name: /心衰综述/ })).toBeInTheDocument();
  });

  it("菜单移出当前收藏夹清空了这一页：回到上一页", async () => {
    pathname = "/library/9";
    removeFrom.mockResolvedValue({ ok: true });
    const { user } = renderWithLibrary(<LibraryView view="9" sort="added" page={2} />, {
      overview: OVERVIEW,
      items: { view: "9", sort: "added", page: 2, data: { ...page([ACTIVE], 51), page: 2 } },
    });
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "移出「组会」" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/library/9"));
  });

  it("移除后点撤销：重新收藏，并把「我的状态」改回已收藏", async () => {
    remove.mockResolvedValue({ ok: true });
    vi.mocked(collectPublication).mockResolvedValue({
      ok: true,
      created: true,
      item: makeLibraryItem({ itemId: "601", key: "11" }),
    });
    const { user, client } = renderWithLibrary(<LibraryView view="all" sort="added" page={1} />, {
      overview: OVERVIEW,
      statuses: [makeItemStatus({ key: "11", collected: true, itemId: "501" })],
      items: {
        view: "all",
        sort: "added",
        page: 1,
        data: page([{ ...ACTIVE, item: { ...ACTIVE.item, collectionIds: [] } }]),
      },
    });
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "从文献库移除" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    const options = vi.mocked(toast.success).mock.calls[0]?.[1] as unknown as {
      action: { onClick: () => void };
    };
    options.action.onClick();
    await waitFor(() =>
      expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toMatchObject([
        { key: "11", collected: true, itemId: "601" },
      ]),
    );
  });

  it("移出当前收藏夹：「我的状态」里这篇的收藏夹同步去掉", async () => {
    pathname = "/library/9";
    removeFrom.mockResolvedValue({ ok: true });
    const { user, client } = renderWithLibrary(<LibraryView view="9" sort="added" page={1} />, {
      overview: OVERVIEW,
      statuses: [
        makeItemStatus({ key: "11", collected: true, itemId: "501", collectionIds: ["9", "10"] }),
      ],
      items: { view: "9", sort: "added", page: 1, data: page([ACTIVE]) },
    });
    await user.click(screen.getByRole("button", { name: "条目操作" }));
    await screen.findByRole("menu");
    await user.click(screen.getByRole("menuitem", { name: "移出「组会」" }));
    await waitFor(() =>
      expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toMatchObject([
        { key: "11", collectionIds: ["10"] },
      ]),
    );
  });
});

describe("LibraryView · 公开计数", () => {
  it("should_show_stats_of_entry_after_meta_and_before_membership", () => {
    renderView("all", [{ ...ACTIVE, stats: { collectorCount: 3, discussionCount: 1 } }]);
    const item = screen.getByRole("article");
    expect(within(item).getByText("3 人收藏")).toBeInTheDocument();
    expect(within(item).getByRole("link", { name: "1 条讨论" })).toHaveAttribute(
      "href",
      "/papers/11#discussion",
    );
  });

  it("should_not_show_stats_when_unavailable", () => {
    renderView("all", [ACTIVE]);
    expect(
      within(screen.getByRole("article")).queryByText(/人收藏|条讨论/),
    ).not.toBeInTheDocument();
  });
});

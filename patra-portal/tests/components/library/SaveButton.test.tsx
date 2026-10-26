import { screen, waitFor } from "@testing-library/react";
import { makeItemStatus, makeLibraryItem, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SaveButton } from "@/components/library/SaveButton";
import { collectPublication } from "@/lib/interaction/library-actions";
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
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
const push = vi.fn();
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push }),
}));

const TITLE =
  "Semaglutide and cardiovascular outcomes in obesity without diabetes: a randomized trial";
const collect = vi.mocked(collectPublication);

beforeEach(() => {
  collect.mockReset();
  push.mockReset();
});

const overview = makeOverview({ collections: [] });

describe("SaveButton", () => {
  it("save.idle：胶囊文字「收藏」，读屏名带标题前 40 字", () => {
    renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="pill" />, {
      statuses: [makeItemStatus({ key: "11" })],
    });
    const button = screen.getByRole("button", { name: `收藏「${TITLE.slice(0, 40)}」` });
    expect(button).toHaveTextContent("收藏");
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("save.saved：「已收藏」，aria-pressed=true，读屏名「已收藏，管理收藏」", () => {
    renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="pill" />, {
      statuses: [makeItemStatus({ key: "11", collected: true, itemId: "501" })],
    });
    const button = screen.getByRole("button", { name: "已收藏，管理收藏" });
    expect(button).toHaveTextContent("已收藏");
    expect(button).toHaveAttribute("aria-pressed", "true");
  });

  it("save.pending-status：不响应点击", async () => {
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      user: undefined,
    });
    const button = screen.getByRole("button", { name: /收藏/ });
    expect(button).toHaveAttribute("aria-disabled", "true");
    await user.click(button);
    expect(collect).not.toHaveBeenCalled();
  });

  it("save.anon：点击去登录，带回当前路径", async () => {
    window.history.replaceState(null, "", "/papers?q=GLP-1");
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      user: null,
      statuses: [],
    });
    await user.click(screen.getByRole("button", { name: /收藏/ }));
    expect(push).toHaveBeenCalledWith("/login?next=%2Fpapers%3Fq%3DGLP-1");
    expect(collect).not.toHaveBeenCalled();
  });

  it("save.unavailable：胶囊文字「收藏暂不可用」；图标形态 title 同文", () => {
    renderWithLibrary(
      <>
        <SaveButton paperKey="99" title={TITLE} variant="pill" />
        <SaveButton paperKey="99" title={TITLE} variant="icon" />
      </>,
      { statuses: [makeItemStatus({ key: "11" })] },
    );
    const [pill, icon] = screen.getAllByRole("button", { name: "收藏暂不可用" });
    expect(pill).toHaveTextContent("收藏暂不可用");
    expect(pill).toHaveAttribute("aria-disabled", "true");
    expect(icon).toHaveAttribute("title", "收藏暂不可用");
  });

  it("点 idle：立即变已收藏并打开浮层，调收藏", async () => {
    collect.mockResolvedValue({ ok: true, created: true, item: makeLibraryItem() });
    const { user, client } = renderWithLibrary(
      <SaveButton paperKey="11" title={TITLE} variant="pill" />,
      { statuses: [makeItemStatus({ key: "11" })], overview },
    );
    await user.click(screen.getByRole("button", { name: /^收藏「/ }));
    expect(screen.getByRole("button", { name: "已收藏，管理收藏" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(await screen.findByText("已收进文献库")).toBeInTheDocument();
    expect(collect).toHaveBeenCalledWith("11");
    await waitFor(() =>
      expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toEqual([
        makeItemStatus({
          key: "11",
          collected: true,
          itemId: "501",
          collectedAt: "2026-10-24T02:00:00Z",
        }),
      ]),
    );
  });

  it("点 saved：只打开浮层，不再调收藏", async () => {
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      statuses: [
        makeItemStatus({
          key: "11",
          collected: true,
          itemId: "501",
          collectedAt: "2026-10-24T02:00:00Z",
        }),
      ],
      overview,
    });
    await user.click(screen.getByRole("button", { name: "已收藏，管理收藏" }));
    expect(await screen.findByText("已在文献库中")).toBeInTheDocument();
    expect(screen.getByText("10 月 24 日收藏")).toBeInTheDocument();
    expect(collect).not.toHaveBeenCalled();
  });

  it("opens popover for collected unavailable item：已失效的已收藏文献照样打开浮层", async () => {
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      statuses: [
        makeItemStatus({
          key: "11",
          collected: true,
          itemId: "501",
          collectedAt: "2026-10-24T02:00:00Z",
        }),
      ],
      overview,
    });
    await user.click(screen.getByRole("button", { name: "已收藏，管理收藏" }));
    expect(await screen.findByRole("button", { name: "从文献库移除" })).toBeInTheDocument();
  });

  it.each([
    ["library-full", "文献库已满 20,000 篇，先移除一些再收藏", false],
    ["gone", "这篇文献已下线，无法收藏", false],
    ["catalog-unavailable", "暂时无法收藏这篇文献，请稍后再试", true],
    ["unavailable", "没有收藏成功", true],
  ] as const)("收藏失败 %s：浮层头部「%s」，按钮回 idle", async (reason, text, retry) => {
    collect.mockResolvedValue({ ok: false, reason });
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      statuses: [makeItemStatus({ key: "11" })],
      overview,
    });
    await user.click(screen.getByRole("button", { name: /^收藏「/ }));
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重试" }) !== null).toBe(retry);
    expect(screen.getByRole("button", { name: /^收藏「/ })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("redirects to login on signed-out：会话失效时去登录", async () => {
    window.history.replaceState(null, "", "/papers/11");
    collect.mockResolvedValue({ ok: false, reason: "signed-out" });
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      statuses: [makeItemStatus({ key: "11" })],
      overview,
    });
    await user.click(screen.getByRole("button", { name: /^收藏「/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/login?next=%2Fpapers%2F11"));
  });
});

describe("SaveButton · 评审修复", () => {
  it("状态陈旧（别处已收藏并写了备注）：收藏回来的条目带备注，浮层显示它而不是「+ 添加备注」", async () => {
    collect.mockResolvedValue({
      ok: true,
      created: false,
      item: makeLibraryItem({ note: "别处写的备注" }),
    });
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title={TITLE} variant="icon" />, {
      statuses: [makeItemStatus({ key: "11" })],
      overview,
    });
    await user.click(screen.getByRole("button", { name: /^收藏「/ }));
    expect(await screen.findByText("别处写的备注")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ 添加备注" })).not.toBeInTheDocument();
  });
});

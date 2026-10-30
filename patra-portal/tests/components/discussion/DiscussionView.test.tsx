import type { QueryClient } from "@tanstack/react-query";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeComment, makeReply, makeThread } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DiscussionView } from "@/components/discussion/DiscussionView";
import { PAPER_DISCUSSION_COPY } from "@/lib/discussion/copy";
import { publishComment } from "@/lib/interaction/discussion-actions";
import type { Thread } from "@/lib/interaction/discussion-schema";
import { setHelpful } from "@/lib/interaction/helpful-actions";
import { queryKeys } from "@/lib/query-keys";

let params = new URLSearchParams();
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/papers/1",
  useSearchParams: () => params,
}));
vi.mock("@/lib/interaction/discussion-actions", () => ({
  publishComment: vi.fn(),
  editComment: vi.fn(),
  deleteComment: vi.fn(),
}));
vi.mock("@/lib/interaction/helpful-actions", () => ({ setHelpful: vi.fn() }));

const SUBJECT = { type: "publication", key: "1" } as const;
const SEED = {
  status: "authenticated",
  emailVerified: true,
  email: "a@b.c",
  avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
} as const;
const NOW = Date.parse("2026-10-27T06:20:00Z");
const fetchMock = vi.fn<typeof fetch>();
const publish = vi.mocked(publishComment);

function seedThreads(threads: Thread[], nextCursor: string | null = null, sort = "asc") {
  return (client: QueryClient) =>
    client.setQueryData(queryKeys.discussionThreads("publication", "1", sort), {
      pages: [{ items: threads, nextCursor }],
      pageParams: [null],
    });
}

function renderView(seed?: (client: QueryClient) => void, user?: null) {
  const element = () => (
    <DiscussionView
      subject={SUBJECT}
      copy={PAPER_DISCUSSION_COPY}
      viewer={user === null ? { status: "anonymous" } : SEED}
      now={NOW}
      basePath="/papers/1"
      shareBase="http://patra.test/papers/1"
    />
  );
  const view = renderDiscussion(element(), { seed, user });
  /// 改了 `params` 之后用新元素重新渲染（同一个元素 React 会跳过）
  return { ...view, rerender: () => view.rerender(element()) };
}

beforeEach(() => {
  params = new URLSearchParams();
  window.history.replaceState(null, "", "/papers/1");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(Response.json([]));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  fetchMock.mockReset();
});

const THREAD = makeThread({
  replies: [makeReply("2"), makeReply("3"), makeReply("4")],
  hasMoreReplies: true,
  repliesCursor: "r1",
});

describe("列表", () => {
  it("根评论 + 前 3 条回复 + 展开更多回复（不带条数）", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ items: [makeReply("5")], nextCursor: null }));
    renderView(seedThreads([THREAD]));
    expect(screen.getAllByRole("article")).toHaveLength(4);
    const more = screen.getByRole("button", { name: "展开更多回复" });
    expect(more).toHaveAttribute("aria-expanded", "false");
    await userEvent.setup().click(more);
    await waitFor(() => expect(screen.getAllByRole("article")).toHaveLength(5));
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/api/comments/1/replies?cursor=r1");
    expect(screen.queryByRole("button", { name: "展开更多回复" })).toBeNull();
  });

  it("排序：点最新改地址并按最新取", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ items: [makeThread()], nextCursor: null }));
    const replace = vi.spyOn(window.history, "replaceState");
    renderView(seedThreads([makeThread()]));
    expect(screen.getByRole("button", { name: "正序" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.setup().click(screen.getByRole("button", { name: "最新" }));
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?sort=latest");
  });

  it("加载更多讨论：带游标", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({
        items: [makeThread({ root: makeComment({ commentId: "8", rootId: "8" }) })],
        nextCursor: null,
      }),
    );
    renderView(seedThreads([makeThread()], "c2"));
    await userEvent.setup().click(screen.getByRole("button", { name: "加载更多讨论" }));
    await waitFor(() =>
      expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
        "/api/discussions/publication/1/threads?sort=asc&cursor=c2",
      ),
    );
    await waitFor(() => expect(screen.queryByRole("button", { name: "加载更多讨论" })).toBeNull());
  });
});

describe("失败不丢已显示的内容", () => {
  it("加载更多失败：已加载的楼还在，按钮旁提示", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    renderView(seedThreads([makeThread()], "c2"));
    await userEvent.setup().click(screen.getByRole("button", { name: "加载更多讨论" }));
    expect(await screen.findByText("没有加载出来，请重试。")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.queryByRole("heading", { name: "讨论暂不可用" })).toBeNull();
  });

  it("切换排序时排序控件一直在", async () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    const { rerender } = renderView(seedThreads([makeThread()]));
    await userEvent.setup().click(screen.getByRole("button", { name: "最新" }));
    params = new URLSearchParams("sort=latest");
    rerender();
    expect(screen.getByRole("button", { name: "正序" })).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(1);
  });
});

describe("空状态", () => {
  it("已验证的用户：引导三行 + 展开的编辑器，没有排序控件", () => {
    renderView(seedThreads([]));
    expect(screen.getByRole("heading", { name: "这篇文献还没有讨论" })).toBeInTheDocument();
    expect(screen.getByText("样本量、对照、统计方法是否站得住")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("例如：每组只有 47 例，而且是回顾性前后对照……"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "最新" })).toBeNull();
  });

  it("匿名访客：登录后参与讨论", () => {
    renderView(seedThreads([]), null);
    expect(screen.getByRole("link", { name: "登录后参与讨论" })).toBeInTheDocument();
  });
});

describe("发表", () => {
  it("最新排序下新评论出现在最前，并播报已发表", async () => {
    params = new URLSearchParams("sort=latest");
    publish.mockResolvedValue({
      ok: true,
      comment: makeComment({ commentId: "9", rootId: "9", body: "新的" }),
    });
    renderView(seedThreads([makeThread()], null, "latest"));
    const user = userEvent.setup();
    await user.click(
      screen.getByRole("button", { name: "写下你对这篇文献的看法、疑问或补充证据…" }),
    );
    fireEvent.change(screen.getByRole("combobox", { name: "评论内容" }), {
      target: { value: "新的" },
    });
    await user.click(screen.getByRole("button", { name: "发表" }));
    await waitFor(() => expect(screen.getAllByRole("article")[0]).toHaveTextContent("新的"));
    expect(screen.getByRole("status")).toHaveTextContent("已发表");
  });

  it("正序且后面还有页：不插入，给去看看", async () => {
    publish.mockResolvedValue({ ok: true, comment: makeComment({ commentId: "9", rootId: "9" }) });
    renderView(seedThreads([makeThread()], "c2"));
    const user = userEvent.setup();
    await user.click(
      screen.getByRole("button", { name: "写下你对这篇文献的看法、疑问或补充证据…" }),
    );
    fireEvent.change(screen.getByRole("combobox", { name: "评论内容" }), {
      target: { value: "x" },
    });
    await user.click(screen.getByRole("button", { name: "发表" }));
    expect(await screen.findByText("已发表。按正序排在最后，")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去看看" })).toHaveAttribute(
      "href",
      "/papers/1?comment=9",
    );
    expect(screen.getAllByRole("article")).toHaveLength(1);
  });
});

describe("回复", () => {
  it("回复框开在这一楼最后；另一个回复框有内容时先确认", async () => {
    const other = makeThread({ root: makeComment({ commentId: "7", rootId: "7" }) });
    renderView(seedThreads([makeThread({ replies: [makeReply("2")] }), other]));
    const user = userEvent.setup();
    const [first] = screen.getAllByRole("button", { name: "回复" });
    await user.click(first as HTMLElement);
    const box = screen.getByRole("combobox", { name: "评论内容" });
    fireEvent.change(box, { target: { value: "写了一半" } });
    const replyButtons = screen.getAllByRole("button", { name: "回复" });
    // 楼 7 的「回复」：最后一个文字按钮是提交按钮，往前数一个
    await user.click(replyButtons.at(-1) as HTMLElement);
    expect(screen.getByText("放弃正在写的回复？")).toBeInTheDocument();
  });

  it("取消回复：焦点回到点开它的回复按钮", async () => {
    renderView(seedThreads([makeThread()]));
    const user = userEvent.setup();
    const trigger = screen.getByRole("button", { name: "回复" });
    await user.click(trigger);
    const form = screen
      .getByRole("combobox", { name: "评论内容" })
      .closest("[data-composer]") as HTMLElement;
    await user.click(within(form).getByRole("button", { name: "取消" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "回复" })).toHaveFocus());
  });

  it("回复成功：接在这一楼最后", async () => {
    publish.mockResolvedValue({ ok: true, comment: makeReply("6", "1", { body: "补充一点" }) });
    renderView(seedThreads([makeThread({ replies: [makeReply("2")] })]));
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "回复" })[0] as HTMLElement);
    fireEvent.change(screen.getByRole("combobox", { name: "评论内容" }), {
      target: { value: "补充一点" },
    });
    const form = screen
      .getByRole("combobox", { name: "评论内容" })
      .closest("[data-composer]") as HTMLElement;
    await user.click(within(form).getByRole("button", { name: "回复" }));
    await waitFor(() =>
      expect(screen.getAllByRole("article").at(-1)).toHaveTextContent("补充一点"),
    );
    expect(publish).toHaveBeenCalledWith(SUBJECT, { body: "补充一点", parentId: "1" });
  });
});

describe("降级", () => {
  it("取不到：讨论暂不可用 + 重试；切回页面时自动重取", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    renderView();
    expect(await screen.findByRole("heading", { name: "讨论暂不可用" })).toBeInTheDocument();
    expect(screen.getByText("文献内容不受影响，请稍后再试。")).toBeInTheDocument();
    const before = fetchMock.mock.calls.length;
    fetchMock.mockResolvedValue(Response.json({ items: [makeThread()], nextCursor: null }));
    window.dispatchEvent(new Event("visibilitychange"));
    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(before));
    expect(await screen.findByText("每组只有 47 例")).toBeInTheDocument();
  });
});

describe("有帮助", () => {
  const first = makeThread({ root: makeComment({ commentId: "1", rootId: "1", helpfulCount: 3 }) });
  const second = makeThread({ root: makeComment({ commentId: "2", rootId: "2", body: "第二楼" }) });

  it("点了数字立刻变、楼不重排，缓存里这条也改了", async () => {
    vi.mocked(setHelpful).mockResolvedValue({
      ok: true,
      result: { helpful: true, helpfulCount: 1 },
    });
    const { client } = renderView(seedThreads([first, second]));
    const secondRoot = document.getElementById("comment-2") as HTMLElement;
    await userEvent.setup().click(within(secondRoot).getByRole("button", { name: "有帮助" }));
    await waitFor(() =>
      expect(
        within(secondRoot).getByRole("button", { name: "有帮助，1 人觉得有帮助" }),
      ).toBeVisible(),
    );
    const ids = screen.getAllByRole("article").map((article) => article.id);
    expect(ids).toEqual(["comment-1", "comment-2"]);
    const data = client.getQueryData<{ pages: { items: Thread[] }[] }>(
      queryKeys.discussionThreads("publication", "1", "asc"),
    );
    expect(data?.pages[0]?.items[1]?.root).toMatchObject({ helpful: true, helpfulCount: 1 });
  });

  it("这条已被删除：提示之后重取这一楼", async () => {
    vi.mocked(setHelpful).mockResolvedValue({ ok: false, reason: "gone" });
    const placeholder = makeThread({
      root: makeComment({
        commentId: "2",
        rootId: "2",
        deleted: true,
        author: null,
        body: null,
        helpful: null,
      }),
      replies: [makeReply("8", "2")],
    });
    fetchMock.mockImplementation(async (input) =>
      String(input).endsWith("/comments/2/thread")
        ? Response.json({ targetId: "2", thread: placeholder })
        : Response.json([]),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      renderView(seedThreads([first, second]));
      const secondRoot = document.getElementById("comment-2") as HTMLElement;
      await userEvent
        .setup({ advanceTimers: vi.advanceTimersByTime })
        .click(within(secondRoot).getByRole("button", { name: "有帮助" }));
      expect(await screen.findByText("这条评论已被删除")).toBeVisible();
      await act(() => vi.advanceTimersByTimeAsync(4100));
      await waitFor(() => expect(screen.getByText("该评论已删除")).toBeVisible());
      expect(fetchMock.mock.calls.map((call) => String(call[0]))).toContain(
        "/api/discussions/publication/1/comments/2/thread",
      );
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("最有帮助", () => {
  const top = makeThread({ root: makeComment({ commentId: "1", rootId: "1", helpfulCount: 5 }) });
  const next = makeThread({ root: makeComment({ commentId: "2", rootId: "2", body: "第二楼" }) });

  it("排序三项，地址 sort=helpful 时选中最有帮助", () => {
    params = new URLSearchParams("sort=helpful");
    renderView(seedThreads([top, next], null, "helpful"));
    const group = screen.getByRole("group", { name: "排序" });
    expect(
      within(group)
        .getAllByRole("button")
        .map((button) => button.textContent),
    ).toEqual(["正序", "最新", "最有帮助"]);
    expect(within(group).getByRole("button", { name: "最有帮助" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("点最有帮助：写进地址", async () => {
    const replace = vi.spyOn(window.history, "replaceState");
    renderView(seedThreads([top, next]));
    await userEvent.setup().click(screen.getByRole("button", { name: "最有帮助" }));
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?sort=helpful");
  });

  it("最有帮助下发表根评论：插在最前、没有去看看", async () => {
    params = new URLSearchParams("sort=helpful");
    publish.mockResolvedValue({
      ok: true,
      comment: makeComment({ commentId: "9", rootId: "9", body: "新的" }),
    });
    renderView(seedThreads([top, next], "c2", "helpful"));
    const user = userEvent.setup();
    await user.click(
      screen.getByRole("button", { name: "写下你对这篇文献的看法、疑问或补充证据…" }),
    );
    fireEvent.change(screen.getByRole("combobox", { name: "评论内容" }), {
      target: { value: "新的" },
    });
    await user.click(screen.getByRole("button", { name: "发表" }));
    await waitFor(() => expect(screen.getAllByRole("article")[0]).toHaveTextContent("新的"));
    expect(screen.queryByRole("link", { name: "去看看" })).toBeNull();
  });

  it("同一楼出现在两页里只渲染一次", () => {
    params = new URLSearchParams("sort=helpful");
    renderView((client: QueryClient) =>
      client.setQueryData(queryKeys.discussionThreads("publication", "1", "helpful"), {
        pages: [
          { items: [top, next], nextCursor: "c1" },
          { items: [next], nextCursor: null },
        ],
        pageParams: [null, "c1"],
      }),
    );
    expect(document.querySelectorAll("#comment-2")).toHaveLength(1);
  });
});

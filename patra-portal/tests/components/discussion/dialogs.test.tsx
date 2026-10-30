import type { QueryClient } from "@tanstack/react-query";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeCard, makeComment, makeReply, makeThread } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DiscussionView } from "@/components/discussion/DiscussionView";
import { PAPER_DISCUSSION_COPY } from "@/lib/discussion/copy";
import { deleteComment, editComment } from "@/lib/interaction/discussion-actions";
import type { Thread } from "@/lib/interaction/discussion-schema";
import { queryKeys } from "@/lib/query-keys";

const toastSuccess = vi.fn();
vi.mock("sonner", () => ({ toast: { success: (...args: unknown[]) => toastSuccess(...args) } }));
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/papers/1",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/interaction/discussion-actions", () => ({
  publishComment: vi.fn(),
  editComment: vi.fn(),
  deleteComment: vi.fn(),
}));

const SEED = {
  status: "authenticated",
  emailVerified: true,
  email: "a@b.c",
  avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
} as const;
const fetchMock = vi.fn<typeof fetch>();

function renderList(threads: Thread[], extraSeed?: (client: QueryClient) => void) {
  return renderDiscussion(
    <DiscussionView
      subject={{ type: "publication", key: "1" }}
      copy={PAPER_DISCUSSION_COPY}
      viewer={SEED}
      now={Date.parse("2026-10-27T06:20:00Z")}
      basePath="/papers/1"
      shareBase="http://patra.test/papers/1"
    />,
    {
      seed: (client: QueryClient) => {
        client.setQueryData(queryKeys.discussionThreads("publication", "1", "asc"), {
          pages: [{ items: threads, nextCursor: null }],
          pageParams: [null],
        });
        extraSeed?.(client);
      },
    },
  );
}

async function openMenuItem(article: HTMLElement, name: string) {
  const user = userEvent.setup();
  await user.click(within(article).getByRole("button", { name: "评论操作" }));
  await user.click(await screen.findByRole("menuitem", { name }));
}

beforeEach(() => {
  window.history.replaceState(null, "", "/papers/1");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  fetchMock.mockReset();
});

describe("查看对话", () => {
  it("列出回复链、当前这条高亮，只看这一楼", async () => {
    const reply = makeReply("5", "1", {
      replyTo: {
        deleted: false,
        anonymous: false,
        author: makeCard({ userId: "9", username: "zhou-min" }),
      },
      body: "不过作者自己也写了",
    });
    fetchMock.mockResolvedValue(
      Response.json({ targetId: "5", items: [makeComment(), makeReply("2"), reply] }),
    );
    const push = vi.spyOn(window.history, "pushState");
    renderList([makeThread({ replies: [makeReply("2"), reply] })]);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "查看对话" }));
    const dialog = await screen.findByRole("dialog", { name: "对话" });
    await waitFor(() => expect(within(dialog).getAllByRole("article")).toHaveLength(3));
    expect(
      within(dialog).getByText("不过作者自己也写了").closest('[aria-current="true"]'),
    ).not.toBeNull();
    expect(within(dialog).queryByRole("button", { name: "回复" })).toBeNull();
    await user.click(within(dialog).getByRole("button", { name: "只看这一楼 →" }));
    expect(push).toHaveBeenLastCalledWith(null, "", "/papers/1?comment=5");
  });
});

describe("修改历史", () => {
  it("版本标签、图例、增删标记；最初版本不标", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        versions: [
          { body: "样本量明显太小", at: "2026-10-26T10:40:00Z" },
          { body: "样本量太小", at: "2026-10-25T13:03:00Z" },
        ],
        mentions: [],
      }),
    );
    renderList([makeThread({ root: makeComment({ edited: true }) })]);
    await userEvent.setup().click(screen.getByRole("button", { name: "已编辑" }));
    const dialog = await screen.findByRole("dialog", { name: "修改历史" });
    expect(
      within(dialog).getByText("发布 5 分钟后的修改会留下记录，所有人都能看到。"),
    ).toBeInTheDocument();
    expect(
      await within(dialog).findByText("当前版本 · 10 月 26 日 18:40 修改"),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("最初版本 · 10 月 25 日 21:03 发表")).toBeInTheDocument();
    expect(within(dialog).getByText("下划线是新增的内容 · 删除线是删掉的内容")).toBeInTheDocument();
    expect(within(dialog).getByText("明显").tagName).toBe("INS");
    expect(dialog.querySelectorAll("ins, del")).toHaveLength(1);
  });

  it("失败：修改历史暂时打不开 + 重试", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    renderList([makeThread({ root: makeComment({ edited: true }) })]);
    await userEvent.setup().click(screen.getByRole("button", { name: "已编辑" }));
    expect(await screen.findByText("修改历史暂时打不开")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });
});

describe("删除", () => {
  it("有回复：说明占位；成功后留占位、toast、对账整楼", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: true, placeholder: true, removedIds: [] });
    fetchMock.mockResolvedValue(
      Response.json({
        targetId: "1",
        thread: makeThread({
          root: makeComment({ deleted: true, author: null, body: null, hasReplies: true }),
          replies: [makeReply("2", "1", { replyTo: null })],
        }),
      }),
    );
    renderList([
      makeThread({
        root: makeComment({ mine: true, hasReplies: true }),
        replies: [makeReply("2")],
      }),
    ]);
    await openMenuItem(screen.getAllByRole("article")[0] as HTMLElement, "删除");
    const dialog = await screen.findByRole("dialog", { name: "删除这条评论？" });
    expect(
      within(dialog).getByText(
        "这条评论下已经有回复。删除后会留下「该评论已删除」，回复照常保留。",
      ),
    ).toBeInTheDocument();
    await userEvent.setup().click(within(dialog).getByRole("button", { name: "删除评论" }));
    await waitFor(() => expect(screen.getByText("该评论已删除")).toBeInTheDocument());
    expect(toastSuccess).toHaveBeenCalledWith("评论已删除");
    await waitFor(() =>
      expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe(
        "/api/discussions/publication/1/comments/1/thread",
      ),
    );
  });

  it("没有回复：删除后不能恢复；失败时对话框里提示", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderList([makeThread({ root: makeComment({ mine: true }) })]);
    await openMenuItem(screen.getByRole("article"), "删除");
    const dialog = await screen.findByRole("dialog", { name: "删除这条评论？" });
    expect(within(dialog).getByText("删除后不能恢复。")).toBeInTheDocument();
    await userEvent.setup().click(within(dialog).getByRole("button", { name: "删除评论" }));
    expect(await within(dialog).findByText("没有删除成功，请稍后再试")).toBeInTheDocument();
  });
});

describe("删除之后", () => {
  const two = () => [
    makeThread({ root: makeComment({ mine: true }) }),
    makeThread({ root: makeComment({ commentId: "7", rootId: "7" }) }),
  ];

  it("别的视图的缓存标为过期：回到单楼或换排序时重取", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: true, placeholder: false, removedIds: ["1"] });
    const single = queryKeys.discussionSingle("publication", "1", "1");
    const latest = queryKeys.discussionThreads("publication", "1", "latest");
    const { client } = renderList(two(), (c) => {
      c.setQueryData(single, { targetId: "1", thread: makeThread() });
      c.setQueryData(latest, { pages: [{ items: two(), nextCursor: null }], pageParams: [null] });
    });
    await openMenuItem(screen.getAllByRole("article")[0] as HTMLElement, "删除");
    await userEvent.setup().click(await screen.findByRole("button", { name: "删除评论" }));
    await waitFor(() => expect(screen.getAllByRole("article")).toHaveLength(1));
    expect(client.getQueryState(single)?.isInvalidated).toBe(true);
    expect(client.getQueryState(latest)?.isInvalidated).toBe(true);
  });

  it("焦点落到下一条评论的时间链接", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: true, placeholder: false, removedIds: ["1"] });
    renderList(two());
    await openMenuItem(screen.getAllByRole("article")[0] as HTMLElement, "删除");
    await userEvent.setup().click(await screen.findByRole("button", { name: "删除评论" }));
    await waitFor(() =>
      expect(document.activeElement?.getAttribute("href")).toBe("/papers/1?comment=7"),
    );
  });

  it("评论已经不在（别处删掉了）：按已删除处理，不报错", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: false, reason: "comment-gone" });
    renderList(two());
    await openMenuItem(screen.getAllByRole("article")[0] as HTMLElement, "删除");
    await userEvent.setup().click(await screen.findByRole("button", { name: "删除评论" }));
    await waitFor(() => expect(screen.getAllByRole("article")).toHaveLength(1));
    expect(screen.queryByText("没有删除成功，请稍后再试")).toBeNull();
    expect(toastSuccess).toHaveBeenCalledWith("评论已删除");
  });
});

describe("编辑与复制链接", () => {
  it("编辑：取原文，保存后替换", async () => {
    fetchMock.mockResolvedValue(Response.json({ body: "原文 @zhou-min" }));
    vi.mocked(editComment).mockResolvedValue({
      ok: true,
      comment: makeComment({ mine: true, body: "改过的" }),
    });
    renderList([makeThread({ root: makeComment({ mine: true }) })]);
    await openMenuItem(screen.getByRole("article"), "编辑");
    const box = await screen.findByRole("combobox", { name: "评论内容" });
    expect(box).toHaveValue("原文 @zhou-min");
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/api/comments/1/source");
    fireEvent.change(box, { target: { value: "改过的" } });
    await userEvent.setup().click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByText("改过的")).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "评论内容" })).toBeNull();
  });

  it("复制链接：完整公开地址，toast 已复制链接", async () => {
    renderList([makeThread()]);
    // user-event 的 setup() 会换掉 navigator.clipboard：先建好 user 再装桩
    const user = userEvent.setup();
    const writeText = vi.fn(async () => {});
    Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
    await user.click(screen.getByRole("button", { name: "评论操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "复制链接" }));
    await waitFor(() =>
      expect(writeText).toHaveBeenCalledWith("http://patra.test/papers/1?comment=1"),
    );
    expect(toastSuccess).toHaveBeenCalledWith("已复制链接");
  });
});

describe("删除后的条数", () => {
  it("should_subtract_one_from_title_total_after_own_delete", async () => {
    vi.mocked(deleteComment).mockResolvedValue({ ok: true, placeholder: false, removedIds: ["1"] });
    renderDiscussion(
      <DiscussionView
        subject={{ type: "publication", key: "1" }}
        copy={PAPER_DISCUSSION_COPY}
        viewer={SEED}
        now={Date.parse("2026-10-27T06:20:00Z")}
        basePath="/papers/1"
        shareBase="http://patra.test/papers/1"
      />,
      {
        seed: (client: QueryClient) =>
          client.setQueryData(queryKeys.discussionThreads("publication", "1", "asc"), {
            pages: [
              {
                items: [
                  makeThread({ root: makeComment({ mine: true }) }),
                  makeThread({ root: makeComment({ commentId: "7", rootId: "7" }) }),
                ],
                nextCursor: null,
                total: 7,
              },
            ],
            pageParams: [null],
          }),
      },
    );
    const heading = screen.getByRole("heading", { name: "讨论 · Discussion" });
    expect(heading.parentElement).toHaveTextContent("7 条");
    await openMenuItem(screen.getAllByRole("article")[0] as HTMLElement, "删除");
    await userEvent.setup().click(await screen.findByRole("button", { name: "删除评论" }));
    await waitFor(() => expect(heading.parentElement).toHaveTextContent("6 条"));
  });
});

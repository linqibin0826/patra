import { dehydrate, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeComment, makeThread } from "@tests/fixtures/discussion";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DiscussionCache } from "@/components/discussion/DiscussionCache";
import { DiscussionView } from "@/components/discussion/DiscussionView";
import { PAPER_DISCUSSION_COPY } from "@/lib/discussion/copy";
import { useThreads } from "@/lib/discussion/queries";
import { publishComment } from "@/lib/interaction/discussion-actions";
import { queryKeys } from "@/lib/query-keys";

/// 排查「刚发表的根评论从列表消失、退回空状态」（2026-10-28）：两条能把发表前的列表写回缓存的路径

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

const SUBJECT = { type: "publication", key: "1" } as const;
const KEY = queryKeys.discussionThreads("publication", "1", "asc");
const EMPTY = { items: [], nextCursor: null };
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  window.history.replaceState(null, "", "/papers/1");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  fetchMock.mockReset();
});

describe("路径一：发表时有一次列表请求还在路上", () => {
  it("请求晚于发表回来（还是空列表）：新评论不被冲掉", async () => {
    let answer: (res: Response) => void = () => {};
    fetchMock.mockImplementation((input) =>
      String(input).includes("/threads")
        ? new Promise<Response>((resolve) => {
            answer = resolve;
          })
        : Promise.resolve(Response.json([])),
    );
    vi.mocked(publishComment).mockResolvedValue({
      ok: true,
      comment: makeComment({ commentId: "9", rootId: "9", body: "刚发表的" }),
    });
    const { client } = renderDiscussion(
      <DiscussionView
        subject={SUBJECT}
        copy={PAPER_DISCUSSION_COPY}
        viewer={{
          status: "authenticated",
          emailVerified: true,
          email: "a@b.c",
          avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
        }}
        now={Date.now()}
        basePath="/papers/1"
        shareBase="http://patra.test/papers/1"
      />,
      {
        seed: (c) => c.setQueryData(KEY, { pages: [EMPTY], pageParams: [null] }),
      },
    );
    // 一次在路上的重取（来源不论：重连、重新挂载、过期）
    void client.refetchQueries({ queryKey: KEY });
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    fireEvent.change(screen.getByRole("combobox", { name: "评论内容" }), {
      target: { value: "刚发表的" },
    });
    await userEvent.setup().click(screen.getByRole("button", { name: "发表" }));
    expect(await screen.findByText("刚发表的")).toBeInTheDocument();

    await act(async () => {
      answer(Response.json(EMPTY));
    });
    await waitFor(() => expect(client.getQueryState(KEY)?.fetchStatus).toBe("idle"));
    expect(screen.getByText("刚发表的")).toBeInTheDocument();
  });
});

describe("路径二：服务端首屏数据再次下发", () => {
  /// 服务端脱水的一份列表，`updatedAt` 是服务端取到它的时间
  function serverState(items: ReturnType<typeof makeThread>[], updatedAt: number) {
    const server = new QueryClient();
    server.setQueryData(
      KEY,
      { pages: [{ items, nextCursor: null }], pageParams: [null] },
      { updatedAt },
    );
    return dehydrate(server);
  }

  function Count() {
    const query = useThreads(SUBJECT, "asc");
    return <p>楼数 {query.data?.pages.flatMap((page) => page.items).length ?? "无"}</p>;
  }

  it("首屏数据只作初值：缓存里已经有了，就不被更晚的一份覆盖", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } });
    const ui = (state: ReturnType<typeof serverState>) => (
      <QueryClientProvider client={client}>
        <DiscussionCache state={state}>
          <Count />
        </DiscussionCache>
      </QueryClientProvider>
    );
    const view = render(ui(serverState([], Date.now() - 1000)));
    expect(screen.getByText("楼数 0")).toBeInTheDocument();

    // 发表：客户端把新评论插进缓存
    act(() => {
      client.setQueryData(KEY, {
        pages: [{ items: [makeThread()], nextCursor: null }],
        pageParams: [null],
      });
    });
    expect(await screen.findByText("楼数 1")).toBeInTheDocument();

    // 一次发表之前就开始的服务端渲染这时才到：时间戳更新，内容是发表前的
    view.rerender(ui(serverState([], Date.now() + 60_000)));
    // 等水合的 effect 与查询的通知（setTimeout 调度）都跑完
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
    });
    expect(screen.getByText("楼数 1")).toBeInTheDocument();
  });

  it("缓存里没有时用首屏数据", () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } });
    render(
      <QueryClientProvider client={client}>
        <DiscussionCache state={serverState([makeThread()], Date.now())}>
          <Count />
        </DiscussionCache>
      </QueryClientProvider>,
    );
    expect(screen.getByText("楼数 1")).toBeInTheDocument();
  });
});

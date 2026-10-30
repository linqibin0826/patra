import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { makeThread, makeThreadPage } from "@tests/fixtures/discussion";
import { makeItemStatus } from "@tests/fixtures/interaction";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { renderToString } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import {
  PaperDiscussionFact,
  PaperSavedFact,
} from "@/components/portal/paper-detail/PaperCountFacts";
import type { ThreadsData } from "@/lib/discussion/cache";
import { patchItemStatus } from "@/lib/library/cache";
import { queryKeys } from "@/lib/query-keys";

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderIn(children: ReactNode, client = new QueryClient()) {
  render(
    <QueryClientProvider client={client}>
      <dl>{children}</dl>
    </QueryClientProvider>,
  );
  return client;
}

/// 事实条里每一格的「dt dd」文本
const cells = () =>
  screen
    .queryAllByRole("term")
    .map((dt) => `${dt.textContent} ${dt.nextElementSibling?.textContent ?? ""}`);

const threads = (total: number): ThreadsData => ({
  pages: [makeThreadPage([makeThread()], { total })],
  pageParams: [null],
});

describe("PaperSavedFact", () => {
  it("should_show_collector_count", () => {
    renderIn(<PaperSavedFact paperKey="11" collectorCount={1234} />);
    expect(cells()).toEqual(["收藏 1,234 人"]);
  });

  it("should_not_render_when_zero", () => {
    renderIn(<PaperSavedFact paperKey="11" collectorCount={0} />);
    expect(cells()).toEqual([]);
  });

  it("should_appear_with_one_after_own_collect", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({ items: [makeItemStatus({ key: "11", collected: false })] }),
      ),
    );
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 60_000 } } });
    client.setQueryData(queryKeys.currentUser, USER);
    renderIn(
      <LibraryStatusProvider keys={["11"]}>
        <PaperSavedFact paperKey="11" collectorCount={0} />
      </LibraryStatusProvider>,
      client,
    );
    await waitFor(() => expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toBeDefined());
    act(() => patchItemStatus(client, "11", { collected: true }));
    await waitFor(() => expect(cells()).toEqual(["收藏 1 人"]));
  });
});

describe("PaperDiscussionFact", () => {
  it("should_show_discussion_count_linking_to_discussion", () => {
    renderIn(<PaperDiscussionFact paperKey="11" discussionCount={7} />);
    expect(cells()).toEqual(["讨论 7 条"]);
    expect(screen.getByRole("link", { name: "7 条" })).toHaveAttribute("href", "#discussion");
  });

  it("should_not_render_when_zero_and_no_discussion_cache", () => {
    renderIn(<PaperDiscussionFact paperKey="11" discussionCount={0} />);
    expect(cells()).toEqual([]);
  });

  it("should_follow_total_of_cached_discussion_list", async () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.discussionThreads("publication", "11", "asc"), threads(8));
    renderIn(<PaperDiscussionFact paperKey="11" discussionCount={7} />, client);
    expect(cells()).toEqual(["讨论 8 条"]);
    act(() => {
      client.setQueryData(queryKeys.discussionThreads("publication", "11", "asc"), threads(9));
    });
    await waitFor(() => expect(cells()).toEqual(["讨论 9 条"]));
  });

  it("should_use_most_recently_updated_sort_when_several_cached", async () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.discussionThreads("publication", "11", "asc"), threads(8));
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(Date.now() + 1_000);
    client.setQueryData(queryKeys.discussionThreads("publication", "11", "latest"), threads(9));
    vi.useRealTimers();
    renderIn(<PaperDiscussionFact paperKey="11" discussionCount={7} />, client);
    expect(cells()).toEqual(["讨论 9 条"]);
  });

  it("should_ignore_other_papers_discussion", () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.discussionThreads("publication", "12", "asc"), threads(8));
    renderIn(<PaperDiscussionFact paperKey="11" discussionCount={7} />, client);
    expect(cells()).toEqual(["讨论 7 条"]);
  });
});

describe("PaperDiscussionFact · 讨论区在渲染中写缓存", () => {
  /// 仿 `DiscussionCache`：渲染期间把首屏写进缓存
  function Hydrator({ client, total }: { client: QueryClient; total: number }) {
    client.setQueryData(queryKeys.discussionThreads("publication", "11", "asc"), threads(total));
    return null;
  }

  it("should_not_update_while_another_component_renders", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const client = new QueryClient();
      const { rerender } = render(
        <QueryClientProvider client={client}>
          <dl>
            <PaperDiscussionFact paperKey="11" discussionCount={7} />
          </dl>
        </QueryClientProvider>,
      );
      rerender(
        <QueryClientProvider client={client}>
          <dl>
            <PaperDiscussionFact paperKey="11" discussionCount={7} />
          </dl>
          <Hydrator client={client} total={9} />
        </QueryClientProvider>,
      );
      await waitFor(() => expect(cells()).toEqual(["讨论 9 条"]));
      expect(error.mock.calls.flat().join(" ")).not.toMatch(/Cannot update a component/);
    } finally {
      error.mockRestore();
    }
  });
});

describe("PaperDiscussionFact · 服务端渲染与水合", () => {
  it("should_render_server_stats_on_server_even_when_discussion_cached", () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.discussionThreads("publication", "11", "asc"), threads(8));
    const html = renderToString(
      <QueryClientProvider client={client}>
        <dl>
          <PaperDiscussionFact paperKey="11" discussionCount={7} />
        </dl>
      </QueryClientProvider>,
    );
    // 服务端输出里数字与「条」之间有 React 的文本分隔注释
    expect(html).toMatch(/>7(<!-- -->)? 条</);
    expect(html).not.toMatch(/>8(<!-- -->)? 条</);
  });
});

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { makeItemStatus } from "@tests/fixtures/interaction";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import { PaperStats } from "@/components/library/PaperStats";
import { patchItemStatus } from "@/lib/library/cache";
import { queryKeys } from "@/lib/query-keys";

afterEach(() => {
  vi.unstubAllGlobals();
});

/// 渲染并返回外层容器（计数行是纯文本，没有可查的角色，按容器文本断言）
function wrap(children: ReactNode, client = new QueryClient()): HTMLElement {
  return render(<QueryClientProvider client={client}>{children}</QueryClientProvider>).container;
}

describe("PaperStats", () => {
  it("should_show_both_counts_with_discussion_link", () => {
    const line = wrap(
      <PaperStats paperKey="k" stats={{ collectorCount: 2, discussionCount: 5 }} />,
    );
    expect(line).toHaveTextContent("2 人收藏 · 5 条讨论");
    expect(screen.getByRole("link", { name: "5 条讨论" })).toHaveAttribute(
      "href",
      "/papers/k#discussion",
    );
  });

  it("should_show_only_discussion_when_no_collectors", () => {
    const line = wrap(
      <PaperStats paperKey="k" stats={{ collectorCount: 0, discussionCount: 5 }} />,
    );
    expect(line).toHaveTextContent(/^5 条讨论$/);
  });

  it("should_show_only_collectors_without_link_when_no_discussion", () => {
    const line = wrap(
      <PaperStats paperKey="k" stats={{ collectorCount: 2, discussionCount: 0 }} />,
    );
    expect(line).toHaveTextContent(/^2 人收藏$/);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("should_render_nothing_when_both_zero", () => {
    expect(
      wrap(<PaperStats paperKey="k" stats={{ collectorCount: 0, discussionCount: 0 }} />),
    ).toBeEmptyDOMElement();
  });

  it("should_render_nothing_when_unavailable", () => {
    expect(wrap(<PaperStats paperKey="k" stats={null} />)).toBeEmptyDOMElement();
  });

  it("should_group_thousands", () => {
    const line = wrap(
      <PaperStats paperKey="k" stats={{ collectorCount: 1234, discussionCount: 0 }} />,
    );
    expect(line).toHaveTextContent("1,234 人收藏");
  });

  it("should_appear_with_one_collector_after_own_collect", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ items: [makeItemStatus({ key: "k", collected: false })] })),
    );
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: 60_000 } } });
    client.setQueryData(queryKeys.currentUser, USER);
    const line = wrap(
      <LibraryStatusProvider keys={["k"]}>
        <PaperStats paperKey="k" stats={{ collectorCount: 0, discussionCount: 3 }} />
      </LibraryStatusProvider>,
      client,
    );
    await waitFor(() => expect(client.getQueryData(queryKeys.libraryStatus(["k"]))).toBeDefined());
    expect(line).toHaveTextContent(/^3 条讨论$/);
    act(() => patchItemStatus(client, "k", { collected: true }));
    await waitFor(() => expect(line).toHaveTextContent("1 人收藏 · 3 条讨论"));
  });
});

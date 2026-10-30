import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { makeExperience, makeFewSummary, makeSummary } from "@tests/fixtures/experience";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  afterEditOrDelete,
  afterPublish,
  useExperienceSummary,
  useJournalExperiences,
  usePostRevisions,
  useUserExperiences,
} from "@/lib/experience/queries";
import { queryKeys } from "@/lib/query-keys";

const fetchMock = vi.fn<typeof fetch>();
let client: QueryClient;

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  client.clear();
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("客户端查询的地址", () => {
  it("汇总按窗口取", async () => {
    fetchMock.mockResolvedValue(Response.json(makeSummary({ window: "all" })));
    const { result } = renderHook(() => useExperienceSummary("100", "all"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/api/journals/100/posts/summary?window=all");
  });

  it("期刊列表第一页不带游标，下一页带", async () => {
    fetchMock
      .mockResolvedValueOnce(
        Response.json({ items: [makeExperience()], nextCursor: "c2", total: 1 }),
      )
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null, total: 1 }));
    const { result } = renderHook(() => useJournalExperiences("100", "helpful"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await result.current.fetchNextPage();
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "/api/journals/100/posts?sort=helpful",
      "/api/journals/100/posts?sort=helpful&cursor=c2",
    ]);
  });

  it("主页列表与修改历史", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null }))
      .mockResolvedValueOnce(Response.json({ items: [] }));
    const user = renderHook(() => useUserExperiences("7"), { wrapper });
    await waitFor(() => expect(user.result.current.isSuccess).toBe(true));
    const revisions = renderHook(() => usePostRevisions("9", true), { wrapper });
    await waitFor(() => expect(revisions.result.current.isSuccess).toBe(true));
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "/api/users/7/posts",
      "/api/posts/9/revisions",
    ]);
  });

  it("修改历史没打开时不取", () => {
    renderHook(() => usePostRevisions("9", false), { wrapper });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("失败进入错误态", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const { result } = renderHook(() => useExperienceSummary("100", "3y"), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("期刊列表换排序时的占位", () => {
  it("缓存被清掉（换了访客）后不拿上一位的列表当占位", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({ items: [makeExperience()], nextCursor: null, total: 1 }),
    );
    const { result, rerender } = renderHook(({ sort }) => useJournalExperiences("100", sort), {
      wrapper,
      initialProps: { sort: "latest" as "latest" | "helpful" },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    fetchMock.mockReturnValue(new Promise(() => {}));
    rerender({ sort: "helpful" });
    expect(result.current.data?.pages).toHaveLength(1);
    client.removeQueries({ queryKey: ["experience"] });
    rerender({ sort: "latest" });
    await waitFor(() => expect(result.current.data).toBeUndefined());
  });
});

describe("afterPublish：写后先取消在途请求，再重取（PAP-124 交接第 5 节）", () => {
  it("先 cancelQueries 再失效汇总、重置列表", async () => {
    const order: string[] = [];
    vi.spyOn(client, "cancelQueries").mockImplementation(async () => {
      order.push("cancel");
    });
    vi.spyOn(client, "invalidateQueries").mockImplementation(async () => {
      order.push("invalidate");
    });
    vi.spyOn(client, "resetQueries").mockImplementation(async () => {
      order.push("reset");
    });
    await afterPublish(client, "100");
    expect(client.resetQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.experienceListAll("100"),
    });
    expect(order[0]).toBe("cancel");
    expect(order).toContain("invalidate");
    expect(order).toContain("reset");
  });

  it("发布之前发出、之后才回来的旧汇总不会盖掉新汇总", async () => {
    let releaseStale: (res: Response) => void = () => {};
    fetchMock
      .mockReturnValueOnce(
        new Promise<Response>((resolve) => {
          releaseStale = resolve;
        }),
      )
      .mockResolvedValueOnce(Response.json(makeFewSummary(1)));
    const { result } = renderHook(() => useExperienceSummary("100", "3y"), { wrapper });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await afterPublish(client, "100");
    releaseStale(Response.json(makeFewSummary(0)));
    await waitFor(() => expect(result.current.data?.count).toBe(1));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(client.getQueryData(queryKeys.experienceSummary("100", "3y"))).toMatchObject({
      count: 1,
    });
  });
});

describe("afterEditOrDelete", () => {
  it("取消并失效期刊、作者主页与修改历史", async () => {
    const cancelled: unknown[] = [];
    const invalidated: unknown[] = [];
    vi.spyOn(client, "cancelQueries").mockImplementation(async (filters) => {
      cancelled.push(filters?.queryKey);
    });
    vi.spyOn(client, "invalidateQueries").mockImplementation(async (filters) => {
      invalidated.push(filters?.queryKey);
    });
    await afterEditOrDelete(client, { journalId: "100", authorUserId: "7", postId: "9" });
    const keys = [
      queryKeys.experienceJournal("100"),
      queryKeys.experienceUser("7"),
      queryKeys.experienceRevisions("9"),
    ];
    expect(cancelled).toEqual(keys);
    expect(invalidated).toEqual(keys);
  });

  it("作者不可知（匿名经验的别人视角不会发生；期刊已失效）时跳过对应的 key", async () => {
    const invalidated: unknown[] = [];
    vi.spyOn(client, "invalidateQueries").mockImplementation(async (filters) => {
      invalidated.push(filters?.queryKey);
    });
    await afterEditOrDelete(client, { journalId: null, authorUserId: null, postId: "9" });
    expect(invalidated).toEqual([queryKeys.experienceRevisions("9")]);
  });
});

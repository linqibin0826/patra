// @vitest-environment node
import { makeThread, makeThreadPage } from "@tests/fixtures/discussion";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { parseDiscussionView, prefetchDiscussion } from "@/lib/discussion/server";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const PAPER = { type: "publication", key: "11" } as const;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar();
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const keysOf = (state: Awaited<ReturnType<typeof prefetchDiscussion>>) =>
  state.queries.map((query) => query.queryKey);

describe("prefetchDiscussion", () => {
  it("列表取到：放进第一页", async () => {
    fetchMock.mockResolvedValue(Response.json(makeThreadPage([makeThread()], { total: 1 })));
    const state = await prefetchDiscussion(PAPER, { mode: "list", sort: "asc" });
    expect(keysOf(state)).toEqual([["discussion", "publication", "11", "threads", "asc"]]);
    expect(state.queries[0]?.state.data).toEqual({
      pages: [makeThreadPage([makeThread()], { total: 1 })],
      pageParams: [null],
    });
  });

  it("最有帮助：按 sort=helpful 取，放进这种排序的缓存", async () => {
    fetchMock.mockResolvedValue(Response.json(makeThreadPage([makeThread()], { total: 1 })));
    const state = await prefetchDiscussion(PAPER, { mode: "list", sort: "helpful" });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain("/threads?sort=helpful");
    expect(keysOf(state)).toEqual([["discussion", "publication", "11", "threads", "helpful"]]);
  });

  it("列表取不到：不放，客户端自己再取", async () => {
    fetchMock.mockResolvedValue(Response.json({}, { status: 503 }));
    expect(keysOf(await prefetchDiscussion(PAPER, { mode: "list", sort: "latest" }))).toEqual([]);
  });

  it("单楼 404：missing", async () => {
    fetchMock.mockResolvedValue(Response.json({ traits: ["COMMENT_NOT_FOUND"] }, { status: 404 }));
    const state = await prefetchDiscussion(PAPER, { mode: "single", commentId: "5" });
    expect(keysOf(state)).toEqual([["discussion", "publication", "11", "single", "5"]]);
    expect(state.queries[0]?.state.data).toEqual({ missing: true });
  });
});

describe("parseDiscussionView", () => {
  it.each([
    [
      { comment: "5", sort: "latest" },
      { mode: "single", commentId: "5" },
    ],
    [{ comment: "x" }, { mode: "list", sort: "asc" }],
    [{ sort: "helpful" }, { mode: "list", sort: "helpful" }],
    [{ sort: ["helpful", "asc"] }, { mode: "list", sort: "asc" }],
    [{ sort: "latest" }, { mode: "list", sort: "latest" }],
    [{ sort: ["latest", "asc"] }, { mode: "list", sort: "asc" }],
    [{}, { mode: "list", sort: "asc" }],
  ])("%o → %o", (params, view) => {
    expect(parseDiscussionView(params)).toEqual(view);
  });
});

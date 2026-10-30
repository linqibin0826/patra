// @vitest-environment node
import { makeComment, makeThread, makeThreadPage } from "@tests/fixtures/discussion";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchMentionSuggestions,
  fetchReplies,
  fetchRevisions,
  fetchSingleThread,
  fetchSource,
  fetchThreads,
  isSubject,
} from "@/lib/interaction/discussion";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const PAPER = { type: "publication", key: "11" } as const;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const call = (index = 0) => fetchMock.mock.calls[index] ?? [];
const headersOf = (index = 0) => (call(index)[1]?.headers ?? {}) as Record<string, string>;

describe("fetchThreads", () => {
  it("带会话请求一页楼，过 schema", async () => {
    const page = makeThreadPage([makeThread()], { nextCursor: "c2", total: 1 });
    fetchMock.mockResolvedValue(Response.json(page));
    await expect(fetchThreads(PAPER, "latest")).resolves.toEqual({ kind: "ok", data: page });
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/discussions/publication/11/threads?sort=latest",
    );
    expect(headersOf()).toMatchObject({ Authorization: "Bearer patra_user_abc" });
  });

  it("游标按查询参数编码", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    await fetchThreads(PAPER, "asc", "a+b/c=");
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/discussions/publication/11/threads?sort=asc&cursor=a%2Bb%2Fc%3D",
    );
  });

  it("optional_read_retries_anonymously_when_token_rejected", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ code: "GW-0401" }, { status: 401 }))
      .mockResolvedValueOnce(Response.json(makeThreadPage([])));
    await expect(fetchThreads(PAPER, "asc")).resolves.toEqual({
      kind: "ok",
      data: makeThreadPage([]),
    });
    expect(headersOf(1)).not.toHaveProperty("Authorization");
  });

  it("没有会话 Cookie：不带令牌照常请求", async () => {
    resetCookieJar();
    fetchMock.mockResolvedValue(Response.json(makeThreadPage([])));
    await expect(fetchThreads(PAPER, "asc")).resolves.toMatchObject({ kind: "ok" });
    expect(headersOf()).not.toHaveProperty("Authorization");
  });

  it.each([
    [503, "unavailable"],
    [404, "not-found"],
  ])("%i → %s", async (status, kind) => {
    fetchMock.mockResolvedValue(Response.json({}, { status }));
    await expect(fetchThreads(PAPER, "asc")).resolves.toEqual({ kind });
  });

  it("形状不对：unavailable", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [{ root: {} }] }));
    await expect(fetchThreads(PAPER, "asc")).resolves.toEqual({ kind: "unavailable" });
  });
});

describe("参数校验", () => {
  it("rejects_bad_params_without_calling_gateway", async () => {
    await expect(fetchThreads({ type: "journal", key: "11" } as never, "asc")).resolves.toEqual({
      kind: "not-found",
    });
    await expect(fetchThreads({ type: "publication", key: "abc" }, "asc")).resolves.toEqual({
      kind: "not-found",
    });
    await expect(fetchSingleThread(PAPER, "0")).resolves.toEqual({ kind: "not-found" });
    await expect(fetchReplies("x")).resolves.toEqual({ kind: "not-found" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("isSubject", () => {
    expect(isSubject("publication", "11")).toBe(true);
    expect(isSubject("post", "9")).toBe(true);
    expect(isSubject("journal", "9")).toBe(false);
    expect(isSubject("post", "09")).toBe(false);
  });
});

describe("其余读接口", () => {
  it("单楼：路径与 404", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ targetId: "5", thread: makeThread() }));
    await expect(fetchSingleThread(PAPER, "5")).resolves.toMatchObject({ kind: "ok" });
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/discussions/publication/11/comments/5/thread",
    );
    fetchMock.mockResolvedValueOnce(
      Response.json({ traits: ["NOT_FOUND", "COMMENT_NOT_FOUND"] }, { status: 404 }),
    );
    await expect(fetchSingleThread(PAPER, "6")).resolves.toEqual({ kind: "not-found" });
  });

  it("展开更多回复带游标", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [makeComment()], nextCursor: null }));
    await fetchReplies("1", "c1");
    expect(call()[0]).toBe("http://gw.test/patra-interaction/comments/1/replies?cursor=c1");
  });

  it("修改历史", async () => {
    const body = { versions: [{ body: "a", at: "2026-10-24T06:20:00Z" }], mentions: [] };
    fetchMock.mockResolvedValue(Response.json(body));
    await expect(fetchRevisions("5")).resolves.toEqual({ kind: "ok", data: body });
  });

  it("编辑原文要登录：没有会话不请求", async () => {
    resetCookieJar();
    await expect(fetchSource("5")).resolves.toEqual({ kind: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("联想：查询词编码", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [] }));
    await fetchMentionSuggestions(PAPER, "陈");
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/discussions/publication/11/mention-suggestions?q=%E9%99%88",
    );
  });
});

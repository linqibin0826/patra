// @vitest-environment node
import { makeComment, makeThread, makeThreadPage } from "@tests/fixtures/discussion";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET as getConversation } from "@/app/api/comments/[id]/conversation/route";
import { GET as getReplies } from "@/app/api/comments/[id]/replies/route";
import { GET as getRevisions } from "@/app/api/comments/[id]/revisions/route";
import { GET as getSource } from "@/app/api/comments/[id]/source/route";
import { GET as getSingle } from "@/app/api/discussions/[type]/[key]/comments/[id]/thread/route";
import { GET as getMentions } from "@/app/api/discussions/[type]/[key]/mention-suggestions/route";
import { GET as getThreads } from "@/app/api/discussions/[type]/[key]/threads/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

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

const get = (path: string) => new Request(`http://localhost:4000${path}`);
const params = <T>(value: T) => ({ params: Promise.resolve(value) });
const gatewayUrl = () => fetchMock.mock.calls[0]?.[0];

describe("讨论的 Route Handler", () => {
  it("一页楼：传 sort 与 cursor，不缓存", async () => {
    fetchMock.mockResolvedValue(Response.json(makeThreadPage([makeThread()], { total: 1 })));
    const res = await getThreads(
      get("/api/discussions/publication/11/threads?sort=latest&cursor=c1"),
      params({ type: "publication", key: "11" }),
    );
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(gatewayUrl()).toBe(
      "http://gw.test/patra-interaction/discussions/publication/11/threads?sort=latest&cursor=c1",
    );
  });

  it("最有帮助原样转发", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    await getThreads(
      get("/api/discussions/publication/11/threads?sort=helpful"),
      params({ type: "publication", key: "11" }),
    );
    expect(gatewayUrl()).toContain("sort=helpful");
  });

  it("sort 不认识时按正序", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    await getThreads(
      get("/api/discussions/publication/11/threads?sort=hot"),
      params({ type: "publication", key: "11" }),
    );
    expect(gatewayUrl()).toContain("sort=asc");
  });

  it("参数不合格 404、不请求网关；不可用 502", async () => {
    const bad = await getThreads(
      get("/api/discussions/journal/11/threads"),
      params({ type: "journal", key: "11" }),
    );
    expect(bad.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
    fetchMock.mockResolvedValue(Response.json({}, { status: 503 }));
    const down = await getThreads(
      get("/api/discussions/publication/11/threads"),
      params({ type: "publication", key: "11" }),
    );
    expect(down.status).toBe(502);
  });

  it("单楼、回复、对话、历史、原文、联想各自的网关路径", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith("/thread")) return Response.json({ targetId: "5", thread: makeThread() });
      if (url.includes("/replies")) return Response.json({ items: [], nextCursor: null });
      if (url.endsWith("/conversation"))
        return Response.json({ targetId: "5", items: [makeComment()] });
      if (url.endsWith("/revisions")) return Response.json({ versions: [], mentions: [] });
      if (url.endsWith("/source")) return Response.json({ body: "原文" });
      return Response.json({ items: [] });
    });
    expect(
      (
        await getSingle(
          get("/api/discussions/post/9/comments/5/thread"),
          params({ type: "post", key: "9", id: "5" }),
        )
      ).status,
    ).toBe(200);
    expect(
      (await getReplies(get("/api/comments/1/replies?cursor=c"), params({ id: "1" }))).status,
    ).toBe(200);
    expect(
      (await getConversation(get("/api/comments/5/conversation"), params({ id: "5" }))).status,
    ).toBe(200);
    expect((await getRevisions(get("/api/comments/5/revisions"), params({ id: "5" }))).status).toBe(
      200,
    );
    const source = await getSource(get("/api/comments/5/source"), params({ id: "5" }));
    expect(await source.json()).toEqual({ body: "原文" });
    expect(
      (
        await getMentions(
          get("/api/discussions/post/9/mention-suggestions?q=zh"),
          params({ type: "post", key: "9" }),
        )
      ).status,
    ).toBe(200);
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "http://gw.test/patra-interaction/discussions/post/9/comments/5/thread",
      "http://gw.test/patra-interaction/comments/1/replies?cursor=c",
      "http://gw.test/patra-interaction/comments/5/conversation",
      "http://gw.test/patra-interaction/comments/5/revisions",
      "http://gw.test/patra-interaction/comments/5/source",
      "http://gw.test/patra-interaction/discussions/post/9/mention-suggestions?q=zh",
    ]);
  });

  it("原文与联想没有会话：401", async () => {
    resetCookieJar();
    expect((await getSource(get("/api/comments/5/source"), params({ id: "5" }))).status).toBe(401);
    expect(
      (
        await getMentions(
          get("/api/discussions/post/9/mention-suggestions?q="),
          params({ type: "post", key: "9" }),
        )
      ).status,
    ).toBe(401);
  });
});

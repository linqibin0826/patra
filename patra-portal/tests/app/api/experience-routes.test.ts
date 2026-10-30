// @vitest-environment node
import { makeExperience, makeRevision, makeSummary } from "@tests/fixtures/experience";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET as getJournalPosts } from "@/app/api/journals/[id]/posts/route";
import { GET as getSummary } from "@/app/api/journals/[id]/posts/summary/route";
import { GET as getRevisions } from "@/app/api/posts/[id]/revisions/route";
import { GET as getUserPosts } from "@/app/api/users/[userId]/posts/route";

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
const params = (id: string) => ({ params: Promise.resolve({ id }) });
const gatewayUrl = () => String(fetchMock.mock.calls[0]?.[0]);

describe("投稿经验的 Route Handler", () => {
  it("期刊的一页经验：透传游标，不缓存", async () => {
    const page = { items: [makeExperience()], nextCursor: null, total: 1 };
    fetchMock.mockResolvedValue(Response.json(page));
    const res = await getJournalPosts(
      get("/api/journals/100/posts?sort=helpful&cursor=c1"),
      params("100"),
    );
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(await res.json()).toEqual(page);
    expect(gatewayUrl()).toBe(
      "http://gw.test/patra-interaction/journals/100/posts?limit=10&sort=helpful&cursor=c1",
    );
  });

  it("期刊的一页经验：sort 不认识时是最新投稿", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null, total: 0 }));
    await getJournalPosts(get("/api/journals/100/posts?sort=hot"), params("100"));
    expect(gatewayUrl()).toContain("sort=latest");
  });

  it("汇总：缺省窗口是近 3 年", async () => {
    fetchMock.mockResolvedValue(Response.json(makeSummary()));
    await getSummary(get("/api/journals/100/posts/summary"), params("100"));
    expect(gatewayUrl()).toBe(
      "http://gw.test/patra-interaction/journals/100/posts/summary?window=3y",
    );
  });

  it("汇总：窗口不合法 422、不请求网关", async () => {
    const res = await getSummary(get("/api/journals/100/posts/summary?window=5y"), params("100"));
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("修改历史与用户的经验", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ items: [makeRevision()] }))
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null }));
    expect((await getRevisions(get("/api/posts/9/revisions"), params("9"))).status).toBe(200);
    expect(
      (
        await getUserPosts(get("/api/users/7/posts?cursor=c2"), {
          params: Promise.resolve({ userId: "7" }),
        })
      ).status,
    ).toBe(200);
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "http://gw.test/patra-interaction/posts/9/revisions",
      "http://gw.test/patra-interaction/users/7/posts?limit=20&cursor=c2",
    ]);
  });

  it("ID 不合格 404、不请求网关；不可用 502", async () => {
    expect((await getJournalPosts(get("/api/journals/x/posts"), params("x"))).status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
    fetchMock.mockResolvedValue(Response.json({}, { status: 503 }));
    expect((await getRevisions(get("/api/posts/9/revisions"), params("9"))).status).toBe(502);
  });
});

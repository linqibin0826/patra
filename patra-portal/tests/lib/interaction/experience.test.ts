// @vitest-environment node
import {
  makeDetail,
  makeExperience,
  makeFewSummary,
  makeRevision,
  makeSummary,
  makeUserExperience,
} from "@tests/fixtures/experience";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchExperienceSummary,
  fetchJournalExperiences,
  fetchPost,
  fetchPostRevisions,
  fetchUserExperiences,
} from "@/lib/interaction/experience";

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

const call = (index = 0) => fetchMock.mock.calls[index] ?? [];
const headersOf = (index = 0) => (call(index)[1]?.headers ?? {}) as Record<string, string>;

describe("fetchJournalExperiences", () => {
  it("带会话取一页（每页 10 条），过 schema", async () => {
    const page = { items: [makeExperience()], nextCursor: "c2", total: 11 };
    fetchMock.mockResolvedValue(Response.json(page));
    await expect(fetchJournalExperiences("100", "latest")).resolves.toEqual({
      kind: "ok",
      data: page,
    });
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/journals/100/posts?limit=10&sort=latest",
    );
    expect(headersOf()).toMatchObject({ Authorization: "Bearer patra_user_abc" });
  });

  it("游标按查询参数编码", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null, total: 0 }));
    await fetchJournalExperiences("100", "latest", "a+b/c=");
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/journals/100/posts?limit=10&sort=latest&cursor=a%2Bb%2Fc%3D",
    );
  });

  it("最有帮助带游标", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null, total: 0 }));
    await fetchJournalExperiences("100", "helpful", "c1");
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/journals/100/posts?limit=10&sort=helpful&cursor=c1",
    );
  });

  it("令牌被网关拒时按访客重取一次", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ code: "GW-0401" }, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null, total: 0 }));
    await expect(fetchJournalExperiences("100", "latest")).resolves.toMatchObject({ kind: "ok" });
    expect(headersOf(1)).not.toHaveProperty("Authorization");
  });

  it("期刊 ID 不合格不请求", async () => {
    await expect(fetchJournalExperiences("abc", "latest")).resolves.toEqual({ kind: "not-found" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("503 → unavailable", async () => {
    fetchMock.mockResolvedValue(Response.json({}, { status: 503 }));
    await expect(fetchJournalExperiences("100", "latest")).resolves.toEqual({
      kind: "unavailable",
    });
  });
});

describe("fetchExperienceSummary", () => {
  it("按窗口取汇总", async () => {
    fetchMock.mockResolvedValue(Response.json(makeSummary({ window: "all" })));
    await expect(fetchExperienceSummary("100", "all")).resolves.toMatchObject({ kind: "ok" });
    expect(call()[0]).toBe(
      "http://gw.test/patra-interaction/journals/100/posts/summary?window=all",
    );
  });

  it("不到 5 条时四项为 null 也能过 schema", async () => {
    fetchMock.mockResolvedValue(Response.json(makeFewSummary(3)));
    await expect(fetchExperienceSummary("100", "3y")).resolves.toEqual({
      kind: "ok",
      data: makeFewSummary(3),
    });
  });

  it("窗口不合法不请求", async () => {
    await expect(fetchExperienceSummary("100", "5y" as "3y")).resolves.toEqual({
      kind: "not-found",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("fetchPost", () => {
  it("取详情", async () => {
    fetchMock.mockResolvedValue(Response.json(makeDetail()));
    await expect(fetchPost("9")).resolves.toEqual({ kind: "ok", data: makeDetail() });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/posts/9");
  });

  it("已删除：experience 为 null 也能过 schema", async () => {
    const deleted = { ...makeDetail(), deleted: true, experience: null };
    fetchMock.mockResolvedValue(Response.json(deleted));
    await expect(fetchPost("9")).resolves.toEqual({ kind: "ok", data: deleted });
  });

  it("404 → not-found", async () => {
    fetchMock.mockResolvedValue(Response.json({}, { status: 404 }));
    await expect(fetchPost("9")).resolves.toEqual({ kind: "not-found" });
  });

  it("ID 不合格不请求", async () => {
    await expect(fetchPost("x")).resolves.toEqual({ kind: "not-found" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("fetchPostRevisions", () => {
  it("取修改历史", async () => {
    const revisions = { items: [makeRevision({ current: true })] };
    fetchMock.mockResolvedValue(Response.json(revisions));
    await expect(fetchPostRevisions("9")).resolves.toEqual({ kind: "ok", data: revisions });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/posts/9/revisions");
  });
});

describe("fetchUserExperiences", () => {
  it("按条数与游标取", async () => {
    const page = { items: [makeUserExperience()], nextCursor: null };
    fetchMock.mockResolvedValue(Response.json(page));
    await expect(fetchUserExperiences("7", { limit: 20, cursor: "c1" })).resolves.toEqual({
      kind: "ok",
      data: page,
    });
    expect(call()[0]).toBe("http://gw.test/patra-interaction/users/7/posts?limit=20&cursor=c1");
  });

  it("用户 ID 不合格不请求", async () => {
    await expect(fetchUserExperiences("0", { limit: 1 })).resolves.toEqual({ kind: "not-found" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

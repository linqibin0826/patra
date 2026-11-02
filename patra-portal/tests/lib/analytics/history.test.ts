// @vitest-environment node
import { makePaper } from "@tests/fixtures/paper";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchReadingHistory, fetchReadingRecording } from "@/lib/analytics/history";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const TOKEN = "patra_user_abc";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: TOKEN });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const PAGE = {
  recordingEnabled: true,
  items: [
    { articleId: "11", lastReadAt: "2026-11-01T07:42:10.177Z", fulltextRead: true },
    { articleId: "12", lastReadAt: "2026-10-31T02:00:00.000Z", fulltextRead: false },
  ],
  nextCursor: "MTc2",
};

/// 按地址回：阅读历史、catalog 批量卡片
function respond(history: Response, cards: Response = Response.json({ items: [] })) {
  fetchMock.mockImplementation(async (url) =>
    String(url).includes("/patra-analytics/") ? history : cards,
  );
}

describe("fetchReadingHistory", () => {
  it("取一页，再按 ID 批量配卡片；catalog 没回的条目 paper 为 null", async () => {
    respond(
      Response.json(PAGE),
      Response.json({
        items: [
          makePaper({ id: "11", title: "Sepsis-3", journal: "JAMA", publishedDate: "2016-02" }),
        ],
      }),
    );
    const read = await fetchReadingHistory("abc");
    const [historyUrl, historyInit] = fetchMock.mock.calls[0] ?? [];
    expect(historyUrl).toBe("http://gw.test/patra-analytics/me/reading-history?cursor=abc");
    expect(historyInit?.headers).toMatchObject({ Authorization: `Bearer ${TOKEN}` });
    expect(String(fetchMock.mock.calls[1]?.[0])).toBe(
      "http://gw.test/patra-catalog/portal/publications/batch?id=11&id=12",
    );
    expect(read).toEqual({
      kind: "ok",
      data: {
        recordingEnabled: true,
        nextCursor: "MTc2",
        items: [
          {
            ...PAGE.items[0],
            paper: { title: "Sepsis-3", journal: "JAMA", year: "2016" },
          },
          { ...PAGE.items[1], paper: null },
        ],
      },
    });
  });

  it("游标做 URL 编码；没有游标时不带", async () => {
    respond(Response.json({ ...PAGE, items: [] }));
    await fetchReadingHistory("a+b/=");
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "http://gw.test/patra-analytics/me/reading-history?cursor=a%2Bb%2F%3D",
    );
    fetchMock.mockClear();
    await fetchReadingHistory();
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "http://gw.test/patra-analytics/me/reading-history",
    );
  });

  it("空页不请求 catalog", async () => {
    respond(Response.json({ ...PAGE, items: [], nextCursor: null }));
    expect(await fetchReadingHistory()).toEqual({
      kind: "ok",
      data: { recordingEnabled: true, items: [], nextCursor: null },
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("没有会话：signed-out，不请求", async () => {
    resetCookieJar({});
    expect(await fetchReadingHistory()).toEqual({ kind: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("analytics 401：signed-out", async () => {
    respond(Response.json({ status: 401, code: "GW-0401" }, { status: 401 }));
    expect(await fetchReadingHistory()).toEqual({ kind: "signed-out" });
  });

  it.each([
    ["500", () => respond(new Response(null, { status: 500 }))],
    ["422（坏游标）", () => respond(Response.json({ status: 422 }, { status: 422 }))],
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
    ["响应形状不对", () => respond(Response.json({ items: "x" }))],
    ["catalog 失败", () => respond(Response.json(PAGE), new Response(null, { status: 500 }))],
  ])("%s：unavailable", async (_, arrange) => {
    arrange();
    expect(await fetchReadingHistory()).toEqual({ kind: "unavailable" });
  });
});

describe("fetchReadingRecording", () => {
  it("读开关", async () => {
    respond(Response.json({ enabled: false }));
    expect(await fetchReadingRecording()).toEqual({ kind: "ok", data: { enabled: false } });
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "http://gw.test/patra-analytics/me/reading-history/recording",
    );
  });

  it("503：unavailable", async () => {
    respond(new Response(null, { status: 503 }));
    expect(await fetchReadingRecording()).toEqual({ kind: "unavailable" });
  });
});

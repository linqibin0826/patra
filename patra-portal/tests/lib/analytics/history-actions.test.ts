// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearHistory,
  deleteHistoryEntry,
  setReadingRecording,
} from "@/lib/analytics/history-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
let jar: ReturnType<typeof resetCookieJar>;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  jar = resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const call = () => fetchMock.mock.calls[0] ?? [];
const problem = (status: number, code: string) =>
  Response.json({ status, code, title: "x" }, { status });

describe("deleteHistoryEntry", () => {
  it("DELETE /me/reading-history/{articleId}，204 成功", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    expect(await deleteHistoryEntry("11")).toEqual({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-analytics/me/reading-history/11");
    expect(call()[1]?.method).toBe("DELETE");
  });

  it.each(["01", "x", ""])("ID 不合格（%s）：不请求，unavailable", async (id) => {
    expect(await deleteHistoryEntry(id)).toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("clearHistory", () => {
  it("DELETE /me/reading-history，204 成功", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    expect(await clearHistory()).toEqual({ ok: true });
    expect(call()[0]).toBe("http://gw.test/patra-analytics/me/reading-history");
    expect(call()[1]?.method).toBe("DELETE");
  });
});

describe("setReadingRecording", () => {
  it("PUT 开关，回保存后的值", async () => {
    fetchMock.mockResolvedValue(Response.json({ enabled: false }));
    expect(await setReadingRecording(false)).toEqual({ ok: true, enabled: false });
    expect(call()[0]).toBe("http://gw.test/patra-analytics/me/reading-history/recording");
    expect(call()[1]?.method).toBe("PUT");
    expect(JSON.parse(String(call()[1]?.body))).toEqual({ enabled: false });
  });

  it("不是布尔值：不请求，unavailable", async () => {
    expect(await setReadingRecording("no" as unknown as boolean)).toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("三个写操作共用的失败", () => {
  const actions = [
    ["删一条", () => deleteHistoryEntry("11")],
    ["清空", () => clearHistory()],
    ["改开关", () => setReadingRecording(true)],
  ] as const;

  it.each(actions)("%s：没有会话 → signed-out，不请求", async (_, act) => {
    resetCookieJar({});
    expect(await act()).toEqual({ ok: false, reason: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(actions)("%s：会话被网关拒绝 → signed-out 并清会话 Cookie", async (_, act) => {
    fetchMock.mockResolvedValue(problem(401, "GW-0401"));
    expect(await act()).toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toContain(CLEARED);
  });

  it.each(actions)("%s：500 / 网络错误 → unavailable", async (_, act) => {
    fetchMock.mockResolvedValueOnce(problem(500, "ANALYTICS-0500"));
    expect(await act()).toEqual({ ok: false, reason: "unavailable" });
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect(await act()).toEqual({ ok: false, reason: "unavailable" });
  });
});

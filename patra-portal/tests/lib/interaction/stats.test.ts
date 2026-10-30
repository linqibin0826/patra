// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchHelpfulReceived, fetchPaperStats } from "@/lib/interaction/stats";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const errorSpy = vi.fn();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(errorSpy);
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
  errorSpy.mockReset();
});

const call = (index = 0) => fetchMock.mock.calls[index] ?? [];
const bodyOf = (index = 0) => JSON.parse(String(call(index)[1]?.body)) as unknown;
const headersOf = (index = 0) => (call(index)[1]?.headers ?? {}) as Record<string, string>;

/// 按请求体回每个 key 一行：收藏人数 = key 的数值，讨论数 = 它的两倍
function echoStats() {
  fetchMock.mockImplementation(async (_url, init) => {
    const { keys } = JSON.parse(String(init?.body)) as { keys: string[] };
    return Response.json({
      items: keys.map((key) => ({
        key,
        collectorCount: Number(key),
        discussionCount: Number(key) * 2,
      })),
    });
  });
}

describe("fetchPaperStats", () => {
  it("should_post_deduped_keys_as_publication_without_session", async () => {
    echoStats();
    await fetchPaperStats(["1", "2", "1"]);
    expect(String(call()[0])).toBe("http://gw.test/patra-interaction/subjects/stats");
    expect(call()[1]?.method).toBe("POST");
    expect(bodyOf()).toEqual({ type: "publication", keys: ["1", "2"] });
    expect(headersOf()).not.toHaveProperty("Authorization");
  });

  it("should_map_items_by_key", async () => {
    echoStats();
    const stats = await fetchPaperStats(["3", "4"]);
    expect(stats?.get("3")).toEqual({ collectorCount: 3, discussionCount: 6 });
    expect(stats?.get("4")).toEqual({ collectorCount: 4, discussionCount: 8 });
  });

  it("should_return_empty_map_without_request_when_no_keys", async () => {
    const stats = await fetchPaperStats([]);
    expect(stats?.size).toBe(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("should_split_into_batches_of_100", async () => {
    echoStats();
    const keys = Array.from({ length: 150 }, (_, index) => String(index + 1));
    const stats = await fetchPaperStats(keys);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((bodyOf(0) as { keys: string[] }).keys).toHaveLength(100);
    expect((bodyOf(1) as { keys: string[] }).keys).toHaveLength(50);
    expect(stats?.size).toBe(150);
  });

  it("should_return_null_when_any_batch_fails", async () => {
    let calls = 0;
    fetchMock.mockImplementation(async (_url, init) => {
      calls += 1;
      if (calls === 2) {
        return Response.json({ code: "INTERACTION-0500" }, { status: 503 });
      }
      const { keys } = JSON.parse(String(init?.body)) as { keys: string[] };
      return Response.json({
        items: keys.map((key) => ({ key, collectorCount: 1, discussionCount: 1 })),
      });
    });
    const keys = Array.from({ length: 150 }, (_, index) => String(index + 1));
    await expect(fetchPaperStats(keys)).resolves.toBeNull();
  });

  it.each([
    ["5xx", () => Response.json({ code: "INTERACTION-0500" }, { status: 500 })],
    ["响应不合法", () => Response.json({ items: [{ key: "1" }] })],
  ])("should_return_null_and_log_when_%s", async (_, reply) => {
    fetchMock.mockResolvedValue(reply());
    await expect(fetchPaperStats(["1"])).resolves.toBeNull();
    expect(errorSpy).toHaveBeenCalled();
  });

  it.each([
    ["超时", new DOMException("timed out", "TimeoutError")],
    ["网络错误", new TypeError("fetch failed")],
  ])("should_return_null_and_log_when_%s", async (_, error) => {
    fetchMock.mockRejectedValue(error);
    await expect(fetchPaperStats(["1"])).resolves.toBeNull();
    expect(errorSpy).toHaveBeenCalled();
  });
});

describe("fetchHelpfulReceived", () => {
  it("should_get_count_by_user_id_without_session", async () => {
    fetchMock.mockResolvedValue(Response.json({ helpfulReceived: 3 }));
    await expect(fetchHelpfulReceived("42")).resolves.toBe(3);
    expect(String(call()[0])).toBe("http://gw.test/patra-interaction/users/42/helpful-received");
    expect(headersOf()).not.toHaveProperty("Authorization");
  });

  it.each([
    ["5xx", () => Promise.resolve(Response.json({}, { status: 503 }))],
    ["超时", () => Promise.reject(new DOMException("timed out", "TimeoutError"))],
    ["响应不合法", () => Promise.resolve(Response.json({ helpfulReceived: -1 }))],
  ])("should_return_null_when_%s", async (_, reply) => {
    fetchMock.mockImplementation(reply);
    await expect(fetchHelpfulReceived("42")).resolves.toBeNull();
  });
});

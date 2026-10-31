// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { callNotification } from "@/lib/notification/request";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const TOKEN = "t".repeat(43);

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" }, { "user-agent": "UA/5" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// `console.error` 收到的全部参数，拼成一个字符串
const errorLog = () =>
  vi
    .mocked(console.error)
    .mock.calls.flat()
    .map((part) => (typeof part === "string" ? part : JSON.stringify(part)))
    .join(" ");

describe("callNotification", () => {
  it("默认带令牌请求 /patra-notification 下的路径，2xx 是 ok", async () => {
    fetchMock.mockResolvedValue(Response.json({ reply: "INSTANT", mention: "INSTANT" }));
    await expect(callNotification("/me/notification-preferences")).resolves.toMatchObject({
      kind: "ok",
      body: { reply: "INSTANT", mention: "INSTANT" },
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-notification/me/notification-preferences");
    expect(init?.headers).toMatchObject({
      Authorization: "Bearer patra_user_abc",
      "User-Agent": "UA/5",
    });
  });

  it("没有 Cookie 且要求登录：no-session，不请求网关", async () => {
    resetCookieJar();
    await expect(callNotification("/me/notification-preferences")).resolves.toEqual({
      kind: "no-session",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("auth: none：有 Cookie 也不带令牌", async () => {
    fetchMock.mockResolvedValue(Response.json({}));
    await callNotification("/unsubscribe", { method: "POST", json: {} }, { auth: "none" });
    const [, init] = fetchMock.mock.calls[0] ?? [];
    expect(init?.headers).not.toHaveProperty("Authorization");
  });

  it("查询参数拼进请求地址，日志只记不带查询串的路径", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const query = new URLSearchParams({ token: TOKEN, category: "REPLY" });
    await expect(callNotification("/unsubscribe", {}, { auth: "none", query })).resolves.toEqual({
      kind: "error",
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://gw.test/patra-notification/unsubscribe?token=${TOKEN}&category=REPLY`,
    );
    expect(errorLog()).toContain("/patra-notification/unsubscribe");
    expect(errorLog()).not.toContain(TOKEN);
  });

  it("网关地址配错时 fetch 的报错信息带着完整地址：日志里也没有令牌", async () => {
    fetchMock.mockImplementation(async (url) => {
      throw new TypeError(`Failed to parse URL from ${String(url)}`);
    });
    const query = new URLSearchParams({ token: TOKEN, category: "REPLY" });
    await expect(callNotification("/unsubscribe", {}, { auth: "none", query })).resolves.toEqual({
      kind: "error",
    });
    expect(errorLog()).toContain("/patra-notification/unsubscribe");
    expect(errorLog()).not.toContain(TOKEN);
  });

  it("4xx / 5xx 解析成 ProblemDetail", async () => {
    fetchMock.mockResolvedValue(
      Response.json(
        { code: "NOTIFICATION-0404", traits: ["NOT_FOUND", "UNSUBSCRIBE_LINK_INVALID"] },
        { status: 404 },
      ),
    );
    const reply = await callNotification("/unsubscribe", {}, { auth: "none" });
    expect(reply.kind).toBe("problem");
    if (reply.kind === "problem") {
      expect(reply.problem.status).toBe(404);
      expect(reply.problem.traits.has("UNSUBSCRIBE_LINK_INVALID")).toBe(true);
    }
  });
});

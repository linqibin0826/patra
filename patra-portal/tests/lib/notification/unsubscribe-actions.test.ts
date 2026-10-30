// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { LinkCategory } from "@/lib/notification/schema";
import { readUnsubscribeLink, unsubscribe } from "@/lib/notification/unsubscribe-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const TOKEN = "Ab3_-".repeat(8).concat("xyz");

const LINK = {
  maskedEmail: "c***@example.com",
  category: "MENTION",
  reply: "INSTANT",
  mention: "INSTANT",
  alreadyUnsubscribed: false,
  othersEnabled: true,
} as const;

const RESULT = {
  category: "MENTION",
  turnedOff: ["MENTION"],
  reply: "INSTANT",
  mention: "OFF",
  othersEnabled: true,
} as const;

const LINK_INVALID = {
  code: "NOTIFICATION-0404",
  traits: ["NOT_FOUND", "UNSUBSCRIBE_LINK_INVALID"],
};

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  // 带着登录状态打开落地页：也不能把会话令牌带给网关
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const respond = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

/// `console.error` 收到的全部参数，拼成一个字符串
const errorLog = () =>
  vi
    .mocked(console.error)
    .mock.calls.flat()
    .map((part) => (typeof part === "string" ? part : JSON.stringify(part)))
    .join(" ");

describe("readUnsubscribeLink", () => {
  it("GET 带令牌与类别的查询串，不带会话令牌，200 读出链接信息", async () => {
    respond(200, LINK);
    await expect(readUnsubscribeLink(TOKEN, "MENTION")).resolves.toEqual({ ok: true, link: LINK });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe(
      `http://gw.test/patra-notification/unsubscribe?token=${TOKEN}&category=MENTION`,
    );
    expect(init?.method ?? "GET").toBe("GET");
    expect(init?.headers).not.toHaveProperty("Authorization");
  });

  it("404 带 UNSUBSCRIBE_LINK_INVALID：invalid", async () => {
    respond(404, LINK_INVALID);
    await expect(readUnsubscribeLink(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "invalid",
    });
  });

  it("没有原因码的 404（网关路由缺失）、503、形状不对：unavailable，日志里没有令牌", async () => {
    respond(404, { code: "GW-0404" });
    await expect(readUnsubscribeLink(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    respond(503, { code: "NOTIFICATION-0503", traits: ["DEP_UNAVAILABLE"] });
    await expect(readUnsubscribeLink(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    respond(200, { ...LINK, maskedEmail: "" });
    await expect(readUnsubscribeLink(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(console.error).toHaveBeenCalled();
    expect(errorLog()).not.toContain(TOKEN);
  });

  it("令牌格式不对、类别不认识：invalid，不请求网关", async () => {
    await expect(readUnsubscribeLink(TOKEN.slice(1), "REPLY")).resolves.toEqual({
      ok: false,
      reason: "invalid",
    });
    await expect(readUnsubscribeLink(TOKEN, "ALL" as LinkCategory)).resolves.toEqual({
      ok: false,
      reason: "invalid",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("unsubscribe", () => {
  it("POST { token, category }，不带会话令牌，200 读出结果", async () => {
    respond(200, RESULT);
    await expect(unsubscribe(TOKEN, "MENTION")).resolves.toEqual({ ok: true, result: RESULT });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-notification/unsubscribe");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ token: TOKEN, category: "MENTION" });
    expect(init?.headers).not.toHaveProperty("Authorization");
  });

  it("关闭全部：类别是 ALL", async () => {
    respond(200, {
      ...RESULT,
      category: "ALL",
      turnedOff: ["REPLY", "MENTION"],
      othersEnabled: false,
    });
    await unsubscribe(TOKEN, "ALL");
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      token: TOKEN,
      category: "ALL",
    });
  });

  it("404 带原因码：invalid；形状不对：unavailable，日志里没有令牌", async () => {
    respond(404, LINK_INVALID);
    await expect(unsubscribe(TOKEN, "REPLY")).resolves.toEqual({ ok: false, reason: "invalid" });
    respond(200, { ...RESULT, turnedOff: ["HELPFUL"] });
    await expect(unsubscribe(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(errorLog()).not.toContain(TOKEN);
  });

  it("请求抛错：unavailable", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(unsubscribe(TOKEN, "REPLY")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
  });
});

// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { callInteraction } from "@/lib/interaction/request";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

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

describe("callInteraction", () => {
  it("带令牌请求 /patra-interaction 下的路径，2xx 是 ok", async () => {
    fetchMock.mockResolvedValue(Response.json({ total: 1 }));
    await expect(callInteraction("/me/library")).resolves.toMatchObject({
      kind: "ok",
      body: { total: 1 },
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-interaction/me/library");
    expect(init?.headers).toMatchObject({ Authorization: "Bearer patra_user_abc" });
  });

  it("auth: none：有 Cookie 也不带令牌", async () => {
    fetchMock.mockResolvedValue(Response.json({}));
    await callInteraction("/discussions/publication/1/threads", {}, { auth: "none" });
    const [, init] = fetchMock.mock.calls[0] ?? [];
    expect(init?.headers).not.toHaveProperty("Authorization");
  });

  it("没有 Cookie 且要求登录：no-session，不请求网关", async () => {
    resetCookieJar();
    await expect(callInteraction("/me/library")).resolves.toEqual({ kind: "no-session" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("登录可选且没有 Cookie：不带令牌照常请求", async () => {
    resetCookieJar();
    fetchMock.mockResolvedValue(Response.json({}));
    await callInteraction("/collections/9", {}, { auth: "optional" });
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty("Authorization");
  });

  it("透传方法、JSON 体与超时", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [] }));
    const timeout = vi.spyOn(AbortSignal, "timeout");
    await callInteraction("/me/library/status", {
      method: "POST",
      json: { type: "publication", keys: ["1"] },
      timeoutMs: 1500,
    });
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ type: "publication", keys: ["1"] });
    expect(timeout).toHaveBeenCalledWith(1500);
  });

  it("4xx / 5xx 是 problem，带解析出的原因码", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ code: "INTERACTION-0409", traits: ["LIBRARY_FULL"] }, { status: 409 }),
    );
    const reply = await callInteraction("/me/library/publication/1", { method: "PUT" });
    expect(reply.kind).toBe("problem");
    if (reply.kind === "problem") {
      expect(reply.problem.status).toBe(409);
      expect(reply.problem.traits.has("LIBRARY_FULL")).toBe(true);
    }
  });

  it("请求抛错（超时、网络）：error", async () => {
    fetchMock.mockRejectedValue(new DOMException("timeout", "TimeoutError"));
    await expect(callInteraction("/me/library")).resolves.toEqual({ kind: "error" });
  });
});

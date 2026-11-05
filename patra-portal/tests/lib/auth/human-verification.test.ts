// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readHumanVerification } from "@/lib/auth/human-verification";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  resetCookieJar();
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const respondWith = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

describe("readHumanVerification", () => {
  it("返回当前厂商和站点标识", async () => {
    respondWith(200, { provider: "turnstile", siteKey: "site-1" });

    await expect(readHumanVerification()).resolves.toEqual({
      provider: "turnstile",
      siteKey: "site-1",
    });
    const [url, init] = fetchMock.mock.calls.at(-1) ?? [];
    expect(String(url)).toBe("http://gw.test:9528/patra-identity/auth/human-verification");
    expect(init?.cache).toBe("no-store");
  });

  it.each([
    ["响应形状不对", { provider: "turnstile" }],
    ["站点标识为空", { provider: "turnstile", siteKey: "" }],
    ["厂商不认识", { provider: "other", siteKey: "site-1" }],
  ])("%s：返回 null", async (_name, body) => {
    respondWith(200, body);

    await expect(readHumanVerification()).resolves.toBeNull();
  });

  it("网关返回错误：返回 null", async () => {
    respondWith(503, { status: 503 });

    await expect(readHumanVerification()).resolves.toBeNull();
  });

  it("网关不可用：返回 null，不抛异常", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));

    await expect(readHumanVerification()).resolves.toBeNull();
  });
});

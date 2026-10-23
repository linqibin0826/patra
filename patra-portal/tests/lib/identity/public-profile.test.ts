// @vitest-environment node
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchPublicProfile,
  isSafeHref,
  joinedLabel,
  linkLabel,
} from "@/lib/identity/public-profile";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar();
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const respond = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

describe("fetchPublicProfile", () => {
  it.each(["a.b", "a/b", "", "a".repeat(65), "陈宇"])(
    "rejects_invalid_username_without_calling_gateway：%j",
    async (username) => {
      await expect(fetchPublicProfile(username)).resolves.toEqual({ kind: "not-found" });
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("passes_uppercase_username_through", async () => {
    respond(200, PUBLIC_PROFILE);
    await fetchPublicProfile("Chen-Yu");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/users/Chen-Yu");
  });

  it("200：found；匿名不带令牌，不跟随重定向", async () => {
    respond(200, PUBLIC_PROFILE);
    await expect(fetchPublicProfile("chen-yu")).resolves.toEqual({
      kind: "found",
      profile: PUBLIC_PROFILE,
    });
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.headers).not.toHaveProperty("Authorization");
    expect(init?.redirect).toBe("manual");
  });

  it("有会话 Cookie：带上令牌（仅登录用户可见、本人视角都靠它）", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    respond(200, PUBLIC_PROFILE);
    await fetchPublicProfile("chen-yu");
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toMatchObject({
      Authorization: "Bearer patra_user_abc",
    });
  });

  it("301 体里有新用户名：moved", async () => {
    fetchMock.mockImplementation(
      async () =>
        new Response(JSON.stringify({ username: "new-name" }), {
          status: 301,
          headers: { Location: "/users/new-name", "Content-Type": "application/json" },
        }),
    );
    await expect(fetchPublicProfile("old-name")).resolves.toEqual({
      kind: "moved",
      username: "new-name",
    });
  });

  it("404：not-found", async () => {
    respond(404, { code: "IDN-0404", traits: ["NOT_FOUND"] });
    await expect(fetchPublicProfile("nobody")).resolves.toEqual({ kind: "not-found" });
  });

  it.each([
    ["500", () => respond(500, { code: "IDN-0500" })],
    ["200 但形状不对", () => respond(200, { username: 1 })],
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
  ])("%s：抛错", async (_label, arrange) => {
    arrange();
    await expect(fetchPublicProfile("chen-yu")).rejects.toThrow();
  });
});

describe("展示用的小函数", () => {
  it("加入时间按 YYYY-MM 直接拆", () => {
    expect(joinedLabel("2026-10")).toBe("加入于 2026 年 10 月");
    expect(joinedLabel("2027-03")).toBe("加入于 2027 年 3 月");
  });

  it("链接：有标题用标题，没有用域名（去掉 www.）", () => {
    expect(linkLabel({ title: "实验室主页", url: "https://lab.example.edu/chen" })).toBe(
      "实验室主页",
    );
    expect(linkLabel({ title: null, url: "https://www.scholar.example.com/chen" })).toBe(
      "scholar.example.com",
    );
  });

  it("只放行 http(s) 链接", () => {
    expect(isSafeHref("https://a.org")).toBe(true);
    expect(isSafeHref("http://a.org")).toBe(true);
    expect(isSafeHref("javascript:alert(1)")).toBe(false);
    expect(isSafeHref("data:text/html,x")).toBe(false);
  });
});

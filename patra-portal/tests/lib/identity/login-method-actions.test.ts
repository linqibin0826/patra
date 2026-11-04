// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startProviderLink, unlinkProvider } from "@/lib/identity/login-method-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const AUTH_URL = "https://orcid.org/oauth/authorize?client_id=c&state=s-9";
const PUBLIC = { host: "patra.linqibin.dev" };

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

function signedIn(headers: Record<string, string> = PUBLIC) {
  return resetCookieJar({ patra_session: "patra_user_abc" }, headers);
}

function problem(status: number, traits: string[] = []) {
  return Response.json({ code: `IDN-0${status}`, traits }, { status });
}

describe("startProviderLink", () => {
  it("成功：写 state 与发起页 Cookie，把地址还给浏览器", async () => {
    const jar = signedIn();
    fetchMock.mockResolvedValue(Response.json({ authorizationUrl: AUTH_URL }));
    await expect(startProviderLink("orcid")).resolves.toEqual({
      ok: true,
      authorizationUrl: AUTH_URL,
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/login-methods/orcid/link");
    expect(init?.method).toBe("POST");
    expect(init?.headers).toMatchObject({ Authorization: "Bearer patra_user_abc" });
    expect(jar.written[0]).toMatch(/^patra_oauth_state=s-9;/);
    expect(jar.written[1]).toContain("from%3Dsettings%26next%3D%252Fsettings%252Fsecurity");
  });

  it("不认识的平台：不拼路径，不请求", async () => {
    signedIn();
    await expect(startProviderLink("../sessions/others")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("tailnet 地址：不请求", async () => {
    signedIn({ host: "100.103.73.27:4000" });
    await expect(startProviderLink("orcid")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["超过再认证窗口", problem(403, ["FORBIDDEN", "REAUTH_REQUIRED"]), "reauth-required"],
    ["邮箱未验证", problem(409, ["CONFLICT", "EMAIL_NOT_VERIFIED"]), "email-not-verified"],
    ["已绑同一家", problem(409, ["CONFLICT", "PROVIDER_ALREADY_LINKED"]), "already-linked"],
    ["冷静期", problem(409, ["CONFLICT", "PENDING_DELETION"]), "pending-deletion"],
    ["会话失效", problem(401), "signed-out"],
    ["这家平台没配置（404）", problem(404), "unavailable"],
    ["其他", problem(500), "unavailable"],
  ])("%s", async (_name, reply, reason) => {
    signedIn();
    fetchMock.mockResolvedValue(reply);
    await expect(startProviderLink("orcid")).resolves.toEqual({ ok: false, reason });
  });

  it("响应里的地址没有 state：unavailable，不写 Cookie", async () => {
    const jar = signedIn();
    fetchMock.mockResolvedValue(Response.json({ authorizationUrl: "https://orcid.org/x" }));
    await expect(startProviderLink("orcid")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(jar.written).toEqual([]);
  });

  it("没有会话：signed-out，不请求", async () => {
    resetCookieJar({}, PUBLIC);
    await expect(startProviderLink("orcid")).resolves.toEqual({
      ok: false,
      reason: "signed-out",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("unlinkProvider", () => {
  it("204：成功；tailnet 上照常", async () => {
    signedIn({ host: "100.103.73.27:4000" });
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(unlinkProvider("github")).resolves.toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/login-methods/github");
    expect(init?.method).toBe("DELETE");
  });

  it("不认识的平台：不请求", async () => {
    signedIn();
    await expect(unlinkProvider("../x")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["唯一的登录方式", problem(409, ["CONFLICT", "LAST_LOGIN_METHOD"]), "last-method"],
    ["再认证", problem(403, ["FORBIDDEN", "REAUTH_REQUIRED"]), "reauth-required"],
    ["冷静期", problem(409, ["CONFLICT", "PENDING_DELETION"]), "pending-deletion"],
    ["其他", problem(500), "unavailable"],
  ])("%s", async (_name, reply, reason) => {
    signedIn();
    fetchMock.mockResolvedValue(reply);
    await expect(unlinkProvider("github")).resolves.toEqual({ ok: false, reason });
  });
});

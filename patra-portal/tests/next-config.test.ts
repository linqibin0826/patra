// @vitest-environment node
import { describe, expect, it, vi } from "vitest";
import nextConfig from "../next.config";

describe("next.config", () => {
  it("开发时不在终端打印 Server Action 的参数：登录、注册的参数里有明文密码", () => {
    expect(nextConfig.logging).toMatchObject({ serverFunctions: false });
  });

  it("开发时不在终端打印一键退订的请求：地址的查询串里有退订令牌", () => {
    const logging = nextConfig.logging;
    const incoming = logging ? logging.incomingRequests : undefined;
    const ignore = typeof incoming === "object" ? (incoming.ignore ?? []) : [];
    expect(ignore.some((pattern) => pattern.test("/unsubscribe/one-click?token=x"))).toBe(true);
    expect(
      ignore.some((pattern) => pattern.test("http://localhost:4000/unsubscribe/one-click?token=x")),
    ).toBe(true);
    expect(ignore.some((pattern) => pattern.test("/unsubscribe"))).toBe(false);
  });

  it("开发时不在终端打印第三方登录的回调：地址的查询串里有授权码和 state", () => {
    const logging = nextConfig.logging;
    const incoming = logging ? logging.incomingRequests : undefined;
    const ignore = typeof incoming === "object" ? (incoming.ignore ?? []) : [];
    expect(
      ignore.some((pattern) => pattern.test("/api/auth/oauth/callback/github?code=x&state=y")),
    ).toBe(true);
    expect(ignore.some((pattern) => pattern.test("/api/auth/oauth/github/start"))).toBe(false);
  });
});

describe("人机验证页面的 CSP", () => {
  const cspOf = async (source: string) => {
    const rules = (await nextConfig.headers?.()) ?? [];
    return rules
      .find((rule) => rule.source === source)
      ?.headers.find((header) => header.key === "Content-Security-Policy")?.value;
  };

  it.each(["/login", "/register", "/forgot-password"])("%s 放行两家的 iframe", async (path) => {
    const csp = await cspOf(path);

    expect(csp).toContain("frame-src https://challenges.cloudflare.com https://api.trustturn.cc");
  });

  it.each(["/login", "/register", "/forgot-password"])(
    "%s 的脚本只放行 Cloudflare，不放行 TRUSTTURN",
    async (path) => {
      const scriptSrc = (await cspOf(path))?.split(";").find((d) => d.includes("script-src"));

      expect(scriptSrc).toContain("https://challenges.cloudflare.com");
      expect(scriptSrc).not.toContain("trustturn");
    },
  );

  it("只有开发模式才放行 unsafe-eval", async () => {
    vi.stubEnv("NODE_ENV", "production");
    expect(await cspOf("/login")).not.toContain("unsafe-eval");

    vi.stubEnv("NODE_ENV", "development");
    expect(await cspOf("/login")).toContain("unsafe-eval");

    vi.unstubAllEnvs();
  });

  it("其他页面没有这个头", async () => {
    const rules = (await nextConfig.headers?.()) ?? [];

    expect(rules.map((rule) => rule.source).sort()).toEqual([
      "/forgot-password",
      "/login",
      "/register",
    ]);
  });
});

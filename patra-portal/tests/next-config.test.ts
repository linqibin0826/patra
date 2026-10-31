// @vitest-environment node
import { describe, expect, it } from "vitest";
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
});

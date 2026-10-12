// @vitest-environment node
import { describe, expect, it } from "vitest";
import nextConfig from "../next.config";

describe("next.config", () => {
  it("开发时不在终端打印 Server Action 的参数：登录、注册的参数里有明文密码", () => {
    expect(nextConfig.logging).toMatchObject({ serverFunctions: false });
  });
});

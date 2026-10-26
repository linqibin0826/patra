// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, describe, expect, it, vi } from "vitest";
import { publicUrl } from "@/lib/public-url";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("publicUrl", () => {
  it("配了门户公开地址：按它拼，去掉末尾斜杠", async () => {
    vi.stubEnv("PATRA_PORTAL_PUBLIC_URL", "http://linqibins-mac-mini:4000/");
    resetCookieJar({}, { host: "100.103.73.27:4000" });
    await expect(publicUrl("/collections/9")).resolves.toBe(
      "http://linqibins-mac-mini:4000/collections/9",
    );
  });

  it("没配：用这次请求的 Host", async () => {
    vi.stubEnv("PATRA_PORTAL_PUBLIC_URL", "");
    resetCookieJar({}, { host: "localhost:4000" });
    await expect(publicUrl("/collections/9")).resolves.toBe("http://localhost:4000/collections/9");
  });
});

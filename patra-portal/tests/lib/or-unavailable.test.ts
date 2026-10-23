import { describe, expect, it } from "vitest";
import { orUnavailable } from "@/lib/or-unavailable";

describe("orUnavailable", () => {
  it("成功时原样返回结果", async () => {
    await expect(orUnavailable(Promise.resolve({ ok: true as const, value: 1 }))).resolves.toEqual({
      ok: true,
      value: 1,
    });
  });

  it("promise 被拒绝（网络断开、Server Action 请求失败）：unavailable", async () => {
    await expect(orUnavailable(Promise.reject(new TypeError("failed")))).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
  });
});

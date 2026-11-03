import { describe, expect, it } from "vitest";
import { newDeviceId, newId, truncatePath } from "@/lib/analytics/ids";
import { DEVICE_ID_PATTERN } from "@/lib/auth/session-cookie";

const UUID_V7 = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("newId", () => {
  it("是小写 UUIDv7", () => {
    expect(newId()).toMatch(UUID_V7);
  });

  it("按时间有序：后生成的不小于先生成的", () => {
    const first = newId();
    const second = newId();
    expect(second >= first).toBe(true);
  });
});

describe("newDeviceId", () => {
  it("是 43 个字符的 base64url，与登录用的设备标识同一格式", () => {
    expect(newDeviceId()).toMatch(DEVICE_ID_PATTERN);
  });
});

describe("truncatePath", () => {
  it("超过 512 个字符截到 512（契约上限）", () => {
    expect(truncatePath(`/${"a".repeat(600)}`)).toHaveLength(512);
  });

  it("不超过时原样", () => {
    expect(truncatePath("/papers/42")).toBe("/papers/42");
  });
});

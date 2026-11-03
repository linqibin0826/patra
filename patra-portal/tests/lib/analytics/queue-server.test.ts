// @vitest-environment node
import { describe, expect, it } from "vitest";
import { flushWithBeacon, track } from "@/lib/analytics/client/queue";

describe("服务端渲染时队列不做事", () => {
  it("track 与 flushWithBeacon 不抛错", () => {
    expect(() => track("page_viewed", {})).not.toThrow();
    expect(() => flushWithBeacon()).not.toThrow();
  });
});

import { describe, expect, it } from "vitest";
import { TOPIC_CLOUD, topicTier } from "@/data/topics";

describe("topicTier", () => {
  it.each([
    [100, 1],
    [88, 1],
    [87, 2],
    [70, 2],
    [69, 3],
    [45, 3],
    [44, 4],
    [25, 4],
    [24, 5],
    [0, 5],
  ])("topicTier(%i) === %i", (heat, tier) => {
    expect(topicTier(heat)).toBe(tier);
  });
});

describe("TOPIC_CLOUD", () => {
  it("含 32 个 topic", () => {
    expect(TOPIC_CLOUD).toHaveLength(32);
  });

  it("按 heat 倒序排列", () => {
    for (let i = 1; i < TOPIC_CLOUD.length; i++) {
      const prev = TOPIC_CLOUD[i - 1];
      const curr = TOPIC_CLOUD[i];
      if (!prev || !curr) throw new Error("unreachable");
      expect(prev.heat).toBeGreaterThanOrEqual(curr.heat);
    }
  });

  it("tier 1 项均带 delta", () => {
    for (const t of TOPIC_CLOUD) {
      if (topicTier(t.heat) === 1) {
        expect(t.delta).toBeDefined();
      }
    }
  });
});

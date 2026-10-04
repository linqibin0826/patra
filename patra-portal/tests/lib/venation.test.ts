import { describe, expect, it } from "vitest";
import { generateVenation, type VeinKind } from "@/lib/venation";

const numbers = (d: string) => [...d.matchAll(/-?\d+(?:\.\d+)?/g)].map((m) => Number(m[0]));

describe("generateVenation 叶脉生成器", () => {
  it("同参同输出（SSR 与 CSR 渲染一致）", () => {
    expect(generateVenation({ seed: 7 })).toEqual(generateVenation({ seed: 7 }));
  });

  it("结构数量：1 叶缘 + 1 主脉 + 2N 二级脉 + 2(N−1) 环结 + 6(N−1) 三级脉", () => {
    const { paths } = generateVenation({ secondaryCount: 8 });
    const count = (k: VeinKind) => paths.filter((p) => p.kind === k).length;
    expect([
      count("margin"),
      count("midrib"),
      count("secondary"),
      count("loop"),
      count("tertiary"),
    ]).toEqual([1, 1, 16, 14, 42]);
  });

  it("种子只改变细微抖动，不改变结构", () => {
    const a = generateVenation({ seed: 1 });
    const b = generateVenation({ seed: 2 });
    expect(a.paths.map((p) => p.kind)).toEqual(b.paths.map((p) => p.kind));
    expect(a.paths.map((p) => p.d)).not.toEqual(b.paths.map((p) => p.d));
  });

  it("所有坐标落在画布内", () => {
    const { width, height, paths } = generateVenation();
    for (const p of paths) {
      const xs = numbers(p.d);
      for (let i = 0; i < xs.length; i += 2) {
        expect(xs[i]).toBeGreaterThanOrEqual(0);
        expect(xs[i]).toBeLessThanOrEqual(width);
        expect(xs[i + 1]).toBeGreaterThanOrEqual(0);
        expect(xs[i + 1]).toBeLessThanOrEqual(height);
      }
    }
  });

  it("绘制顺序：叶缘与主脉为 0，三级脉晚于全部二级脉，取值落在 [0, 1]", () => {
    const { paths } = generateVenation();
    const orders = (k: VeinKind) => paths.filter((p) => p.kind === k).map((p) => p.order);
    expect([...orders("margin"), ...orders("midrib")]).toEqual([0, 0]);
    expect(Math.min(...orders("tertiary"))).toBeGreaterThan(Math.max(...orders("secondary")));
    for (const p of paths) {
      expect(p.order).toBeGreaterThanOrEqual(0);
      expect(p.order).toBeLessThanOrEqual(1);
    }
  });
});

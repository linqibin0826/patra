/// 叶脉生成器：在「主脉参数 t × 法向偏移 v」坐标里描述羽状环结脉（brochidodromous），再映射到平面。
/// 纯函数、同参同输出（SSR 与 CSR 一致），供首页「图 1」、页脚水印、状态页与空态的叶脉母题共用。

export type VeinKind = "margin" | "midrib" | "secondary" | "loop" | "tertiary";

export interface VeinPath {
  kind: VeinKind;
  /** SVG path data（绝对坐标，1 位小数） */
  d: string;
  /** 0–1 的绘制次序：描线入场据此错峰（叶缘与主脉先，二级脉自叶基向叶尖，细脉最后） */
  order: number;
}

export interface Venation {
  width: number;
  height: number;
  paths: VeinPath[];
}

export interface VenationOptions {
  /** 伪随机种子：只影响细微抖动，不影响结构数量 */
  seed?: number;
  /** 每侧二级脉条数 */
  secondaryCount?: number;
}

interface Point {
  x: number;
  y: number;
}

interface Secondary {
  /** 二级脉末端在主脉上的参数 */
  end: number;
  /** 末端距叶缘的相对位置（0–1，乘以半宽） */
  outward: number;
  pts: Point[];
}

const WIDTH = 420;
const HEIGHT = 940;
const BASE: Point = { x: 214, y: 860 };
const TIP: Point = { x: 190, y: 40 };
const CTRL: Point = { x: 262, y: 470 };
const HALF_WIDTH = 160;
const SHAPE_A = 0.75;
const SHAPE_B = 1.1;
const T_STAR = SHAPE_A / (SHAPE_A + SHAPE_B);
const F_MAX = T_STAR ** SHAPE_A * (1 - T_STAR) ** SHAPE_B;
const TERTIARY_STOPS = [0.28, 0.5, 0.72] as const;
const T_MAX = 0.995;

/// 线性同余伪随机数：同 seed 同序列。
function lcg(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/// 主脉参数 t 处的叶片半宽：t^a(1−t)^b 归一到 HALF_WIDTH（叶基与叶尖为 0）。
function halfWidth(t: number): number {
  if (t <= 0 || t >= 1) return 0;
  return (HALF_WIDTH * (t ** SHAPE_A * (1 - t) ** SHAPE_B)) / F_MAX;
}

/// 主脉（二次贝塞尔，叶基 → 叶尖）上的点。
function midrib(t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * BASE.x + 2 * u * t * CTRL.x + t * t * TIP.x,
    y: u * u * BASE.y + 2 * u * t * CTRL.y + t * t * TIP.y,
  };
}

/// 主脉在 t 处的单位法向量（左侧为正）。
function normal(t: number): Point {
  const x = 2 * (1 - t) * (CTRL.x - BASE.x) + 2 * t * (TIP.x - CTRL.x);
  const y = 2 * (1 - t) * (CTRL.y - BASE.y) + 2 * t * (TIP.y - CTRL.y);
  const len = Math.hypot(x, y);
  return { x: -y / len, y: x / len };
}

/// 叶面坐标（主脉参数 t、侧别、法向偏移 v）→ 平面坐标。
function at(t: number, side: 1 | -1, v: number): Point {
  const m = midrib(t);
  const n = normal(t);
  return { x: m.x + side * n.x * v, y: m.y + side * n.y * v };
}

const fmt = (p: Point) => `${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
const polyline = (pts: Point[]) => `M${pts.map(fmt).join(" L")}`;

/// 在折线点列上按比例取点（避开首尾，三级脉不与主脉 / 叶缘相接）。
function pick(pts: Point[], ratio: number): Point {
  const i = Math.round(ratio * (pts.length - 1));
  return pts[Math.max(1, Math.min(pts.length - 2, i))] as Point;
}

export function generateVenation({
  seed = 11,
  secondaryCount = 10,
}: VenationOptions = {}): Venation {
  const rnd = lcg(seed);
  const paths: VeinPath[] = [];

  // 叶缘：左缘自基至尖，右缘自尖回基，闭合
  const outline: Point[] = [];
  for (let i = 0; i <= 160; i++) outline.push(at(i / 160, 1, halfWidth(i / 160)));
  for (let i = 160; i >= 0; i--) outline.push(at(i / 160, -1, halfWidth(i / 160)));
  paths.push({ kind: "margin", d: `${polyline(outline)} Z`, order: 0 });

  // 主脉 + 叶柄
  const stem: Point[] = [{ x: BASE.x + 5, y: BASE.y + 34 }];
  for (let i = 0; i <= 80; i++) stem.push(midrib(i / 80));
  paths.push({ kind: "midrib", d: polyline(stem), order: 0 });

  // 二级脉：两侧交错，以锐角斜向叶尖发出，先快速外伸、再沿叶缘上弯（v ∝ 1 − (1 − τ)^1.9）
  const sides: Record<"left" | "right", Secondary[]> = { left: [], right: [] };
  for (let i = 0; i < secondaryCount; i++) {
    for (const side of [1, -1] as const) {
      const t0 = 0.04 + 0.84 * ((i + (side === 1 ? 0.15 : 0.6)) / secondaryCount) ** 1.05;
      const reach = 0.13 + 0.08 * (1 - t0) + (rnd() - 0.5) * 0.015;
      const outward = 0.86 + (rnd() - 0.5) * 0.04;
      const pts: Point[] = [];
      for (let j = 0; j <= 22; j++) {
        const tau = j / 22;
        const t = Math.min(t0 + reach * tau, T_MAX);
        pts.push(at(t, side, halfWidth(t) * outward * (1 - (1 - tau) ** 1.9)));
      }
      sides[side === 1 ? "left" : "right"].push({
        end: Math.min(t0 + reach, T_MAX),
        outward,
        pts,
      });
      paths.push({ kind: "secondary", d: polyline(pts), order: 0.08 + 0.5 * t0 });
    }
  }

  // 环结（相邻二级脉末端沿叶缘内侧相连）与三级横脉
  for (const [key, side] of [
    ["left", 1],
    ["right", -1],
  ] as const) {
    const veins = sides[key];
    for (let k = 0; k < veins.length - 1; k++) {
      const a = veins[k] as Secondary;
      const b = veins[k + 1] as Secondary;
      const loop: Point[] = [];
      for (let j = 0; j <= 10; j++) {
        const s = j / 10;
        const t = Math.min(a.end + (b.end - a.end) * s, T_MAX);
        const k0 = a.outward + (b.outward - a.outward) * s + 0.06 * Math.sin(Math.PI * s);
        loop.push(at(t, side, halfWidth(t) * k0));
      }
      paths.push({ kind: "loop", d: polyline(loop), order: 0.56 + 0.3 * a.end });

      for (const stop of TERTIARY_STOPS) {
        const p = pick(a.pts, stop + (rnd() - 0.5) * 0.08);
        const q = pick(b.pts, stop - 0.1 + (rnd() - 0.5) * 0.08);
        const m = {
          x: (p.x + q.x) / 2 + (rnd() - 0.5) * 7,
          y: (p.y + q.y) / 2 + (rnd() - 0.5) * 7,
        };
        paths.push({
          kind: "tertiary",
          d: `M${fmt(p)} Q${fmt(m)} ${fmt(q)}`,
          order: 0.7 + 0.28 * a.end,
        });
      }
    }
  }

  return { width: WIDTH, height: HEIGHT, paths };
}

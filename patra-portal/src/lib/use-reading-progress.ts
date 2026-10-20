import { useEffect, useState } from "react";

/// 吸顶线：TopNav 57 + 面包屑 45 + 网格上边距 32 = 134px，与侧栏吸顶偏移一致。
const STICKY_LINE = 134;

/// 吸顶线在区间 [top, bottom) 里的位置，夹在 0–1。
export function progressIn(top: number, bottom: number, line: number): number {
  if (bottom <= top) return line >= top ? 1 : 0;
  return Math.min(1, Math.max(0, (line - top) / (bottom - top)));
}

/// 正文剩余阅读分钟：按未读比例向上取整。
export function remainingMinutes(readMin: number, bodyProgress: number): number {
  return Math.ceil(readMin * (1 - bodyProgress));
}

export interface ReadingProgress {
  /** 正文进度：从正文第一个章节到声明区（没有声明区到参考文献，再没有到正文末尾） */
  body: number;
  /** 整篇进度：从全文区块头到许可与出处底部 */
  whole: number;
  /** 正文顶部是否已越过吸顶线 */
  started: boolean;
}

function measure(): ReadingProgress {
  const rect = (selector: string) => document.querySelector(selector)?.getBoundingClientRect();
  const body = rect("[data-ft-body]");
  const bodyEnd = rect("#declarations") ?? rect("#references");
  const whole = rect("#fulltext");
  const license = rect("#license");
  return {
    body: body ? progressIn(body.top, bodyEnd?.top ?? body.bottom, STICKY_LINE) : 0,
    whole: whole ? progressIn(whole.top, license?.bottom ?? whole.bottom, STICKY_LINE) : 0,
    started: body ? body.top <= STICKY_LINE : false,
  };
}

/// 阅读进度（spec §5.15 / §5.16）：目录卡用正文进度算剩余分钟，面包屑显示整篇进度。
/// 滚动、窗口尺寸变化时重新量（只量 4 个元素，开销可以忽略）。
export function useReadingProgress(): ReadingProgress {
  const [progress, setProgress] = useState<ReadingProgress>({
    body: 0,
    whole: 0,
    started: false,
  });

  useEffect(() => {
    const update = () => setProgress(measure());
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => {
      window.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, []);

  return progress;
}

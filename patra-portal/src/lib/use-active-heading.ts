import { useEffect, useState } from "react";

/// 吸顶线：TopNav 57 + 面包屑 45 + 16 留白，与正文标题的 `scroll-margin-top: 117px` 对齐；
/// 留一点余量，目录跳转落在 117px 的标题也算进入。
const STICKY_LINE = 140;

/// 当前所在章节：按文档顺序传入各章节标题的 id，返回标题已越过吸顶线的最后一个；
/// 还没读到正文时为 null。滚动、窗口尺寸变化时重新判断（几十个标题，逐个量位置的开销可以忽略）。
export function useActiveHeading(ids: readonly string[]): string | null {
  const [active, setActive] = useState<string | null>(null);

  useEffect(() => {
    const update = () => {
      let current: string | null = null;
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= STICKY_LINE) {
          current = id;
        }
      }
      setActive(current);
    };
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => {
      window.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, [ids]);

  return active;
}

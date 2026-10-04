import { useEffect, useRef, useState } from "react";

/// 横向滚动容器的右缘渐隐提示：只有右侧还有内容可滑时才返回 `fadeEnd = true`，
/// 内容放得下或已滑到尽头时为 false。容器尺寸变化（窗口缩放、选项增减）时重新判断。
export function useScrollFade<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [fadeEnd, setFadeEnd] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    // 留 1px 容差：缩放比例下 scrollLeft 可能是小数
    const update = () => setFadeEnd(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
    update();
    el.addEventListener("scroll", update, { passive: true });
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(update);
    observer?.observe(el);
    return () => {
      el.removeEventListener("scroll", update);
      observer?.disconnect();
    };
  }, []);

  return { ref, fadeEnd };
}

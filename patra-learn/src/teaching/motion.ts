// patra-learn/src/teaching/motion.ts
// 教学组件的动效：时长与缓动对应设计简报 2.3 节的 token，统一走 GSAP。
// 不跟随系统「减少动态效果」设置（release spec 2.1 交互、决策 D）
"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { type RefObject, useLayoutEffect, useRef } from "react";

gsap.registerPlugin(useGSAP);

/// 秒：悬停按下 / 揭晓展开 / 逐步动画里物件移动
export const DURATION = { quick: 0.16, reveal: 0.28, move: 0.56 } as const;

export const EASE = { out: "power3.out", inOut: "power2.inOut", spring: "back.out(1.6)" } as const;

/// 从下方浮入（判定条、说明、展开的内容）
export function rise(el: Element | null) {
  if (!el) return;
  gsap.fromTo(
    el,
    { opacity: 0, y: 6 },
    { opacity: 1, y: 0, duration: DURATION.reveal, ease: EASE.out },
  );
}

/// 弹一下（状态机的当前状态）
export function pop(el: Element | null) {
  if (!el) return;
  gsap.fromTo(
    el,
    { scale: 1 },
    { scale: 1.06, duration: DURATION.reveal / 2, ease: EASE.spring, yoyo: true, repeat: 1 },
  );
}

/// 左右抖动（点了不会发生的事件、进程崩溃的那条线）
export function shake(el: Element | null) {
  if (!el) return;
  gsap.fromTo(
    el,
    { x: 0 },
    { keyframes: { x: [-5, 5, -3, 3, 0] }, duration: 0.36, ease: EASE.inOut },
  );
}

/// 平移到新位置：从上一次的位置补间到 0（FLIP），前进后退都是同一条路径
export function slideFrom(el: Element | null, dx: number, dy: number) {
  if (!el || (dx === 0 && dy === 0)) return;
  gsap.fromTo(
    el,
    { x: dx, y: dy },
    { x: 0, y: 0, duration: DURATION.move, ease: EASE.inOut, overwrite: true },
  );
}

/// key 变化（以及首次挂载）时对 ref 播一次动效
export function useMotionOnChange<T extends Element>(
  effect: (el: T | null) => void,
  key: unknown,
): RefObject<T | null> {
  const ref = useRef<T>(null);
  useGSAP(() => effect(ref.current), { dependencies: [key], scope: ref });
  return ref;
}

/// 位置从 (x, y) 变了就从旧位置滑过来；补间进行中再变，从当前看到的位置接着滑
export function useSlide<T extends HTMLElement>(x: number, y: number): RefObject<T | null> {
  const ref = useRef<T>(null);
  const prev = useRef<{ x: number; y: number } | null>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    const p = prev.current;
    prev.current = { x, y };
    if (!el || !p) return;
    const offsetX = Number(gsap.getProperty(el, "x")) || 0;
    const offsetY = Number(gsap.getProperty(el, "y")) || 0;
    slideFrom(el, p.x + offsetX - x, p.y + offsetY - y);
  }, [x, y]);
  return ref;
}

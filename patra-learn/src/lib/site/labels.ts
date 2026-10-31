// patra-learn/src/lib/site/labels.ts
// 内容模型里的英文常量 → 页面上的中文名
import type { Difficulty, EntryType } from "@/lib/content/types";

export const TYPE_LABELS: Record<EntryType, string> = {
  concept: "概念",
  troubleshooting: "排查",
  decision: "决策",
  pitfall: "坑",
};

export const DIFFICULTY_LABELS: Record<Difficulty, string> = {
  intro: "入门",
  basic: "基础",
  advanced: "进阶",
  practice: "实战",
};

/// 四档难度各写给谁（简报第 5 节、release spec 2.1）
export const DIFFICULTY_AUDIENCE: Record<Difficulty, string> = {
  intro: "没写过后端",
  basic: "会写简单接口",
  advanced: "做过项目，开始碰到真实问题",
  practice: "Patra 里真实发生过的事",
};

/// 掌握度（与教学组件 Mastery 的三档一致；这里单独写一份，服务端组件不能从 "use client" 模块取值）
export const LEVEL_LABELS = { 1: "刚接触", 2: "理解中", 3: "吃透了" } as const;

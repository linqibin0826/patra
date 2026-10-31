// patra-learn/src/teaching/registry.ts
// 组件登记表：条目正文能用的组件只来自这里（决策 D）。只放校验要知道的东西，不 import React，
// 所以内容校验能在 node 环境直接跑；同名实现在 src/mdx-components.tsx。
// 新增组件：这里加一条 + mdx-components.tsx 加实现，校验自动生效。
import { z } from "zod";

/// 教学角色：校验「开头诊断题、结尾复述 / 迁移题」靠它识别
export type ComponentRole = "diagnostic" | "recap" | "transfer";

export interface ComponentSpec {
  /// 作者能写的参数；必须是 z.strictObject，未登记的参数直接报错
  readonly props: z.ZodObject;
  /// 组件标签之间能不能、要不要写内容
  readonly children: "none" | "optional" | "required";
  readonly role?: ComponentRole;
}

export const componentRegistry = {
  /// 代码引用：at 写成 `路径@40位SHA` 或 `路径@pending`，格式由内容校验的 code-refs 规则检查
  CodeRef: {
    props: z.strictObject({ at: z.string().min(1), lines: z.string().optional() }),
    children: "none",
  },
  /// 诊断题：正文第一块；页面注入本篇前置，答不上来时引导先读
  Diagnose: {
    props: z.strictObject({ question: z.string().min(1) }),
    children: "optional",
    role: "diagnostic",
  },
  /// 复述题：正文最后一块（与迁移题二选一）
  Recap: {
    props: z.strictObject({ prompt: z.string().min(1) }),
    children: "optional",
    role: "recap",
  },
  /// 迁移题：正文最后一块（与复述题二选一）
  Transfer: {
    props: z.strictObject({ prompt: z.string().min(1) }),
    children: "optional",
    role: "transfer",
  },
} as const satisfies Record<string, ComponentSpec>;

export type ComponentName = keyof typeof componentRegistry;

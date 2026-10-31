// patra-learn/src/mdx-components.tsx
// 组件名 → React 实现。条目页编译 MDX 时整份传给 components（名字一致由 registry.test.ts 守）
import { CodeRef } from "@/components/code-ref";
import { Diagnose, Recap, Transfer } from "@/teaching/placeholders";

export const mdxComponents = { CodeRef, Diagnose, Recap, Transfer };

// patra-learn/src/mdx-components.tsx
// 组件名 → React 实现。条目页编译 MDX 时整份传给 components（名字一致由 registry.test.ts 守）
import { CodeRef } from "@/components/code-ref";
import {
  Callout,
  Compare,
  DebugTrail,
  DecisionOptions,
  Diagnose,
  Params,
  Quiz,
  Recap,
  Sequence,
  StateMachine,
  Stepper,
  Structure,
  Transfer,
} from "@/teaching/minimal";

export const mdxComponents = {
  CodeRef,
  Diagnose,
  Recap,
  Transfer,
  Quiz,
  Structure,
  Sequence,
  StateMachine,
  Stepper,
  Params,
  Compare,
  Callout,
  DebugTrail,
  DecisionOptions,
};

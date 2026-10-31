// patra-learn/src/mdx-components.tsx
// 组件名 → React 实现。条目页编译 MDX 时整份传给 components（名字一致由 registry.test.ts 守）
import { CodeRef } from "@/components/code-ref";
import { Callout } from "@/teaching/callout";
import { Compare } from "@/teaching/compare";
import { DebugTrail } from "@/teaching/debug-trail";
import { DecisionOptions } from "@/teaching/decision-options";
import { Diagnose } from "@/teaching/diagnose";
import { Params } from "@/teaching/params";
import { Quiz } from "@/teaching/quiz";
import { Recap, Transfer } from "@/teaching/self-check";
import { Sequence } from "@/teaching/sequence";
import { StateMachine } from "@/teaching/state-machine";
import { Stepper } from "@/teaching/stepper";
import { Structure } from "@/teaching/structure";

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

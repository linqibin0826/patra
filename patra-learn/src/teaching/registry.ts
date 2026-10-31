// patra-learn/src/teaching/registry.ts
// 组件登记表：条目正文能用的组件只来自这里（决策 D）。只放校验要知道的东西，不 import React，
// 所以内容校验能在 node 环境直接跑；同名实现在 src/mdx-components.tsx。
// 新增组件：这里加一条 + mdx-components.tsx 加实现，校验自动生效。写法说明见同目录 README.md。
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

// ---------- 共用的参数形状 ----------

const text = z.string().trim().min(1, "不能为空");

/// 图里的节点、状态、参与者、控件的 id：组件内唯一，供连线与步骤引用
const id = z.string().regex(/^[a-z][a-z0-9-]*$/, "id 只能用小写字母、数字和横线，并以字母开头");

/// 选择题的正确项，写选项字母
const letter = z.enum(["A", "B", "C", "D"]);

const LETTERS = ["A", "B", "C", "D"] as const;

/// 第几列 / 第几行，从 1 数
const gridIndex = z.number().int().min(1).max(6);

/// 结果徽章的语气：ok 成功（绿）、bad 失败（红）、wait 悬而未决（琥珀）
const tone = z.enum(["ok", "bad", "wait"]);

/// 一句判定：ok 绿底、bad 红底；head 是加粗的开头
const verdict = z.strictObject({ tone: z.enum(["ok", "bad"]), head: text.optional(), text });

/// 结果表的一行：value 写一个结果，或按先后写一串徽章
const result = z.strictObject({
  label: text,
  value: z.union([text, z.array(z.strictObject({ text, tone })).min(1).max(4)]),
  tone: tone.optional(),
});

/// 一道单选题（诊断题、考考自己、你猜会怎样、参数组件的预测共用）
const choiceFields = {
  question: text,
  options: z.array(text).min(2, "至少 2 个选项").max(4, "最多 4 个选项"),
  answer: letter,
};

/// 正确项字母必须落在选项范围内
function answerInRange(
  v: { options: string[]; answer: (typeof LETTERS)[number] },
  ctx: z.RefinementCtx,
  path: (string | number)[] = [],
) {
  if (LETTERS.indexOf(v.answer) >= v.options.length) {
    ctx.addIssue({
      code: "custom",
      path: [...path, "answer"],
      message: `只有 ${v.options.length} 个选项，answer 写到了 ${v.answer}`,
    });
  }
}

const choice = z
  .strictObject({ ...choiceFields, explain: text })
  .superRefine((v, ctx) => answerInRange(v, ctx));

/// 检查 refs 里的每个 id 都在 known 里
function refsExist(
  known: Set<string>,
  refs: { value: string; path: (string | number)[] }[],
  what: string,
  ctx: z.RefinementCtx,
) {
  for (const ref of refs) {
    if (!known.has(ref.value)) {
      ctx.addIssue({
        code: "custom",
        path: ref.path,
        message: `没有 id 为「${ref.value}」的${what}`,
      });
    }
  }
}

/// id 在列表里不能重复
function uniqueIds(items: { id: string }[], key: string, ctx: z.RefinementCtx): Set<string> {
  const seen = new Set<string>();
  items.forEach((item, i) => {
    if (seen.has(item.id)) {
      ctx.addIssue({ code: "custom", path: [key, i, "id"], message: `id「${item.id}」重复了` });
    }
    seen.add(item.id);
  });
  return seen;
}

/// 复述题 / 迁移题的参考要点
const points = z
  .array(z.strictObject({ label: text.optional(), text }))
  .min(2, "参考要点至少 2 条")
  .max(5, "参考要点最多 5 条");

const selfCheck = z.strictObject({ prompt: text, points, placeholder: text.optional() });

// ---------- 图示 ----------

const structureProps = z
  .strictObject({
    title: text,
    intro: text.optional(),
    nodes: z
      .array(
        z.strictObject({
          id,
          label: text,
          sub: text.optional(),
          code: z.boolean().optional(),
          col: gridIndex,
          row: gridIndex.optional(),
          desc: text,
        }),
      )
      .min(2)
      .max(12),
    edges: z
      .array(
        z.strictObject({
          from: id,
          to: id,
          label: text.optional(),
          desc: text.optional(),
          dashed: z.boolean().optional(),
        }),
      )
      .max(16)
      .default([]),
    traces: z
      .array(
        z.strictObject({
          button: text,
          packet: text,
          hops: z
            .array(z.strictObject({ at: id, caption: text, drop: z.boolean().optional() }))
            .min(2)
            .max(8),
        }),
      )
      .max(3)
      .optional(),
  })
  .superRefine((v, ctx) => {
    const ids = uniqueIds(v.nodes, "nodes", ctx);
    const cells = new Set<string>();
    v.nodes.forEach((n, i) => {
      const cell = `${n.col}:${n.row ?? 1}`;
      if (cells.has(cell)) {
        ctx.addIssue({
          code: "custom",
          path: ["nodes", i, "row"],
          message: `第 ${n.col} 列第 ${n.row ?? 1} 行已经有节点了`,
        });
      }
      cells.add(cell);
    });
    refsExist(
      ids,
      v.edges.flatMap((e, i) => [
        { value: e.from, path: ["edges", i, "from"] },
        { value: e.to, path: ["edges", i, "to"] },
      ]),
      "节点",
      ctx,
    );
    refsExist(
      ids,
      (v.traces ?? []).flatMap((t, i) =>
        t.hops.map((h, j) => ({ value: h.at, path: ["traces", i, "hops", j, "at"] })),
      ),
      "节点",
      ctx,
    );
  });

const sequenceProps = z
  .strictObject({
    title: text,
    actors: z
      .array(z.strictObject({ id, label: text, sub: text.optional() }))
      .min(2)
      .max(5),
    messages: z
      .array(
        z.strictObject({
          from: id,
          to: id,
          label: text,
          caption: text,
          time: text.optional(),
          bad: z.boolean().optional(),
          dashed: z.boolean().optional(),
        }),
      )
      .min(1)
      .max(12),
    summary: z.strictObject({ time: text.optional(), caption: text }).optional(),
  })
  .superRefine((v, ctx) => {
    const ids = uniqueIds(v.actors, "actors", ctx);
    v.messages.forEach((m, i) => {
      if (m.from === m.to) {
        ctx.addIssue({
          code: "custom",
          path: ["messages", i, "to"],
          message: "from 和 to 不能相同",
        });
      }
    });
    refsExist(
      ids,
      v.messages.flatMap((m, i) => [
        { value: m.from, path: ["messages", i, "from"] },
        { value: m.to, path: ["messages", i, "to"] },
      ]),
      "参与者",
      ctx,
    );
  });

const stateMachineProps = z
  .strictObject({
    title: text,
    subject: text.optional(),
    states: z
      .array(
        z.strictObject({
          id,
          label: text,
          sub: text.optional(),
          desc: text,
          col: gridIndex,
          row: gridIndex.optional(),
          dashed: z.boolean().optional(),
        }),
      )
      .min(2)
      .max(8),
    start: id,
    events: z
      .array(z.strictObject({ id, label: text }))
      .min(1)
      .max(8),
    transitions: z
      .array(z.strictObject({ from: id, event: id, to: id, label: text.optional() }))
      .min(1)
      .max(16),
  })
  .superRefine((v, ctx) => {
    const states = uniqueIds(v.states, "states", ctx);
    const events = uniqueIds(v.events, "events", ctx);
    refsExist(states, [{ value: v.start, path: ["start"] }], "状态", ctx);
    refsExist(
      states,
      v.transitions.flatMap((t, i) => [
        { value: t.from, path: ["transitions", i, "from"] },
        { value: t.to, path: ["transitions", i, "to"] },
      ]),
      "状态",
      ctx,
    );
    refsExist(
      events,
      v.transitions.map((t, i) => ({ value: t.event, path: ["transitions", i, "event"] })),
      "事件",
      ctx,
    );
    const pairs = new Set<string>();
    v.transitions.forEach((t, i) => {
      const key = `${t.from}:${t.event}`;
      if (pairs.has(key)) {
        ctx.addIssue({
          code: "custom",
          path: ["transitions", i],
          message: `「${t.from}」上的事件「${t.event}」写了两次，同一状态下一个事件只能去一个地方`,
        });
      }
      pairs.add(key);
    });
  });

/// 逐步动画里一条连线的引用，写成 `起点>终点`
const linkRef = z.string().regex(/^[a-z][a-z0-9-]*>[a-z][a-z0-9-]*$/, "写成「起点 id>终点 id」");

const stepperProps = z
  .strictObject({
    title: text,
    nodes: z
      .array(z.strictObject({ id, label: text, sub: text.optional() }))
      .min(2)
      .max(5),
    links: z
      .array(z.strictObject({ from: id, to: id, label: text.optional() }))
      .max(8)
      .default([]),
    steps: z
      .array(
        z.strictObject({
          caption: text,
          focus: z.array(id).optional(),
          links: z.array(linkRef).optional(),
          tokens: z
            .array(
              z.strictObject({
                id,
                label: text,
                at: id,
                state: z.enum(["faded", "dropped"]).optional(),
              }),
            )
            .max(3)
            .optional(),
          notes: z
            .array(z.strictObject({ at: id, text, tone: tone.optional() }))
            .max(5)
            .optional(),
        }),
      )
      .min(2)
      .max(10),
    guesses: z
      .array(
        z
          .strictObject({ before: z.number().int().min(2), ...choiceFields, explain: text })
          .superRefine((v, ctx) => answerInRange(v, ctx)),
      )
      .max(3)
      .optional(),
  })
  .superRefine((v, ctx) => {
    const ids = uniqueIds(v.nodes, "nodes", ctx);
    refsExist(
      ids,
      v.links.flatMap((l, i) => [
        { value: l.from, path: ["links", i, "from"] },
        { value: l.to, path: ["links", i, "to"] },
      ]),
      "节点",
      ctx,
    );
    const links = new Set(v.links.map((l) => `${l.from}>${l.to}`));
    v.steps.forEach((s, i) => {
      refsExist(
        ids,
        [
          ...(s.focus ?? []).map((f, j) => ({ value: f, path: ["steps", i, "focus", j] })),
          ...(s.tokens ?? []).map((t, j) => ({
            value: t.at,
            path: ["steps", i, "tokens", j, "at"],
          })),
          ...(s.notes ?? []).map((n, j) => ({ value: n.at, path: ["steps", i, "notes", j, "at"] })),
        ],
        "节点",
        ctx,
      );
      refsExist(
        links,
        (s.links ?? []).map((l, j) => ({ value: l, path: ["steps", i, "links", j] })),
        "连线（先在 links 里声明）",
        ctx,
      );
    });
    const befores = new Set<number>();
    (v.guesses ?? []).forEach((g, i) => {
      if (g.before > v.steps.length) {
        ctx.addIssue({
          code: "custom",
          path: ["guesses", i, "before"],
          message: `只有 ${v.steps.length} 步，before 写到了 ${g.before}`,
        });
      }
      if (befores.has(g.before)) {
        ctx.addIssue({
          code: "custom",
          path: ["guesses", i, "before"],
          message: `第 ${g.before} 步前已经有一道「你猜」了`,
        });
      }
      befores.add(g.before);
    });
  });

// ---------- 交互 ----------

const control = z.discriminatedUnion("type", [
  z.strictObject({ type: z.literal("switch"), id, label: text, default: z.boolean() }),
  z.strictObject({
    type: z.literal("slider"),
    id,
    label: text,
    min: z.number(),
    max: z.number(),
    step: z.number().positive(),
    default: z.number(),
    unit: text.optional(),
  }),
]);

const paramsProps = z
  .strictObject({
    title: text,
    guess: choice.optional(),
    controls: z.array(control).min(1).max(5),
    cases: z
      .array(
        z.strictObject({
          when: z.record(id, z.union([z.boolean(), z.tuple([z.number(), z.number()])])).optional(),
          results: z.array(result).max(5).default([]),
          verdict,
        }),
      )
      .min(1)
      .max(12),
  })
  .superRefine((v, ctx) => {
    const ids = uniqueIds(v.controls, "controls", ctx);
    const byId = new Map(v.controls.map((c) => [c.id, c]));
    v.controls.forEach((c, i) => {
      if (c.type === "slider" && !(c.min < c.max && c.default >= c.min && c.default <= c.max)) {
        ctx.addIssue({
          code: "custom",
          path: ["controls", i],
          message: "滑块要满足 min < max，且 default 在 min 与 max 之间",
        });
      }
    });
    v.cases.forEach((cs, i) => {
      for (const [key, cond] of Object.entries(cs.when ?? {})) {
        const c = byId.get(key);
        if (!ids.has(key) || !c) {
          ctx.addIssue({
            code: "custom",
            path: ["cases", i, "when", key],
            message: `没有 id 为「${key}」的控件`,
          });
        } else if ((c.type === "switch") !== (typeof cond === "boolean")) {
          ctx.addIssue({
            code: "custom",
            path: ["cases", i, "when", key],
            message: c.type === "switch" ? "开关写 true 或 false" : "滑块写 [下限, 上限]",
          });
        }
      }
    });
    const last = v.cases.at(-1);
    if (last?.when && Object.keys(last.when).length > 0) {
      ctx.addIssue({
        code: "custom",
        path: ["cases", v.cases.length - 1, "when"],
        message: "最后一个 case 不写 when，作为其余组合的兜底",
      });
    }
  });

const compareProps = z
  .strictObject({
    title: text,
    failureLabel: text.optional(),
    variants: z
      .array(
        z.strictObject({
          label: text,
          steps: z.array(text).min(2).max(8),
          results: z.array(result).max(5).default([]),
          verdict,
          failure: z
            .strictObject({
              after: z.number().int().min(1),
              results: z.array(result).max(5).default([]),
              verdict,
            })
            .optional(),
        }),
      )
      .min(2)
      .max(4),
  })
  .superRefine((v, ctx) => {
    v.variants.forEach((variant, i) => {
      if (variant.failure && variant.failure.after >= variant.steps.length) {
        ctx.addIssue({
          code: "custom",
          path: ["variants", i, "failure", "after"],
          message: `after 要小于步骤数 ${variant.steps.length}（崩在两步之间）`,
        });
      }
      if (v.failureLabel && !variant.failure) {
        ctx.addIssue({
          code: "custom",
          path: ["variants", i, "failure"],
          message: "写了 failureLabel，每种写法都要写 failure",
        });
      }
      if (!v.failureLabel && variant.failure) {
        ctx.addIssue({
          code: "custom",
          path: ["failureLabel"],
          message: "写了 failure，要用 failureLabel 说明开关模拟的是什么",
        });
      }
    });
  });

// ---------- 排版块 ----------

const debugTrailProps = z
  .strictObject({
    steps: z
      .array(
        z.strictObject({
          hypothesis: text,
          result: text,
          verdict: z.enum(["ruled-out", "closer", "found"]),
        }),
      )
      .min(2)
      .max(8),
  })
  .superRefine((v, ctx) => {
    if (v.steps.at(-1)?.verdict !== "found") {
      ctx.addIssue({
        code: "custom",
        path: ["steps", v.steps.length - 1, "verdict"],
        message: "最后一步要是 found（找到根因）",
      });
    }
  });

const decisionOptionsProps = z
  .strictObject({
    options: z
      .array(
        z.strictObject({
          name: text,
          summary: text,
          pros: text,
          cons: text,
          chosen: z.boolean().optional(),
        }),
      )
      .min(2)
      .max(4),
  })
  .superRefine((v, ctx) => {
    const n = v.options.filter((o) => o.chosen).length;
    if (n !== 1) {
      ctx.addIssue({
        code: "custom",
        path: ["options"],
        message: `要有且只有一个 chosen，现在是 ${n} 个`,
      });
    }
  });

export const componentRegistry = {
  /// 代码引用：at 写成 `路径@40位SHA` 或 `路径@pending`，格式由内容校验的 code-refs 规则检查
  CodeRef: {
    props: z.strictObject({ at: z.string().min(1), lines: z.string().optional() }),
    children: "none",
  },
  /// 开头诊断题：正文第一块；单选，答错或答不上来时页面推荐本篇前置
  Diagnose: {
    props: z
      .strictObject({
        ...choiceFields,
        ifRight: text,
        ifWrong: text,
        suggest: z.string().min(1).optional(),
      })
      .superRefine((v, ctx) => answerInRange(v, ctx)),
    children: "none",
    role: "diagnostic",
  },
  /// 复述题：正文最后一块（与迁移题二选一）；读者先写，再对照参考要点自评
  Recap: { props: selfCheck, children: "optional", role: "recap" },
  /// 迁移题：正文最后一块（与复述题二选一），能出迁移题就出迁移题
  Transfer: { props: selfCheck, children: "optional", role: "transfer" },
  /// 考考自己：正文中间的单选小测，1～5 题
  Quiz: {
    props: z.strictObject({ title: text.optional(), questions: z.array(choice).min(1).max(5) }),
    children: "none",
  },
  /// 结构图：几个部件之间的一种关系；点方块看职责，可选「发一条」让消息沿路径走
  Structure: { props: structureProps, children: "none" },
  /// 时序图：几个参与者之间按时间先后的消息，一条一条往下走
  Sequence: { props: sequenceProps, children: "none" },
  /// 状态机：点事件推进状态；当前状态下不会发生的事件会被拒绝并说明
  StateMachine: { props: stateMachineProps, children: "none" },
  /// 逐步动画：读者点「下一步」才往前走，关键步骤前可插「你猜会怎样」
  Stepper: { props: stepperProps, children: "none" },
  /// 参数滑块与开关：参数变了结果怎么变；可先让读者预测
  Params: { props: paramsProps, children: "none" },
  /// 对比切换：几种写法并排比，可选开关模拟「崩在中间」
  Compare: { props: compareProps, children: "none" },
  /// 强调块：一句话 / 打个比方 / 顺带想通的 / 小心 / 代价，内容写 Markdown
  Callout: {
    props: z.strictObject({
      kind: z.enum(["lead", "analogy", "insight", "warn", "cost"]),
      label: text.optional(),
    }),
    children: "required",
  },
  /// 排查路径：一步一个假设，读者点「测一下，看结果」才展开结果
  DebugTrail: { props: debugTrailProps, children: "none" },
  /// 决策选项：2～4 个选项，点开看好处与代价，选中的那个标「选了这个」
  DecisionOptions: { props: decisionOptionsProps, children: "none" },
} as const satisfies Record<string, ComponentSpec>;

export type ComponentName = keyof typeof componentRegistry;

/// 各组件的作者参数类型（实现的 props 以它为准）；MDX 运行时不经 zod 解析，所以取输入类型，默认值由实现补
export type PropsOf<N extends ComponentName> = z.input<(typeof componentRegistry)[N]["props"]>;

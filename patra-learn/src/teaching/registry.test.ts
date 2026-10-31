import { describe, expect, it } from "vitest";
import { mdxComponents } from "@/mdx-components";
import { componentRegistry } from "./registry";

describe("组件登记表与实现", () => {
  it("登记表里的每个组件都有实现，实现也都登记过", () => {
    expect(Object.keys(mdxComponents).sort()).toEqual(Object.keys(componentRegistry).sort());
  });
});

/// 校验参数，返回出问题的路径与消息
function problems(name: keyof typeof componentRegistry, props: unknown) {
  const result = componentRegistry[name].props.safeParse(props);
  return result.success ? [] : result.error.issues.map((i) => `${i.path.join(".")}：${i.message}`);
}

const choice = { question: "问", options: ["甲", "乙"], answer: "A" };

describe("选择题", () => {
  it("answer 超出选项范围时报错", () => {
    expect(problems("Diagnose", { ...choice, answer: "C", ifRight: "对", ifWrong: "错" })).toEqual([
      "answer：只有 2 个选项，answer 写到了 C",
    ]);
    expect(problems("Quiz", { questions: [{ ...choice, answer: "D", explain: "因为" }] })).toEqual([
      "questions.0.answer：只有 2 个选项，answer 写到了 D",
    ]);
  });

  it("诊断题要写答对、答错两种说明", () => {
    expect(problems("Diagnose", choice).length).toBe(2);
  });
});

describe("复述题与迁移题", () => {
  it("参考要点至少 2 条", () => {
    expect(problems("Transfer", { prompt: "换个场景", points: [{ text: "一条" }] })).toEqual([
      "points：参考要点至少 2 条",
    ]);
  });
});

describe("结构图", () => {
  const nodes = [
    { id: "a", label: "A", col: 1, desc: "甲" },
    { id: "b", label: "B", col: 2, desc: "乙" },
  ];

  it("连线与路径只能引用已声明的节点", () => {
    expect(
      problems("Structure", {
        title: "图",
        nodes,
        edges: [{ from: "a", to: "c" }],
        traces: [
          {
            button: "发",
            packet: "m",
            hops: [
              { at: "a", caption: "1" },
              { at: "x", caption: "2" },
            ],
          },
        ],
      }),
    ).toEqual(["edges.0.to：没有 id 为「c」的节点", "traces.0.hops.1.at：没有 id 为「x」的节点"]);
  });

  it("两个节点不能占同一格", () => {
    expect(
      problems("Structure", { title: "图", nodes: [nodes[0], { ...nodes[1], col: 1 }] }),
    ).toEqual(["nodes.1.row：第 1 列第 1 行已经有节点了"]);
  });

  it("id 只能是小写字母、数字、横线", () => {
    expect(
      problems("Structure", { title: "图", nodes: [nodes[0], { ...nodes[1], id: "B" }] }),
    ).toEqual(["nodes.1.id：id 只能用小写字母、数字和横线，并以字母开头"]);
  });
});

describe("时序图", () => {
  it("消息两端必须是参与者且不能相同", () => {
    expect(
      problems("Sequence", {
        title: "图",
        actors: [
          { id: "a", label: "A" },
          { id: "b", label: "B" },
        ],
        messages: [
          { from: "a", to: "a", label: "x", caption: "y" },
          { from: "a", to: "z", label: "x", caption: "y" },
        ],
      }),
    ).toEqual(["messages.0.to：from 和 to 不能相同", "messages.1.to：没有 id 为「z」的参与者"]);
  });
});

describe("状态机", () => {
  const base = {
    title: "图",
    states: [
      { id: "s0", label: "开始", desc: "x", col: 1 },
      { id: "s1", label: "结束", desc: "y", col: 2 },
    ],
    start: "s0",
    events: [{ id: "go", label: "走" }],
  };

  it("同一状态下同一事件只能去一个地方", () => {
    expect(
      problems("StateMachine", {
        ...base,
        transitions: [
          { from: "s0", event: "go", to: "s1" },
          { from: "s0", event: "go", to: "s0" },
        ],
      }),
    ).toEqual(["transitions.1：「s0」上的事件「go」写了两次，同一状态下一个事件只能去一个地方"]);
  });

  it("起始状态与事件必须存在", () => {
    expect(
      problems("StateMachine", {
        ...base,
        start: "nope",
        transitions: [{ from: "s0", event: "stop", to: "s1" }],
      }),
    ).toEqual(["start：没有 id 为「nope」的状态", "transitions.0.event：没有 id 为「stop」的事件"]);
  });
});

describe("逐步动画", () => {
  const base = {
    title: "图",
    nodes: [
      { id: "a", label: "A" },
      { id: "b", label: "B" },
    ],
    links: [{ from: "a", to: "b" }],
  };

  it("步骤里的连线要先声明，你猜不能超出步数", () => {
    expect(
      problems("Stepper", {
        ...base,
        steps: [{ caption: "1", links: ["b>a"] }, { caption: "2" }],
        guesses: [{ before: 3, ...choice, explain: "因为" }],
      }),
    ).toEqual([
      "steps.0.links.0：没有 id 为「b>a」的连线（先在 links 里声明）",
      "guesses.0.before：只有 2 步，before 写到了 3",
    ]);
  });
});

describe("参数滑块与开关", () => {
  const controls = [
    { type: "switch", id: "bound", label: "有队列", default: true },
    { type: "slider", id: "interval", label: "间隔", min: 10, max: 300, step: 10, default: 60 },
  ];
  const fallback = { verdict: { tone: "ok", text: "兜底" } };

  it("最后一个 case 必须是兜底", () => {
    expect(
      problems("Params", {
        title: "试试",
        controls,
        cases: [{ when: { bound: true }, verdict: { tone: "ok", text: "x" } }],
      }),
    ).toEqual(["cases.0.when：最后一个 case 不写 when，作为其余组合的兜底"]);
  });

  it("when 的值要和控件类型对上", () => {
    expect(
      problems("Params", {
        title: "试试",
        controls,
        cases: [{ when: { bound: [1, 2], interval: true, nope: true }, ...fallback }, fallback],
      }),
    ).toEqual([
      "cases.0.when.bound：开关写 true 或 false",
      "cases.0.when.interval：滑块写 [下限, 上限]",
      "cases.0.when.nope：没有 id 为「nope」的控件",
    ]);
  });

  it("滑块默认值要在范围内", () => {
    expect(
      problems("Params", {
        title: "试试",
        controls: [{ ...controls[1], default: 400 }],
        cases: [fallback],
      }),
    ).toEqual(["controls.0：滑块要满足 min < max，且 default 在 min 与 max 之间"]);
  });
});

describe("对比切换", () => {
  const variant = (failure?: object) => ({
    label: "写法",
    steps: ["一", "二", "三"],
    verdict: { tone: "ok", text: "好" },
    ...(failure ? { failure } : {}),
  });
  const failure = { after: 2, verdict: { tone: "bad", text: "坏" } };

  it("写了开关，每种写法都要写崩溃时的结果", () => {
    expect(
      problems("Compare", {
        title: "比",
        failureLabel: "崩",
        variants: [variant(failure), variant()],
      }),
    ).toEqual(["variants.1.failure：写了 failureLabel，每种写法都要写 failure"]);
  });

  it("崩溃位置要在两步之间", () => {
    expect(
      problems("Compare", {
        title: "比",
        failureLabel: "崩",
        variants: [variant({ ...failure, after: 3 }), variant(failure)],
      }),
    ).toEqual(["variants.0.failure.after：after 要小于步骤数 3（崩在两步之间）"]);
  });
});

describe("排查路径与决策选项", () => {
  it("排查路径最后一步是找到根因", () => {
    expect(
      problems("DebugTrail", {
        steps: [
          { hypothesis: "a", result: "b", verdict: "found" },
          { hypothesis: "c", result: "d", verdict: "closer" },
        ],
      }),
    ).toEqual(["steps.1.verdict：最后一步要是 found（找到根因）"]);
  });

  it("决策选项有且只有一个选中", () => {
    const option = { name: "甲", summary: "s", pros: "p", cons: "c" };
    expect(problems("DecisionOptions", { options: [option, option] })).toEqual([
      "options：要有且只有一个 chosen，现在是 0 个",
    ]);
  });
});

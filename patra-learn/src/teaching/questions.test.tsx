// @vitest-environment jsdom
// 诊断题、考考自己、复述 / 迁移题：按画板 C-Diagnose / C-Quiz / C-Transfer 的状态逐一走一遍
import { describe, expect, it } from "vitest";
import { Diagnose } from "./diagnose";
import { Quiz } from "./quiz";
import { Recap, Transfer } from "./self-check";
import { fireEvent, render, screen } from "./testing/render";

const diagnose = {
  question: "崩了会怎样？",
  options: ["都在", "行回滚了，消息发出去了", "都没有"],
  answer: "B" as const,
  ifRight: "往下读会讲怎么办。",
  ifWrong: "答案是 B，这题考事务。",
};
const prerequisites = [
  { slug: "db-transactions", title: "事务" },
  { slug: "msg-dual-write-problem", title: "双写问题", meta: "进阶 · 7 分钟" },
];

const option = (text: string) => screen.getByRole("button", { name: new RegExp(text) });

describe("Diagnose", () => {
  it("未答：显示题干、选项和「我答不上来」", () => {
    render(<Diagnose {...diagnose} />);
    expect(screen.getByText("崩了会怎样？")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: /^[ABC]/ })).toHaveLength(3);
    expect(screen.getByRole("button", { name: "我答不上来" })).toBeTruthy();
  });

  it("答对：给肯定与说明，选项锁住，可以收起", () => {
    render(<Diagnose {...diagnose} prerequisites={prerequisites} />);
    fireEvent.click(option("行回滚了"));
    expect(screen.getByText("答对了。")).toBeTruthy();
    expect(screen.getByText("往下读会讲怎么办。")).toBeTruthy();
    expect(option("都在").hasAttribute("disabled")).toBe(true);
    expect(screen.queryByText("建议先读这篇前置")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "收起，开始读" }));
    expect(screen.getByText("开头这题答对了，已经有前置知识。")).toBeTruthy();
  });

  it("答错：给解释，推荐 suggest 指定的前置，带两个出口", () => {
    render(
      <Diagnose {...diagnose} suggest="msg-dual-write-problem" prerequisites={prerequisites} />,
    );
    fireEvent.click(option("都在"));
    expect(screen.getByText("不太对。")).toBeTruthy();
    expect(screen.getByText("答案是 B，这题考事务。")).toBeTruthy();
    expect(screen.getByRole("link", { name: "双写问题" }).getAttribute("href")).toBe(
      "/entries/msg-dual-write-problem",
    );
    expect(screen.getByText("进阶 · 7 分钟")).toBeTruthy();
    expect(screen.getByRole("link", { name: "先去读这篇" })).toBeTruthy();
  });

  it("答不上来：同样给解释，不写 suggest 时推荐第一篇前置", () => {
    render(<Diagnose {...diagnose} prerequisites={prerequisites} />);
    fireEvent.click(screen.getByRole("button", { name: "我答不上来" }));
    expect(screen.getByText("没关系。")).toBeTruthy();
    expect(screen.getByRole("link", { name: "事务" })).toBeTruthy();
  });

  it("答错后直接往下读：收成一行，能重新答", () => {
    render(<Diagnose {...diagnose} prerequisites={prerequisites} />);
    fireEvent.click(option("都没有"));
    fireEvent.click(screen.getByRole("button", { name: "我直接往下读" }));
    expect(screen.getByText("开头这题没答上，建议先读《事务》。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "重新答" }));
    expect(screen.getByRole("button", { name: "我答不上来" })).toBeTruthy();
  });

  it("没有前置时答错只给解释", () => {
    render(<Diagnose {...diagnose} />);
    fireEvent.click(option("都在"));
    expect(screen.queryByText("建议先读这篇前置")).toBeNull();
    expect(screen.getByRole("button", { name: "我直接往下读" })).toBeTruthy();
  });
});

describe("Quiz", () => {
  const questions = [
    { question: "第一题", options: ["错", "对"], answer: "B" as const, explain: "解释一" },
    { question: "第二题", options: ["对", "错"], answer: "A" as const, explain: "解释二" },
  ];

  it("先选再对答案；答错可以再试；做完给第一次就答对的题数", () => {
    render(<Quiz questions={questions} />);
    const check = () => screen.getByRole("button", { name: "对一下答案" });
    expect(check().hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: /^[AB]对/ }));
    fireEvent.click(check());
    expect(screen.getByText("对。")).toBeTruthy();
    expect(screen.getByText("解释一")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "下一题" }));
    expect(screen.getByText("第二题")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /^[AB]错/ }));
    fireEvent.click(check());
    expect(screen.getByText("不对。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "再试一次" }));
    fireEvent.click(screen.getByRole("button", { name: /^[AB]对/ }));
    fireEvent.click(check());
    fireEvent.click(screen.getByRole("button", { name: "看结果" }));
    expect(screen.getByTestId("quiz-score").textContent).toBe("1");
    expect(screen.getByText("/ 2 题第一次就答对")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "从头再做" }));
    expect(screen.getByText("第一题")).toBeTruthy();
  });

  it("默认标题是「考考自己」，进度显示第几题", () => {
    render(<Quiz questions={questions} />);
    expect(screen.getByText("考考自己")).toBeTruthy();
    expect(screen.getByText("第 1 题 / 2")).toBeTruthy();
  });
});

describe("Recap / Transfer", () => {
  const points = [
    { label: "同一个事务", text: "一起提交。" },
    { label: "提交后再发", text: "发成功再删。" },
    { text: "仓库要去重。" },
  ];

  it("还没写时不能对照；想不出来可以直接看参考", () => {
    render(<Transfer prompt="换个场景" points={points} />);
    expect(screen.getByText("换个场景：迁移题")).toBeTruthy();
    expect(screen.getByRole("button", { name: "写好了，对照参考" }).hasAttribute("disabled")).toBe(
      true,
    );
    fireEvent.click(screen.getByRole("button", { name: "想不出来，直接看" }));
    expect(screen.getByText("一起提交。")).toBeTruthy();
    expect(screen.getByText("还没打勾。对照上面几条，看看自己写到了哪些。")).toBeTruthy();
  });

  it("写了之后对照参考，逐条打勾自评", () => {
    render(<Transfer prompt="换个场景" points={points} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "我会先写一行登记" } });
    fireEvent.click(screen.getByRole("button", { name: "写好了，对照参考" }));
    fireEvent.click(screen.getByRole("button", { name: /一起提交/ }));
    expect(screen.getByTestId("self-check-score").textContent).toBe("1/3");
    expect(screen.getByText("没打勾的「提交后再发」，回到正文对应的小节再看一眼。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /发成功再删/ }));
    fireEvent.click(screen.getByRole("button", { name: /仓库要去重/ }));
    expect(screen.getByText("都想到了，这个思路你已经能搬到别的系统里用。")).toBeTruthy();
  });

  it("复述题的标题与补充参考答案", () => {
    render(
      <Recap prompt="说说发件箱" points={points}>
        <p>补充的参考答案</p>
      </Recap>,
    );
    expect(screen.getByText("用自己的话复述")).toBeTruthy();
    expect(screen.queryByText("补充的参考答案")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "想不出来，直接看" }));
    expect(screen.getByText("补充的参考答案")).toBeTruthy();
  });
});

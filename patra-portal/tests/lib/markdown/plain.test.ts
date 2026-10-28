import { describe, expect, it } from "vitest";
import { excerpt, markdownToPlain } from "@/lib/markdown/plain";

describe("markdownToPlain：列表摘录与页面描述用的纯文本", () => {
  it("去掉强调标记", () => {
    expect(markdownToPlain("**粗** _斜_ *也斜*")).toBe("粗 斜 也斜");
  });

  it("引用、列表、有序列表按段落之间空一格", () => {
    expect(markdownToPlain("> 引用\n\n- 列表\n\n1. 有序")).toBe("引用 列表 有序");
  });

  it("行内代码只留内容", () => {
    expect(markdownToPlain("用了 `lme4` 包")).toBe("用了 lme4 包");
  });

  it("链接留文字，图片留替代文字", () => {
    expect(markdownToPlain("见[原文](https://x.y)与![图](https://x.y/a.png)")).toBe("见原文与图");
  });

  it("标题去掉井号", () => {
    expect(markdownToPlain("# 标题\n正文")).toBe("标题 正文");
  });

  it("连续空行与换行折叠成一个空格，去首尾空白", () => {
    expect(markdownToPlain("  第一段\n\n\n第二段\n第三行  ")).toBe("第一段 第二段 第三行");
  });

  it("HTML 标签不进纯文本（final review M4，Review Focus 5）", () => {
    expect(markdownToPlain("前<b>粗</b>后")).toBe("前粗后");
    expect(markdownToPlain("<div>块</div>\n\n正文")).toBe("正文");
  });

  it("空正文是空串", () => {
    expect(markdownToPlain("   ")).toBe("");
  });
});

describe("excerpt：按字素截断", () => {
  it("超出加省略号", () => {
    expect(excerpt("一二三四五", 3)).toBe("一二三…");
  });

  it("不超出原样返回", () => {
    expect(excerpt("**一二三**", 3)).toBe("一二三");
  });

  it("emoji 组合不被截半", () => {
    expect(excerpt("👩‍🔬👩‍🔬👩‍🔬", 2)).toBe("👩‍🔬👩‍🔬…");
  });
});

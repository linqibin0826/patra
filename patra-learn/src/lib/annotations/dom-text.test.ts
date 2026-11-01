// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { rangeAt, spanOf, textOf } from "./dom-text";

let root: HTMLElement;

beforeEach(() => {
  document.body.innerHTML =
    '<p id="outside">外面</p><div id="root"><p>发布事件时，<b>为每个</b>监听器记一行。</p><p>处理成功才算完成。</p></div>';
  root = document.getElementById("root") as HTMLElement;
});

describe("正文文本与 DOM 选区互换", () => {
  it("textOf 按文档顺序拼出全部文字", () => {
    expect(textOf(root)).toBe("发布事件时，为每个监听器记一行。处理成功才算完成。");
  });

  it("spanOf 把跨元素的选区换成文本里的位置", () => {
    const range = document.createRange();
    const bold = root.querySelector("b")?.firstChild as Text;
    const second = root.querySelectorAll("p")[1]?.firstChild as Text;
    range.setStart(bold, 2);
    range.setEnd(second, 4);
    const span = spanOf(root, range);
    expect(span).toEqual({ start: 8, end: 20 });
    expect(textOf(root).slice(8, 20)).toBe("个监听器记一行。处理成功");
  });

  it("选区有一头在正文外面时返回 null", () => {
    const range = document.createRange();
    range.setStart(document.getElementById("outside")?.firstChild as Text, 0);
    range.setEnd(root.querySelector("b")?.firstChild as Text, 1);
    expect(spanOf(root, range)).toBeNull();
  });

  it("rangeAt 把文本位置换回选区，内容一致", () => {
    expect(rangeAt(root, 8, 20)?.toString()).toBe("个监听器记一行。处理成功");
    expect(rangeAt(root, 0, 2)?.toString()).toBe("发布");
  });

  it("位置超出文本长度时返回 null", () => {
    expect(rangeAt(root, 10, 999)).toBeNull();
  });
});

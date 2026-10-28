import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MarkdownBody } from "@/components/discussion/MarkdownBody";
import type { Mention } from "@/lib/interaction/discussion-schema";

const ZHOU: Mention = { userId: "42", username: "zhou-min", displayName: "周敏", deleted: false };

function renderBody(source: string, mentions: readonly Mention[] = []) {
  return render(<MarkdownBody source={source} mentions={mentions} />).container;
}

describe("MarkdownBody 白名单", () => {
  it("renders_whitelisted_formats", () => {
    const container = renderBody(
      "**粗** *斜* _也斜_\n\n> 引\n> 用\n\n- a\n- b\n\n1. c\n2. d\n\n用 `x` 曲线\n\n[NEJM](https://www.nejm.org/)",
    );
    expect(container.querySelector("strong")?.textContent).toBe("粗");
    expect(container.querySelectorAll("em")).toHaveLength(2);
    expect(container.querySelectorAll("blockquote")).toHaveLength(1);
    expect(container.querySelectorAll("ul > li")).toHaveLength(2);
    expect(container.querySelectorAll("ol > li")).toHaveLength(2);
    expect(container.querySelector("code")?.textContent).toBe("x");
    const link = screen.getByRole("link", { name: /NEJM（新窗口打开）/ });
    expect(link).toHaveAttribute("href", "https://www.nejm.org/");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "nofollow ugc noopener");
  });

  it.each([
    ["<b>粗</b>", "<b>粗</b>"],
    ["| a | b |\n|---|---|", "| a | b |"],
    ["```\ncode\n```", "```"],
    ["    indented", "indented"],
    ["---", "---"],
    ["[x]: https://a.b", "[x]: https://a.b"],
  ])("keeps_disallowed_syntax_as_literal_text: %j", (source, visible) => {
    const container = renderBody(source);
    expect(container.textContent).toContain(visible);
    expect(container.querySelector("h1,h2,b,table,pre,hr")).toBeNull();
  });

  it.each([
    ["# 测试评论", "测试评论"],
    ["### 三级", "三级"],
    ["标题\n===", "标题"],
    ["标题\n---", "标题"],
  ])("标题显示成同字号的粗体段落，不出现井号：%j", (source, text) => {
    const container = renderBody(`${source}\n\n正文`);
    expect(container.querySelector("h1,h2,h3,h4,h5,h6")).toBeNull();
    const strong = container.querySelector("p > strong");
    expect(strong?.textContent).toBe(text);
    expect(container.textContent).not.toContain("#");
    expect(container.querySelectorAll("p")).toHaveLength(2);
  });

  it("井号后面没有空格不是标题，按原文显示", () => {
    expect(renderBody("#标签").textContent).toBe("#标签");
  });

  it("never_renders_images", () => {
    const container = renderBody("看图 ![图](https://example.com/a.png) 完");
    expect(container.querySelectorAll("img")).toHaveLength(0);
    expect(container.textContent).toContain("![图](https://example.com/a.png)");
    expect(container.querySelector("a")).toBeNull();
  });

  it("围栏代码按原文显示并保留换行", () => {
    const container = renderBody("```\ncode\n```");
    expect(container.querySelector("code")).toBeNull();
    expect(container.querySelectorAll("br")).toHaveLength(2);
  });

  it("flattens_nested_lists", () => {
    const container = renderBody("- a\n  - b");
    expect(container.querySelectorAll("ul")).toHaveLength(1);
    expect(container.textContent).toContain("- b");
  });

  it("keeps_single_line_breaks", () => {
    const one = renderBody("a\nb");
    expect(one.querySelectorAll("p")).toHaveLength(1);
    expect(one.querySelector("p br")).not.toBeNull();
    expect(renderBody("a\n\nb").querySelectorAll("p")).toHaveLength(2);
  });

  it("escapes_show_literal", () => {
    const container = renderBody("\\*不是斜体\\*");
    expect(container.querySelector("em")).toBeNull();
    expect(container.textContent).toBe("*不是斜体*");
  });

  it.each(["[x](javascript:alert(1))", "[x](mailto:a@b.c)"])(
    "rejects_non_http_links: %s",
    (source) => {
      const container = renderBody(source);
      expect(container.querySelector("a")).toBeNull();
      expect(container.textContent).toContain("x");
    },
  );
});

describe("MarkdownBody 提及", () => {
  it("链到主页：有用户名", () => {
    renderBody("同意 <@42>", [ZHOU]);
    expect(screen.getByRole("link", { name: "@zhou-min" })).toHaveAttribute("href", "/u/zhou-min");
  });

  it("主页对访客不可见：@昵称，不链接", () => {
    const container = renderBody("同意 <@42>", [{ ...ZHOU, username: null }]);
    expect(container.textContent).toBe("同意 @周敏");
    expect(container.querySelector("a")).toBeNull();
  });

  it.each([
    ["已注销", [{ userId: "42", username: null, displayName: null, deleted: true }]],
    ["实体里没有", []],
  ])("@已注销用户：%s", (_, mentions) => {
    const container = renderBody("同意 <@42>", mentions);
    expect(container.textContent).toBe("同意 @已注销用户");
    expect(container.querySelector("a")).toBeNull();
  });

  it("does_not_treat_escaped_or_code_markers_as_mentions", () => {
    const escaped = renderBody("看 \\<@42>", [ZHOU]);
    expect(escaped.textContent).toBe("看 <@42>");
    expect(escaped.querySelector("a")).toBeNull();
    const code = renderBody("`<@42>`", [ZHOU]);
    expect(code.querySelector("code")?.textContent).toBe("<@42>");
    for (const literal of ["<@042>", "<@>"]) {
      expect(renderBody(literal, [ZHOU]).textContent).toBe(literal);
    }
  });
});

describe("MarkdownBody 自动链接", () => {
  it.each(["PMID 27940189", "PMID: 27940189", "PMID:27940189", "pmid 27940189"])(
    "PMID 链到 PubMed：%s",
    (text) => {
      renderBody(`见 ${text} 一文`);
      const link = screen.getByRole("link", { name: `${text}（新窗口打开）` });
      expect(link).toHaveAttribute("href", "https://pubmed.ncbi.nlm.nih.gov/27940189/");
    },
  );

  it("前面紧跟字母数字的 PMID 不链接", () => {
    expect(renderBody("aPMID 1").querySelector("a")).toBeNull();
  });

  it("裸 DOI 链到 doi.org，末尾句号不进链接", () => {
    renderBody("参考 10.1001/jama.2019.22176。");
    const link = screen.getByRole("link", { name: "10.1001/jama.2019.22176（新窗口打开）" });
    expect(link).toHaveAttribute("href", "https://doi.org/10.1001/jama.2019.22176");
  });

  it("doi: 前缀算进链接文字", () => {
    renderBody("doi:10.1001/x");
    expect(screen.getByRole("link", { name: "doi:10.1001/x（新窗口打开）" })).toHaveAttribute(
      "href",
      "https://doi.org/10.1001/x",
    );
  });

  it("https://doi.org/… 只出一个链接", () => {
    const container = renderBody("https://doi.org/10.1001/x");
    expect(container.querySelectorAll("a")).toHaveLength(1);
    expect(container.querySelector("a")).toHaveAttribute("href", "https://doi.org/10.1001/x");
  });

  it("裸网址超过 48 个字符时中间省略，title 是完整网址", () => {
    const url = "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5629227/";
    const container = renderBody(`见 ${url}。`);
    const link = container.querySelector("a");
    expect(link).toHaveAttribute("href", url);
    expect(link).toHaveAttribute("title", url);
    const shown = link?.firstChild?.textContent ?? "";
    expect(shown).toContain("…");
    expect([...shown].length).toBeLessThanOrEqual(48);
  });

  it("短网址原样显示，句号不进链接", () => {
    const container = renderBody("见 https://a.b/c。");
    expect(container.querySelector("a")).toHaveAttribute("href", "https://a.b/c");
    expect(container.textContent).toContain("https://a.b/c（新窗口打开）。");
  });

  it("已有链接与行内代码里的不处理", () => {
    const container = renderBody("[PMID 1](https://a.b) 与 `PMID 2`");
    expect(container.querySelectorAll("a")).toHaveLength(1);
    expect(container.querySelector("a")).toHaveAttribute("href", "https://a.b");
  });
});

/** 详情页标题的拆分结果（spec §5.1）。 */
export interface TitleParts {
  /** 主标题（可含白名单富文本标签，交给 `RichInlineText` 渲染） */
  main: string;
  /** 冒号后的副标题；没有拆开时为 null */
  sub: string | null;
  /** 字级：`md` 只给没拆开、且纯文本超过 120 字符的超长标题 */
  size: "lg" | "md";
}

const LONG_TITLE = 120;

/** 去掉标签后的纯文本（只用来量长度、数词）。 */
function plainText(html: string): string {
  return html.replace(/<[^>]*>/g, "");
}

/**
 * 找第一个可以拆开的冒号：半角冒号后面要跟空白，全角冒号不要求；
 * 冒号必须在所有富文本元素外面（`<i>a: b</i>` 里的不算）。返回冒号的下标与长度。
 */
function findSplitColon(title: string): { index: number; length: number } | null {
  let depth = 0;
  for (let i = 0; i < title.length; i += 1) {
    const ch = title[i];
    if (ch === "<") {
      const close = title.indexOf(">", i);
      if (close < 0) return null;
      const tag = title.slice(i, close + 1);
      if (tag.startsWith("</")) depth -= 1;
      else if (!tag.endsWith("/>")) depth += 1;
      i = close;
      continue;
    }
    if (depth > 0) continue;
    if (ch === "：") return { index: i, length: 1 };
    if (ch === ":" && /\s/.test(title[i + 1] ?? "")) return { index: i, length: 1 };
  }
  return null;
}

/**
 * 标题拆分：去掉末尾的一个英文句点；在第一个「冒号 + 空白」（或全角冒号）处拆成主标题与副标题。
 * 冒号前只有一个西文单词（`Re:`、`Editorial:`）时不拆。没拆开且纯文本超过 120 字符时降到 `md` 档。
 */
export function splitTitle(title: string): TitleParts {
  const trimmed = title.trim().replace(/(?<!\.)\.$/, "");
  const colon = findSplitColon(trimmed);
  if (colon) {
    const main = trimmed.slice(0, colon.index).trim();
    const sub = trimmed.slice(colon.index + colon.length).trim();
    const mainPlain = plainText(main).trim();
    if (sub && mainPlain && !/^[A-Za-z]+$/.test(mainPlain)) {
      return { main, sub, size: "lg" };
    }
  }
  return {
    main: trimmed,
    sub: null,
    size: plainText(trimmed).length > LONG_TITLE ? "md" : "lg",
  };
}

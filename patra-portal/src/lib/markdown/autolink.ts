import type { Link, PhrasingContent, Root } from "mdast";
import { findAndReplace } from "mdast-util-find-and-replace";

/// 自动链接的种类，写在链接节点的 `data-autolink` 上：裸网址显示时要中间省略
export type AutolinkKind = "url" | "pmid" | "doi";

/// 裸网址：`http(s)://` 到空白、`<`、`>` 或中文字符、全角标点为止（中文紧贴网址时不吞后面的字）
const URL_RE = /https?:\/\/[^\s<>　-鿿＀-￯]+/gi;
/// `PMID 27940189`、`PMID: 27940189`、`PMID:27940189`，不分大小写，前面不是字母数字
const PMID_RE = /(?<![A-Za-z0-9])PMID:?\s?([1-9]\d{0,8})(?!\d)/gi;
/// DOI：可选 `doi:` 前缀 + `10.` + 4–9 位注册号 + `/` + 后缀
const DOI_RE = /(?<![A-Za-z0-9])(?:doi:\s?)?(10\.\d{4,9}\/[^\s<>　-鿿＀-￯]+)/gi;

/// 结尾总是去掉的标点（中英文）
const TRAILING = new Set([..."。，；：！？、」』】》“”‘’.,;:!?'\"）"]);
/// 只在不成对时去掉的右括号
const CLOSERS: Record<string, string> = { ")": "(", "]": "[", "}": "{" };

/// 去掉网址、DOI 末尾不属于它的标点：中英文句读一律去掉，右括号只在没有对应左括号时去掉
export function trimTrailingPunctuation(value: string): string {
  let end = value.length;
  while (end > 0) {
    const last = value[end - 1] as string;
    const opener = CLOSERS[last];
    if (opener) {
      const head = value.slice(0, end);
      if (head.split(opener).length - 1 >= head.split(last).length - 1) {
        break;
      }
    } else if (!TRAILING.has(last)) {
      break;
    }
    end -= 1;
  }
  return value.slice(0, end);
}

function link(url: string, text: string, kind: AutolinkKind): Link {
  return {
    type: "link",
    url,
    children: [{ type: "text", value: text }],
    data: { hProperties: { dataAutolink: kind } },
  };
}

/// 链接 + 去掉的尾巴（放回成文字）
function withTail(node: Link, tail: string): PhrasingContent[] {
  return tail ? [node, { type: "text", value: tail }] : [node];
}

/// unified 插件：文本里的网址、PMID、DOI 变成链接（简报 §3.7）。已有链接与行内代码里的不处理；网址先于 DOI，
/// `https://doi.org/…` 只算一个链接
export function remarkAutolink() {
  return (tree: Root) => {
    findAndReplace(
      tree,
      [
        [
          URL_RE,
          (match: string) => {
            const url = trimTrailingPunctuation(match);
            return withTail(link(url, url, "url"), match.slice(url.length));
          },
        ],
        [
          PMID_RE,
          (match: string, pmid: string) =>
            link(`https://pubmed.ncbi.nlm.nih.gov/${pmid}/`, match, "pmid"),
        ],
        [
          DOI_RE,
          (match: string) => {
            const text = trimTrailingPunctuation(match);
            const doi = text.replace(/^doi:\s?/i, "");
            return withTail(link(`https://doi.org/${doi}`, text, "doi"), match.slice(text.length));
          },
        ],
      ],
      { ignore: ["link", "inlineCode"] },
    );
  };
}

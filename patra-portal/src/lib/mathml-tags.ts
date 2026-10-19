/**
 * MathML 元素白名单：全文正文（`parseMathml`）与摘要（`parseInlineMarkup`）共用。
 * 来源：PAP-86 解析夹具里出现过的全部 21 个标签（`patra-catalog-infra` 测试资源 `jats/mathml-tags.txt`），
 * 加上后端 spec §6.2 点名的 `mroot` / `menclose` / `mphantom` / `mmultiscripts`（含其子元素
 * `mprescripts` / `none`）与 `semantics`。后端只去命名空间前缀、不裁剪，安全边界在这里。
 * `mfenced` 在白名单里，但全文渲染前会被改写成 `mrow` + `mo`（Chrome 的 MathML Core 不认它）。
 */
export const MATH_ELEMENT_TAGS: ReadonlySet<string> = new Set([
  "math",
  "menclose",
  "mfenced",
  "mfrac",
  "mi",
  "mmultiscripts",
  "mn",
  "mo",
  "mover",
  "mpadded",
  "mphantom",
  "mprescripts",
  "mroot",
  "mrow",
  "mspace",
  "msqrt",
  "mstyle",
  "msub",
  "msubsup",
  "msup",
  "mtable",
  "mtd",
  "mtext",
  "mtr",
  "munder",
  "munderover",
  "none",
  "semantics",
]);

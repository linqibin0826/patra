// patra-learn/src/lib/annotations/anchor.ts
// 批注挂在哪段文字上：存选中的原文与前后各一小段上下文（同 W3C Web Annotation 的 TextQuoteSelector），
// 不存 DOM 路径或字符位置，条目改版后只要原文还在就能重新挂回去。纯函数，只处理字符串

/// 前后文各留多少个字符
const CONTEXT = 32;

export interface Quote {
  /// 选中的原文
  exact: string;
  /// 原文前面紧挨着的一段
  prefix: string;
  /// 原文后面紧挨着的一段
  suffix: string;
}

export interface TextSpan {
  start: number;
  end: number;
}

/// 文本里 [start, end) 这一段 → 原文与前后文
export function describeQuote(text: string, start: number, end: number): Quote {
  return {
    exact: text.slice(start, end),
    prefix: text.slice(Math.max(0, start - CONTEXT), start),
    suffix: text.slice(end, end + CONTEXT),
  };
}

/// a 的结尾与 b 的结尾有几个字符相同
function sameTail(a: string, b: string) {
  let n = 0;
  while (n < a.length && n < b.length && a[a.length - 1 - n] === b[b.length - 1 - n]) n++;
  return n;
}

/// a 的开头与 b 的开头有几个字符相同
function sameHead(a: string, b: string) {
  let n = 0;
  while (n < a.length && n < b.length && a[n] === b[n]) n++;
  return n;
}

/// 在当前文本里找回原文；出现多次时取前后文最像的那一处，原文不在了返回 null
export function locateQuote(text: string, quote: Quote): TextSpan | null {
  if (!quote.exact) return null;
  let best: TextSpan | null = null;
  let bestScore = -1;
  for (let at = text.indexOf(quote.exact); at >= 0; at = text.indexOf(quote.exact, at + 1)) {
    const end = at + quote.exact.length;
    const score =
      sameTail(text.slice(Math.max(0, at - CONTEXT), at), quote.prefix) +
      sameHead(text.slice(end, end + CONTEXT), quote.suffix);
    if (score > bestScore) {
      best = { start: at, end };
      bestScore = score;
    }
  }
  return best;
}

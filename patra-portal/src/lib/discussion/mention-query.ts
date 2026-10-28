import { graphemeCount } from "@/lib/grapheme";

/// 光标前正在输入的 @ 查询：`start` 是 `@` 的下标，`query` 是 `@` 与光标之间的字
export interface MentionQuery {
  start: number;
  query: string;
}

/// 查询词里允许的字符：字母、数字、`-`、`_`、汉字（简报 §3.6）
const QUERY_CHAR = /[A-Za-z0-9_\-一-鿿]/;
/// `@` 前面是这些字符时不触发（邮箱、`你好@chen`）
const WORD_BEFORE = /[\p{L}\p{N}_@]/u;

/// 光标所在的 @ 查询：光标前是 `@` + 查询字符，`@` 前面是开头、空白或标点。输入空格、标点或光标离开这个词就是 null
export function findMentionQuery(value: string, caret: number): MentionQuery | null {
  let index = caret;
  while (index > 0 && QUERY_CHAR.test(value[index - 1] as string)) {
    index -= 1;
  }
  const at = index - 1;
  if (at < 0 || value[at] !== "@") {
    return null;
  }
  const before = at > 0 ? (value[at - 1] as string) : undefined;
  if (before !== undefined && WORD_BEFORE.test(before)) {
    return null;
  }
  return { start: at, query: value.slice(index, caret) };
}

/// 选中候选：把 `@查询词` 换成 `@用户名 `（按昵称搜到的也插入用户名），光标落在空格后
export function replaceMention(
  value: string,
  query: MentionQuery,
  username: string,
): { value: string; caret: number } {
  const inserted = `@${username} `;
  const end = query.start + 1 + query.query.length;
  return {
    value: value.slice(0, query.start) + inserted + value.slice(end),
    caret: query.start + inserted.length,
  };
}

/// 没有结果且查询词超过 6 个字时自动收起：多半是在正常打字，不是在找人
export function shouldAutoClose(query: string, resultCount: number): boolean {
  return resultCount === 0 && graphemeCount(query) > 6;
}

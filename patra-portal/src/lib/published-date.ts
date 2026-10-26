/// 出版日期按实际精度写（简报 §3.19）：catalog 给 ISO 降精度字符串 `2019` / `2019-03` / `2019-03-15`，
/// 长度即精度，不补出原文没有的月日。
const PUBLISHED_DATE = /^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?$/;

/// 详情页作者行、文献库条目、公开收藏夹条目用：`2019` / `2019 年 3 月` / `2019 年 3 月 15 日`；形状不对时为 null
export function formatPublishedDate(date: string | null): string | null {
  const match = date === null ? null : PUBLISHED_DATE.exec(date);
  if (!match) {
    return null;
  }
  const [, year, month, day] = match;
  if (month === undefined) {
    return year as string;
  }
  const monthPart = `${year} 年 ${Number(month)} 月`;
  return day === undefined ? monthPart : `${monthPart} ${Number(day)} 日`;
}

/// 首页卡片、检索结果列表项只写年份：取出版日期的年，没有时回落到 `year` 字段
export function publishedYear(paper: {
  publishedDate: string | null;
  year: number | null;
}): string | null {
  const year = paper.publishedDate ? PUBLISHED_DATE.exec(paper.publishedDate)?.[1] : undefined;
  return year ?? (paper.year === null ? null : String(paper.year));
}

import { Fragment } from "react";

/// 中文破折号「——」：Newsreader、IBM Plex Mono 等拉丁字体排在字体栈前面，会抢走 U+2014，
/// 两段之间留缝且偏短。这里把破折号单独交给中文字体（Noto 的破折号相连成一道），字距清零。
export function CjkDashText({ children }: { children: string }) {
  const parts = children.split("——");
  return parts.map((part, i) => (
    // 文本切分结果静态不重排，位置即身份
    // biome-ignore lint/suspicious/noArrayIndexKey: 同上
    <Fragment key={i}>
      {i > 0 && (
        <span data-cjk-dash className="font-[family-name:var(--font-noto-sans-sc)] tracking-normal">
          ——
        </span>
      )}
      {part}
    </Fragment>
  ));
}

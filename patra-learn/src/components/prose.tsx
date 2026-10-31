// patra-learn/src/components/prose.tsx
// 条目正文里 Markdown 元素的样式（画板 l-section / l-h2 / l-p / l-codebox）。条目页编译 MDX 时与教学组件一起传入；
// 不进组件登记表，作者照常写 Markdown
import type { ComponentProps } from "react";

const H2 = (props: ComponentProps<"h2">) => (
  <h2
    className="mt-10 mb-3 font-bold font-display text-h2 text-ink leading-[1.4] first:mt-0"
    {...props}
  />
);

const H3 = (props: ComponentProps<"h3">) => (
  <h3 className="mt-7 mb-2 font-bold font-display text-h3 text-ink leading-[1.45]" {...props} />
);

const P = (props: ComponentProps<"p">) => <p className="my-3 text-ink-2" {...props} />;

const Ul = (props: ComponentProps<"ul">) => (
  <ul className="my-3 list-disc pl-6 text-ink-2 marker:text-ink-5" {...props} />
);

const Ol = (props: ComponentProps<"ol">) => (
  <ol className="my-3 list-decimal pl-6 text-ink-2 marker:text-ink-4" {...props} />
);

const Li = (props: ComponentProps<"li">) => <li className="my-1 pl-1" {...props} />;

const Blockquote = (props: ComponentProps<"blockquote">) => (
  <blockquote
    className="my-4 rounded-card bg-moss px-5.5 py-1 text-ink [&_p]:text-ink"
    {...props}
  />
);

/// 表格外包一层横向滚动，窄屏不撑破页面
const Table = (props: ComponentProps<"table">) => (
  <div className="my-4 overflow-x-auto rounded-inner border border-line bg-surface">
    <table className="w-full border-collapse text-[15px] leading-[1.7]" {...props} />
  </div>
);

const Th = (props: ComponentProps<"th">) => (
  <th
    className="border-line border-b bg-surface-soft px-3.5 py-2 text-left font-semibold font-ui text-ink text-sm"
    {...props}
  />
);

const Td = (props: ComponentProps<"td">) => (
  <td className="border-line-soft border-b px-3.5 py-2 align-top text-ink-2" {...props} />
);

/// 代码块深色；里面的 code 去掉行内代码的底色
const Pre = (props: ComponentProps<"pre">) => (
  <pre
    className="my-4 overflow-x-auto rounded-xl bg-ink p-4 font-mono text-[13px] text-code-ink leading-[1.7] [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-[1em] [&_code]:text-code-ink"
    {...props}
  />
);

const Code = (props: ComponentProps<"code">) => (
  <code
    className="rounded-[4px] bg-line-soft px-[5px] py-px font-mono text-[0.86em] text-ink"
    {...props}
  />
);

const A = (props: ComponentProps<"a">) => (
  <a
    className="underline decoration-link-line underline-offset-[3px] hover:decoration-teal"
    {...props}
  />
);

const Strong = (props: ComponentProps<"strong">) => (
  <strong className="font-semibold text-ink" {...props} />
);

const Hr = () => <hr className="my-8 border-0 border-line border-t" />;

export const proseComponents = {
  h2: H2,
  h3: H3,
  p: P,
  ul: Ul,
  ol: Ol,
  li: Li,
  blockquote: Blockquote,
  table: Table,
  th: Th,
  td: Td,
  pre: Pre,
  code: Code,
  a: A,
  strong: Strong,
  hr: Hr,
};

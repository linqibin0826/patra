import Link from "next/link";

export interface EmptyStateAction {
  href: string;
  label: string;
}

interface EmptyStateProps {
  title: string;
  description: string;
  actions: EmptyStateAction[];
}

const ACTION_CLASS =
  "rounded-md border border-(--border-default) px-4 py-2 text-sm transition hover:border-ink-300 hover:bg-paper-100";

/**
 * 浏览页空态外壳（RSC 纯渲染）：衬线标题 + 说明 + 按钮组，文案由调用方决定。
 */
export function EmptyState({ title, description, actions }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-4 py-20 text-center">
      <h2 className="font-serif text-2xl font-medium text-(--fg-1)">{title}</h2>
      <p className="max-w-sm text-sm text-(--fg-3)">{description}</p>
      <div className="flex flex-wrap justify-center gap-3">
        {actions.map((action) => (
          <Link key={action.href + action.label} href={action.href} className={ACTION_CLASS}>
            {action.label}
          </Link>
        ))}
      </div>
    </div>
  );
}

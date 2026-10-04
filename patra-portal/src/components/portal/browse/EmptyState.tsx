import Link from "next/link";
import { Venation } from "@/components/portal/Venation";
import { btnSecondary } from "@/lib/portal-ui";

export interface EmptyStateAction {
  href: string;
  label: string;
}

interface EmptyStateProps {
  title: string;
  description: string;
  actions: EmptyStateAction[];
}

/**
 * 浏览页空态外壳（RSC 纯渲染）：一片静态小叶 + 衬线标题 + 说明 + 药丸按钮组，文案由调用方决定。
 */
export function EmptyState({ title, description, actions }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-4 border-y border-border-subtle py-20 text-center">
      <Venation className="mb-2 h-24 w-auto -rotate-12 opacity-60" />
      <h2 className="max-w-[24em] font-serif text-3xl leading-heading font-medium tracking-tight text-balance text-fg-1">
        {title}
      </h2>
      <p className="max-w-sm text-sm leading-relaxed text-fg-3">{description}</p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        {actions.map((action) => (
          <Link key={action.href + action.label} href={action.href} className={btnSecondary}>
            {action.label}
          </Link>
        ))}
      </div>
    </div>
  );
}

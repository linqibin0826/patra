import Link from "next/link";
import { Pill } from "@/components/portal/Pill";
import { StatusPill } from "@/components/ui/status-pill";
import { occupationLabel } from "@/lib/identity/occupation";
import type { PublicProfile } from "@/lib/identity/public-profile";

interface ProfileAboutProps {
  profile: PublicProfile;
  self: boolean;
  /// 本人视角且专业背景只给自己看：标题旁「仅你可见」
  professionalSelfOnly: boolean;
}

/// 公开主页正文「关于」（简报 §3.13）：专业背景与关注领域两栏（窄屏单列，专业背景在上）。只填了一部分
/// 就只显示有的；两块都空时别人视角不渲染，本人视角提示去完善资料。以后的标签栏不渲染、不占空白。
export function ProfileAbout({ profile, self, professionalSelfOnly }: ProfileAboutProps) {
  const { professional, researchInterests } = profile;
  if (!professional && researchInterests.length === 0) {
    return self ? (
      <div className="mx-auto max-w-page px-gutter pt-10 pb-30 text-md text-fg-2">
        你的主页还很空。
        <Link
          href="/settings/profile"
          className="text-link transition-colors hover:text-link-hover"
        >
          <span className="link-draw">完善资料</span>
        </Link>
      </div>
    ) : (
      <div className="h-30" />
    );
  }
  const role = professional
    ? [professional.occupation && occupationLabel(professional.occupation), professional.jobTitle]
        .filter(Boolean)
        .join(" · ")
    : "";
  return (
    <div className="mx-auto grid max-w-page grid-cols-2 gap-12 px-gutter pt-10 pb-30 max-[880px]:grid-cols-1 max-[880px]:gap-8 max-[880px]:pt-7 max-[880px]:pb-16">
      {professional && (
        <section aria-labelledby="profile-professional">
          <h2
            id="profile-professional"
            className="mb-3.5 flex items-center gap-2.5 font-serif text-xl font-medium text-fg-1"
          >
            专业背景
            {professionalSelfOnly && <StatusPill tone="outline">仅你可见</StatusPill>}
          </h2>
          <div className="flex flex-col gap-2 text-md leading-6 text-fg-1">
            {role && <span>{role}</span>}
            {professional.organization && (
              <span className="break-all">{professional.organization.name}</span>
            )}
            {professional.department && (
              <span className="text-fg-2">{professional.department}</span>
            )}
          </div>
        </section>
      )}
      {researchInterests.length > 0 && (
        <section aria-labelledby="profile-interests">
          <h2 id="profile-interests" className="mb-3.5 font-serif text-xl font-medium text-fg-1">
            关注领域
          </h2>
          <ul className="flex flex-wrap gap-2" lang="en">
            {researchInterests.map((interest) => (
              <li key={interest.ui}>
                <Pill
                  href={`/papers?q=${encodeURIComponent(interest.name)}`}
                  title={`检索 ${interest.name} 相关文献`}
                  className="text-fg-1"
                >
                  {interest.name}
                </Pill>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

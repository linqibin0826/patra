import { render, screen } from "@testing-library/react";
import { PUBLIC_PROFILE } from "@tests/fixtures/public-profile";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/components/profile/ProfileHelpfulReceived", () => ({
  ProfileHelpfulReceived: ({ userId }: { userId: string }) => <p>获赞桩 {userId}</p>,
}));

import { ProfileHeader } from "@/components/profile/ProfileHeader";

const follows = (a: Node, b: Node) =>
  Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

describe("ProfileHeader · 获赞", () => {
  it("should_put_likes_received_between_username_and_bio", () => {
    render(
      <ProfileHeader
        profile={{ ...PUBLIC_PROFILE, bio: "心内科医生" }}
        self={false}
        linksPending={false}
      />,
    );
    const likes = screen.getByText(`获赞桩 ${PUBLIC_PROFILE.userId}`);
    expect(follows(screen.getByText(`@${PUBLIC_PROFILE.username}`), likes)).toBe(true);
    expect(follows(likes, screen.getByText("心内科医生"))).toBe(true);
  });
});

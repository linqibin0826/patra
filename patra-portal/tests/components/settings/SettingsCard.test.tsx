import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SettingsCard } from "@/components/settings/SettingsCard";

describe("SettingsCard", () => {
  it("asidePlacement=block：附加内容排在说明之后（窄屏落到说明下方）", () => {
    render(
      <SettingsCard
        title="登录设备"
        description="这里列出所有正在登录的设备。"
        aside={<button type="button">退出其他所有设备</button>}
        asidePlacement="block"
      >
        <p>列表</p>
      </SettingsCard>,
    );
    const description = screen.getByText("这里列出所有正在登录的设备。");
    const button = screen.getByRole("button", { name: "退出其他所有设备" });
    expect(
      description.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("默认：附加内容在标题行里，排在说明之前", () => {
    render(
      <SettingsCard title="关注领域" description="说明" aside={<span>3 / 10</span>}>
        <p>列表</p>
      </SettingsCard>,
    );
    const description = screen.getByText("说明");
    const aside = screen.getByText("3 / 10");
    expect(
      description.compareDocumentPosition(aside) & Node.DOCUMENT_POSITION_PRECEDING,
    ).toBeTruthy();
  });
});

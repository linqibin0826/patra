#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
标准化 JavaDoc 标签工具

将所有 Java 文件中的 @author 和 @since 标签统一修改为：
- @author linqibin
- @since 0.1.0
"""

import re
from pathlib import Path
from typing import Tuple

# 目标值
TARGET_AUTHOR = "linqibin"
TARGET_SINCE = "0.1.0"


def normalize_author_tag(content: str) -> Tuple[str, int]:
    """
    标准化 @author 标签

    处理两种格式：
    1. Markdown 风格: /// @author xxx
    2. 传统风格: * @author xxx

    Returns:
        (修改后的内容, 修改次数)
    """
    count = 0

    # 处理 Markdown 风格 /// @author
    def replace_markdown_author(match):
        nonlocal count
        current_author = match.group(1)
        if current_author != TARGET_AUTHOR:
            count += 1
        return f"/// @author {TARGET_AUTHOR}"

    content = re.sub(
        r'/// @author (.+?)$',
        replace_markdown_author,
        content,
        flags=re.MULTILINE
    )

    # 处理传统风格 * @author
    def replace_traditional_author(match):
        nonlocal count
        indent = match.group(1)
        current_author = match.group(2)
        if current_author != TARGET_AUTHOR:
            count += 1
        return f"{indent}* @author {TARGET_AUTHOR}"

    content = re.sub(
        r'^(\s+)\* @author (.+?)$',
        replace_traditional_author,
        content,
        flags=re.MULTILINE
    )

    return content, count


def normalize_since_tag(content: str) -> Tuple[str, int]:
    """
    标准化 @since 标签

    处理格式：
    1. /// @since xxx
    2. /// @since xxx (说明)
    3. * @since xxx
    4. * @since xxx (说明)

    统一替换为: @since 0.1.0

    Returns:
        (修改后的内容, 修改次数)
    """
    count = 0

    # 处理 Markdown 风格 /// @since
    def replace_markdown_since(match):
        nonlocal count
        current_version = match.group(1).strip()
        # 提取版本号（去掉可能的说明）
        version_only = current_version.split()[0]
        if version_only != TARGET_SINCE:
            count += 1
        return f"/// @since {TARGET_SINCE}"

    content = re.sub(
        r'/// @since (.+?)$',
        replace_markdown_since,
        content,
        flags=re.MULTILINE
    )

    # 处理传统风格 * @since
    def replace_traditional_since(match):
        nonlocal count
        indent = match.group(1)
        current_version = match.group(2).strip()
        # 提取版本号（去掉可能的说明）
        version_only = current_version.split()[0]
        if version_only != TARGET_SINCE:
            count += 1
        return f"{indent}* @since {TARGET_SINCE}"

    content = re.sub(
        r'^(\s+)\* @since (.+?)$',
        replace_traditional_since,
        content,
        flags=re.MULTILINE
    )

    return content, count


def process_java_file(file_path: Path) -> Tuple[bool, int, int]:
    """
    处理单个 Java 文件

    Returns:
        (是否修改, @author 修改次数, @since 修改次数)
    """
    try:
        content = file_path.read_text(encoding='utf-8')
        original_content = content

        # 标准化 @author
        content, author_count = normalize_author_tag(content)

        # 标准化 @since
        content, since_count = normalize_since_tag(content)

        # 如果有修改，写回文件
        if content != original_content:
            file_path.write_text(content, encoding='utf-8')
            return True, author_count, since_count

        return False, 0, 0

    except Exception as e:
        print(f"❌ 处理文件失败: {file_path}")
        print(f"   错误: {e}")
        return False, 0, 0


def main():
    """主函数"""
    print("🚀 JavaDoc 标签标准化工具")
    print("=" * 50)
    print(f"目标作者: {TARGET_AUTHOR}")
    print(f"目标版本: {TARGET_SINCE}")
    print("=" * 50)

    # 获取项目根目录
    project_root = Path(__file__).parent.parent

    # 统计变量
    total_files = 0
    modified_files = 0
    total_author_changes = 0
    total_since_changes = 0

    # 遍历所有 Java 文件
    for java_file in project_root.rglob("*.java"):
        # 跳过 target 目录
        if 'target' in java_file.parts:
            continue

        total_files += 1
        modified, author_count, since_count = process_java_file(java_file)

        if modified:
            modified_files += 1
            total_author_changes += author_count
            total_since_changes += since_count

            if author_count > 0 or since_count > 0:
                print(f"✅ {java_file.relative_to(project_root)}")
                if author_count > 0:
                    print(f"   - @author: {author_count} 处修改")
                if since_count > 0:
                    print(f"   - @since: {since_count} 处修改")

    # 输出统计结果
    print("\n" + "=" * 50)
    print("📊 处理完成统计:")
    print(f"  总文件数: {total_files}")
    print(f"  修改文件数: {modified_files}")
    print(f"  @author 修改次数: {total_author_changes}")
    print(f"  @since 修改次数: {total_since_changes}")
    print("=" * 50)
    print("✨ 完成！")


if __name__ == "__main__":
    main()

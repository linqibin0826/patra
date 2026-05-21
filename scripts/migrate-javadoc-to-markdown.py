#!/usr/bin/env python3
"""
JavaDoc 到 Markdown 迁移脚本
将传统 HTML 风格的 JavaDoc 注释转换为 JDK 23+ 支持的 Markdown 风格

使用方法：
    python migrate-javadoc-to-markdown.py [目录路径] [--dry-run] [--verbose]

参数：
    目录路径: 要处理的项目根目录（默认为当前目录）
    --dry-run: 仅预览，不实际修改文件
    --verbose: 显示详细处理信息
    --backup: 创建 .bak 备份文件

示例：
    # 预览模式
    python migrate-javadoc-to-markdown.py . --dry-run

    # 执行迁移并备份
    python migrate-javadoc-to-markdown.py . --backup

    # 迁移特定模块
    python migrate-javadoc-to-markdown.py patra-catalog/

作者: Patra Team
版本: 1.0.0
"""

import os
import re
import sys
import argparse
import shutil
from pathlib import Path
from typing import List, Tuple, Optional
from dataclasses import dataclass
from datetime import datetime


@dataclass
class ConversionStats:
    """转换统计信息"""
    files_processed: int = 0
    files_modified: int = 0
    javadocs_converted: int = 0
    errors: List[str] = None

    def __post_init__(self):
        if self.errors is None:
            self.errors = []


class JavaDocConverter:
    """JavaDoc 到 Markdown 转换器"""

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.stats = ConversionStats()

    def convert_html_to_markdown(self, content: str) -> str:
        """
        将 HTML 标签转换为 Markdown 语法
        """
        # 处理段落标签
        content = re.sub(r'<p>\s*', '\n', content)
        content = re.sub(r'\s*</p>', '\n', content)

        # 处理列表
        # 无序列表
        content = re.sub(r'<ul>\s*', '\n', content)
        content = re.sub(r'\s*</ul>', '\n', content)
        content = re.sub(r'<li>\s*', '- ', content)
        content = re.sub(r'\s*</li>', '', content)

        # 有序列表（需要更复杂的处理）
        def replace_ordered_list(match):
            ol_content = match.group(1)
            items = re.findall(r'<li>(.*?)(?:</li>|(?=<li>))', ol_content, re.DOTALL)
            result = '\n'
            for i, item in enumerate(items, 1):
                result += f'{i}. {item.strip()}\n'
            return result

        content = re.sub(r'<ol>(.*?)</ol>', replace_ordered_list, content, flags=re.DOTALL)

        # 处理强调和加粗
        content = re.sub(r'<b>(.*?)</b>', r'**\1**', content)
        content = re.sub(r'<strong>(.*?)</strong>', r'**\1**', content)
        content = re.sub(r'<em>(.*?)</em>', r'*\1*', content)
        content = re.sub(r'<i>(.*?)</i>', r'*\1*', content)

        # 处理标题
        content = re.sub(r'<h1>(.*?)</h1>', r'# \1', content)
        content = re.sub(r'<h2>(.*?)</h2>', r'## \1', content)
        content = re.sub(r'<h3>(.*?)</h3>', r'### \1', content)
        content = re.sub(r'<h4>(.*?)</h4>', r'#### \1', content)

        # 处理代码块
        # 处理 <pre>{@code ...}</pre> 组合
        def replace_pre_code_block(match):
            code_content = match.group(1)
            # 移除 {@code 和 }
            code_content = re.sub(r'\{@code\s*', '', code_content)
            code_content = re.sub(r'\s*\}', '', code_content)
            return f'```java\n{code_content.strip()}\n```'

        content = re.sub(r'<pre>\s*\{@code(.*?)\}\s*</pre>',
                        replace_pre_code_block, content, flags=re.DOTALL)

        # 处理普通的 <pre> 块
        content = re.sub(r'<pre>(.*?)</pre>', r'```\n\1\n```', content, flags=re.DOTALL)

        # 处理内联代码
        content = re.sub(r'<code>(.*?)</code>', r'`\1`', content)
        content = re.sub(r'\{@code\s+(.*?)\}', r'`\1`', content)

        # 处理链接（保留 JavaDoc 特有的链接格式）
        # {@link} 和 @see 保持不变，因为它们是 JavaDoc 特有的

        # 处理换行标签
        content = re.sub(r'<br\s*/?>', '\n', content)

        # 清理多余的空行
        content = re.sub(r'\n{3,}', '\n\n', content)

        return content

    def convert_javadoc_comment(self, javadoc: str) -> str:
        """
        将单个 JavaDoc 注释转换为 Markdown 风格
        """
        # 检查是否已经是 Markdown 风格
        if javadoc.strip().startswith('///'):
            return javadoc

        # 首先提取纯内容（移除 /** 和 */ 以及每行的 *）
        # 匹配 /** ... */
        content = javadoc
        content = re.sub(r'/\*\*\s*', '', content)  # 移除开始的 /**
        content = re.sub(r'\s*\*/', '', content)    # 移除结束的 */

        # 分行处理，移除每行开头的 *
        lines = content.split('\n')
        cleaned_lines = []
        for line in lines:
            # 移除行首的 * （可能有前导空格）
            cleaned = re.sub(r'^\s*\*\s?', '', line)
            cleaned_lines.append(cleaned)

        # 合并清理后的内容
        content = '\n'.join(cleaned_lines)

        # 转换 HTML 到 Markdown
        content = self.convert_html_to_markdown(content)

        # 将内容转换为 /// 格式
        lines = content.split('\n')
        result_lines = []

        for line in lines:
            # 每行都添加 /// 前缀
            result_lines.append(f'/// {line}')

        result = '\n'.join(result_lines)

        # 清理连续的空 /// 行（保留最多2个）
        result = re.sub(r'(///\s*\n){3,}', '///\n///\n', result)

        return result

    def convert_field_javadoc(self, javadoc: str) -> str:
        """
        处理字段的 JavaDoc（保持简短的单行注释不变）
        """
        # 提取内容
        content = javadoc.strip()

        # 如果是单行简单注释（没有复杂的 HTML 或多行内容）
        if content.startswith('/**') and content.endswith('*/') and '\n' not in content:
            # 检查是否包含 HTML 标签或需要转换的内容
            if not any(tag in content for tag in ['<', '@param', '@return', '@throws', '@see', '{@']):
                # 保持原样
                return javadoc

        # 否则转换为 Markdown 风格
        return self.convert_javadoc_comment(javadoc)

    def process_java_file(self, file_path: Path, dry_run: bool = False,
                         backup: bool = False) -> bool:
        """
        处理单个 Java 文件
        返回是否修改了文件
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()

            original_content = content
            modified = False

            # 匹配类/接口/方法级别的 JavaDoc（多行）
            pattern_multiline = r'/\*\*(?:[^*]|\*(?!/))*\*/'

            def replace_multiline_javadoc(match):
                nonlocal modified
                original = match.group(0)
                converted = self.convert_javadoc_comment(original)
                if original != converted:
                    modified = True
                    self.stats.javadocs_converted += 1
                return converted

            content = re.sub(pattern_multiline, replace_multiline_javadoc,
                           content, flags=re.DOTALL)

            # 处理字段的单行 JavaDoc（可选）
            pattern_field = r'/\*\*\s*([^*]+?)\s*\*/'

            def replace_field_javadoc(match):
                nonlocal modified
                original = match.group(0)
                # 对于简单的字段注释，保持原样
                if not any(tag in original for tag in ['<', '@param', '@return', '@throws']):
                    return original
                converted = self.convert_javadoc_comment(original)
                if original != converted:
                    modified = True
                    self.stats.javadocs_converted += 1
                return converted

            content = re.sub(pattern_field, replace_field_javadoc, content)

            if modified:
                if not dry_run:
                    # 创建备份
                    if backup:
                        backup_path = file_path.with_suffix(file_path.suffix + '.bak')
                        shutil.copy2(file_path, backup_path)
                        if self.verbose:
                            print(f"  备份创建: {backup_path}")

                    # 写入修改后的内容
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(content)

                self.stats.files_modified += 1
                return True

            return False

        except Exception as e:
            error_msg = f"处理文件 {file_path} 时出错: {e}"
            self.stats.errors.append(error_msg)
            print(f"❌ {error_msg}")
            return False

    def process_directory(self, directory: Path, dry_run: bool = False,
                         backup: bool = False) -> ConversionStats:
        """
        递归处理目录中的所有 Java 文件
        """
        java_files = list(directory.rglob('*.java'))

        # 排除 target 和 build 目录
        java_files = [f for f in java_files
                      if 'target' not in str(f) and 'build' not in str(f)]

        print(f"找到 {len(java_files)} 个 Java 文件")

        if dry_run:
            print("🔍 预览模式 - 不会实际修改文件")

        for file_path in java_files:
            self.stats.files_processed += 1

            if self.verbose:
                print(f"处理: {file_path}")

            if self.process_java_file(file_path, dry_run, backup):
                relative_path = file_path.relative_to(directory)
                status = "✅ 已修改" if not dry_run else "📝 将修改"
                print(f"{status}: {relative_path}")

        return self.stats

    def process_markdown_files(self, directory: Path, dry_run: bool = False,
                              backup: bool = False) -> int:
        """
        处理 Markdown 文件中的 Java 代码块
        """
        md_files = list(directory.rglob('*.md'))
        modified_count = 0

        for file_path in md_files:
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()

                original_content = content
                modified = False

                # 匹配 Java 代码块
                pattern = r'```java\n(.*?)\n```'

                def replace_java_block(match):
                    nonlocal modified
                    java_code = match.group(1)

                    # 转换代码块中的 JavaDoc
                    def replace_javadoc_in_code(javadoc_match):
                        return self.convert_javadoc_comment(javadoc_match.group(0))

                    converted_code = re.sub(r'/\*\*(?:[^*]|\*(?!/))*\*/',
                                           replace_javadoc_in_code,
                                           java_code, flags=re.DOTALL)

                    if java_code != converted_code:
                        modified = True

                    return f'```java\n{converted_code}\n```'

                content = re.sub(pattern, replace_java_block, content, flags=re.DOTALL)

                if modified:
                    if not dry_run:
                        if backup:
                            backup_path = file_path.with_suffix(file_path.suffix + '.bak')
                            shutil.copy2(file_path, backup_path)

                        with open(file_path, 'w', encoding='utf-8') as f:
                            f.write(content)

                    modified_count += 1
                    status = "✅ 已修改" if not dry_run else "📝 将修改"
                    print(f"{status} Markdown: {file_path.relative_to(directory)}")

            except Exception as e:
                print(f"❌ 处理 Markdown 文件 {file_path} 时出错: {e}")

        return modified_count


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description='将传统 HTML 风格的 JavaDoc 转换为 Markdown 风格',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    parser.add_argument('directory', nargs='?', default='.',
                       help='要处理的目录路径（默认为当前目录）')
    parser.add_argument('--dry-run', action='store_true',
                       help='预览模式，不实际修改文件')
    parser.add_argument('--verbose', action='store_true',
                       help='显示详细处理信息')
    parser.add_argument('--backup', action='store_true',
                       help='创建 .bak 备份文件')
    parser.add_argument('--include-md', action='store_true',
                       help='同时处理 .md 文件中的 Java 代码')

    args = parser.parse_args()

    # 确认目录存在
    directory = Path(args.directory).resolve()
    if not directory.exists():
        print(f"❌ 目录不存在: {directory}")
        sys.exit(1)

    print(f"🚀 JavaDoc 到 Markdown 迁移工具")
    print(f"📁 处理目录: {directory}")
    print(f"⏰ 开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("-" * 50)

    # 创建转换器
    converter = JavaDocConverter(verbose=args.verbose)

    # 处理 Java 文件
    stats = converter.process_directory(directory, args.dry_run, args.backup)

    # 处理 Markdown 文件（如果需要）
    md_count = 0
    if args.include_md:
        print("\n处理 Markdown 文件中的代码...")
        md_count = converter.process_markdown_files(directory, args.dry_run, args.backup)

    # 打印统计信息
    print("\n" + "=" * 50)
    print("📊 转换统计:")
    print(f"  Java 文件处理: {stats.files_processed}")
    print(f"  Java 文件修改: {stats.files_modified}")
    print(f"  JavaDoc 转换: {stats.javadocs_converted}")
    if args.include_md:
        print(f"  Markdown 文件修改: {md_count}")

    if stats.errors:
        print(f"\n⚠️ 错误 ({len(stats.errors)} 个):")
        for error in stats.errors[:5]:  # 只显示前 5 个错误
            print(f"  - {error}")
        if len(stats.errors) > 5:
            print(f"  ... 还有 {len(stats.errors) - 5} 个错误")

    if args.dry_run:
        print("\n💡 这是预览模式，没有实际修改文件")
        print("   移除 --dry-run 参数来执行实际转换")

    print(f"\n✨ 完成！")


if __name__ == '__main__':
    main()
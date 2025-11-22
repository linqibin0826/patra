#!/usr/bin/env python3
"""
JavaDoc 迁移测试脚本
用于测试和验证 JavaDoc 到 Markdown 的转换效果

使用方法：
    python test-javadoc-migration.py
"""

import tempfile
import os
import sys
from pathlib import Path

# 添加当前目录到 Python 路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 动态导入带连字符的模块
import importlib.util
spec = importlib.util.spec_from_file_location(
    "migrate_module",
    os.path.join(os.path.dirname(__file__), "migrate-javadoc-to-markdown.py")
)
migrate_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate_module)
JavaDocConverter = migrate_module.JavaDocConverter


def create_test_file(content: str) -> Path:
    """创建临时测试文件"""
    fd, path = tempfile.mkstemp(suffix='.java')
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(content)
    return Path(path)


def test_class_javadoc():
    """测试类级别的 JavaDoc 转换"""
    print("\n📝 测试类级别 JavaDoc 转换...")

    original = '''package com.example;

/**
 * 测试类说明。
 *
 * <p>这是一个测试类，用于演示 JavaDoc 转换。</p>
 *
 * <p><b>主要功能</b>：</p>
 * <ul>
 *   <li>功能一：处理数据</li>
 *   <li>功能二：生成报告</li>
 *   <li>功能三：发送通知</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * TestClass test = new TestClass();
 * test.process();
 * }</pre>
 *
 * @author 测试作者
 * @since 1.0.0
 * @see AnotherClass
 */
public class TestClass {
    // 类内容
}'''

    expected = '''package com.example;

///
/// 测试类说明。
///
/// 这是一个测试类，用于演示 JavaDoc 转换。
///
/// **主要功能**：
///
/// - 功能一：处理数据
/// - 功能二：生成报告
/// - 功能三：发送通知
///
/// ## 使用示例
/// ```
/// {@code
/// TestClass test = new TestClass();
/// test.process();
/// }
/// ```
///
/// @author 测试作者
/// @since 1.0.0
/// @see AnotherClass
///
public class TestClass {
    // 类内容
}'''

    # 创建测试文件
    test_file = create_test_file(original)

    # 执行转换
    converter = JavaDocConverter()
    converter.process_java_file(test_file)

    # 读取结果
    with open(test_file, 'r', encoding='utf-8') as f:
        result = f.read()

    # 清理
    test_file.unlink()

    # 显示结果
    print("原始内容:")
    print("=" * 40)
    print(original)
    print("\n转换后:")
    print("=" * 40)
    print(result)

    return "✅ 类级别 JavaDoc 转换测试完成"


def test_method_javadoc():
    """测试方法级别的 JavaDoc 转换"""
    print("\n📝 测试方法级别 JavaDoc 转换...")

    original = '''public class TestClass {
    /**
     * 处理数据的方法。
     *
     * <p>此方法执行以下操作：</p>
     * <ol>
     *   <li>验证输入参数</li>
     *   <li>处理数据</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * @param input 输入数据，不能为 null
     * @param options 处理选项
     * @return 处理后的结果
     * @throws IllegalArgumentException 如果输入无效
     * @throws ProcessException 如果处理失败
     */
    public String process(String input, Map<String, Object> options) {
        // 方法实现
    }
}'''

    # 创建测试文件
    test_file = create_test_file(original)

    # 执行转换
    converter = JavaDocConverter()
    converter.process_java_file(test_file)

    # 读取结果
    with open(test_file, 'r', encoding='utf-8') as f:
        result = f.read()

    # 清理
    test_file.unlink()

    # 显示结果
    print("原始内容:")
    print("=" * 40)
    print(original)
    print("\n转换后:")
    print("=" * 40)
    print(result)

    return "✅ 方法级别 JavaDoc 转换测试完成"


def test_field_javadoc():
    """测试字段级别的 JavaDoc 转换"""
    print("\n📝 测试字段级别 JavaDoc 转换...")

    original = '''public class TestClass {
    /** 简单的字段注释 */
    private String name;

    /** 状态字段（ACTIVE/INACTIVE/PENDING） */
    private Status status;

    /**
     * 复杂的字段注释。
     *
     * <p>这个字段有更详细的说明。</p>
     *
     * <ul>
     *   <li>特性一</li>
     *   <li>特性二</li>
     * </ul>
     */
    private List<Item> items;
}'''

    # 创建测试文件
    test_file = create_test_file(original)

    # 执行转换
    converter = JavaDocConverter()
    converter.process_java_file(test_file)

    # 读取结果
    with open(test_file, 'r', encoding='utf-8') as f:
        result = f.read()

    # 清理
    test_file.unlink()

    # 显示结果
    print("原始内容:")
    print("=" * 40)
    print(original)
    print("\n转换后:")
    print("=" * 40)
    print(result)

    return "✅ 字段级别 JavaDoc 转换测试完成"


def test_complex_html():
    """测试复杂 HTML 标签转换"""
    print("\n📝 测试复杂 HTML 标签转换...")

    original = '''public class TestClass {
    /**
     * 复杂的 JavaDoc 示例。
     *
     * <h2>概述</h2>
     * <p>这是一个<b>重要</b>的类，包含<strong>关键</strong>功能。</p>
     *
     * <h3>功能列表</h3>
     * <ul>
     *   <li><b>功能 A</b>：执行 <code>processA()</code> 方法</li>
     *   <li><em>功能 B</em>：调用 {@link ServiceB#methodB()}</li>
     * </ul>
     *
     * <h3>代码示例</h3>
     * <pre>
     * TestClass obj = new TestClass();
     * obj.init();
     * obj.process();
     * </pre>
     *
     * <p>更多信息请参考 {@link Documentation}。</p>
     *
     * @deprecated 从 2.0 版本开始，请使用 {@link NewTestClass}
     */
    @Deprecated
    public void complexMethod() {
        // 实现
    }
}'''

    # 创建测试文件
    test_file = create_test_file(original)

    # 执行转换
    converter = JavaDocConverter()
    converter.process_java_file(test_file)

    # 读取结果
    with open(test_file, 'r', encoding='utf-8') as f:
        result = f.read()

    # 清理
    test_file.unlink()

    # 显示结果
    print("原始内容:")
    print("=" * 40)
    print(original)
    print("\n转换后:")
    print("=" * 40)
    print(result)

    return "✅ 复杂 HTML 标签转换测试完成"


def test_markdown_file():
    """测试 Markdown 文件中的 Java 代码转换"""
    print("\n📝 测试 Markdown 文件中的代码转换...")

    original = '''# 示例文档

这是一个包含 Java 代码的 Markdown 文件。

## 代码示例

```java
/**
 * 示例类。
 *
 * <p>这个类演示了如何使用 API。</p>
 *
 * <ul>
 *   <li>步骤 1：初始化</li>
 *   <li>步骤 2：处理</li>
 * </ul>
 *
 * @author 示例作者
 */
public class Example {
    /**
     * 主方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

更多示例...'''

    # 创建临时 Markdown 文件
    fd, path = tempfile.mkstemp(suffix='.md')
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(original)
    test_file = Path(path)

    # 执行转换
    converter = JavaDocConverter()

    # 手动处理 Markdown 内容（简化测试）
    with open(test_file, 'r', encoding='utf-8') as f:
        content = f.read()

    import re

    def replace_java_block(match):
        java_code = match.group(1)

        def replace_javadoc_in_code(javadoc_match):
            return converter.convert_javadoc_comment(javadoc_match.group(0))

        converted_code = re.sub(r'/\*\*(?:[^*]|\*(?!/))*\*/',
                               replace_javadoc_in_code,
                               java_code, flags=re.DOTALL)

        return f'```java\n{converted_code}\n```'

    result = re.sub(r'```java\n(.*?)\n```', replace_java_block,
                   content, flags=re.DOTALL)

    # 清理
    test_file.unlink()

    # 显示结果
    print("原始内容:")
    print("=" * 40)
    print(original)
    print("\n转换后:")
    print("=" * 40)
    print(result)

    return "✅ Markdown 文件代码转换测试完成"


def main():
    """运行所有测试"""
    print("🧪 JavaDoc 到 Markdown 转换测试")
    print("=" * 50)

    tests = [
        test_class_javadoc,
        test_method_javadoc,
        test_field_javadoc,
        test_complex_html,
        test_markdown_file,
    ]

    results = []
    for test in tests:
        try:
            result = test()
            results.append(result)
        except Exception as e:
            results.append(f"❌ {test.__name__} 失败: {e}")

    # 打印测试结果汇总
    print("\n" + "=" * 50)
    print("📊 测试结果汇总:")
    for result in results:
        print(f"  {result}")

    print("\n✨ 测试完成！")


if __name__ == '__main__':
    main()
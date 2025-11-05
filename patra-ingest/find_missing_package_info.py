#!/usr/bin/env python3
"""查找缺少 package-info.java 的 Java 包目录"""

import os
from pathlib import Path
from collections import defaultdict

def find_java_packages(root_dir):
    """查找所有 Java 源码包目录"""
    packages = set()

    for root, dirs, files in os.walk(root_dir):
        # 只处理 src/main/java 目录
        if 'src/main/java' not in root:
            continue

        # 排除测试目录
        if '/test/' in root or '/tests/' in root:
            continue

        # 如果目录中有 .java 文件,这是一个包
        java_files = [f for f in files if f.endswith('.java') and f != 'package-info.java']
        if java_files:
            packages.add(root)

    return sorted(packages)

def has_package_info(pkg_dir):
    """检查包目录是否有 package-info.java"""
    return os.path.exists(os.path.join(pkg_dir, 'package-info.java'))

def get_module_name(pkg_dir):
    """从路径中提取模块名"""
    if 'patra-ingest-domain' in pkg_dir:
        return 'domain'
    elif 'patra-ingest-app' in pkg_dir:
        return 'app'
    elif 'patra-ingest-adapter' in pkg_dir:
        return 'adapter'
    elif 'patra-ingest-infra' in pkg_dir:
        return 'infra'
    elif 'patra-ingest-api' in pkg_dir:
        return 'api'
    elif 'patra-ingest-boot' in pkg_dir:
        return 'boot'
    return 'unknown'

def get_package_name(pkg_dir):
    """从路径中提取包名"""
    if 'src/main/java/' in pkg_dir:
        pkg = pkg_dir.split('src/main/java/')[1]
        return pkg.replace('/', '.')
    return pkg_dir

def main():
    root = '/Users/linqibin/Desktop/Patra-api/patra-ingest'

    # 找到所有包
    all_packages = find_java_packages(root)

    # 按模块分组
    by_module = defaultdict(list)

    missing_count = 0
    total_count = 0

    for pkg in all_packages:
        total_count += 1
        module = get_module_name(pkg)
        pkg_name = get_package_name(pkg)

        if not has_package_info(pkg):
            missing_count += 1
            by_module[module].append((pkg, pkg_name))

    # 打印统计
    print(f"# 包文档覆盖率分析\n")
    print(f"总包数: {total_count}")
    print(f"缺少 package-info.java: {missing_count}")
    print(f"覆盖率: {(total_count - missing_count) / total_count * 100:.1f}%\n")

    # 按模块打印缺失列表
    print("## 缺失 package-info.java 的包\n")

    for module in ['domain', 'app', 'adapter', 'infra', 'api', 'boot']:
        if module in by_module:
            print(f"### {module} 层 ({len(by_module[module])} 个包)")
            for pkg_path, pkg_name in by_module[module]:
                print(f"- {pkg_name}")
                print(f"  路径: {pkg_path}")
            print()

if __name__ == '__main__':
    main()

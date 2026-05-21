#!/usr/bin/env python3
"""
LetPub 期刊数据爬虫 — 根据本地 ISSN 列表逐条查询 LetPub，解析期刊指标数据。

用法:
    python3 scrape_letpub.py                  # 正常运行（自动断点续传）
    python3 scrape_letpub.py --limit 50       # 只跑 50 条（测试用）
    python3 scrape_letpub.py --delay 2.0      # 调整请求间隔（秒）
    python3 scrape_letpub.py --reset          # 清除进度，从头开始

输入: venues_issn.tsv（同目录下，从 MySQL 导出）
输出: letpub_results.csv（爬取结果）+ progress.json（断点记录）

仅供个人学习使用，请勿商业化或公开部署。
"""

import csv
import json
import os
import re
import sys
import time
import argparse
from pathlib import Path
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup

# ─── 配置 ─────────────────────────────────────────────

SCRIPT_DIR = Path(__file__).parent
INPUT_FILE = SCRIPT_DIR / "venues_issn.tsv"
OUTPUT_FILE = SCRIPT_DIR / "letpub_results.csv"
PROGRESS_FILE = SCRIPT_DIR / "progress.json"

BASE_URL = "https://www.letpub.com.cn"
SEARCH_URL = f"{BASE_URL}/index.php"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}

# 输出 CSV 的列定义
OUTPUT_FIELDS = [
    "venue_id",
    "title",
    "issn_l",
    "letpub_journal_id",
    "letpub_name",
    # 基本信息
    "research_direction",
    "country",
    "language",
    "frequency",
    "start_year",
    "articles_per_year",
    "gold_oa_percent",
    "research_article_percent",
    # JCR 分区（取第一个学科）
    "jcr_subject",
    "jcr_collection",
    "jif_quartile",
    "jif_rank",
    "jci_quartile",
    "jci_rank",
    # 中科院分区（最新版）
    "cas_version",
    "cas_major_category",
    "cas_major_quartile",
    "cas_minor_subject",
    "cas_minor_quartile",
    "cas_top_journal",
    "cas_review_journal",
    # 预警名单
    "warning_list_status",
    # 审稿与录用
    "review_speed_official",
    "review_speed_user",
    "acceptance_rate",
    # 费用
    "apc_info",
    # 收录
    "indexed_in",
    # 爬取状态
    "scrape_status",  # found / not_found / error
]


# ─── 工具函数 ──────────────────────────────────────────

def clean_text(text: str) -> str:
    """清理 HTML 残留和多余空白。"""
    if not text:
        return ""
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def safe_find_text(soup, text_label: str, search_scope=None) -> str:
    """在 soup 中找到包含 text_label 的 td，返回其下一个兄弟 td 的文本。"""
    scope = search_scope or soup
    for td in scope.find_all("td"):
        if text_label in td.get_text():
            next_td = td.find_next_sibling("td")
            if next_td:
                return clean_text(next_td.get_text())
    return ""


# ─── 第一步：通过 ISSN 搜索 LetPub 获取 journalid ──────

def search_journal_id(session: requests.Session, issn: str) -> str | None:
    """通过 ISSN 在 LetPub 搜索，返回 journalid（字符串），未找到返回 None。"""
    params = {
        "page": "journalapp",
        "view": "search",
        "searchname": "",
        "searchissn": issn,
        "searchfield": "",
        "searchimpactlow": "",
        "searchimpacthigh": "",
        "searchsci498telecomkey": "",
        "searchcategory1": "",
        "searchcategory2": "",
        "searchjcrkind": "",
        "searchopenaccess": "",
        "searchsort": "relevance",
    }
    resp = session.get(SEARCH_URL, params=params, headers=HEADERS, verify=False, timeout=30)
    resp.raise_for_status()

    # 从搜索结果页中提取 journalid
    match = re.search(r"journalid=(\d+)", resp.text)
    return match.group(1) if match else None


# ─── 第二步：解析期刊详情页 ──────────────────────────────

def parse_detail_page(session: requests.Session, journal_id: str) -> dict:
    """获取并解析 LetPub 期刊详情页，返回提取的字段字典。"""
    url = f"{SEARCH_URL}?journalid={journal_id}&page=journalapp&view=detail"
    resp = session.get(url, headers=HEADERS, verify=False, timeout=30)
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")
    data = {"letpub_journal_id": journal_id}

    # --- 期刊名（h1 标签） ---
    h1 = soup.find("h1")
    if h1:
        # h1 中可能包含收藏按钮等，取第一段纯文本
        data["letpub_name"] = clean_text(h1.get_text().split("期刊收藏夹")[0])

    # --- 基本信息表格区域 ---
    # 这些字段在 "涉及的研究方向" 附近的表格中
    all_text = resp.text

    data["research_direction"] = _extract_field(all_text, "涉及的研究方向")
    data["country"] = _extract_field(all_text, "出版国家或地区")
    data["language"] = _extract_field(all_text, "出版语言")
    data["frequency"] = _extract_field(all_text, "出版周期")
    data["start_year"] = _extract_field(all_text, "出版年份")

    # Gold OA 占比
    oa_match = re.search(r"Gold OA文章占比.*?</td>\s*<td[^>]*>\s*([\d.]+%)", all_text, re.DOTALL)
    data["gold_oa_percent"] = oa_match.group(1) if oa_match else ""

    # 研究类文章占比
    research_match = re.search(r"研究类文章占比.*?([\d.]+%)", all_text, re.DOTALL)
    data["research_article_percent"] = research_match.group(1) if research_match else ""

    # 年文章数
    articles_match = re.search(r"年文章数.*?</td>\s*<td[^>]*>\s*(\d[\d,]*)", all_text, re.DOTALL)
    data["articles_per_year"] = articles_match.group(1).replace(",", "") if articles_match else ""

    # --- JCR 分区（WOS 分区，取第一个学科） ---
    _parse_jcr_partition(all_text, data)

    # --- 中科院分区（取最新版） ---
    _parse_cas_partition(all_text, data)

    # --- 预警名单 ---
    warning_match = re.search(r"(20\d{2}年\d{1,2}月发布的\d{4}版：[^<\n]{2,40})", all_text)
    data["warning_list_status"] = clean_text(warning_match.group(1)) if warning_match else ""

    # --- 审稿速度 ---
    review_section = re.search(
        r"平均审稿速度.*?</td>\s*<td[^>]*>(.*?)</td>",
        all_text, re.DOTALL
    )
    if review_section:
        review_text = clean_text(re.sub(r"<[^>]+>", " ", review_section.group(1)))
        # 拆分官方数据和网友数据
        official_match = re.search(r"期刊官网数据：(.*?)(?:网友|$)", review_text)
        user_match = re.search(r"网友分享经验：\s*(.*?)$", review_text)
        data["review_speed_official"] = clean_text(official_match.group(1)) if official_match else ""
        data["review_speed_user"] = clean_text(user_match.group(1)) if user_match else review_text

    # --- 录用比例 ---
    accept_match = re.search(r"平均录用比例.*?网友分享经验：\s*([\d.]+%)", all_text, re.DOTALL)
    data["acceptance_rate"] = accept_match.group(1) if accept_match else ""

    # --- APC ---
    apc_match = re.search(
        r"APC文章处理费信息.*?</td>\s*<td[^>]*>(.*?)</td>",
        all_text, re.DOTALL
    )
    if apc_match:
        apc_text = clean_text(re.sub(r"<[^>]+>", " ", apc_match.group(1)))
        data["apc_info"] = apc_text[:200]  # 截断过长内容

    # --- 收录数据库 ---
    indexed_match = re.search(
        r"Science Citation Index|SCIE|Scopus|PubMed",
        all_text
    )
    # 更精确地提取收录信息
    sci_section = re.search(
        r"SCI期刊收录.*?</td>\s*<td[^>]*>(.*?)</td>",
        all_text, re.DOTALL
    )
    if sci_section:
        indexed_text = clean_text(re.sub(r"<[^>]+>", " ", sci_section.group(1)))
        data["indexed_in"] = indexed_text[:200]

    return data


def _extract_field(html: str, label: str) -> str:
    """从 HTML 中提取 label 对应的下一个 td 的纯文本值。"""
    pattern = rf"{re.escape(label)}\s*</td>\s*<td[^>]*>(.*?)</td>"
    match = re.search(pattern, html, re.DOTALL)
    if match:
        return clean_text(re.sub(r"<[^>]+>", " ", match.group(1)))
    return ""


def _parse_jcr_partition(html: str, data: dict):
    """解析 WOS JCR 分区信息（取第一个学科的 JIF 分区）。"""
    data.setdefault("jcr_subject", "")
    data.setdefault("jcr_collection", "")
    data.setdefault("jif_quartile", "")
    data.setdefault("jif_rank", "")
    data.setdefault("jci_quartile", "")
    data.setdefault("jci_rank", "")

    # 寻找 "按JIF指标学科分区" 后面的表格行
    jif_pos = html.find("按JIF指标学科分区")
    if jif_pos < 0:
        return

    # 截取 JIF 区域（到 JCI 区域之前）
    jci_pos = html.find("按JCI指标学科分区", jif_pos)
    jif_section = html[jif_pos:jci_pos] if jci_pos > jif_pos else html[jif_pos:jif_pos + 3000]

    # 提取第一个学科行: "学科：XXX" -> SCIE -> Q1 -> 28/204
    rows = re.findall(
        r"学科：(.*?)</td>.*?<td[^>]*>(SCIE|SSCI|ESCI|AHCI)</td>\s*<td[^>]*>(Q[1-4])</td>\s*<td[^>]*>([\d/]+)",
        jif_section, re.DOTALL
    )
    if rows:
        subject, collection, quartile, rank = rows[0]
        data["jcr_subject"] = clean_text(re.sub(r"<[^>]+>", "", subject))
        data["jcr_collection"] = collection
        data["jif_quartile"] = quartile
        data["jif_rank"] = rank

    # JCI 分区（类似结构）
    if jci_pos > 0:
        jci_section = html[jci_pos:jci_pos + 3000]
        jci_rows = re.findall(
            r"学科：(.*?)</td>.*?<td[^>]*>(?:SCIE|SSCI|ESCI|AHCI)</td>\s*<td[^>]*>(Q[1-4])</td>\s*<td[^>]*>([\d/]+)",
            jci_section, re.DOTALL
        )
        if jci_rows:
            data["jci_quartile"] = jci_rows[0][1]
            data["jci_rank"] = jci_rows[0][2]


def _parse_cas_partition(html: str, data: dict):
    """解析中科院分区（取最新版）。"""
    data.setdefault("cas_version", "")
    data.setdefault("cas_major_category", "")
    data.setdefault("cas_major_quartile", "")
    data.setdefault("cas_minor_subject", "")
    data.setdefault("cas_minor_quartile", "")
    data.setdefault("cas_top_journal", "")
    data.setdefault("cas_review_journal", "")

    # 中科院分区标题格式: "中国科学院期刊分区 （ 2025年3月最新升级版）"
    cas_match = re.search(
        r"中国科学院期刊分区\s*（\s*(20\d{2}年\d{1,2}月[^）]*?)）",
        html
    )
    if not cas_match:
        return

    data["cas_version"] = clean_text(cas_match.group(1))

    # 分区数据在紧随其后的表格中
    # 格式: "大类学科 小类学科 Top期刊 综述期刊 计算机科学 3区1"
    cas_pos = cas_match.start()

    # 找到分区数据区域 — 通常在"点击查看"之后
    trend_pos = html.find("点击查看中国科学院期刊分区趋势图", cas_pos)
    if trend_pos < 0:
        return

    # 从趋势图链接后截取分区数据表格
    cas_section = html[trend_pos:trend_pos + 2000]

    # 大类+分区: 如 "计算机科学 3区" 或 "医学 1区"
    # 格式在 td 中: "大类学科小类学科Top期刊综述期刊{大类名} {大类分区}区{小类分区}"
    major_match = re.search(
        r"大类学科小类学科Top期刊综述期刊\s*(\S+?)\s+(\d)区",
        clean_text(re.sub(r"<[^>]+>", " ", cas_section))
    )
    if major_match:
        data["cas_major_category"] = major_match.group(1)
        data["cas_major_quartile"] = f"{major_match.group(2)}区"

    # 小类学科 — 紧接在大类之后的行
    # 格式: "COMPUTER SCIENCE, ARTIFICIAL INTELLIGENCE 计算机：人工智能" -> "2区" -> ...
    minor_tds = re.findall(
        r"<td[^>]*>([A-Z][A-Z, &;]+.*?)</td>\s*<td[^>]*>(\d区.*?)</td>",
        cas_section, re.DOTALL
    )
    if minor_tds:
        subject_raw = clean_text(re.sub(r"<[^>]+>", " ", minor_tds[0][0]))
        quartiles_raw = clean_text(re.sub(r"<[^>]+>", " ", minor_tds[0][1]))
        data["cas_minor_subject"] = subject_raw
        # 第一个 "X区" 就是小类分区
        q_match = re.search(r"(\d区)", quartiles_raw)
        data["cas_minor_quartile"] = q_match.group(1) if q_match else quartiles_raw

    # Top 期刊 / 综述期刊 — 在分区表后面的 "是/否"
    top_matches = re.findall(r"<td[^>]*>\s*(是|否)\s*</td>", cas_section)
    if len(top_matches) >= 2:
        data["cas_top_journal"] = top_matches[0]
        data["cas_review_journal"] = top_matches[1]
    elif len(top_matches) == 1:
        data["cas_top_journal"] = top_matches[0]


# ─── 主流程 ───────────────────────────────────────────

def load_input() -> list[dict]:
    """从 TSV 文件加载期刊列表。"""
    venues = []
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            venues.append(row)
    print(f"[INFO] 加载了 {len(venues)} 个期刊")
    return venues


def load_progress() -> set:
    """加载已完成的 venue_id 集合。"""
    if PROGRESS_FILE.exists():
        with open(PROGRESS_FILE, "r") as f:
            data = json.load(f)
            return set(data.get("completed", []))
    return set()


def save_progress(completed: set):
    """保存进度。"""
    with open(PROGRESS_FILE, "w") as f:
        json.dump({"completed": list(completed)}, f)


def init_csv():
    """初始化输出 CSV（如果不存在则写入表头）。"""
    if not OUTPUT_FILE.exists():
        with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=OUTPUT_FIELDS)
            writer.writeheader()


def append_result(row: dict):
    """追加一行结果到 CSV。"""
    with open(OUTPUT_FILE, "a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=OUTPUT_FIELDS)
        writer.writerow({k: row.get(k, "") for k in OUTPUT_FIELDS})


def main():
    parser = argparse.ArgumentParser(description="LetPub 期刊数据爬虫")
    parser.add_argument("--limit", type=int, default=0, help="限制爬取数量（0=不限制）")
    parser.add_argument("--delay", type=float, default=1.5, help="请求间隔秒数（默认 1.5）")
    parser.add_argument("--reset", action="store_true", help="清除进度，从头开始")
    args = parser.parse_args()

    if args.reset:
        PROGRESS_FILE.unlink(missing_ok=True)
        OUTPUT_FILE.unlink(missing_ok=True)
        print("[INFO] 已清除进度和输出文件")

    venues = load_input()
    completed = load_progress()
    init_csv()

    # 过滤已完成的
    pending = [v for v in venues if v["venue_id"] not in completed]
    if args.limit > 0:
        pending = pending[:args.limit]

    print(f"[INFO] 已完成: {len(completed)}, 待处理: {len(pending)}")
    if not pending:
        print("[INFO] 全部完成!")
        return

    # 禁用 SSL 警告（LetPub 证书问题）
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    session = requests.Session()
    found_count = 0
    not_found_count = 0
    error_count = 0

    for i, venue in enumerate(pending):
        venue_id = venue["venue_id"]
        title = venue["title"]
        issn = venue["issn_l"]

        progress_pct = (len(completed) + i + 1) / len(venues) * 100
        print(f"[{len(completed) + i + 1}/{len(venues)}] ({progress_pct:.1f}%) {title} (ISSN: {issn})", end=" ... ")

        row = {
            "venue_id": venue_id,
            "title": title,
            "issn_l": issn,
            "scrape_status": "error",
        }

        try:
            # 第一步：搜索 journalid
            journal_id = search_journal_id(session, issn)

            if not journal_id:
                row["scrape_status"] = "not_found"
                not_found_count += 1
                print("未找到")
            else:
                # 第二步：解析详情页
                time.sleep(0.5)  # 搜索和详情之间的间隔
                detail = parse_detail_page(session, journal_id)
                row.update(detail)
                row["scrape_status"] = "found"
                found_count += 1
                cas_q = row.get("cas_major_quartile", "-")
                jif_q = row.get("jif_quartile", "-")
                print(f"找到 (JCR: {jif_q}, 中科院: {cas_q})")

        except requests.RequestException as e:
            error_count += 1
            print(f"网络错误: {e}")
        except Exception as e:
            error_count += 1
            print(f"解析错误: {e}")

        # 写入结果并保存进度
        append_result(row)
        completed.add(venue_id)

        # 每 50 条保存一次进度
        if (i + 1) % 50 == 0:
            save_progress(completed)
            print(f"  [进度已保存] 找到: {found_count}, 未找到: {not_found_count}, 错误: {error_count}")

        # 请求间隔
        if i < len(pending) - 1:
            time.sleep(args.delay)

    # 最终保存
    save_progress(completed)
    print(f"\n{'=' * 60}")
    print(f"爬取完成!")
    print(f"  找到: {found_count}")
    print(f"  未找到: {not_found_count}")
    print(f"  错误: {error_count}")
    print(f"  结果保存在: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()

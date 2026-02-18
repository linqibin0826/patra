/* ====================================================================
 * 迁移: V1.4.2 - 语言中文名称种子数据
 * ====================================================================
 * 目的: 为 50 个 BCP 47 语言添加中文名称别名
 *
 * 数据结构:
 *   - sys_dict_item_alias: NAME_ZH 标准下的中文语言名称别名
 *
 * ID 规划:
 *   - sys_dict_item_alias (NAME_ZH): 800000000000007xxx (001-050)
 *
 * 数据来源:
 *   - CLDR (Unicode Common Locale Data Repository)
 *   - 参照 V1.3.0 的 50 个语言字典项，按 item_id 顺序映射
 * ==================================================================== */


INSERT INTO patra_registry.sys_dict_item_alias
(id, item_id, source_standard, external_code, external_label)

SELECT * FROM (
  -- 001 en English → 英语
  SELECT 800000000000007001 AS id, 800000000000003001 AS item_id, 'name_zh' AS source_standard, '英语' AS external_code, NULL AS external_label UNION ALL
  -- 002 zh Chinese → 中文
  SELECT 800000000000007002, 800000000000003002, 'name_zh', '中文', NULL UNION ALL
  -- 003 es Spanish → 西班牙语
  SELECT 800000000000007003, 800000000000003003, 'name_zh', '西班牙语', NULL UNION ALL
  -- 004 fr French → 法语
  SELECT 800000000000007004, 800000000000003004, 'name_zh', '法语', NULL UNION ALL
  -- 005 de German → 德语
  SELECT 800000000000007005, 800000000000003005, 'name_zh', '德语', NULL UNION ALL
  -- 006 ja Japanese → 日语
  SELECT 800000000000007006, 800000000000003006, 'name_zh', '日语', NULL UNION ALL
  -- 007 pt Portuguese → 葡萄牙语
  SELECT 800000000000007007, 800000000000003007, 'name_zh', '葡萄牙语', NULL UNION ALL
  -- 008 ru Russian → 俄语
  SELECT 800000000000007008, 800000000000003008, 'name_zh', '俄语', NULL UNION ALL
  -- 009 ar Arabic → 阿拉伯语
  SELECT 800000000000007009, 800000000000003009, 'name_zh', '阿拉伯语', NULL UNION ALL
  -- 010 ko Korean → 韩语
  SELECT 800000000000007010, 800000000000003010, 'name_zh', '韩语', NULL UNION ALL
  -- 011 it Italian → 意大利语
  SELECT 800000000000007011, 800000000000003011, 'name_zh', '意大利语', NULL UNION ALL
  -- 012 nl Dutch → 荷兰语
  SELECT 800000000000007012, 800000000000003012, 'name_zh', '荷兰语', NULL UNION ALL
  -- 013 pl Polish → 波兰语
  SELECT 800000000000007013, 800000000000003013, 'name_zh', '波兰语', NULL UNION ALL
  -- 014 tr Turkish → 土耳其语
  SELECT 800000000000007014, 800000000000003014, 'name_zh', '土耳其语', NULL UNION ALL
  -- 015 sv Swedish → 瑞典语
  SELECT 800000000000007015, 800000000000003015, 'name_zh', '瑞典语', NULL UNION ALL
  -- 016 da Danish → 丹麦语
  SELECT 800000000000007016, 800000000000003016, 'name_zh', '丹麦语', NULL UNION ALL
  -- 017 no Norwegian → 挪威语
  SELECT 800000000000007017, 800000000000003017, 'name_zh', '挪威语', NULL UNION ALL
  -- 018 fi Finnish → 芬兰语
  SELECT 800000000000007018, 800000000000003018, 'name_zh', '芬兰语', NULL UNION ALL
  -- 019 cs Czech → 捷克语
  SELECT 800000000000007019, 800000000000003019, 'name_zh', '捷克语', NULL UNION ALL
  -- 020 hu Hungarian → 匈牙利语
  SELECT 800000000000007020, 800000000000003020, 'name_zh', '匈牙利语', NULL UNION ALL
  -- 021 el Greek → 希腊语
  SELECT 800000000000007021, 800000000000003021, 'name_zh', '希腊语', NULL UNION ALL
  -- 022 he Hebrew → 希伯来语
  SELECT 800000000000007022, 800000000000003022, 'name_zh', '希伯来语', NULL UNION ALL
  -- 023 th Thai → 泰语
  SELECT 800000000000007023, 800000000000003023, 'name_zh', '泰语', NULL UNION ALL
  -- 024 vi Vietnamese → 越南语
  SELECT 800000000000007024, 800000000000003024, 'name_zh', '越南语', NULL UNION ALL
  -- 025 id Indonesian → 印度尼西亚语
  SELECT 800000000000007025, 800000000000003025, 'name_zh', '印度尼西亚语', NULL UNION ALL
  -- 026 ms Malay → 马来语
  SELECT 800000000000007026, 800000000000003026, 'name_zh', '马来语', NULL UNION ALL
  -- 027 hi Hindi → 印地语
  SELECT 800000000000007027, 800000000000003027, 'name_zh', '印地语', NULL UNION ALL
  -- 028 bn Bengali → 孟加拉语
  SELECT 800000000000007028, 800000000000003028, 'name_zh', '孟加拉语', NULL UNION ALL
  -- 029 ta Tamil → 泰米尔语
  SELECT 800000000000007029, 800000000000003029, 'name_zh', '泰米尔语', NULL UNION ALL
  -- 030 te Telugu → 泰卢固语
  SELECT 800000000000007030, 800000000000003030, 'name_zh', '泰卢固语', NULL UNION ALL
  -- 031 mr Marathi → 马拉地语
  SELECT 800000000000007031, 800000000000003031, 'name_zh', '马拉地语', NULL UNION ALL
  -- 032 gu Gujarati → 古吉拉特语
  SELECT 800000000000007032, 800000000000003032, 'name_zh', '古吉拉特语', NULL UNION ALL
  -- 033 kn Kannada → 卡纳达语
  SELECT 800000000000007033, 800000000000003033, 'name_zh', '卡纳达语', NULL UNION ALL
  -- 034 ml Malayalam → 马拉雅拉姆语
  SELECT 800000000000007034, 800000000000003034, 'name_zh', '马拉雅拉姆语', NULL UNION ALL
  -- 035 pa Punjabi → 旁遮普语
  SELECT 800000000000007035, 800000000000003035, 'name_zh', '旁遮普语', NULL UNION ALL
  -- 036 ur Urdu → 乌尔都语
  SELECT 800000000000007036, 800000000000003036, 'name_zh', '乌尔都语', NULL UNION ALL
  -- 037 fa Persian → 波斯语
  SELECT 800000000000007037, 800000000000003037, 'name_zh', '波斯语', NULL UNION ALL
  -- 038 uk Ukrainian → 乌克兰语
  SELECT 800000000000007038, 800000000000003038, 'name_zh', '乌克兰语', NULL UNION ALL
  -- 039 ro Romanian → 罗马尼亚语
  SELECT 800000000000007039, 800000000000003039, 'name_zh', '罗马尼亚语', NULL UNION ALL
  -- 040 bg Bulgarian → 保加利亚语
  SELECT 800000000000007040, 800000000000003040, 'name_zh', '保加利亚语', NULL UNION ALL
  -- 041 hr Croatian → 克罗地亚语
  SELECT 800000000000007041, 800000000000003041, 'name_zh', '克罗地亚语', NULL UNION ALL
  -- 042 sr Serbian → 塞尔维亚语
  SELECT 800000000000007042, 800000000000003042, 'name_zh', '塞尔维亚语', NULL UNION ALL
  -- 043 sk Slovak → 斯洛伐克语
  SELECT 800000000000007043, 800000000000003043, 'name_zh', '斯洛伐克语', NULL UNION ALL
  -- 044 sl Slovenian → 斯洛文尼亚语
  SELECT 800000000000007044, 800000000000003044, 'name_zh', '斯洛文尼亚语', NULL UNION ALL
  -- 045 lt Lithuanian → 立陶宛语
  SELECT 800000000000007045, 800000000000003045, 'name_zh', '立陶宛语', NULL UNION ALL
  -- 046 lv Latvian → 拉脱维亚语
  SELECT 800000000000007046, 800000000000003046, 'name_zh', '拉脱维亚语', NULL UNION ALL
  -- 047 et Estonian → 爱沙尼亚语
  SELECT 800000000000007047, 800000000000003047, 'name_zh', '爱沙尼亚语', NULL UNION ALL
  -- 048 ca Catalan → 加泰罗尼亚语
  SELECT 800000000000007048, 800000000000003048, 'name_zh', '加泰罗尼亚语', NULL UNION ALL
  -- 049 af Afrikaans → 南非荷兰语
  SELECT 800000000000007049, 800000000000003049, 'name_zh', '南非荷兰语', NULL UNION ALL
  -- 050 la Latin → 拉丁语
  SELECT 800000000000007050, 800000000000003050, 'name_zh', '拉丁语', NULL
) AS tmp
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_dict_item_alias
  WHERE source_standard = 'name_zh' AND item_id IN (
    SELECT id FROM patra_registry.sys_dict_item WHERE type_id = 800000000000000002
  )
);


/* ====================================================================
 * V1.4.2 语言中文名称种子数据完成
 * ====================================================================
 * 统计:
 *   - 50 个 NAME_ZH 别名（覆盖所有 V1.3.0 语言）
 * ==================================================================== */

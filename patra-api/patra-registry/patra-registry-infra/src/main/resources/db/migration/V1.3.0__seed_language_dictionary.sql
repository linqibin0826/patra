/* ====================================================================
 * 迁移: V1.3.0 - 语言字典种子数据
 * ====================================================================
 * 目的: 初始化 BCP 47 语言代码字典，支持 ISO 639-3 到 BCP 47 的转换
 *
 * 数据结构:
 *   - sys_dict_type: language 字典类型
 *   - sys_reference_standard: BCP_47（规范标准）, ISO_639_3（外部标准）, NAME_EN（别名标准）
 *   - sys_dict_item: 50 个常用语言（item_code = BCP 47 两字母代码，如 en, zh, ja）
 *   - sys_dict_item_alias: ISO_639_3 和 NAME_EN 标准下的别名映射
 *
 * 转换示例:
 *   - PubMed ISO 639-3 "eng" → BCP 47 "en"
 *   - PubMed ISO 639-3 "chi"/"zho" → BCP 47 "zh"
 *   - 英文名称 "English" → BCP 47 "en"
 *
 * ID 规划:
 *   - sys_dict_type (language): 800000000000000002
 *   - sys_reference_standard: 900000000000000010-012
 *   - sys_dict_item (语言): 800000000000003xxx (001-050)
 *   - sys_dict_item_alias (ISO_639_3): 800000000000004xxx (001-070)
 *   - sys_dict_item_alias (NAME_EN): 800000000000005xxx (001-050)
 *
 * 数据来源:
 *   - ISO 639-1: https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
 *   - ISO 639-3: https://iso639-3.sil.org/code_tables/639/data
 *   - BCP 47: https://www.ietf.org/rfc/bcp/bcp47.txt
 * ==================================================================== */


/* ====================================================================
 * 第1部分: 创建 language 字典类型
 * ==================================================================== */

INSERT INTO patra_registry.sys_dict_type
(id, type_code, type_name, description, allow_custom_items, is_system,
 created_at, updated_at, version)
SELECT
  800000000000000002,
  'language',
  '语言',
  'BCP 47 语言标签标准，item_code 为 ISO 639-1 两字母代码（小写如 en, zh, ja）',
  0,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_dict_type
  WHERE type_code = 'language' AND deleted_at IS NULL
);


/* ====================================================================
 * 第2部分: 创建来源标准记录
 * ==================================================================== */

-- BCP_47（规范标准）
INSERT INTO patra_registry.sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
SELECT
  900000000000000010,
  'language',
  'BCP_47',
  'BCP 47 语言标签',
  '平台规范标准，item_code 遵循此格式（ISO 639-1 两字母代码，小写）',
  1,
  1,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_reference_standard
  WHERE dict_type_code = 'language' AND standard_code = 'BCP_47'
);

-- ISO_639_3（外部标准 - PubMed 使用）
INSERT INTO patra_registry.sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
SELECT
  900000000000000011,
  'language',
  'ISO_639_3',
  'ISO 639-3 三字母代码',
  'PubMed LSIOU 使用的语言编码标准（如 eng, chi, jpn），需转换为 BCP 47',
  2,
  0,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_reference_standard
  WHERE dict_type_code = 'language' AND standard_code = 'ISO_639_3'
);

-- NAME_EN（外部标准 - 英文名称）
INSERT INTO patra_registry.sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
SELECT
  900000000000000012,
  'language',
  'NAME_EN',
  '英文名称',
  '语言的英文全名（如 English, Chinese, Japanese）',
  3,
  0,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_reference_standard
  WHERE dict_type_code = 'language' AND standard_code = 'NAME_EN'
);


/* ====================================================================
 * 第3部分: 插入字典项（50 个常用语言）
 * item_code = BCP 47 / ISO 639-1 两字母代码（小写）
 * item_name = 英文名称
 * ==================================================================== */

INSERT INTO patra_registry.sys_dict_item
(id, type_id, item_code, item_name, display_order, is_default, enabled,
 created_at, updated_at, version)
SELECT * FROM (
  SELECT 800000000000003001 AS id, 800000000000000002 AS type_id, 'en' AS item_code, 'English' AS item_name, 1 AS display_order, 1 AS is_default, 1 AS enabled, NOW(6) AS created_at, NOW(6) AS updated_at, 0 AS version UNION ALL
  SELECT 800000000000003002, 800000000000000002, 'zh', 'Chinese', 2, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003003, 800000000000000002, 'es', 'Spanish', 3, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003004, 800000000000000002, 'fr', 'French', 4, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003005, 800000000000000002, 'de', 'German', 5, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003006, 800000000000000002, 'ja', 'Japanese', 6, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003007, 800000000000000002, 'pt', 'Portuguese', 7, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003008, 800000000000000002, 'ru', 'Russian', 8, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003009, 800000000000000002, 'ar', 'Arabic', 9, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003010, 800000000000000002, 'ko', 'Korean', 10, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003011, 800000000000000002, 'it', 'Italian', 11, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003012, 800000000000000002, 'nl', 'Dutch', 12, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003013, 800000000000000002, 'pl', 'Polish', 13, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003014, 800000000000000002, 'tr', 'Turkish', 14, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003015, 800000000000000002, 'sv', 'Swedish', 15, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003016, 800000000000000002, 'da', 'Danish', 16, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003017, 800000000000000002, 'no', 'Norwegian', 17, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003018, 800000000000000002, 'fi', 'Finnish', 18, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003019, 800000000000000002, 'cs', 'Czech', 19, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003020, 800000000000000002, 'hu', 'Hungarian', 20, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003021, 800000000000000002, 'el', 'Greek', 21, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003022, 800000000000000002, 'he', 'Hebrew', 22, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003023, 800000000000000002, 'th', 'Thai', 23, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003024, 800000000000000002, 'vi', 'Vietnamese', 24, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003025, 800000000000000002, 'id', 'Indonesian', 25, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003026, 800000000000000002, 'ms', 'Malay', 26, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003027, 800000000000000002, 'hi', 'Hindi', 27, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003028, 800000000000000002, 'bn', 'Bengali', 28, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003029, 800000000000000002, 'ta', 'Tamil', 29, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003030, 800000000000000002, 'te', 'Telugu', 30, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003031, 800000000000000002, 'mr', 'Marathi', 31, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003032, 800000000000000002, 'gu', 'Gujarati', 32, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003033, 800000000000000002, 'kn', 'Kannada', 33, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003034, 800000000000000002, 'ml', 'Malayalam', 34, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003035, 800000000000000002, 'pa', 'Punjabi', 35, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003036, 800000000000000002, 'ur', 'Urdu', 36, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003037, 800000000000000002, 'fa', 'Persian', 37, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003038, 800000000000000002, 'uk', 'Ukrainian', 38, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003039, 800000000000000002, 'ro', 'Romanian', 39, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003040, 800000000000000002, 'bg', 'Bulgarian', 40, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003041, 800000000000000002, 'hr', 'Croatian', 41, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003042, 800000000000000002, 'sr', 'Serbian', 42, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003043, 800000000000000002, 'sk', 'Slovak', 43, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003044, 800000000000000002, 'sl', 'Slovenian', 44, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003045, 800000000000000002, 'lt', 'Lithuanian', 45, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003046, 800000000000000002, 'lv', 'Latvian', 46, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003047, 800000000000000002, 'et', 'Estonian', 47, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003048, 800000000000000002, 'ca', 'Catalan', 48, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003049, 800000000000000002, 'af', 'Afrikaans', 49, 0, 1, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000003050, 800000000000000002, 'la', 'Latin', 50, 0, 1, NOW(6), NOW(6), 0
) AS tmp
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_dict_item
  WHERE type_id = 800000000000000002 AND deleted_at IS NULL
);


/* ====================================================================
 * 第4部分: 插入 ISO_639_3 标准别名
 * 映射 PubMed 使用的 ISO 639-3 三字母代码到 BCP 47 两字母代码
 * 注意：部分语言有两个 ISO 639-3 代码（如 chi/zho, fre/fra, ger/deu）
 * ==================================================================== */

INSERT INTO patra_registry.sys_dict_item_alias
(id, item_id, source_standard, external_code, external_label,
 created_at, updated_at, version)
SELECT * FROM (
  -- 英语: eng → en
  SELECT 800000000000004001 AS id, 800000000000003001 AS item_id, 'iso_639_3' AS source_standard, 'eng' AS external_code, 'English' AS external_label, NOW(6) AS created_at, NOW(6) AS updated_at, 0 AS version UNION ALL
  -- 中文: chi/zho → zh（两个代码都映射到同一个 BCP 47）
  SELECT 800000000000004002, 800000000000003002, 'iso_639_3', 'chi', 'Chinese', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004003, 800000000000003002, 'iso_639_3', 'zho', 'Chinese', NOW(6), NOW(6), 0 UNION ALL
  -- 西班牙语: spa → es
  SELECT 800000000000004004, 800000000000003003, 'iso_639_3', 'spa', 'Spanish', NOW(6), NOW(6), 0 UNION ALL
  -- 法语: fre/fra → fr
  SELECT 800000000000004005, 800000000000003004, 'iso_639_3', 'fre', 'French', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004006, 800000000000003004, 'iso_639_3', 'fra', 'French', NOW(6), NOW(6), 0 UNION ALL
  -- 德语: ger/deu → de
  SELECT 800000000000004007, 800000000000003005, 'iso_639_3', 'ger', 'German', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004008, 800000000000003005, 'iso_639_3', 'deu', 'German', NOW(6), NOW(6), 0 UNION ALL
  -- 日语: jpn → ja
  SELECT 800000000000004009, 800000000000003006, 'iso_639_3', 'jpn', 'Japanese', NOW(6), NOW(6), 0 UNION ALL
  -- 葡萄牙语: por → pt
  SELECT 800000000000004010, 800000000000003007, 'iso_639_3', 'por', 'Portuguese', NOW(6), NOW(6), 0 UNION ALL
  -- 俄语: rus → ru
  SELECT 800000000000004011, 800000000000003008, 'iso_639_3', 'rus', 'Russian', NOW(6), NOW(6), 0 UNION ALL
  -- 阿拉伯语: ara → ar
  SELECT 800000000000004012, 800000000000003009, 'iso_639_3', 'ara', 'Arabic', NOW(6), NOW(6), 0 UNION ALL
  -- 韩语: kor → ko
  SELECT 800000000000004013, 800000000000003010, 'iso_639_3', 'kor', 'Korean', NOW(6), NOW(6), 0 UNION ALL
  -- 意大利语: ita → it
  SELECT 800000000000004014, 800000000000003011, 'iso_639_3', 'ita', 'Italian', NOW(6), NOW(6), 0 UNION ALL
  -- 荷兰语: nld/dut → nl
  SELECT 800000000000004015, 800000000000003012, 'iso_639_3', 'nld', 'Dutch', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004016, 800000000000003012, 'iso_639_3', 'dut', 'Dutch', NOW(6), NOW(6), 0 UNION ALL
  -- 波兰语: pol → pl
  SELECT 800000000000004017, 800000000000003013, 'iso_639_3', 'pol', 'Polish', NOW(6), NOW(6), 0 UNION ALL
  -- 土耳其语: tur → tr
  SELECT 800000000000004018, 800000000000003014, 'iso_639_3', 'tur', 'Turkish', NOW(6), NOW(6), 0 UNION ALL
  -- 瑞典语: swe → sv
  SELECT 800000000000004019, 800000000000003015, 'iso_639_3', 'swe', 'Swedish', NOW(6), NOW(6), 0 UNION ALL
  -- 丹麦语: dan → da
  SELECT 800000000000004020, 800000000000003016, 'iso_639_3', 'dan', 'Danish', NOW(6), NOW(6), 0 UNION ALL
  -- 挪威语: nor → no
  SELECT 800000000000004021, 800000000000003017, 'iso_639_3', 'nor', 'Norwegian', NOW(6), NOW(6), 0 UNION ALL
  -- 芬兰语: fin → fi
  SELECT 800000000000004022, 800000000000003018, 'iso_639_3', 'fin', 'Finnish', NOW(6), NOW(6), 0 UNION ALL
  -- 捷克语: ces/cze → cs
  SELECT 800000000000004023, 800000000000003019, 'iso_639_3', 'ces', 'Czech', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004024, 800000000000003019, 'iso_639_3', 'cze', 'Czech', NOW(6), NOW(6), 0 UNION ALL
  -- 匈牙利语: hun → hu
  SELECT 800000000000004025, 800000000000003020, 'iso_639_3', 'hun', 'Hungarian', NOW(6), NOW(6), 0 UNION ALL
  -- 希腊语: ell/gre → el
  SELECT 800000000000004026, 800000000000003021, 'iso_639_3', 'ell', 'Greek', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004027, 800000000000003021, 'iso_639_3', 'gre', 'Greek', NOW(6), NOW(6), 0 UNION ALL
  -- 希伯来语: heb → he
  SELECT 800000000000004028, 800000000000003022, 'iso_639_3', 'heb', 'Hebrew', NOW(6), NOW(6), 0 UNION ALL
  -- 泰语: tha → th
  SELECT 800000000000004029, 800000000000003023, 'iso_639_3', 'tha', 'Thai', NOW(6), NOW(6), 0 UNION ALL
  -- 越南语: vie → vi
  SELECT 800000000000004030, 800000000000003024, 'iso_639_3', 'vie', 'Vietnamese', NOW(6), NOW(6), 0 UNION ALL
  -- 印尼语: ind → id
  SELECT 800000000000004031, 800000000000003025, 'iso_639_3', 'ind', 'Indonesian', NOW(6), NOW(6), 0 UNION ALL
  -- 马来语: msa/may → ms
  SELECT 800000000000004032, 800000000000003026, 'iso_639_3', 'msa', 'Malay', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004033, 800000000000003026, 'iso_639_3', 'may', 'Malay', NOW(6), NOW(6), 0 UNION ALL
  -- 印地语: hin → hi
  SELECT 800000000000004034, 800000000000003027, 'iso_639_3', 'hin', 'Hindi', NOW(6), NOW(6), 0 UNION ALL
  -- 孟加拉语: ben → bn
  SELECT 800000000000004035, 800000000000003028, 'iso_639_3', 'ben', 'Bengali', NOW(6), NOW(6), 0 UNION ALL
  -- 泰米尔语: tam → ta
  SELECT 800000000000004036, 800000000000003029, 'iso_639_3', 'tam', 'Tamil', NOW(6), NOW(6), 0 UNION ALL
  -- 泰卢固语: tel → te
  SELECT 800000000000004037, 800000000000003030, 'iso_639_3', 'tel', 'Telugu', NOW(6), NOW(6), 0 UNION ALL
  -- 马拉地语: mar → mr
  SELECT 800000000000004038, 800000000000003031, 'iso_639_3', 'mar', 'Marathi', NOW(6), NOW(6), 0 UNION ALL
  -- 古吉拉特语: guj → gu
  SELECT 800000000000004039, 800000000000003032, 'iso_639_3', 'guj', 'Gujarati', NOW(6), NOW(6), 0 UNION ALL
  -- 卡纳达语: kan → kn
  SELECT 800000000000004040, 800000000000003033, 'iso_639_3', 'kan', 'Kannada', NOW(6), NOW(6), 0 UNION ALL
  -- 马拉雅拉姆语: mal → ml
  SELECT 800000000000004041, 800000000000003034, 'iso_639_3', 'mal', 'Malayalam', NOW(6), NOW(6), 0 UNION ALL
  -- 旁遮普语: pan → pa
  SELECT 800000000000004042, 800000000000003035, 'iso_639_3', 'pan', 'Punjabi', NOW(6), NOW(6), 0 UNION ALL
  -- 乌尔都语: urd → ur
  SELECT 800000000000004043, 800000000000003036, 'iso_639_3', 'urd', 'Urdu', NOW(6), NOW(6), 0 UNION ALL
  -- 波斯语: fas/per → fa
  SELECT 800000000000004044, 800000000000003037, 'iso_639_3', 'fas', 'Persian', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004045, 800000000000003037, 'iso_639_3', 'per', 'Persian', NOW(6), NOW(6), 0 UNION ALL
  -- 乌克兰语: ukr → uk
  SELECT 800000000000004046, 800000000000003038, 'iso_639_3', 'ukr', 'Ukrainian', NOW(6), NOW(6), 0 UNION ALL
  -- 罗马尼亚语: ron/rum → ro
  SELECT 800000000000004047, 800000000000003039, 'iso_639_3', 'ron', 'Romanian', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004048, 800000000000003039, 'iso_639_3', 'rum', 'Romanian', NOW(6), NOW(6), 0 UNION ALL
  -- 保加利亚语: bul → bg
  SELECT 800000000000004049, 800000000000003040, 'iso_639_3', 'bul', 'Bulgarian', NOW(6), NOW(6), 0 UNION ALL
  -- 克罗地亚语: hrv → hr
  SELECT 800000000000004050, 800000000000003041, 'iso_639_3', 'hrv', 'Croatian', NOW(6), NOW(6), 0 UNION ALL
  -- 塞尔维亚语: srp → sr
  SELECT 800000000000004051, 800000000000003042, 'iso_639_3', 'srp', 'Serbian', NOW(6), NOW(6), 0 UNION ALL
  -- 斯洛伐克语: slk/slo → sk
  SELECT 800000000000004052, 800000000000003043, 'iso_639_3', 'slk', 'Slovak', NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000004053, 800000000000003043, 'iso_639_3', 'slo', 'Slovak', NOW(6), NOW(6), 0 UNION ALL
  -- 斯洛文尼亚语: slv → sl
  SELECT 800000000000004054, 800000000000003044, 'iso_639_3', 'slv', 'Slovenian', NOW(6), NOW(6), 0 UNION ALL
  -- 立陶宛语: lit → lt
  SELECT 800000000000004055, 800000000000003045, 'iso_639_3', 'lit', 'Lithuanian', NOW(6), NOW(6), 0 UNION ALL
  -- 拉脱维亚语: lav → lv
  SELECT 800000000000004056, 800000000000003046, 'iso_639_3', 'lav', 'Latvian', NOW(6), NOW(6), 0 UNION ALL
  -- 爱沙尼亚语: est → et
  SELECT 800000000000004057, 800000000000003047, 'iso_639_3', 'est', 'Estonian', NOW(6), NOW(6), 0 UNION ALL
  -- 加泰罗尼亚语: cat → ca
  SELECT 800000000000004058, 800000000000003048, 'iso_639_3', 'cat', 'Catalan', NOW(6), NOW(6), 0 UNION ALL
  -- 南非荷兰语: afr → af
  SELECT 800000000000004059, 800000000000003049, 'iso_639_3', 'afr', 'Afrikaans', NOW(6), NOW(6), 0 UNION ALL
  -- 拉丁语: lat → la
  SELECT 800000000000004060, 800000000000003050, 'iso_639_3', 'lat', 'Latin', NOW(6), NOW(6), 0
) AS tmp
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_dict_item_alias
  WHERE source_standard = 'iso_639_3' AND item_id IN (
    SELECT id FROM patra_registry.sys_dict_item WHERE type_id = 800000000000000002
  )
);


/* ====================================================================
 * 第5部分: 插入 NAME_EN 标准别名（英文全名）
 * ==================================================================== */

INSERT INTO patra_registry.sys_dict_item_alias
(id, item_id, source_standard, external_code, external_label,
 created_at, updated_at, version)
SELECT * FROM (
  SELECT 800000000000005001 AS id, 800000000000003001 AS item_id, 'name_en' AS source_standard, 'English' AS external_code, NULL AS external_label, NOW(6) AS created_at, NOW(6) AS updated_at, 0 AS version UNION ALL
  SELECT 800000000000005002, 800000000000003002, 'name_en', 'Chinese', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005003, 800000000000003003, 'name_en', 'Spanish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005004, 800000000000003004, 'name_en', 'French', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005005, 800000000000003005, 'name_en', 'German', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005006, 800000000000003006, 'name_en', 'Japanese', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005007, 800000000000003007, 'name_en', 'Portuguese', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005008, 800000000000003008, 'name_en', 'Russian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005009, 800000000000003009, 'name_en', 'Arabic', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005010, 800000000000003010, 'name_en', 'Korean', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005011, 800000000000003011, 'name_en', 'Italian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005012, 800000000000003012, 'name_en', 'Dutch', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005013, 800000000000003013, 'name_en', 'Polish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005014, 800000000000003014, 'name_en', 'Turkish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005015, 800000000000003015, 'name_en', 'Swedish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005016, 800000000000003016, 'name_en', 'Danish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005017, 800000000000003017, 'name_en', 'Norwegian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005018, 800000000000003018, 'name_en', 'Finnish', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005019, 800000000000003019, 'name_en', 'Czech', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005020, 800000000000003020, 'name_en', 'Hungarian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005021, 800000000000003021, 'name_en', 'Greek', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005022, 800000000000003022, 'name_en', 'Hebrew', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005023, 800000000000003023, 'name_en', 'Thai', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005024, 800000000000003024, 'name_en', 'Vietnamese', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005025, 800000000000003025, 'name_en', 'Indonesian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005026, 800000000000003026, 'name_en', 'Malay', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005027, 800000000000003027, 'name_en', 'Hindi', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005028, 800000000000003028, 'name_en', 'Bengali', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005029, 800000000000003029, 'name_en', 'Tamil', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005030, 800000000000003030, 'name_en', 'Telugu', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005031, 800000000000003031, 'name_en', 'Marathi', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005032, 800000000000003032, 'name_en', 'Gujarati', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005033, 800000000000003033, 'name_en', 'Kannada', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005034, 800000000000003034, 'name_en', 'Malayalam', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005035, 800000000000003035, 'name_en', 'Punjabi', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005036, 800000000000003036, 'name_en', 'Urdu', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005037, 800000000000003037, 'name_en', 'Persian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005038, 800000000000003038, 'name_en', 'Ukrainian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005039, 800000000000003039, 'name_en', 'Romanian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005040, 800000000000003040, 'name_en', 'Bulgarian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005041, 800000000000003041, 'name_en', 'Croatian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005042, 800000000000003042, 'name_en', 'Serbian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005043, 800000000000003043, 'name_en', 'Slovak', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005044, 800000000000003044, 'name_en', 'Slovenian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005045, 800000000000003045, 'name_en', 'Lithuanian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005046, 800000000000003046, 'name_en', 'Latvian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005047, 800000000000003047, 'name_en', 'Estonian', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005048, 800000000000003048, 'name_en', 'Catalan', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005049, 800000000000003049, 'name_en', 'Afrikaans', NULL, NOW(6), NOW(6), 0 UNION ALL
  SELECT 800000000000005050, 800000000000003050, 'name_en', 'Latin', NULL, NOW(6), NOW(6), 0
) AS tmp
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_dict_item_alias
  WHERE source_standard = 'name_en' AND item_id IN (
    SELECT id FROM patra_registry.sys_dict_item WHERE type_id = 800000000000000002
  )
);


/* ====================================================================
 * V1.3.0 语言字典种子数据完成
 * ====================================================================
 * 统计:
 *   - 1 个字典类型 (language)
 *   - 3 个来源标准 (BCP_47, ISO_639_3, NAME_EN)
 *   - 50 个字典项 (常用语言)
 *   - 60 个 ISO_639_3 别名 (含多代码映射)
 *   - 50 个 NAME_EN 别名
 * ==================================================================== */

-- language 字典种子：常用语言（50 个条目、4 个参考标准、ISO 639-3 与英文、中文名外部编码）。
-- 可重复执行：id 写死为小整数（雪花 ID 远大于此，不会冲突），按 id 冲突时更新全部业务列；内容改了 Flyway 会重跑。
-- id 段：类型 2；条目 3001～3050；参考标准 10～13；ISO 639-3 4xxx；英文名 5xxx；中文名 7xxx。

INSERT INTO sys_dict_type
(id, type_code, type_name, description, allow_custom_items, is_system)
SELECT
  2,
  'language',
  '语言',
  'BCP 47 语言标签标准，item_code 为 ISO 639-1 两字母代码（小写如 en, zh, ja）',
  false,
  true
ON CONFLICT (id) DO UPDATE SET
    type_code = EXCLUDED.type_code,
    type_name = EXCLUDED.type_name,
    description = EXCLUDED.description,
    allow_custom_items = EXCLUDED.allow_custom_items,
    is_system = EXCLUDED.is_system;


/* ====================================================================
 * 第2部分: 创建来源标准记录
 * ==================================================================== */

-- BCP_47（规范标准）
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
SELECT
  10,
  'language',
  'BCP_47',
  'BCP 47 语言标签',
  '平台规范标准，item_code 遵循此格式（ISO 639-1 两字母代码，小写）',
  1,
  true,
  true
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;

-- ISO_639_3（外部标准 - PubMed 使用）
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
SELECT
  11,
  'language',
  'ISO_639_3',
  'ISO 639-3 三字母代码',
  'PubMed LSIOU 使用的语言编码标准（如 eng, chi, jpn），需转换为 BCP 47',
  2,
  false,
  true
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;

-- NAME_EN（外部标准 - 英文名称）
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
SELECT
  12,
  'language',
  'NAME_EN',
  '英文名称',
  '语言的英文全名（如 English, Chinese, Japanese）',
  3,
  false,
  true
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;


/* ====================================================================
 * 第3部分: 插入字典项（50 个常用语言）
 * item_code = BCP 47 / ISO 639-1 两字母代码（小写）
 * item_name = 英文名称
 * ==================================================================== */

INSERT INTO sys_dict_item
(id, dict_type_id, item_code, item_name, display_order, is_default, enabled)
SELECT * FROM (
  SELECT 3001 AS id, 2 AS dict_type_id, 'en' AS item_code, 'English' AS item_name, 1 AS display_order, true AS is_default, true AS enabled UNION ALL
  SELECT 3002, 2, 'zh', 'Chinese', 2, false, true UNION ALL
  SELECT 3003, 2, 'es', 'Spanish', 3, false, true UNION ALL
  SELECT 3004, 2, 'fr', 'French', 4, false, true UNION ALL
  SELECT 3005, 2, 'de', 'German', 5, false, true UNION ALL
  SELECT 3006, 2, 'ja', 'Japanese', 6, false, true UNION ALL
  SELECT 3007, 2, 'pt', 'Portuguese', 7, false, true UNION ALL
  SELECT 3008, 2, 'ru', 'Russian', 8, false, true UNION ALL
  SELECT 3009, 2, 'ar', 'Arabic', 9, false, true UNION ALL
  SELECT 3010, 2, 'ko', 'Korean', 10, false, true UNION ALL
  SELECT 3011, 2, 'it', 'Italian', 11, false, true UNION ALL
  SELECT 3012, 2, 'nl', 'Dutch', 12, false, true UNION ALL
  SELECT 3013, 2, 'pl', 'Polish', 13, false, true UNION ALL
  SELECT 3014, 2, 'tr', 'Turkish', 14, false, true UNION ALL
  SELECT 3015, 2, 'sv', 'Swedish', 15, false, true UNION ALL
  SELECT 3016, 2, 'da', 'Danish', 16, false, true UNION ALL
  SELECT 3017, 2, 'no', 'Norwegian', 17, false, true UNION ALL
  SELECT 3018, 2, 'fi', 'Finnish', 18, false, true UNION ALL
  SELECT 3019, 2, 'cs', 'Czech', 19, false, true UNION ALL
  SELECT 3020, 2, 'hu', 'Hungarian', 20, false, true UNION ALL
  SELECT 3021, 2, 'el', 'Greek', 21, false, true UNION ALL
  SELECT 3022, 2, 'he', 'Hebrew', 22, false, true UNION ALL
  SELECT 3023, 2, 'th', 'Thai', 23, false, true UNION ALL
  SELECT 3024, 2, 'vi', 'Vietnamese', 24, false, true UNION ALL
  SELECT 3025, 2, 'id', 'Indonesian', 25, false, true UNION ALL
  SELECT 3026, 2, 'ms', 'Malay', 26, false, true UNION ALL
  SELECT 3027, 2, 'hi', 'Hindi', 27, false, true UNION ALL
  SELECT 3028, 2, 'bn', 'Bengali', 28, false, true UNION ALL
  SELECT 3029, 2, 'ta', 'Tamil', 29, false, true UNION ALL
  SELECT 3030, 2, 'te', 'Telugu', 30, false, true UNION ALL
  SELECT 3031, 2, 'mr', 'Marathi', 31, false, true UNION ALL
  SELECT 3032, 2, 'gu', 'Gujarati', 32, false, true UNION ALL
  SELECT 3033, 2, 'kn', 'Kannada', 33, false, true UNION ALL
  SELECT 3034, 2, 'ml', 'Malayalam', 34, false, true UNION ALL
  SELECT 3035, 2, 'pa', 'Punjabi', 35, false, true UNION ALL
  SELECT 3036, 2, 'ur', 'Urdu', 36, false, true UNION ALL
  SELECT 3037, 2, 'fa', 'Persian', 37, false, true UNION ALL
  SELECT 3038, 2, 'uk', 'Ukrainian', 38, false, true UNION ALL
  SELECT 3039, 2, 'ro', 'Romanian', 39, false, true UNION ALL
  SELECT 3040, 2, 'bg', 'Bulgarian', 40, false, true UNION ALL
  SELECT 3041, 2, 'hr', 'Croatian', 41, false, true UNION ALL
  SELECT 3042, 2, 'sr', 'Serbian', 42, false, true UNION ALL
  SELECT 3043, 2, 'sk', 'Slovak', 43, false, true UNION ALL
  SELECT 3044, 2, 'sl', 'Slovenian', 44, false, true UNION ALL
  SELECT 3045, 2, 'lt', 'Lithuanian', 45, false, true UNION ALL
  SELECT 3046, 2, 'lv', 'Latvian', 46, false, true UNION ALL
  SELECT 3047, 2, 'et', 'Estonian', 47, false, true UNION ALL
  SELECT 3048, 2, 'ca', 'Catalan', 48, false, true UNION ALL
  SELECT 3049, 2, 'af', 'Afrikaans', 49, false, true UNION ALL
  SELECT 3050, 2, 'la', 'Latin', 50, false, true
) AS tmp
ON CONFLICT (id) DO UPDATE SET
    dict_type_id = EXCLUDED.dict_type_id,
    item_code = EXCLUDED.item_code,
    item_name = EXCLUDED.item_name,
    display_order = EXCLUDED.display_order,
    is_default = EXCLUDED.is_default,
    enabled = EXCLUDED.enabled;


/* ====================================================================
 * 第4部分: 插入 ISO_639_3 标准别名
 * 映射 PubMed 使用的 ISO 639-3 三字母代码到 BCP 47 两字母代码
 * 注意：部分语言有两个 ISO 639-3 代码（如 chi/zho, fre/fra, ger/deu）
 * ==================================================================== */

INSERT INTO sys_dict_item_alias
(id, dict_item_id, source_standard, external_code, external_label)

SELECT * FROM (
  -- 英语: eng → en
  SELECT 4001 AS id, 3001 AS dict_item_id, 'iso_639_3' AS source_standard, 'eng' AS external_code, 'English' AS external_label UNION ALL
  -- 中文: chi/zho → zh（两个代码都映射到同一个 BCP 47）
  SELECT 4002, 3002, 'iso_639_3', 'chi', 'Chinese' UNION ALL
  SELECT 4003, 3002, 'iso_639_3', 'zho', 'Chinese' UNION ALL
  -- 西班牙语: spa → es
  SELECT 4004, 3003, 'iso_639_3', 'spa', 'Spanish' UNION ALL
  -- 法语: fre/fra → fr
  SELECT 4005, 3004, 'iso_639_3', 'fre', 'French' UNION ALL
  SELECT 4006, 3004, 'iso_639_3', 'fra', 'French' UNION ALL
  -- 德语: ger/deu → de
  SELECT 4007, 3005, 'iso_639_3', 'ger', 'German' UNION ALL
  SELECT 4008, 3005, 'iso_639_3', 'deu', 'German' UNION ALL
  -- 日语: jpn → ja
  SELECT 4009, 3006, 'iso_639_3', 'jpn', 'Japanese' UNION ALL
  -- 葡萄牙语: por → pt
  SELECT 4010, 3007, 'iso_639_3', 'por', 'Portuguese' UNION ALL
  -- 俄语: rus → ru
  SELECT 4011, 3008, 'iso_639_3', 'rus', 'Russian' UNION ALL
  -- 阿拉伯语: ara → ar
  SELECT 4012, 3009, 'iso_639_3', 'ara', 'Arabic' UNION ALL
  -- 韩语: kor → ko
  SELECT 4013, 3010, 'iso_639_3', 'kor', 'Korean' UNION ALL
  -- 意大利语: ita → it
  SELECT 4014, 3011, 'iso_639_3', 'ita', 'Italian' UNION ALL
  -- 荷兰语: nld/dut → nl
  SELECT 4015, 3012, 'iso_639_3', 'nld', 'Dutch' UNION ALL
  SELECT 4016, 3012, 'iso_639_3', 'dut', 'Dutch' UNION ALL
  -- 波兰语: pol → pl
  SELECT 4017, 3013, 'iso_639_3', 'pol', 'Polish' UNION ALL
  -- 土耳其语: tur → tr
  SELECT 4018, 3014, 'iso_639_3', 'tur', 'Turkish' UNION ALL
  -- 瑞典语: swe → sv
  SELECT 4019, 3015, 'iso_639_3', 'swe', 'Swedish' UNION ALL
  -- 丹麦语: dan → da
  SELECT 4020, 3016, 'iso_639_3', 'dan', 'Danish' UNION ALL
  -- 挪威语: nor → no
  SELECT 4021, 3017, 'iso_639_3', 'nor', 'Norwegian' UNION ALL
  -- 芬兰语: fin → fi
  SELECT 4022, 3018, 'iso_639_3', 'fin', 'Finnish' UNION ALL
  -- 捷克语: ces/cze → cs
  SELECT 4023, 3019, 'iso_639_3', 'ces', 'Czech' UNION ALL
  SELECT 4024, 3019, 'iso_639_3', 'cze', 'Czech' UNION ALL
  -- 匈牙利语: hun → hu
  SELECT 4025, 3020, 'iso_639_3', 'hun', 'Hungarian' UNION ALL
  -- 希腊语: ell/gre → el
  SELECT 4026, 3021, 'iso_639_3', 'ell', 'Greek' UNION ALL
  SELECT 4027, 3021, 'iso_639_3', 'gre', 'Greek' UNION ALL
  -- 希伯来语: heb → he
  SELECT 4028, 3022, 'iso_639_3', 'heb', 'Hebrew' UNION ALL
  -- 泰语: tha → th
  SELECT 4029, 3023, 'iso_639_3', 'tha', 'Thai' UNION ALL
  -- 越南语: vie → vi
  SELECT 4030, 3024, 'iso_639_3', 'vie', 'Vietnamese' UNION ALL
  -- 印尼语: ind → id
  SELECT 4031, 3025, 'iso_639_3', 'ind', 'Indonesian' UNION ALL
  -- 马来语: msa/may → ms
  SELECT 4032, 3026, 'iso_639_3', 'msa', 'Malay' UNION ALL
  SELECT 4033, 3026, 'iso_639_3', 'may', 'Malay' UNION ALL
  -- 印地语: hin → hi
  SELECT 4034, 3027, 'iso_639_3', 'hin', 'Hindi' UNION ALL
  -- 孟加拉语: ben → bn
  SELECT 4035, 3028, 'iso_639_3', 'ben', 'Bengali' UNION ALL
  -- 泰米尔语: tam → ta
  SELECT 4036, 3029, 'iso_639_3', 'tam', 'Tamil' UNION ALL
  -- 泰卢固语: tel → te
  SELECT 4037, 3030, 'iso_639_3', 'tel', 'Telugu' UNION ALL
  -- 马拉地语: mar → mr
  SELECT 4038, 3031, 'iso_639_3', 'mar', 'Marathi' UNION ALL
  -- 古吉拉特语: guj → gu
  SELECT 4039, 3032, 'iso_639_3', 'guj', 'Gujarati' UNION ALL
  -- 卡纳达语: kan → kn
  SELECT 4040, 3033, 'iso_639_3', 'kan', 'Kannada' UNION ALL
  -- 马拉雅拉姆语: mal → ml
  SELECT 4041, 3034, 'iso_639_3', 'mal', 'Malayalam' UNION ALL
  -- 旁遮普语: pan → pa
  SELECT 4042, 3035, 'iso_639_3', 'pan', 'Punjabi' UNION ALL
  -- 乌尔都语: urd → ur
  SELECT 4043, 3036, 'iso_639_3', 'urd', 'Urdu' UNION ALL
  -- 波斯语: fas/per → fa
  SELECT 4044, 3037, 'iso_639_3', 'fas', 'Persian' UNION ALL
  SELECT 4045, 3037, 'iso_639_3', 'per', 'Persian' UNION ALL
  -- 乌克兰语: ukr → uk
  SELECT 4046, 3038, 'iso_639_3', 'ukr', 'Ukrainian' UNION ALL
  -- 罗马尼亚语: ron/rum → ro
  SELECT 4047, 3039, 'iso_639_3', 'ron', 'Romanian' UNION ALL
  SELECT 4048, 3039, 'iso_639_3', 'rum', 'Romanian' UNION ALL
  -- 保加利亚语: bul → bg
  SELECT 4049, 3040, 'iso_639_3', 'bul', 'Bulgarian' UNION ALL
  -- 克罗地亚语: hrv → hr
  SELECT 4050, 3041, 'iso_639_3', 'hrv', 'Croatian' UNION ALL
  -- 塞尔维亚语: srp → sr
  SELECT 4051, 3042, 'iso_639_3', 'srp', 'Serbian' UNION ALL
  -- 斯洛伐克语: slk/slo → sk
  SELECT 4052, 3043, 'iso_639_3', 'slk', 'Slovak' UNION ALL
  SELECT 4053, 3043, 'iso_639_3', 'slo', 'Slovak' UNION ALL
  -- 斯洛文尼亚语: slv → sl
  SELECT 4054, 3044, 'iso_639_3', 'slv', 'Slovenian' UNION ALL
  -- 立陶宛语: lit → lt
  SELECT 4055, 3045, 'iso_639_3', 'lit', 'Lithuanian' UNION ALL
  -- 拉脱维亚语: lav → lv
  SELECT 4056, 3046, 'iso_639_3', 'lav', 'Latvian' UNION ALL
  -- 爱沙尼亚语: est → et
  SELECT 4057, 3047, 'iso_639_3', 'est', 'Estonian' UNION ALL
  -- 加泰罗尼亚语: cat → ca
  SELECT 4058, 3048, 'iso_639_3', 'cat', 'Catalan' UNION ALL
  -- 南非荷兰语: afr → af
  SELECT 4059, 3049, 'iso_639_3', 'afr', 'Afrikaans' UNION ALL
  -- 拉丁语: lat → la
  SELECT 4060, 3050, 'iso_639_3', 'lat', 'Latin'
) AS tmp
ON CONFLICT (id) DO UPDATE SET
    dict_item_id = EXCLUDED.dict_item_id,
    source_standard = EXCLUDED.source_standard,
    external_code = EXCLUDED.external_code,
    external_label = EXCLUDED.external_label;


/* ====================================================================
 * 第5部分: 插入 NAME_EN 标准别名（英文全名）
 * ==================================================================== */

INSERT INTO sys_dict_item_alias
(id, dict_item_id, source_standard, external_code, external_label)

SELECT * FROM (
  SELECT 5001 AS id, 3001 AS dict_item_id, 'name_en' AS source_standard, 'English' AS external_code, NULL AS external_label UNION ALL
  SELECT 5002, 3002, 'name_en', 'Chinese', NULL UNION ALL
  SELECT 5003, 3003, 'name_en', 'Spanish', NULL UNION ALL
  SELECT 5004, 3004, 'name_en', 'French', NULL UNION ALL
  SELECT 5005, 3005, 'name_en', 'German', NULL UNION ALL
  SELECT 5006, 3006, 'name_en', 'Japanese', NULL UNION ALL
  SELECT 5007, 3007, 'name_en', 'Portuguese', NULL UNION ALL
  SELECT 5008, 3008, 'name_en', 'Russian', NULL UNION ALL
  SELECT 5009, 3009, 'name_en', 'Arabic', NULL UNION ALL
  SELECT 5010, 3010, 'name_en', 'Korean', NULL UNION ALL
  SELECT 5011, 3011, 'name_en', 'Italian', NULL UNION ALL
  SELECT 5012, 3012, 'name_en', 'Dutch', NULL UNION ALL
  SELECT 5013, 3013, 'name_en', 'Polish', NULL UNION ALL
  SELECT 5014, 3014, 'name_en', 'Turkish', NULL UNION ALL
  SELECT 5015, 3015, 'name_en', 'Swedish', NULL UNION ALL
  SELECT 5016, 3016, 'name_en', 'Danish', NULL UNION ALL
  SELECT 5017, 3017, 'name_en', 'Norwegian', NULL UNION ALL
  SELECT 5018, 3018, 'name_en', 'Finnish', NULL UNION ALL
  SELECT 5019, 3019, 'name_en', 'Czech', NULL UNION ALL
  SELECT 5020, 3020, 'name_en', 'Hungarian', NULL UNION ALL
  SELECT 5021, 3021, 'name_en', 'Greek', NULL UNION ALL
  SELECT 5022, 3022, 'name_en', 'Hebrew', NULL UNION ALL
  SELECT 5023, 3023, 'name_en', 'Thai', NULL UNION ALL
  SELECT 5024, 3024, 'name_en', 'Vietnamese', NULL UNION ALL
  SELECT 5025, 3025, 'name_en', 'Indonesian', NULL UNION ALL
  SELECT 5026, 3026, 'name_en', 'Malay', NULL UNION ALL
  SELECT 5027, 3027, 'name_en', 'Hindi', NULL UNION ALL
  SELECT 5028, 3028, 'name_en', 'Bengali', NULL UNION ALL
  SELECT 5029, 3029, 'name_en', 'Tamil', NULL UNION ALL
  SELECT 5030, 3030, 'name_en', 'Telugu', NULL UNION ALL
  SELECT 5031, 3031, 'name_en', 'Marathi', NULL UNION ALL
  SELECT 5032, 3032, 'name_en', 'Gujarati', NULL UNION ALL
  SELECT 5033, 3033, 'name_en', 'Kannada', NULL UNION ALL
  SELECT 5034, 3034, 'name_en', 'Malayalam', NULL UNION ALL
  SELECT 5035, 3035, 'name_en', 'Punjabi', NULL UNION ALL
  SELECT 5036, 3036, 'name_en', 'Urdu', NULL UNION ALL
  SELECT 5037, 3037, 'name_en', 'Persian', NULL UNION ALL
  SELECT 5038, 3038, 'name_en', 'Ukrainian', NULL UNION ALL
  SELECT 5039, 3039, 'name_en', 'Romanian', NULL UNION ALL
  SELECT 5040, 3040, 'name_en', 'Bulgarian', NULL UNION ALL
  SELECT 5041, 3041, 'name_en', 'Croatian', NULL UNION ALL
  SELECT 5042, 3042, 'name_en', 'Serbian', NULL UNION ALL
  SELECT 5043, 3043, 'name_en', 'Slovak', NULL UNION ALL
  SELECT 5044, 3044, 'name_en', 'Slovenian', NULL UNION ALL
  SELECT 5045, 3045, 'name_en', 'Lithuanian', NULL UNION ALL
  SELECT 5046, 3046, 'name_en', 'Latvian', NULL UNION ALL
  SELECT 5047, 3047, 'name_en', 'Estonian', NULL UNION ALL
  SELECT 5048, 3048, 'name_en', 'Catalan', NULL UNION ALL
  SELECT 5049, 3049, 'name_en', 'Afrikaans', NULL UNION ALL
  SELECT 5050, 3050, 'name_en', 'Latin', NULL
) AS tmp
ON CONFLICT (id) DO UPDATE SET
    dict_item_id = EXCLUDED.dict_item_id,
    source_standard = EXCLUDED.source_standard,
    external_code = EXCLUDED.external_code,
    external_label = EXCLUDED.external_label;


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


/* ====================================================================
 * language NAME_ZH 来源标准（合并自 V1.4.0）
 * ==================================================================== */

-- language NAME_ZH 标准
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
SELECT
  13,
  'language',
  'NAME_ZH',
  '中文名称',
  '中文语言名称（CLDR 简体中文数据），用于前端本地化展示',
  4,
  false,
  true
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;


/* ====================================================================
 * language NAME_ZH 别名（合并自 V1.4.2，50 条）
 * ==================================================================== */

INSERT INTO sys_dict_item_alias
(id, dict_item_id, source_standard, external_code, external_label)

SELECT * FROM (
  -- 001 en English → 英语
  SELECT 7001 AS id, 3001 AS dict_item_id, 'name_zh' AS source_standard, '英语' AS external_code, NULL AS external_label UNION ALL
  -- 002 zh Chinese → 中文
  SELECT 7002, 3002, 'name_zh', '中文', NULL UNION ALL
  -- 003 es Spanish → 西班牙语
  SELECT 7003, 3003, 'name_zh', '西班牙语', NULL UNION ALL
  -- 004 fr French → 法语
  SELECT 7004, 3004, 'name_zh', '法语', NULL UNION ALL
  -- 005 de German → 德语
  SELECT 7005, 3005, 'name_zh', '德语', NULL UNION ALL
  -- 006 ja Japanese → 日语
  SELECT 7006, 3006, 'name_zh', '日语', NULL UNION ALL
  -- 007 pt Portuguese → 葡萄牙语
  SELECT 7007, 3007, 'name_zh', '葡萄牙语', NULL UNION ALL
  -- 008 ru Russian → 俄语
  SELECT 7008, 3008, 'name_zh', '俄语', NULL UNION ALL
  -- 009 ar Arabic → 阿拉伯语
  SELECT 7009, 3009, 'name_zh', '阿拉伯语', NULL UNION ALL
  -- 010 ko Korean → 韩语
  SELECT 7010, 3010, 'name_zh', '韩语', NULL UNION ALL
  -- 011 it Italian → 意大利语
  SELECT 7011, 3011, 'name_zh', '意大利语', NULL UNION ALL
  -- 012 nl Dutch → 荷兰语
  SELECT 7012, 3012, 'name_zh', '荷兰语', NULL UNION ALL
  -- 013 pl Polish → 波兰语
  SELECT 7013, 3013, 'name_zh', '波兰语', NULL UNION ALL
  -- 014 tr Turkish → 土耳其语
  SELECT 7014, 3014, 'name_zh', '土耳其语', NULL UNION ALL
  -- 015 sv Swedish → 瑞典语
  SELECT 7015, 3015, 'name_zh', '瑞典语', NULL UNION ALL
  -- 016 da Danish → 丹麦语
  SELECT 7016, 3016, 'name_zh', '丹麦语', NULL UNION ALL
  -- 017 no Norwegian → 挪威语
  SELECT 7017, 3017, 'name_zh', '挪威语', NULL UNION ALL
  -- 018 fi Finnish → 芬兰语
  SELECT 7018, 3018, 'name_zh', '芬兰语', NULL UNION ALL
  -- 019 cs Czech → 捷克语
  SELECT 7019, 3019, 'name_zh', '捷克语', NULL UNION ALL
  -- 020 hu Hungarian → 匈牙利语
  SELECT 7020, 3020, 'name_zh', '匈牙利语', NULL UNION ALL
  -- 021 el Greek → 希腊语
  SELECT 7021, 3021, 'name_zh', '希腊语', NULL UNION ALL
  -- 022 he Hebrew → 希伯来语
  SELECT 7022, 3022, 'name_zh', '希伯来语', NULL UNION ALL
  -- 023 th Thai → 泰语
  SELECT 7023, 3023, 'name_zh', '泰语', NULL UNION ALL
  -- 024 vi Vietnamese → 越南语
  SELECT 7024, 3024, 'name_zh', '越南语', NULL UNION ALL
  -- 025 id Indonesian → 印度尼西亚语
  SELECT 7025, 3025, 'name_zh', '印度尼西亚语', NULL UNION ALL
  -- 026 ms Malay → 马来语
  SELECT 7026, 3026, 'name_zh', '马来语', NULL UNION ALL
  -- 027 hi Hindi → 印地语
  SELECT 7027, 3027, 'name_zh', '印地语', NULL UNION ALL
  -- 028 bn Bengali → 孟加拉语
  SELECT 7028, 3028, 'name_zh', '孟加拉语', NULL UNION ALL
  -- 029 ta Tamil → 泰米尔语
  SELECT 7029, 3029, 'name_zh', '泰米尔语', NULL UNION ALL
  -- 030 te Telugu → 泰卢固语
  SELECT 7030, 3030, 'name_zh', '泰卢固语', NULL UNION ALL
  -- 031 mr Marathi → 马拉地语
  SELECT 7031, 3031, 'name_zh', '马拉地语', NULL UNION ALL
  -- 032 gu Gujarati → 古吉拉特语
  SELECT 7032, 3032, 'name_zh', '古吉拉特语', NULL UNION ALL
  -- 033 kn Kannada → 卡纳达语
  SELECT 7033, 3033, 'name_zh', '卡纳达语', NULL UNION ALL
  -- 034 ml Malayalam → 马拉雅拉姆语
  SELECT 7034, 3034, 'name_zh', '马拉雅拉姆语', NULL UNION ALL
  -- 035 pa Punjabi → 旁遮普语
  SELECT 7035, 3035, 'name_zh', '旁遮普语', NULL UNION ALL
  -- 036 ur Urdu → 乌尔都语
  SELECT 7036, 3036, 'name_zh', '乌尔都语', NULL UNION ALL
  -- 037 fa Persian → 波斯语
  SELECT 7037, 3037, 'name_zh', '波斯语', NULL UNION ALL
  -- 038 uk Ukrainian → 乌克兰语
  SELECT 7038, 3038, 'name_zh', '乌克兰语', NULL UNION ALL
  -- 039 ro Romanian → 罗马尼亚语
  SELECT 7039, 3039, 'name_zh', '罗马尼亚语', NULL UNION ALL
  -- 040 bg Bulgarian → 保加利亚语
  SELECT 7040, 3040, 'name_zh', '保加利亚语', NULL UNION ALL
  -- 041 hr Croatian → 克罗地亚语
  SELECT 7041, 3041, 'name_zh', '克罗地亚语', NULL UNION ALL
  -- 042 sr Serbian → 塞尔维亚语
  SELECT 7042, 3042, 'name_zh', '塞尔维亚语', NULL UNION ALL
  -- 043 sk Slovak → 斯洛伐克语
  SELECT 7043, 3043, 'name_zh', '斯洛伐克语', NULL UNION ALL
  -- 044 sl Slovenian → 斯洛文尼亚语
  SELECT 7044, 3044, 'name_zh', '斯洛文尼亚语', NULL UNION ALL
  -- 045 lt Lithuanian → 立陶宛语
  SELECT 7045, 3045, 'name_zh', '立陶宛语', NULL UNION ALL
  -- 046 lv Latvian → 拉脱维亚语
  SELECT 7046, 3046, 'name_zh', '拉脱维亚语', NULL UNION ALL
  -- 047 et Estonian → 爱沙尼亚语
  SELECT 7047, 3047, 'name_zh', '爱沙尼亚语', NULL UNION ALL
  -- 048 ca Catalan → 加泰罗尼亚语
  SELECT 7048, 3048, 'name_zh', '加泰罗尼亚语', NULL UNION ALL
  -- 049 af Afrikaans → 南非荷兰语
  SELECT 7049, 3049, 'name_zh', '南非荷兰语', NULL UNION ALL
  -- 050 la Latin → 拉丁语
  SELECT 7050, 3050, 'name_zh', '拉丁语', NULL
) AS tmp
ON CONFLICT (id) DO UPDATE SET
    dict_item_id = EXCLUDED.dict_item_id,
    source_standard = EXCLUDED.source_standard,
    external_code = EXCLUDED.external_code,
    external_label = EXCLUDED.external_label;


/* ====================================================================
 * V1.4.2 语言中文名称种子数据完成
 * ====================================================================
 * 统计:
 *   - 50 个 NAME_ZH 别名（覆盖所有 V1.3.0 语言）
 * ==================================================================== */

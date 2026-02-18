/* ====================================================================
 * 迁移: V1.4.0 - 添加 NAME_ZH 参考标准
 * ====================================================================
 * 目的: 为 country 和 language 字典类型各添加一条 NAME_ZH 参考标准记录，
 *       用于存储中文名称别名。
 *
 * ID 规划:
 *   - country  NAME_ZH: 900000000000000004 (续接 NAME_EN = 03)
 *   - language NAME_ZH: 900000000000000013 (续接 NAME_EN = 12)
 *
 * 数据来源: CLDR (Unicode Common Locale Data Repository) 中文区域数据
 * ==================================================================== */


-- country NAME_ZH 标准
INSERT INTO patra_registry.sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
SELECT
  900000000000000004,
  'country',
  'NAME_ZH',
  '中文名称',
  '中文国家/地区名称（CLDR 简体中文数据），用于前端本地化展示',
  30,
  0,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_reference_standard
  WHERE dict_type_code = 'country' AND standard_code = 'NAME_ZH'
);

-- language NAME_ZH 标准
INSERT INTO patra_registry.sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
SELECT
  900000000000000013,
  'language',
  'NAME_ZH',
  '中文名称',
  '中文语言名称（CLDR 简体中文数据），用于前端本地化展示',
  4,
  0,
  1,
  NOW(6),
  NOW(6),
  0
WHERE NOT EXISTS (
  SELECT 1 FROM patra_registry.sys_reference_standard
  WHERE dict_type_code = 'language' AND standard_code = 'NAME_ZH'
);

-- country 字典种子：ISO 3166-1 国家和地区（249 个条目、3 个参考标准、英文与中文名各 249 条外部编码）。
-- 可重复执行：id 写死为小整数（雪花 ID 远大于此，不会冲突），按 id 冲突时更新全部业务列；内容改了 Flyway 会重跑。
-- id 段：类型 1；条目 1001～1249；参考标准 2～4；英文名 2001～2249；中文名 6001～6249。

INSERT INTO sys_dict_type
(id, type_code, type_name, description, allow_custom_items, is_system)
SELECT
  1,
  'country',
  '国家/地区',
  'ISO 3166-1 国家和地区代码标准，item_code 为 alpha-2 两字母代码',
  false,
  true
ON CONFLICT (id) DO UPDATE SET
    type_code = EXCLUDED.type_code,
    type_name = EXCLUDED.type_name,
    description = EXCLUDED.description,
    allow_custom_items = EXCLUDED.allow_custom_items,
    is_system = EXCLUDED.is_system;


/* ====================================================================
 * 第2部分: 插入字典项（249 个国家/地区）
 * item_code = ISO 3166-1 alpha-2
 * item_name = 英文名称
 * display_order = ISO numeric code
 * ==================================================================== */

INSERT INTO sys_dict_item
(id, dict_type_id, item_code, item_name, display_order, is_default, enabled)
SELECT * FROM (
  SELECT 1001 AS id, 1 AS dict_type_id, 'AF' AS item_code, 'Afghanistan' AS item_name, 4 AS display_order, false AS is_default, true AS enabled UNION ALL
  SELECT 1002, 1, 'AX', 'Åland Islands', 248, false, true UNION ALL
  SELECT 1003, 1, 'AL', 'Albania', 8, false, true UNION ALL
  SELECT 1004, 1, 'DZ', 'Algeria', 12, false, true UNION ALL
  SELECT 1005, 1, 'AS', 'American Samoa', 16, false, true UNION ALL
  SELECT 1006, 1, 'AD', 'Andorra', 20, false, true UNION ALL
  SELECT 1007, 1, 'AO', 'Angola', 24, false, true UNION ALL
  SELECT 1008, 1, 'AI', 'Anguilla', 660, false, true UNION ALL
  SELECT 1009, 1, 'AQ', 'Antarctica', 10, false, true UNION ALL
  SELECT 1010, 1, 'AG', 'Antigua and Barbuda', 28, false, true UNION ALL
  SELECT 1011, 1, 'AR', 'Argentina', 32, false, true UNION ALL
  SELECT 1012, 1, 'AM', 'Armenia', 51, false, true UNION ALL
  SELECT 1013, 1, 'AW', 'Aruba', 533, false, true UNION ALL
  SELECT 1014, 1, 'AU', 'Australia', 36, false, true UNION ALL
  SELECT 1015, 1, 'AT', 'Austria', 40, false, true UNION ALL
  SELECT 1016, 1, 'AZ', 'Azerbaijan', 31, false, true UNION ALL
  SELECT 1017, 1, 'BS', 'Bahamas', 44, false, true UNION ALL
  SELECT 1018, 1, 'BH', 'Bahrain', 48, false, true UNION ALL
  SELECT 1019, 1, 'BD', 'Bangladesh', 50, false, true UNION ALL
  SELECT 1020, 1, 'BB', 'Barbados', 52, false, true UNION ALL
  SELECT 1021, 1, 'BY', 'Belarus', 112, false, true UNION ALL
  SELECT 1022, 1, 'BE', 'Belgium', 56, false, true UNION ALL
  SELECT 1023, 1, 'BZ', 'Belize', 84, false, true UNION ALL
  SELECT 1024, 1, 'BJ', 'Benin', 204, false, true UNION ALL
  SELECT 1025, 1, 'BM', 'Bermuda', 60, false, true UNION ALL
  SELECT 1026, 1, 'BT', 'Bhutan', 64, false, true UNION ALL
  SELECT 1027, 1, 'BO', 'Bolivia', 68, false, true UNION ALL
  SELECT 1028, 1, 'BQ', 'Bonaire, Sint Eustatius and Saba', 535, false, true UNION ALL
  SELECT 1029, 1, 'BA', 'Bosnia and Herzegovina', 70, false, true UNION ALL
  SELECT 1030, 1, 'BW', 'Botswana', 72, false, true UNION ALL
  SELECT 1031, 1, 'BV', 'Bouvet Island', 74, false, true UNION ALL
  SELECT 1032, 1, 'BR', 'Brazil', 76, false, true UNION ALL
  SELECT 1033, 1, 'IO', 'British Indian Ocean Territory', 86, false, true UNION ALL
  SELECT 1034, 1, 'BN', 'Brunei Darussalam', 96, false, true UNION ALL
  SELECT 1035, 1, 'BG', 'Bulgaria', 100, false, true UNION ALL
  SELECT 1036, 1, 'BF', 'Burkina Faso', 854, false, true UNION ALL
  SELECT 1037, 1, 'BI', 'Burundi', 108, false, true UNION ALL
  SELECT 1038, 1, 'CV', 'Cabo Verde', 132, false, true UNION ALL
  SELECT 1039, 1, 'KH', 'Cambodia', 116, false, true UNION ALL
  SELECT 1040, 1, 'CM', 'Cameroon', 120, false, true UNION ALL
  SELECT 1041, 1, 'CA', 'Canada', 124, false, true UNION ALL
  SELECT 1042, 1, 'KY', 'Cayman Islands', 136, false, true UNION ALL
  SELECT 1043, 1, 'CF', 'Central African Republic', 140, false, true UNION ALL
  SELECT 1044, 1, 'TD', 'Chad', 148, false, true UNION ALL
  SELECT 1045, 1, 'CL', 'Chile', 152, false, true UNION ALL
  SELECT 1046, 1, 'CN', 'China', 156, false, true UNION ALL
  SELECT 1047, 1, 'CX', 'Christmas Island', 162, false, true UNION ALL
  SELECT 1048, 1, 'CC', 'Cocos (Keeling) Islands', 166, false, true UNION ALL
  SELECT 1049, 1, 'CO', 'Colombia', 170, false, true UNION ALL
  SELECT 1050, 1, 'KM', 'Comoros', 174, false, true UNION ALL
  SELECT 1051, 1, 'CD', 'Congo (Democratic Republic)', 180, false, true UNION ALL
  SELECT 1052, 1, 'CG', 'Congo', 178, false, true UNION ALL
  SELECT 1053, 1, 'CK', 'Cook Islands', 184, false, true UNION ALL
  SELECT 1054, 1, 'CR', 'Costa Rica', 188, false, true UNION ALL
  SELECT 1055, 1, 'CI', 'Côte d''Ivoire', 384, false, true UNION ALL
  SELECT 1056, 1, 'HR', 'Croatia', 191, false, true UNION ALL
  SELECT 1057, 1, 'CU', 'Cuba', 192, false, true UNION ALL
  SELECT 1058, 1, 'CW', 'Curaçao', 531, false, true UNION ALL
  SELECT 1059, 1, 'CY', 'Cyprus', 196, false, true UNION ALL
  SELECT 1060, 1, 'CZ', 'Czechia', 203, false, true UNION ALL
  SELECT 1061, 1, 'DK', 'Denmark', 208, false, true UNION ALL
  SELECT 1062, 1, 'DJ', 'Djibouti', 262, false, true UNION ALL
  SELECT 1063, 1, 'DM', 'Dominica', 212, false, true UNION ALL
  SELECT 1064, 1, 'DO', 'Dominican Republic', 214, false, true UNION ALL
  SELECT 1065, 1, 'EC', 'Ecuador', 218, false, true UNION ALL
  SELECT 1066, 1, 'EG', 'Egypt', 818, false, true UNION ALL
  SELECT 1067, 1, 'SV', 'El Salvador', 222, false, true UNION ALL
  SELECT 1068, 1, 'GQ', 'Equatorial Guinea', 226, false, true UNION ALL
  SELECT 1069, 1, 'ER', 'Eritrea', 232, false, true UNION ALL
  SELECT 1070, 1, 'EE', 'Estonia', 233, false, true UNION ALL
  SELECT 1071, 1, 'SZ', 'Eswatini', 748, false, true UNION ALL
  SELECT 1072, 1, 'ET', 'Ethiopia', 231, false, true UNION ALL
  SELECT 1073, 1, 'FK', 'Falkland Islands', 238, false, true UNION ALL
  SELECT 1074, 1, 'FO', 'Faroe Islands', 234, false, true UNION ALL
  SELECT 1075, 1, 'FJ', 'Fiji', 242, false, true UNION ALL
  SELECT 1076, 1, 'FI', 'Finland', 246, false, true UNION ALL
  SELECT 1077, 1, 'FR', 'France', 250, false, true UNION ALL
  SELECT 1078, 1, 'GF', 'French Guiana', 254, false, true UNION ALL
  SELECT 1079, 1, 'PF', 'French Polynesia', 258, false, true UNION ALL
  SELECT 1080, 1, 'TF', 'French Southern Territories', 260, false, true UNION ALL
  SELECT 1081, 1, 'GA', 'Gabon', 266, false, true UNION ALL
  SELECT 1082, 1, 'GM', 'Gambia', 270, false, true UNION ALL
  SELECT 1083, 1, 'GE', 'Georgia', 268, false, true UNION ALL
  SELECT 1084, 1, 'DE', 'Germany', 276, false, true UNION ALL
  SELECT 1085, 1, 'GH', 'Ghana', 288, false, true UNION ALL
  SELECT 1086, 1, 'GI', 'Gibraltar', 292, false, true UNION ALL
  SELECT 1087, 1, 'GR', 'Greece', 300, false, true UNION ALL
  SELECT 1088, 1, 'GL', 'Greenland', 304, false, true UNION ALL
  SELECT 1089, 1, 'GD', 'Grenada', 308, false, true UNION ALL
  SELECT 1090, 1, 'GP', 'Guadeloupe', 312, false, true UNION ALL
  SELECT 1091, 1, 'GU', 'Guam', 316, false, true UNION ALL
  SELECT 1092, 1, 'GT', 'Guatemala', 320, false, true UNION ALL
  SELECT 1093, 1, 'GG', 'Guernsey', 831, false, true UNION ALL
  SELECT 1094, 1, 'GN', 'Guinea', 324, false, true UNION ALL
  SELECT 1095, 1, 'GW', 'Guinea-Bissau', 624, false, true UNION ALL
  SELECT 1096, 1, 'GY', 'Guyana', 328, false, true UNION ALL
  SELECT 1097, 1, 'HT', 'Haiti', 332, false, true UNION ALL
  SELECT 1098, 1, 'HM', 'Heard Island and McDonald Islands', 334, false, true UNION ALL
  SELECT 1099, 1, 'VA', 'Holy See', 336, false, true UNION ALL
  SELECT 1100, 1, 'HN', 'Honduras', 340, false, true UNION ALL
  SELECT 1101, 1, 'HK', 'Hong Kong', 344, false, true UNION ALL
  SELECT 1102, 1, 'HU', 'Hungary', 348, false, true UNION ALL
  SELECT 1103, 1, 'IS', 'Iceland', 352, false, true UNION ALL
  SELECT 1104, 1, 'IN', 'India', 356, false, true UNION ALL
  SELECT 1105, 1, 'ID', 'Indonesia', 360, false, true UNION ALL
  SELECT 1106, 1, 'IR', 'Iran', 364, false, true UNION ALL
  SELECT 1107, 1, 'IQ', 'Iraq', 368, false, true UNION ALL
  SELECT 1108, 1, 'IE', 'Ireland', 372, false, true UNION ALL
  SELECT 1109, 1, 'IM', 'Isle of Man', 833, false, true UNION ALL
  SELECT 1110, 1, 'IL', 'Israel', 376, false, true UNION ALL
  SELECT 1111, 1, 'IT', 'Italy', 380, false, true UNION ALL
  SELECT 1112, 1, 'JM', 'Jamaica', 388, false, true UNION ALL
  SELECT 1113, 1, 'JP', 'Japan', 392, false, true UNION ALL
  SELECT 1114, 1, 'JE', 'Jersey', 832, false, true UNION ALL
  SELECT 1115, 1, 'JO', 'Jordan', 400, false, true UNION ALL
  SELECT 1116, 1, 'KZ', 'Kazakhstan', 398, false, true UNION ALL
  SELECT 1117, 1, 'KE', 'Kenya', 404, false, true UNION ALL
  SELECT 1118, 1, 'KI', 'Kiribati', 296, false, true UNION ALL
  SELECT 1119, 1, 'KP', 'Korea (Democratic People''s Republic)', 408, false, true UNION ALL
  SELECT 1120, 1, 'KR', 'Korea (Republic)', 410, false, true UNION ALL
  SELECT 1121, 1, 'KW', 'Kuwait', 414, false, true UNION ALL
  SELECT 1122, 1, 'KG', 'Kyrgyzstan', 417, false, true UNION ALL
  SELECT 1123, 1, 'LA', 'Lao People''s Democratic Republic', 418, false, true UNION ALL
  SELECT 1124, 1, 'LV', 'Latvia', 428, false, true UNION ALL
  SELECT 1125, 1, 'LB', 'Lebanon', 422, false, true UNION ALL
  SELECT 1126, 1, 'LS', 'Lesotho', 426, false, true UNION ALL
  SELECT 1127, 1, 'LR', 'Liberia', 430, false, true UNION ALL
  SELECT 1128, 1, 'LY', 'Libya', 434, false, true UNION ALL
  SELECT 1129, 1, 'LI', 'Liechtenstein', 438, false, true UNION ALL
  SELECT 1130, 1, 'LT', 'Lithuania', 440, false, true UNION ALL
  SELECT 1131, 1, 'LU', 'Luxembourg', 442, false, true UNION ALL
  SELECT 1132, 1, 'MO', 'Macao', 446, false, true UNION ALL
  SELECT 1133, 1, 'MK', 'Republic of North Macedonia', 807, false, true UNION ALL
  SELECT 1134, 1, 'MG', 'Madagascar', 450, false, true UNION ALL
  SELECT 1135, 1, 'MW', 'Malawi', 454, false, true UNION ALL
  SELECT 1136, 1, 'MY', 'Malaysia', 458, false, true UNION ALL
  SELECT 1137, 1, 'MV', 'Maldives', 462, false, true UNION ALL
  SELECT 1138, 1, 'ML', 'Mali', 466, false, true UNION ALL
  SELECT 1139, 1, 'MT', 'Malta', 470, false, true UNION ALL
  SELECT 1140, 1, 'MH', 'Marshall Islands', 584, false, true UNION ALL
  SELECT 1141, 1, 'MQ', 'Martinique', 474, false, true UNION ALL
  SELECT 1142, 1, 'MR', 'Mauritania', 478, false, true UNION ALL
  SELECT 1143, 1, 'MU', 'Mauritius', 480, false, true UNION ALL
  SELECT 1144, 1, 'YT', 'Mayotte', 175, false, true UNION ALL
  SELECT 1145, 1, 'MX', 'Mexico', 484, false, true UNION ALL
  SELECT 1146, 1, 'FM', 'Micronesia (Federated States)', 583, false, true UNION ALL
  SELECT 1147, 1, 'MD', 'Moldova', 498, false, true UNION ALL
  SELECT 1148, 1, 'MC', 'Monaco', 492, false, true UNION ALL
  SELECT 1149, 1, 'MN', 'Mongolia', 496, false, true UNION ALL
  SELECT 1150, 1, 'ME', 'Montenegro', 499, false, true UNION ALL
  SELECT 1151, 1, 'MS', 'Montserrat', 500, false, true UNION ALL
  SELECT 1152, 1, 'MA', 'Morocco', 504, false, true UNION ALL
  SELECT 1153, 1, 'MZ', 'Mozambique', 508, false, true UNION ALL
  SELECT 1154, 1, 'MM', 'Myanmar', 104, false, true UNION ALL
  SELECT 1155, 1, 'NA', 'Namibia', 516, false, true UNION ALL
  SELECT 1156, 1, 'NR', 'Nauru', 520, false, true UNION ALL
  SELECT 1157, 1, 'NP', 'Nepal', 524, false, true UNION ALL
  SELECT 1158, 1, 'NL', 'Netherlands', 528, false, true UNION ALL
  SELECT 1159, 1, 'NC', 'New Caledonia', 540, false, true UNION ALL
  SELECT 1160, 1, 'NZ', 'New Zealand', 554, false, true UNION ALL
  SELECT 1161, 1, 'NI', 'Nicaragua', 558, false, true UNION ALL
  SELECT 1162, 1, 'NE', 'Niger', 562, false, true UNION ALL
  SELECT 1163, 1, 'NG', 'Nigeria', 566, false, true UNION ALL
  SELECT 1164, 1, 'NU', 'Niue', 570, false, true UNION ALL
  SELECT 1165, 1, 'NF', 'Norfolk Island', 574, false, true UNION ALL
  SELECT 1166, 1, 'MP', 'Northern Mariana Islands', 580, false, true UNION ALL
  SELECT 1167, 1, 'NO', 'Norway', 578, false, true UNION ALL
  SELECT 1168, 1, 'OM', 'Oman', 512, false, true UNION ALL
  SELECT 1169, 1, 'PK', 'Pakistan', 586, false, true UNION ALL
  SELECT 1170, 1, 'PW', 'Palau', 585, false, true UNION ALL
  SELECT 1171, 1, 'PS', 'Palestine, State of', 275, false, true UNION ALL
  SELECT 1172, 1, 'PA', 'Panama', 591, false, true UNION ALL
  SELECT 1173, 1, 'PG', 'Papua New Guinea', 598, false, true UNION ALL
  SELECT 1174, 1, 'PY', 'Paraguay', 600, false, true UNION ALL
  SELECT 1175, 1, 'PE', 'Peru', 604, false, true UNION ALL
  SELECT 1176, 1, 'PH', 'Philippines', 608, false, true UNION ALL
  SELECT 1177, 1, 'PN', 'Pitcairn', 612, false, true UNION ALL
  SELECT 1178, 1, 'PL', 'Poland', 616, false, true UNION ALL
  SELECT 1179, 1, 'PT', 'Portugal', 620, false, true UNION ALL
  SELECT 1180, 1, 'PR', 'Puerto Rico', 630, false, true UNION ALL
  SELECT 1181, 1, 'QA', 'Qatar', 634, false, true UNION ALL
  SELECT 1182, 1, 'RE', 'Réunion', 638, false, true UNION ALL
  SELECT 1183, 1, 'RO', 'Romania', 642, false, true UNION ALL
  SELECT 1184, 1, 'RU', 'Russian Federation', 643, false, true UNION ALL
  SELECT 1185, 1, 'RW', 'Rwanda', 646, false, true UNION ALL
  SELECT 1186, 1, 'BL', 'Saint Barthélemy', 652, false, true UNION ALL
  SELECT 1187, 1, 'SH', 'Saint Helena, Ascension and Tristan da Cunha', 654, false, true UNION ALL
  SELECT 1188, 1, 'KN', 'Saint Kitts and Nevis', 659, false, true UNION ALL
  SELECT 1189, 1, 'LC', 'Saint Lucia', 662, false, true UNION ALL
  SELECT 1190, 1, 'MF', 'Saint Martin', 663, false, true UNION ALL
  SELECT 1191, 1, 'PM', 'Saint Pierre and Miquelon', 666, false, true UNION ALL
  SELECT 1192, 1, 'VC', 'Saint Vincent and the Grenadines', 670, false, true UNION ALL
  SELECT 1193, 1, 'WS', 'Samoa', 882, false, true UNION ALL
  SELECT 1194, 1, 'SM', 'San Marino', 674, false, true UNION ALL
  SELECT 1195, 1, 'ST', 'Sao Tome and Principe', 678, false, true UNION ALL
  SELECT 1196, 1, 'SA', 'Saudi Arabia', 682, false, true UNION ALL
  SELECT 1197, 1, 'SN', 'Senegal', 686, false, true UNION ALL
  SELECT 1198, 1, 'RS', 'Serbia', 688, false, true UNION ALL
  SELECT 1199, 1, 'SC', 'Seychelles', 690, false, true UNION ALL
  SELECT 1200, 1, 'SL', 'Sierra Leone', 694, false, true UNION ALL
  SELECT 1201, 1, 'SG', 'Singapore', 702, false, true UNION ALL
  SELECT 1202, 1, 'SX', 'Sint Maarten', 534, false, true UNION ALL
  SELECT 1203, 1, 'SK', 'Slovakia', 703, false, true UNION ALL
  SELECT 1204, 1, 'SI', 'Slovenia', 705, false, true UNION ALL
  SELECT 1205, 1, 'SB', 'Solomon Islands', 90, false, true UNION ALL
  SELECT 1206, 1, 'SO', 'Somalia', 706, false, true UNION ALL
  SELECT 1207, 1, 'ZA', 'South Africa', 710, false, true UNION ALL
  SELECT 1208, 1, 'GS', 'South Georgia and the South Sandwich Islands', 239, false, true UNION ALL
  SELECT 1209, 1, 'SS', 'South Sudan', 728, false, true UNION ALL
  SELECT 1210, 1, 'ES', 'Spain', 724, false, true UNION ALL
  SELECT 1211, 1, 'LK', 'Sri Lanka', 144, false, true UNION ALL
  SELECT 1212, 1, 'SD', 'Sudan', 729, false, true UNION ALL
  SELECT 1213, 1, 'SR', 'Suriname', 740, false, true UNION ALL
  SELECT 1214, 1, 'SJ', 'Svalbard and Jan Mayen', 744, false, true UNION ALL
  SELECT 1215, 1, 'SE', 'Sweden', 752, false, true UNION ALL
  SELECT 1216, 1, 'CH', 'Switzerland', 756, false, true UNION ALL
  SELECT 1217, 1, 'SY', 'Syrian Arab Republic', 760, false, true UNION ALL
  SELECT 1218, 1, 'TW', 'Taiwan', 158, false, true UNION ALL
  SELECT 1219, 1, 'TJ', 'Tajikistan', 762, false, true UNION ALL
  SELECT 1220, 1, 'TZ', 'Tanzania, United Republic of', 834, false, true UNION ALL
  SELECT 1221, 1, 'TH', 'Thailand', 764, false, true UNION ALL
  SELECT 1222, 1, 'TL', 'Timor-Leste', 626, false, true UNION ALL
  SELECT 1223, 1, 'TG', 'Togo', 768, false, true UNION ALL
  SELECT 1224, 1, 'TK', 'Tokelau', 772, false, true UNION ALL
  SELECT 1225, 1, 'TO', 'Tonga', 776, false, true UNION ALL
  SELECT 1226, 1, 'TT', 'Trinidad and Tobago', 780, false, true UNION ALL
  SELECT 1227, 1, 'TN', 'Tunisia', 788, false, true UNION ALL
  SELECT 1228, 1, 'TR', 'Turkey', 792, false, true UNION ALL
  SELECT 1229, 1, 'TM', 'Turkmenistan', 795, false, true UNION ALL
  SELECT 1230, 1, 'TC', 'Turks and Caicos Islands', 796, false, true UNION ALL
  SELECT 1231, 1, 'TV', 'Tuvalu', 798, false, true UNION ALL
  SELECT 1232, 1, 'UG', 'Uganda', 800, false, true UNION ALL
  SELECT 1233, 1, 'UA', 'Ukraine', 804, false, true UNION ALL
  SELECT 1234, 1, 'AE', 'United Arab Emirates', 784, false, true UNION ALL
  SELECT 1235, 1, 'GB', 'United Kingdom', 826, false, true UNION ALL
  SELECT 1236, 1, 'UM', 'United States Minor Outlying Islands', 581, false, true UNION ALL
  SELECT 1237, 1, 'US', 'United States of America', 840, false, true UNION ALL
  SELECT 1238, 1, 'UY', 'Uruguay', 858, false, true UNION ALL
  SELECT 1239, 1, 'UZ', 'Uzbekistan', 860, false, true UNION ALL
  SELECT 1240, 1, 'VU', 'Vanuatu', 548, false, true UNION ALL
  SELECT 1241, 1, 'VE', 'Venezuela', 862, false, true UNION ALL
  SELECT 1242, 1, 'VN', 'Viet Nam', 704, false, true UNION ALL
  SELECT 1243, 1, 'VG', 'Virgin Islands (British)', 92, false, true UNION ALL
  SELECT 1244, 1, 'VI', 'Virgin Islands (U.S.)', 850, false, true UNION ALL
  SELECT 1245, 1, 'WF', 'Wallis and Futuna', 876, false, true UNION ALL
  SELECT 1246, 1, 'EH', 'Western Sahara', 732, false, true UNION ALL
  SELECT 1247, 1, 'YE', 'Yemen', 887, false, true UNION ALL
  SELECT 1248, 1, 'ZM', 'Zambia', 894, false, true UNION ALL
  SELECT 1249, 1, 'ZW', 'Zimbabwe', 716, false, true
) AS source
ON CONFLICT (id) DO UPDATE SET
    dict_type_id = EXCLUDED.dict_type_id,
    item_code = EXCLUDED.item_code,
    item_name = EXCLUDED.item_name,
    display_order = EXCLUDED.display_order,
    is_default = EXCLUDED.is_default,
    enabled = EXCLUDED.enabled;


/* ====================================================================
 * 第3部分: 插入 NAME_EN 别名（英文国家名称 → ISO alpha-2）
 * source_standard = 'name_en'（小写，与查询时一致）
 * external_code = 英文国家名称
 * ==================================================================== */

INSERT INTO sys_dict_item_alias
(id, dict_item_id, source_standard, external_code, external_label)

SELECT * FROM (
  SELECT 2001 AS id, 1001 AS dict_item_id, 'name_en' AS source_standard, 'Afghanistan' AS external_code, 'AF' AS external_label UNION ALL
  SELECT 2002, 1002, 'name_en', 'Åland Islands', 'AX' UNION ALL
  SELECT 2003, 1003, 'name_en', 'Albania', 'AL' UNION ALL
  SELECT 2004, 1004, 'name_en', 'Algeria', 'DZ' UNION ALL
  SELECT 2005, 1005, 'name_en', 'American Samoa', 'AS' UNION ALL
  SELECT 2006, 1006, 'name_en', 'Andorra', 'AD' UNION ALL
  SELECT 2007, 1007, 'name_en', 'Angola', 'AO' UNION ALL
  SELECT 2008, 1008, 'name_en', 'Anguilla', 'AI' UNION ALL
  SELECT 2009, 1009, 'name_en', 'Antarctica', 'AQ' UNION ALL
  SELECT 2010, 1010, 'name_en', 'Antigua and Barbuda', 'AG' UNION ALL
  SELECT 2011, 1011, 'name_en', 'Argentina', 'AR' UNION ALL
  SELECT 2012, 1012, 'name_en', 'Armenia', 'AM' UNION ALL
  SELECT 2013, 1013, 'name_en', 'Aruba', 'AW' UNION ALL
  SELECT 2014, 1014, 'name_en', 'Australia', 'AU' UNION ALL
  SELECT 2015, 1015, 'name_en', 'Austria', 'AT' UNION ALL
  SELECT 2016, 1016, 'name_en', 'Azerbaijan', 'AZ' UNION ALL
  SELECT 2017, 1017, 'name_en', 'Bahamas', 'BS' UNION ALL
  SELECT 2018, 1018, 'name_en', 'Bahrain', 'BH' UNION ALL
  SELECT 2019, 1019, 'name_en', 'Bangladesh', 'BD' UNION ALL
  SELECT 2020, 1020, 'name_en', 'Barbados', 'BB' UNION ALL
  SELECT 2021, 1021, 'name_en', 'Belarus', 'BY' UNION ALL
  SELECT 2022, 1022, 'name_en', 'Belgium', 'BE' UNION ALL
  SELECT 2023, 1023, 'name_en', 'Belize', 'BZ' UNION ALL
  SELECT 2024, 1024, 'name_en', 'Benin', 'BJ' UNION ALL
  SELECT 2025, 1025, 'name_en', 'Bermuda', 'BM' UNION ALL
  SELECT 2026, 1026, 'name_en', 'Bhutan', 'BT' UNION ALL
  SELECT 2027, 1027, 'name_en', 'Bolivia', 'BO' UNION ALL
  SELECT 2028, 1028, 'name_en', 'Bonaire, Sint Eustatius and Saba', 'BQ' UNION ALL
  SELECT 2029, 1029, 'name_en', 'Bosnia and Herzegovina', 'BA' UNION ALL
  SELECT 2030, 1030, 'name_en', 'Botswana', 'BW' UNION ALL
  SELECT 2031, 1031, 'name_en', 'Bouvet Island', 'BV' UNION ALL
  SELECT 2032, 1032, 'name_en', 'Brazil', 'BR' UNION ALL
  SELECT 2033, 1033, 'name_en', 'British Indian Ocean Territory', 'IO' UNION ALL
  SELECT 2034, 1034, 'name_en', 'Brunei Darussalam', 'BN' UNION ALL
  SELECT 2035, 1035, 'name_en', 'Bulgaria', 'BG' UNION ALL
  SELECT 2036, 1036, 'name_en', 'Burkina Faso', 'BF' UNION ALL
  SELECT 2037, 1037, 'name_en', 'Burundi', 'BI' UNION ALL
  SELECT 2038, 1038, 'name_en', 'Cabo Verde', 'CV' UNION ALL
  SELECT 2039, 1039, 'name_en', 'Cambodia', 'KH' UNION ALL
  SELECT 2040, 1040, 'name_en', 'Cameroon', 'CM' UNION ALL
  SELECT 2041, 1041, 'name_en', 'Canada', 'CA' UNION ALL
  SELECT 2042, 1042, 'name_en', 'Cayman Islands', 'KY' UNION ALL
  SELECT 2043, 1043, 'name_en', 'Central African Republic', 'CF' UNION ALL
  SELECT 2044, 1044, 'name_en', 'Chad', 'TD' UNION ALL
  SELECT 2045, 1045, 'name_en', 'Chile', 'CL' UNION ALL
  SELECT 2046, 1046, 'name_en', 'China', 'CN' UNION ALL
  SELECT 2047, 1047, 'name_en', 'Christmas Island', 'CX' UNION ALL
  SELECT 2048, 1048, 'name_en', 'Cocos (Keeling) Islands', 'CC' UNION ALL
  SELECT 2049, 1049, 'name_en', 'Colombia', 'CO' UNION ALL
  SELECT 2050, 1050, 'name_en', 'Comoros', 'KM' UNION ALL
  SELECT 2051, 1051, 'name_en', 'Congo (Democratic Republic)', 'CD' UNION ALL
  SELECT 2052, 1052, 'name_en', 'Congo', 'CG' UNION ALL
  SELECT 2053, 1053, 'name_en', 'Cook Islands', 'CK' UNION ALL
  SELECT 2054, 1054, 'name_en', 'Costa Rica', 'CR' UNION ALL
  SELECT 2055, 1055, 'name_en', 'Côte d''Ivoire', 'CI' UNION ALL
  SELECT 2056, 1056, 'name_en', 'Croatia', 'HR' UNION ALL
  SELECT 2057, 1057, 'name_en', 'Cuba', 'CU' UNION ALL
  SELECT 2058, 1058, 'name_en', 'Curaçao', 'CW' UNION ALL
  SELECT 2059, 1059, 'name_en', 'Cyprus', 'CY' UNION ALL
  SELECT 2060, 1060, 'name_en', 'Czechia', 'CZ' UNION ALL
  SELECT 2061, 1061, 'name_en', 'Denmark', 'DK' UNION ALL
  SELECT 2062, 1062, 'name_en', 'Djibouti', 'DJ' UNION ALL
  SELECT 2063, 1063, 'name_en', 'Dominica', 'DM' UNION ALL
  SELECT 2064, 1064, 'name_en', 'Dominican Republic', 'DO' UNION ALL
  SELECT 2065, 1065, 'name_en', 'Ecuador', 'EC' UNION ALL
  SELECT 2066, 1066, 'name_en', 'Egypt', 'EG' UNION ALL
  SELECT 2067, 1067, 'name_en', 'El Salvador', 'SV' UNION ALL
  SELECT 2068, 1068, 'name_en', 'Equatorial Guinea', 'GQ' UNION ALL
  SELECT 2069, 1069, 'name_en', 'Eritrea', 'ER' UNION ALL
  SELECT 2070, 1070, 'name_en', 'Estonia', 'EE' UNION ALL
  SELECT 2071, 1071, 'name_en', 'Eswatini', 'SZ' UNION ALL
  SELECT 2072, 1072, 'name_en', 'Ethiopia', 'ET' UNION ALL
  SELECT 2073, 1073, 'name_en', 'Falkland Islands', 'FK' UNION ALL
  SELECT 2074, 1074, 'name_en', 'Faroe Islands', 'FO' UNION ALL
  SELECT 2075, 1075, 'name_en', 'Fiji', 'FJ' UNION ALL
  SELECT 2076, 1076, 'name_en', 'Finland', 'FI' UNION ALL
  SELECT 2077, 1077, 'name_en', 'France', 'FR' UNION ALL
  SELECT 2078, 1078, 'name_en', 'French Guiana', 'GF' UNION ALL
  SELECT 2079, 1079, 'name_en', 'French Polynesia', 'PF' UNION ALL
  SELECT 2080, 1080, 'name_en', 'French Southern Territories', 'TF' UNION ALL
  SELECT 2081, 1081, 'name_en', 'Gabon', 'GA' UNION ALL
  SELECT 2082, 1082, 'name_en', 'Gambia', 'GM' UNION ALL
  SELECT 2083, 1083, 'name_en', 'Georgia', 'GE' UNION ALL
  SELECT 2084, 1084, 'name_en', 'Germany', 'DE' UNION ALL
  SELECT 2085, 1085, 'name_en', 'Ghana', 'GH' UNION ALL
  SELECT 2086, 1086, 'name_en', 'Gibraltar', 'GI' UNION ALL
  SELECT 2087, 1087, 'name_en', 'Greece', 'GR' UNION ALL
  SELECT 2088, 1088, 'name_en', 'Greenland', 'GL' UNION ALL
  SELECT 2089, 1089, 'name_en', 'Grenada', 'GD' UNION ALL
  SELECT 2090, 1090, 'name_en', 'Guadeloupe', 'GP' UNION ALL
  SELECT 2091, 1091, 'name_en', 'Guam', 'GU' UNION ALL
  SELECT 2092, 1092, 'name_en', 'Guatemala', 'GT' UNION ALL
  SELECT 2093, 1093, 'name_en', 'Guernsey', 'GG' UNION ALL
  SELECT 2094, 1094, 'name_en', 'Guinea', 'GN' UNION ALL
  SELECT 2095, 1095, 'name_en', 'Guinea-Bissau', 'GW' UNION ALL
  SELECT 2096, 1096, 'name_en', 'Guyana', 'GY' UNION ALL
  SELECT 2097, 1097, 'name_en', 'Haiti', 'HT' UNION ALL
  SELECT 2098, 1098, 'name_en', 'Heard Island and McDonald Islands', 'HM' UNION ALL
  SELECT 2099, 1099, 'name_en', 'Holy See', 'VA' UNION ALL
  SELECT 2100, 1100, 'name_en', 'Honduras', 'HN' UNION ALL
  SELECT 2101, 1101, 'name_en', 'Hong Kong', 'HK' UNION ALL
  SELECT 2102, 1102, 'name_en', 'Hungary', 'HU' UNION ALL
  SELECT 2103, 1103, 'name_en', 'Iceland', 'IS' UNION ALL
  SELECT 2104, 1104, 'name_en', 'India', 'IN' UNION ALL
  SELECT 2105, 1105, 'name_en', 'Indonesia', 'ID' UNION ALL
  SELECT 2106, 1106, 'name_en', 'Iran', 'IR' UNION ALL
  SELECT 2107, 1107, 'name_en', 'Iraq', 'IQ' UNION ALL
  SELECT 2108, 1108, 'name_en', 'Ireland', 'IE' UNION ALL
  SELECT 2109, 1109, 'name_en', 'Isle of Man', 'IM' UNION ALL
  SELECT 2110, 1110, 'name_en', 'Israel', 'IL' UNION ALL
  SELECT 2111, 1111, 'name_en', 'Italy', 'IT' UNION ALL
  SELECT 2112, 1112, 'name_en', 'Jamaica', 'JM' UNION ALL
  SELECT 2113, 1113, 'name_en', 'Japan', 'JP' UNION ALL
  SELECT 2114, 1114, 'name_en', 'Jersey', 'JE' UNION ALL
  SELECT 2115, 1115, 'name_en', 'Jordan', 'JO' UNION ALL
  SELECT 2116, 1116, 'name_en', 'Kazakhstan', 'KZ' UNION ALL
  SELECT 2117, 1117, 'name_en', 'Kenya', 'KE' UNION ALL
  SELECT 2118, 1118, 'name_en', 'Kiribati', 'KI' UNION ALL
  SELECT 2119, 1119, 'name_en', 'Korea (Democratic People''s Republic)', 'KP' UNION ALL
  SELECT 2120, 1120, 'name_en', 'Korea (Republic)', 'KR' UNION ALL
  SELECT 2121, 1121, 'name_en', 'Kuwait', 'KW' UNION ALL
  SELECT 2122, 1122, 'name_en', 'Kyrgyzstan', 'KG' UNION ALL
  SELECT 2123, 1123, 'name_en', 'Lao People''s Democratic Republic', 'LA' UNION ALL
  SELECT 2124, 1124, 'name_en', 'Latvia', 'LV' UNION ALL
  SELECT 2125, 1125, 'name_en', 'Lebanon', 'LB' UNION ALL
  SELECT 2126, 1126, 'name_en', 'Lesotho', 'LS' UNION ALL
  SELECT 2127, 1127, 'name_en', 'Liberia', 'LR' UNION ALL
  SELECT 2128, 1128, 'name_en', 'Libya', 'LY' UNION ALL
  SELECT 2129, 1129, 'name_en', 'Liechtenstein', 'LI' UNION ALL
  SELECT 2130, 1130, 'name_en', 'Lithuania', 'LT' UNION ALL
  SELECT 2131, 1131, 'name_en', 'Luxembourg', 'LU' UNION ALL
  SELECT 2132, 1132, 'name_en', 'Macao', 'MO' UNION ALL
  SELECT 2133, 1133, 'name_en', 'Republic of North Macedonia', 'MK' UNION ALL
  SELECT 2134, 1134, 'name_en', 'Madagascar', 'MG' UNION ALL
  SELECT 2135, 1135, 'name_en', 'Malawi', 'MW' UNION ALL
  SELECT 2136, 1136, 'name_en', 'Malaysia', 'MY' UNION ALL
  SELECT 2137, 1137, 'name_en', 'Maldives', 'MV' UNION ALL
  SELECT 2138, 1138, 'name_en', 'Mali', 'ML' UNION ALL
  SELECT 2139, 1139, 'name_en', 'Malta', 'MT' UNION ALL
  SELECT 2140, 1140, 'name_en', 'Marshall Islands', 'MH' UNION ALL
  SELECT 2141, 1141, 'name_en', 'Martinique', 'MQ' UNION ALL
  SELECT 2142, 1142, 'name_en', 'Mauritania', 'MR' UNION ALL
  SELECT 2143, 1143, 'name_en', 'Mauritius', 'MU' UNION ALL
  SELECT 2144, 1144, 'name_en', 'Mayotte', 'YT' UNION ALL
  SELECT 2145, 1145, 'name_en', 'Mexico', 'MX' UNION ALL
  SELECT 2146, 1146, 'name_en', 'Micronesia (Federated States)', 'FM' UNION ALL
  SELECT 2147, 1147, 'name_en', 'Moldova', 'MD' UNION ALL
  SELECT 2148, 1148, 'name_en', 'Monaco', 'MC' UNION ALL
  SELECT 2149, 1149, 'name_en', 'Mongolia', 'MN' UNION ALL
  SELECT 2150, 1150, 'name_en', 'Montenegro', 'ME' UNION ALL
  SELECT 2151, 1151, 'name_en', 'Montserrat', 'MS' UNION ALL
  SELECT 2152, 1152, 'name_en', 'Morocco', 'MA' UNION ALL
  SELECT 2153, 1153, 'name_en', 'Mozambique', 'MZ' UNION ALL
  SELECT 2154, 1154, 'name_en', 'Myanmar', 'MM' UNION ALL
  SELECT 2155, 1155, 'name_en', 'Namibia', 'NA' UNION ALL
  SELECT 2156, 1156, 'name_en', 'Nauru', 'NR' UNION ALL
  SELECT 2157, 1157, 'name_en', 'Nepal', 'NP' UNION ALL
  SELECT 2158, 1158, 'name_en', 'Netherlands', 'NL' UNION ALL
  SELECT 2159, 1159, 'name_en', 'New Caledonia', 'NC' UNION ALL
  SELECT 2160, 1160, 'name_en', 'New Zealand', 'NZ' UNION ALL
  SELECT 2161, 1161, 'name_en', 'Nicaragua', 'NI' UNION ALL
  SELECT 2162, 1162, 'name_en', 'Niger', 'NE' UNION ALL
  SELECT 2163, 1163, 'name_en', 'Nigeria', 'NG' UNION ALL
  SELECT 2164, 1164, 'name_en', 'Niue', 'NU' UNION ALL
  SELECT 2165, 1165, 'name_en', 'Norfolk Island', 'NF' UNION ALL
  SELECT 2166, 1166, 'name_en', 'Northern Mariana Islands', 'MP' UNION ALL
  SELECT 2167, 1167, 'name_en', 'Norway', 'NO' UNION ALL
  SELECT 2168, 1168, 'name_en', 'Oman', 'OM' UNION ALL
  SELECT 2169, 1169, 'name_en', 'Pakistan', 'PK' UNION ALL
  SELECT 2170, 1170, 'name_en', 'Palau', 'PW' UNION ALL
  SELECT 2171, 1171, 'name_en', 'Palestine, State of', 'PS' UNION ALL
  SELECT 2172, 1172, 'name_en', 'Panama', 'PA' UNION ALL
  SELECT 2173, 1173, 'name_en', 'Papua New Guinea', 'PG' UNION ALL
  SELECT 2174, 1174, 'name_en', 'Paraguay', 'PY' UNION ALL
  SELECT 2175, 1175, 'name_en', 'Peru', 'PE' UNION ALL
  SELECT 2176, 1176, 'name_en', 'Philippines', 'PH' UNION ALL
  SELECT 2177, 1177, 'name_en', 'Pitcairn', 'PN' UNION ALL
  SELECT 2178, 1178, 'name_en', 'Poland', 'PL' UNION ALL
  SELECT 2179, 1179, 'name_en', 'Portugal', 'PT' UNION ALL
  SELECT 2180, 1180, 'name_en', 'Puerto Rico', 'PR' UNION ALL
  SELECT 2181, 1181, 'name_en', 'Qatar', 'QA' UNION ALL
  SELECT 2182, 1182, 'name_en', 'Réunion', 'RE' UNION ALL
  SELECT 2183, 1183, 'name_en', 'Romania', 'RO' UNION ALL
  SELECT 2184, 1184, 'name_en', 'Russian Federation', 'RU' UNION ALL
  SELECT 2185, 1185, 'name_en', 'Rwanda', 'RW' UNION ALL
  SELECT 2186, 1186, 'name_en', 'Saint Barthélemy', 'BL' UNION ALL
  SELECT 2187, 1187, 'name_en', 'Saint Helena, Ascension and Tristan da Cunha', 'SH' UNION ALL
  SELECT 2188, 1188, 'name_en', 'Saint Kitts and Nevis', 'KN' UNION ALL
  SELECT 2189, 1189, 'name_en', 'Saint Lucia', 'LC' UNION ALL
  SELECT 2190, 1190, 'name_en', 'Saint Martin', 'MF' UNION ALL
  SELECT 2191, 1191, 'name_en', 'Saint Pierre and Miquelon', 'PM' UNION ALL
  SELECT 2192, 1192, 'name_en', 'Saint Vincent and the Grenadines', 'VC' UNION ALL
  SELECT 2193, 1193, 'name_en', 'Samoa', 'WS' UNION ALL
  SELECT 2194, 1194, 'name_en', 'San Marino', 'SM' UNION ALL
  SELECT 2195, 1195, 'name_en', 'Sao Tome and Principe', 'ST' UNION ALL
  SELECT 2196, 1196, 'name_en', 'Saudi Arabia', 'SA' UNION ALL
  SELECT 2197, 1197, 'name_en', 'Senegal', 'SN' UNION ALL
  SELECT 2198, 1198, 'name_en', 'Serbia', 'RS' UNION ALL
  SELECT 2199, 1199, 'name_en', 'Seychelles', 'SC' UNION ALL
  SELECT 2200, 1200, 'name_en', 'Sierra Leone', 'SL' UNION ALL
  SELECT 2201, 1201, 'name_en', 'Singapore', 'SG' UNION ALL
  SELECT 2202, 1202, 'name_en', 'Sint Maarten', 'SX' UNION ALL
  SELECT 2203, 1203, 'name_en', 'Slovakia', 'SK' UNION ALL
  SELECT 2204, 1204, 'name_en', 'Slovenia', 'SI' UNION ALL
  SELECT 2205, 1205, 'name_en', 'Solomon Islands', 'SB' UNION ALL
  SELECT 2206, 1206, 'name_en', 'Somalia', 'SO' UNION ALL
  SELECT 2207, 1207, 'name_en', 'South Africa', 'ZA' UNION ALL
  SELECT 2208, 1208, 'name_en', 'South Georgia and the South Sandwich Islands', 'GS' UNION ALL
  SELECT 2209, 1209, 'name_en', 'South Sudan', 'SS' UNION ALL
  SELECT 2210, 1210, 'name_en', 'Spain', 'ES' UNION ALL
  SELECT 2211, 1211, 'name_en', 'Sri Lanka', 'LK' UNION ALL
  SELECT 2212, 1212, 'name_en', 'Sudan', 'SD' UNION ALL
  SELECT 2213, 1213, 'name_en', 'Suriname', 'SR' UNION ALL
  SELECT 2214, 1214, 'name_en', 'Svalbard and Jan Mayen', 'SJ' UNION ALL
  SELECT 2215, 1215, 'name_en', 'Sweden', 'SE' UNION ALL
  SELECT 2216, 1216, 'name_en', 'Switzerland', 'CH' UNION ALL
  SELECT 2217, 1217, 'name_en', 'Syrian Arab Republic', 'SY' UNION ALL
  SELECT 2218, 1218, 'name_en', 'Taiwan', 'TW' UNION ALL
  SELECT 2219, 1219, 'name_en', 'Tajikistan', 'TJ' UNION ALL
  SELECT 2220, 1220, 'name_en', 'Tanzania, United Republic of', 'TZ' UNION ALL
  SELECT 2221, 1221, 'name_en', 'Thailand', 'TH' UNION ALL
  SELECT 2222, 1222, 'name_en', 'Timor-Leste', 'TL' UNION ALL
  SELECT 2223, 1223, 'name_en', 'Togo', 'TG' UNION ALL
  SELECT 2224, 1224, 'name_en', 'Tokelau', 'TK' UNION ALL
  SELECT 2225, 1225, 'name_en', 'Tonga', 'TO' UNION ALL
  SELECT 2226, 1226, 'name_en', 'Trinidad and Tobago', 'TT' UNION ALL
  SELECT 2227, 1227, 'name_en', 'Tunisia', 'TN' UNION ALL
  SELECT 2228, 1228, 'name_en', 'Turkey', 'TR' UNION ALL
  SELECT 2229, 1229, 'name_en', 'Turkmenistan', 'TM' UNION ALL
  SELECT 2230, 1230, 'name_en', 'Turks and Caicos Islands', 'TC' UNION ALL
  SELECT 2231, 1231, 'name_en', 'Tuvalu', 'TV' UNION ALL
  SELECT 2232, 1232, 'name_en', 'Uganda', 'UG' UNION ALL
  SELECT 2233, 1233, 'name_en', 'Ukraine', 'UA' UNION ALL
  SELECT 2234, 1234, 'name_en', 'United Arab Emirates', 'AE' UNION ALL
  SELECT 2235, 1235, 'name_en', 'United Kingdom', 'GB' UNION ALL
  SELECT 2236, 1236, 'name_en', 'United States Minor Outlying Islands', 'UM' UNION ALL
  SELECT 2237, 1237, 'name_en', 'United States of America', 'US' UNION ALL
  SELECT 2238, 1238, 'name_en', 'Uruguay', 'UY' UNION ALL
  SELECT 2239, 1239, 'name_en', 'Uzbekistan', 'UZ' UNION ALL
  SELECT 2240, 1240, 'name_en', 'Vanuatu', 'VU' UNION ALL
  SELECT 2241, 1241, 'name_en', 'Venezuela', 'VE' UNION ALL
  SELECT 2242, 1242, 'name_en', 'Viet Nam', 'VN' UNION ALL
  SELECT 2243, 1243, 'name_en', 'Virgin Islands (British)', 'VG' UNION ALL
  SELECT 2244, 1244, 'name_en', 'Virgin Islands (U.S.)', 'VI' UNION ALL
  SELECT 2245, 1245, 'name_en', 'Wallis and Futuna', 'WF' UNION ALL
  SELECT 2246, 1246, 'name_en', 'Western Sahara', 'EH' UNION ALL
  SELECT 2247, 1247, 'name_en', 'Yemen', 'YE' UNION ALL
  SELECT 2248, 1248, 'name_en', 'Zambia', 'ZM' UNION ALL
  SELECT 2249, 1249, 'name_en', 'Zimbabwe', 'ZW'
) AS source
ON CONFLICT (id) DO UPDATE SET
    dict_item_id = EXCLUDED.dict_item_id,
    source_standard = EXCLUDED.source_standard,
    external_code = EXCLUDED.external_code,
    external_label = EXCLUDED.external_label;


/* ====================================================================
 * 国家字典种子数据结束
 * ==================================================================== */


-- country ISO_3166_1_ALPHA2（规范标准）与 NAME_EN 标准
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
VALUES (
  2,
  'country',
  'ISO_3166_1_ALPHA2',
  'ISO 3166-1 alpha-2',
  '国家代码两字母标准（平台规范标准，item_code 采用此格式）',
  10,
  true,
  true
)
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;

INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
VALUES (
  3,
  'country',
  'NAME_EN',
  'English Name',
  '英文名称标准（用于名称型输入，需转换为 ISO 代码）',
  20,
  false,
  true
)
ON CONFLICT (id) DO UPDATE SET
    dict_type_code = EXCLUDED.dict_type_code,
    standard_code = EXCLUDED.standard_code,
    standard_name = EXCLUDED.standard_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    is_canonical = EXCLUDED.is_canonical,
    enabled = EXCLUDED.enabled;


-- country NAME_ZH 标准
INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled)
SELECT
  4,
  'country',
  'NAME_ZH',
  '中文名称',
  '中文国家/地区名称（CLDR 简体中文数据），用于前端本地化展示',
  30,
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
 * 第4部分: NAME_ZH 别名（249 条）
 * ==================================================================== */

INSERT INTO sys_dict_item_alias
(id, dict_item_id, source_standard, external_code, external_label)

SELECT * FROM (
  SELECT 6001 AS id, 1001 AS dict_item_id, 'name_zh' AS source_standard, '阿富汗' AS external_code, 'AF' AS external_label UNION ALL
  SELECT 6002, 1002, 'name_zh', '奥兰群岛', 'AX' UNION ALL
  SELECT 6003, 1003, 'name_zh', '阿尔巴尼亚', 'AL' UNION ALL
  SELECT 6004, 1004, 'name_zh', '阿尔及利亚', 'DZ' UNION ALL
  SELECT 6005, 1005, 'name_zh', '美属萨摩亚', 'AS' UNION ALL
  SELECT 6006, 1006, 'name_zh', '安道尔', 'AD' UNION ALL
  SELECT 6007, 1007, 'name_zh', '安哥拉', 'AO' UNION ALL
  SELECT 6008, 1008, 'name_zh', '安圭拉', 'AI' UNION ALL
  SELECT 6009, 1009, 'name_zh', '南极洲', 'AQ' UNION ALL
  SELECT 6010, 1010, 'name_zh', '安提瓜和巴布达', 'AG' UNION ALL
  SELECT 6011, 1011, 'name_zh', '阿根廷', 'AR' UNION ALL
  SELECT 6012, 1012, 'name_zh', '亚美尼亚', 'AM' UNION ALL
  SELECT 6013, 1013, 'name_zh', '阿鲁巴', 'AW' UNION ALL
  SELECT 6014, 1014, 'name_zh', '澳大利亚', 'AU' UNION ALL
  SELECT 6015, 1015, 'name_zh', '奥地利', 'AT' UNION ALL
  SELECT 6016, 1016, 'name_zh', '阿塞拜疆', 'AZ' UNION ALL
  SELECT 6017, 1017, 'name_zh', '巴哈马', 'BS' UNION ALL
  SELECT 6018, 1018, 'name_zh', '巴林', 'BH' UNION ALL
  SELECT 6019, 1019, 'name_zh', '孟加拉国', 'BD' UNION ALL
  SELECT 6020, 1020, 'name_zh', '巴巴多斯', 'BB' UNION ALL
  SELECT 6021, 1021, 'name_zh', '白俄罗斯', 'BY' UNION ALL
  SELECT 6022, 1022, 'name_zh', '比利时', 'BE' UNION ALL
  SELECT 6023, 1023, 'name_zh', '伯利兹', 'BZ' UNION ALL
  SELECT 6024, 1024, 'name_zh', '贝宁', 'BJ' UNION ALL
  SELECT 6025, 1025, 'name_zh', '百慕大', 'BM' UNION ALL
  SELECT 6026, 1026, 'name_zh', '不丹', 'BT' UNION ALL
  SELECT 6027, 1027, 'name_zh', '玻利维亚', 'BO' UNION ALL
  SELECT 6028, 1028, 'name_zh', '荷属加勒比区', 'BQ' UNION ALL
  SELECT 6029, 1029, 'name_zh', '波斯尼亚和黑塞哥维那', 'BA' UNION ALL
  SELECT 6030, 1030, 'name_zh', '博茨瓦纳', 'BW' UNION ALL
  SELECT 6031, 1031, 'name_zh', '布韦岛', 'BV' UNION ALL
  SELECT 6032, 1032, 'name_zh', '巴西', 'BR' UNION ALL
  SELECT 6033, 1033, 'name_zh', '英属印度洋领地', 'IO' UNION ALL
  SELECT 6034, 1034, 'name_zh', '文莱', 'BN' UNION ALL
  SELECT 6035, 1035, 'name_zh', '保加利亚', 'BG' UNION ALL
  SELECT 6036, 1036, 'name_zh', '布基纳法索', 'BF' UNION ALL
  SELECT 6037, 1037, 'name_zh', '布隆迪', 'BI' UNION ALL
  SELECT 6038, 1038, 'name_zh', '佛得角', 'CV' UNION ALL
  SELECT 6039, 1039, 'name_zh', '柬埔寨', 'KH' UNION ALL
  SELECT 6040, 1040, 'name_zh', '喀麦隆', 'CM' UNION ALL
  SELECT 6041, 1041, 'name_zh', '加拿大', 'CA' UNION ALL
  SELECT 6042, 1042, 'name_zh', '开曼群岛', 'KY' UNION ALL
  SELECT 6043, 1043, 'name_zh', '中非共和国', 'CF' UNION ALL
  SELECT 6044, 1044, 'name_zh', '乍得', 'TD' UNION ALL
  SELECT 6045, 1045, 'name_zh', '智利', 'CL' UNION ALL
  SELECT 6046, 1046, 'name_zh', '中国', 'CN' UNION ALL
  SELECT 6047, 1047, 'name_zh', '圣诞岛', 'CX' UNION ALL
  SELECT 6048, 1048, 'name_zh', '科科斯（基林）群岛', 'CC' UNION ALL
  SELECT 6049, 1049, 'name_zh', '哥伦比亚', 'CO' UNION ALL
  SELECT 6050, 1050, 'name_zh', '科摩罗', 'KM' UNION ALL
  SELECT 6051, 1051, 'name_zh', '刚果（金）', 'CD' UNION ALL
  SELECT 6052, 1052, 'name_zh', '刚果（布）', 'CG' UNION ALL
  SELECT 6053, 1053, 'name_zh', '库克群岛', 'CK' UNION ALL
  SELECT 6054, 1054, 'name_zh', '哥斯达黎加', 'CR' UNION ALL
  SELECT 6055, 1055, 'name_zh', '科特迪瓦', 'CI' UNION ALL
  SELECT 6056, 1056, 'name_zh', '克罗地亚', 'HR' UNION ALL
  SELECT 6057, 1057, 'name_zh', '古巴', 'CU' UNION ALL
  SELECT 6058, 1058, 'name_zh', '库拉索', 'CW' UNION ALL
  SELECT 6059, 1059, 'name_zh', '塞浦路斯', 'CY' UNION ALL
  SELECT 6060, 1060, 'name_zh', '捷克', 'CZ' UNION ALL
  SELECT 6061, 1061, 'name_zh', '丹麦', 'DK' UNION ALL
  SELECT 6062, 1062, 'name_zh', '吉布提', 'DJ' UNION ALL
  SELECT 6063, 1063, 'name_zh', '多米尼克', 'DM' UNION ALL
  SELECT 6064, 1064, 'name_zh', '多米尼加共和国', 'DO' UNION ALL
  SELECT 6065, 1065, 'name_zh', '厄瓜多尔', 'EC' UNION ALL
  SELECT 6066, 1066, 'name_zh', '埃及', 'EG' UNION ALL
  SELECT 6067, 1067, 'name_zh', '萨尔瓦多', 'SV' UNION ALL
  SELECT 6068, 1068, 'name_zh', '赤道几内亚', 'GQ' UNION ALL
  SELECT 6069, 1069, 'name_zh', '厄立特里亚', 'ER' UNION ALL
  SELECT 6070, 1070, 'name_zh', '爱沙尼亚', 'EE' UNION ALL
  SELECT 6071, 1071, 'name_zh', '斯威士兰', 'SZ' UNION ALL
  SELECT 6072, 1072, 'name_zh', '埃塞俄比亚', 'ET' UNION ALL
  SELECT 6073, 1073, 'name_zh', '福克兰群岛', 'FK' UNION ALL
  SELECT 6074, 1074, 'name_zh', '法罗群岛', 'FO' UNION ALL
  SELECT 6075, 1075, 'name_zh', '斐济', 'FJ' UNION ALL
  SELECT 6076, 1076, 'name_zh', '芬兰', 'FI' UNION ALL
  SELECT 6077, 1077, 'name_zh', '法国', 'FR' UNION ALL
  SELECT 6078, 1078, 'name_zh', '法属圭亚那', 'GF' UNION ALL
  SELECT 6079, 1079, 'name_zh', '法属波利尼西亚', 'PF' UNION ALL
  SELECT 6080, 1080, 'name_zh', '法属南部领地', 'TF' UNION ALL
  SELECT 6081, 1081, 'name_zh', '加蓬', 'GA' UNION ALL
  SELECT 6082, 1082, 'name_zh', '冈比亚', 'GM' UNION ALL
  SELECT 6083, 1083, 'name_zh', '格鲁吉亚', 'GE' UNION ALL
  SELECT 6084, 1084, 'name_zh', '德国', 'DE' UNION ALL
  SELECT 6085, 1085, 'name_zh', '加纳', 'GH' UNION ALL
  SELECT 6086, 1086, 'name_zh', '直布罗陀', 'GI' UNION ALL
  SELECT 6087, 1087, 'name_zh', '希腊', 'GR' UNION ALL
  SELECT 6088, 1088, 'name_zh', '格陵兰', 'GL' UNION ALL
  SELECT 6089, 1089, 'name_zh', '格林纳达', 'GD' UNION ALL
  SELECT 6090, 1090, 'name_zh', '瓜德罗普', 'GP' UNION ALL
  SELECT 6091, 1091, 'name_zh', '关岛', 'GU' UNION ALL
  SELECT 6092, 1092, 'name_zh', '危地马拉', 'GT' UNION ALL
  SELECT 6093, 1093, 'name_zh', '根西岛', 'GG' UNION ALL
  SELECT 6094, 1094, 'name_zh', '几内亚', 'GN' UNION ALL
  SELECT 6095, 1095, 'name_zh', '几内亚比绍', 'GW' UNION ALL
  SELECT 6096, 1096, 'name_zh', '圭亚那', 'GY' UNION ALL
  SELECT 6097, 1097, 'name_zh', '海地', 'HT' UNION ALL
  SELECT 6098, 1098, 'name_zh', '赫德岛和麦克唐纳群岛', 'HM' UNION ALL
  SELECT 6099, 1099, 'name_zh', '梵蒂冈', 'VA' UNION ALL
  SELECT 6100, 1100, 'name_zh', '洪都拉斯', 'HN' UNION ALL
  SELECT 6101, 1101, 'name_zh', '中国香港', 'HK' UNION ALL
  SELECT 6102, 1102, 'name_zh', '匈牙利', 'HU' UNION ALL
  SELECT 6103, 1103, 'name_zh', '冰岛', 'IS' UNION ALL
  SELECT 6104, 1104, 'name_zh', '印度', 'IN' UNION ALL
  SELECT 6105, 1105, 'name_zh', '印度尼西亚', 'ID' UNION ALL
  SELECT 6106, 1106, 'name_zh', '伊朗', 'IR' UNION ALL
  SELECT 6107, 1107, 'name_zh', '伊拉克', 'IQ' UNION ALL
  SELECT 6108, 1108, 'name_zh', '爱尔兰', 'IE' UNION ALL
  SELECT 6109, 1109, 'name_zh', '马恩岛', 'IM' UNION ALL
  SELECT 6110, 1110, 'name_zh', '以色列', 'IL' UNION ALL
  SELECT 6111, 1111, 'name_zh', '意大利', 'IT' UNION ALL
  SELECT 6112, 1112, 'name_zh', '牙买加', 'JM' UNION ALL
  SELECT 6113, 1113, 'name_zh', '日本', 'JP' UNION ALL
  SELECT 6114, 1114, 'name_zh', '泽西岛', 'JE' UNION ALL
  SELECT 6115, 1115, 'name_zh', '约旦', 'JO' UNION ALL
  SELECT 6116, 1116, 'name_zh', '哈萨克斯坦', 'KZ' UNION ALL
  SELECT 6117, 1117, 'name_zh', '肯尼亚', 'KE' UNION ALL
  SELECT 6118, 1118, 'name_zh', '基里巴斯', 'KI' UNION ALL
  SELECT 6119, 1119, 'name_zh', '朝鲜', 'KP' UNION ALL
  SELECT 6120, 1120, 'name_zh', '韩国', 'KR' UNION ALL
  SELECT 6121, 1121, 'name_zh', '科威特', 'KW' UNION ALL
  SELECT 6122, 1122, 'name_zh', '吉尔吉斯斯坦', 'KG' UNION ALL
  SELECT 6123, 1123, 'name_zh', '老挝', 'LA' UNION ALL
  SELECT 6124, 1124, 'name_zh', '拉脱维亚', 'LV' UNION ALL
  SELECT 6125, 1125, 'name_zh', '黎巴嫩', 'LB' UNION ALL
  SELECT 6126, 1126, 'name_zh', '莱索托', 'LS' UNION ALL
  SELECT 6127, 1127, 'name_zh', '利比里亚', 'LR' UNION ALL
  SELECT 6128, 1128, 'name_zh', '利比亚', 'LY' UNION ALL
  SELECT 6129, 1129, 'name_zh', '列支敦士登', 'LI' UNION ALL
  SELECT 6130, 1130, 'name_zh', '立陶宛', 'LT' UNION ALL
  SELECT 6131, 1131, 'name_zh', '卢森堡', 'LU' UNION ALL
  SELECT 6132, 1132, 'name_zh', '中国澳门', 'MO' UNION ALL
  SELECT 6133, 1133, 'name_zh', '北马其顿', 'MK' UNION ALL
  SELECT 6134, 1134, 'name_zh', '马达加斯加', 'MG' UNION ALL
  SELECT 6135, 1135, 'name_zh', '马拉维', 'MW' UNION ALL
  SELECT 6136, 1136, 'name_zh', '马来西亚', 'MY' UNION ALL
  SELECT 6137, 1137, 'name_zh', '马尔代夫', 'MV' UNION ALL
  SELECT 6138, 1138, 'name_zh', '马里', 'ML' UNION ALL
  SELECT 6139, 1139, 'name_zh', '马耳他', 'MT' UNION ALL
  SELECT 6140, 1140, 'name_zh', '马绍尔群岛', 'MH' UNION ALL
  SELECT 6141, 1141, 'name_zh', '马提尼克', 'MQ' UNION ALL
  SELECT 6142, 1142, 'name_zh', '毛里塔尼亚', 'MR' UNION ALL
  SELECT 6143, 1143, 'name_zh', '毛里求斯', 'MU' UNION ALL
  SELECT 6144, 1144, 'name_zh', '马约特', 'YT' UNION ALL
  SELECT 6145, 1145, 'name_zh', '墨西哥', 'MX' UNION ALL
  SELECT 6146, 1146, 'name_zh', '密克罗尼西亚联邦', 'FM' UNION ALL
  SELECT 6147, 1147, 'name_zh', '摩尔多瓦', 'MD' UNION ALL
  SELECT 6148, 1148, 'name_zh', '摩纳哥', 'MC' UNION ALL
  SELECT 6149, 1149, 'name_zh', '蒙古', 'MN' UNION ALL
  SELECT 6150, 1150, 'name_zh', '黑山', 'ME' UNION ALL
  SELECT 6151, 1151, 'name_zh', '蒙特塞拉特', 'MS' UNION ALL
  SELECT 6152, 1152, 'name_zh', '摩洛哥', 'MA' UNION ALL
  SELECT 6153, 1153, 'name_zh', '莫桑比克', 'MZ' UNION ALL
  SELECT 6154, 1154, 'name_zh', '缅甸', 'MM' UNION ALL
  SELECT 6155, 1155, 'name_zh', '纳米比亚', 'NA' UNION ALL
  SELECT 6156, 1156, 'name_zh', '瑙鲁', 'NR' UNION ALL
  SELECT 6157, 1157, 'name_zh', '尼泊尔', 'NP' UNION ALL
  SELECT 6158, 1158, 'name_zh', '荷兰', 'NL' UNION ALL
  SELECT 6159, 1159, 'name_zh', '新喀里多尼亚', 'NC' UNION ALL
  SELECT 6160, 1160, 'name_zh', '新西兰', 'NZ' UNION ALL
  SELECT 6161, 1161, 'name_zh', '尼加拉瓜', 'NI' UNION ALL
  SELECT 6162, 1162, 'name_zh', '尼日尔', 'NE' UNION ALL
  SELECT 6163, 1163, 'name_zh', '尼日利亚', 'NG' UNION ALL
  SELECT 6164, 1164, 'name_zh', '纽埃', 'NU' UNION ALL
  SELECT 6165, 1165, 'name_zh', '诺福克岛', 'NF' UNION ALL
  SELECT 6166, 1166, 'name_zh', '北马里亚纳群岛', 'MP' UNION ALL
  SELECT 6167, 1167, 'name_zh', '挪威', 'NO' UNION ALL
  SELECT 6168, 1168, 'name_zh', '阿曼', 'OM' UNION ALL
  SELECT 6169, 1169, 'name_zh', '巴基斯坦', 'PK' UNION ALL
  SELECT 6170, 1170, 'name_zh', '帕劳', 'PW' UNION ALL
  SELECT 6171, 1171, 'name_zh', '巴勒斯坦', 'PS' UNION ALL
  SELECT 6172, 1172, 'name_zh', '巴拿马', 'PA' UNION ALL
  SELECT 6173, 1173, 'name_zh', '巴布亚新几内亚', 'PG' UNION ALL
  SELECT 6174, 1174, 'name_zh', '巴拉圭', 'PY' UNION ALL
  SELECT 6175, 1175, 'name_zh', '秘鲁', 'PE' UNION ALL
  SELECT 6176, 1176, 'name_zh', '菲律宾', 'PH' UNION ALL
  SELECT 6177, 1177, 'name_zh', '皮特凯恩群岛', 'PN' UNION ALL
  SELECT 6178, 1178, 'name_zh', '波兰', 'PL' UNION ALL
  SELECT 6179, 1179, 'name_zh', '葡萄牙', 'PT' UNION ALL
  SELECT 6180, 1180, 'name_zh', '波多黎各', 'PR' UNION ALL
  SELECT 6181, 1181, 'name_zh', '卡塔尔', 'QA' UNION ALL
  SELECT 6182, 1182, 'name_zh', '留尼汪', 'RE' UNION ALL
  SELECT 6183, 1183, 'name_zh', '罗马尼亚', 'RO' UNION ALL
  SELECT 6184, 1184, 'name_zh', '俄罗斯', 'RU' UNION ALL
  SELECT 6185, 1185, 'name_zh', '卢旺达', 'RW' UNION ALL
  SELECT 6186, 1186, 'name_zh', '圣巴泰勒米', 'BL' UNION ALL
  SELECT 6187, 1187, 'name_zh', '圣赫勒拿、阿森松和特里斯坦-达库尼亚', 'SH' UNION ALL
  SELECT 6188, 1188, 'name_zh', '圣基茨和尼维斯', 'KN' UNION ALL
  SELECT 6189, 1189, 'name_zh', '圣卢西亚', 'LC' UNION ALL
  SELECT 6190, 1190, 'name_zh', '法属圣马丁', 'MF' UNION ALL
  SELECT 6191, 1191, 'name_zh', '圣皮埃尔和密克隆', 'PM' UNION ALL
  SELECT 6192, 1192, 'name_zh', '圣文森特和格林纳丁斯', 'VC' UNION ALL
  SELECT 6193, 1193, 'name_zh', '萨摩亚', 'WS' UNION ALL
  SELECT 6194, 1194, 'name_zh', '圣马力诺', 'SM' UNION ALL
  SELECT 6195, 1195, 'name_zh', '圣多美和普林西比', 'ST' UNION ALL
  SELECT 6196, 1196, 'name_zh', '沙特阿拉伯', 'SA' UNION ALL
  SELECT 6197, 1197, 'name_zh', '塞内加尔', 'SN' UNION ALL
  SELECT 6198, 1198, 'name_zh', '塞尔维亚', 'RS' UNION ALL
  SELECT 6199, 1199, 'name_zh', '塞舌尔', 'SC' UNION ALL
  SELECT 6200, 1200, 'name_zh', '塞拉利昂', 'SL' UNION ALL
  SELECT 6201, 1201, 'name_zh', '新加坡', 'SG' UNION ALL
  SELECT 6202, 1202, 'name_zh', '荷属圣马丁', 'SX' UNION ALL
  SELECT 6203, 1203, 'name_zh', '斯洛伐克', 'SK' UNION ALL
  SELECT 6204, 1204, 'name_zh', '斯洛文尼亚', 'SI' UNION ALL
  SELECT 6205, 1205, 'name_zh', '所罗门群岛', 'SB' UNION ALL
  SELECT 6206, 1206, 'name_zh', '索马里', 'SO' UNION ALL
  SELECT 6207, 1207, 'name_zh', '南非', 'ZA' UNION ALL
  SELECT 6208, 1208, 'name_zh', '南乔治亚和南桑威奇群岛', 'GS' UNION ALL
  SELECT 6209, 1209, 'name_zh', '南苏丹', 'SS' UNION ALL
  SELECT 6210, 1210, 'name_zh', '西班牙', 'ES' UNION ALL
  SELECT 6211, 1211, 'name_zh', '斯里兰卡', 'LK' UNION ALL
  SELECT 6212, 1212, 'name_zh', '苏丹', 'SD' UNION ALL
  SELECT 6213, 1213, 'name_zh', '苏里南', 'SR' UNION ALL
  SELECT 6214, 1214, 'name_zh', '斯瓦尔巴和扬马延', 'SJ' UNION ALL
  SELECT 6215, 1215, 'name_zh', '瑞典', 'SE' UNION ALL
  SELECT 6216, 1216, 'name_zh', '瑞士', 'CH' UNION ALL
  SELECT 6217, 1217, 'name_zh', '叙利亚', 'SY' UNION ALL
  SELECT 6218, 1218, 'name_zh', '中国台湾', 'TW' UNION ALL
  SELECT 6219, 1219, 'name_zh', '塔吉克斯坦', 'TJ' UNION ALL
  SELECT 6220, 1220, 'name_zh', '坦桑尼亚', 'TZ' UNION ALL
  SELECT 6221, 1221, 'name_zh', '泰国', 'TH' UNION ALL
  SELECT 6222, 1222, 'name_zh', '东帝汶', 'TL' UNION ALL
  SELECT 6223, 1223, 'name_zh', '多哥', 'TG' UNION ALL
  SELECT 6224, 1224, 'name_zh', '托克劳', 'TK' UNION ALL
  SELECT 6225, 1225, 'name_zh', '汤加', 'TO' UNION ALL
  SELECT 6226, 1226, 'name_zh', '特立尼达和多巴哥', 'TT' UNION ALL
  SELECT 6227, 1227, 'name_zh', '突尼斯', 'TN' UNION ALL
  SELECT 6228, 1228, 'name_zh', '土耳其', 'TR' UNION ALL
  SELECT 6229, 1229, 'name_zh', '土库曼斯坦', 'TM' UNION ALL
  SELECT 6230, 1230, 'name_zh', '特克斯和凯科斯群岛', 'TC' UNION ALL
  SELECT 6231, 1231, 'name_zh', '图瓦卢', 'TV' UNION ALL
  SELECT 6232, 1232, 'name_zh', '乌干达', 'UG' UNION ALL
  SELECT 6233, 1233, 'name_zh', '乌克兰', 'UA' UNION ALL
  SELECT 6234, 1234, 'name_zh', '阿联酋', 'AE' UNION ALL
  SELECT 6235, 1235, 'name_zh', '英国', 'GB' UNION ALL
  SELECT 6236, 1236, 'name_zh', '美国本土外小岛屿', 'UM' UNION ALL
  SELECT 6237, 1237, 'name_zh', '美国', 'US' UNION ALL
  SELECT 6238, 1238, 'name_zh', '乌拉圭', 'UY' UNION ALL
  SELECT 6239, 1239, 'name_zh', '乌兹别克斯坦', 'UZ' UNION ALL
  SELECT 6240, 1240, 'name_zh', '瓦努阿图', 'VU' UNION ALL
  SELECT 6241, 1241, 'name_zh', '委内瑞拉', 'VE' UNION ALL
  SELECT 6242, 1242, 'name_zh', '越南', 'VN' UNION ALL
  SELECT 6243, 1243, 'name_zh', '英属维尔京群岛', 'VG' UNION ALL
  SELECT 6244, 1244, 'name_zh', '美属维尔京群岛', 'VI' UNION ALL
  SELECT 6245, 1245, 'name_zh', '瓦利斯和富图纳', 'WF' UNION ALL
  SELECT 6246, 1246, 'name_zh', '西撒哈拉', 'EH' UNION ALL
  SELECT 6247, 1247, 'name_zh', '也门', 'YE' UNION ALL
  SELECT 6248, 1248, 'name_zh', '赞比亚', 'ZM' UNION ALL
  SELECT 6249, 1249, 'name_zh', '津巴布韦', 'ZW'
) AS source
ON CONFLICT (id) DO UPDATE SET
    dict_item_id = EXCLUDED.dict_item_id,
    source_standard = EXCLUDED.source_standard,
    external_code = EXCLUDED.external_code,
    external_label = EXCLUDED.external_label;

INSERT INTO patra_registry.`reg_provenance` (provenance_code, provenance_name, base_url_default, timezone_default,
                                             docs_url, is_active,
                                             lifecycle_status_code, record_remarks, created_at, created_by,
                                             created_by_name,
                                             updated_at, updated_by, updated_by_name, version, ip_address, deleted)
VALUES ('PUBMED', 'PubMed', 'https://eutils.ncbi.nlm.nih.gov/entrez/eutils/', 'UTC',
        'https://www.ncbi.nlm.nih.gov/books/NBK25501/', 1, 'ACTIVE',
        JSON_ARRAY(JSON_OBJECT('time', '2025-09-01 10:30:00', 'by', '系统管理员', 'note', '初始化注册 PubMed 来源')),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.10'), 0),
       ('EPMC', 'Europe PMC', 'https://www.ebi.ac.uk/europepmc/webservices/rest/', 'UTC',
        'https://europepmc.org/RestfulWebService', 1, 'ACTIVE',
        JSON_ARRAY(JSON_OBJECT('time', '2025-09-01 10:45:00', 'by', '系统管理员', 'note',
                               '初始化注册 Europe PMC 来源')), NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员',
        0, INET6_ATON('192.168.1.11'), 0);


INSERT INTO patra_registry.reg_prov_window_offset_cfg (id, provenance_id, scope_code, task_type, effective_from,
                                                       effective_to, window_mode_code, window_size_value,
                                                       window_size_unit_code, calendar_align_to, lookback_value,
                                                       lookback_unit_code, overlap_value, overlap_unit_code,
                                                       watermark_lag_seconds, offset_type_code, offset_field_name,
                                                       offset_date_format, default_date_field_name, max_ids_per_window,
                                                       max_window_span_seconds, lifecycle_status_code, record_remarks,
                                                       created_at, created_by, created_by_name, updated_at, updated_by,
                                                       updated_by_name, version, ip_address, deleted)
VALUES (1, 1, 'TASK', 'HARVEST', '2025-09-01 00:00:00.000000', null, 'CALENDAR', 1, 'DAY', 'DAY', 2, 'HOUR', 1, 'HOUR',
        3600, 'DATE', 'EDAT', 'YYYYMMDD', 'EDAT', 50000, 2592000, 'ACTIVE', '[
    {
      "by": "系统管理员",
      "note": "PubMed Harvest 任务窗口配置",
      "time": "2025-09-01 12:00:00"
    }
  ]', '2025-09-24 21:47:47.971285', 1001, '系统管理员', '2025-09-24 21:53:41.720510', 1001, '系统管理员', 0, 0xC0A80114,
        0);
INSERT INTO patra_registry.reg_prov_window_offset_cfg (id, provenance_id, scope_code, task_type, effective_from,
                                                       effective_to, window_mode_code, window_size_value,
                                                       window_size_unit_code, calendar_align_to, lookback_value,
                                                       lookback_unit_code, overlap_value, overlap_unit_code,
                                                       watermark_lag_seconds, offset_type_code, offset_field_name,
                                                       offset_date_format, default_date_field_name, max_ids_per_window,
                                                       max_window_span_seconds, lifecycle_status_code, record_remarks,
                                                       created_at, created_by, created_by_name, updated_at, updated_by,
                                                       updated_by_name, version, ip_address, deleted)
VALUES (2, 1, 'SOURCE', null, '2025-09-01 00:00:00.000000', null, 'CALENDAR', 7, 'DAY', 'DAY', 1, 'DAY', null, null,
        86400, 'DATE', 'PDAT', 'YYYYMMDD', 'PDAT', null, 7776000, 'ACTIVE', '[
    {
      "by": "系统管理员",
      "note": "PubMed 来源级窗口配置",
      "time": "2025-09-01 12:05:00"
    }
  ]', '2025-09-24 21:47:47.971285', 1001, '系统管理员', '2025-09-24 21:47:47.971285', 1001, '系统管理员', 0, 0xC0A80115,
        0);

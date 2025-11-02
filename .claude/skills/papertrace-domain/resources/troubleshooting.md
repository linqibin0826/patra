# 故障排除指南

## 概览

基于实际数据库模式和架构的常见 Papertrace 问题诊断指南。

---

## Plan Issues

### Issue: Plan Stuck in SLICING

**Symptoms**:
- Plan status: `SLICING` for hours
- No PlanSlice records created
- No error logs

**Diagnosis**:
```sql
SELECT id, plan_key, status_code, slice_strategy_code, created_at
FROM ing_plan
WHERE status_code = 'SLICING'
  AND created_at < NOW() - INTERVAL 1 HOUR;
```

**Root Causes**:
1. SlicePlanner threw exception (check logs: `SlicePlanningException`)
2. Transaction rollback during slice creation
3. WindowSpec JSON invalid (check `window_spec_json` column)

**Solutions**:
```sql
-- Check window spec
SELECT id, window_spec_json
FROM ing_plan
WHERE id = <plan_id>;

-- Manually transition to ARCHIVED if unrecoverable
UPDATE ing_plan
SET status_code = 'ARCHIVED',
    archived_at = NOW(),
    archive_reason = 'Slicing failed - manual intervention'
WHERE id = <plan_id>;
```

---

### Issue: Plan Missing Expression Prototype

**Symptoms**:
- Plan created but slices fail
- Error: `expr_proto_snapshot_json is NULL`

**Diagnosis**:
```sql
SELECT id, expr_proto_hash, expr_proto_snapshot_json
FROM ing_plan
WHERE id = <plan_id>;
```

**Root Cause**: Registry API returned null/empty ExprSnapshot

**Solutions**:
```sql
-- Verify registry has expression for this operation
-- Check patra-registry database
SELECT *
FROM reg_expr_field_dict f
JOIN reg_expr_capability c ON f.id = c.field_id
WHERE c.id IN (
  SELECT capability_id
  FROM reg_prov_expr_render_rule
  WHERE provenance_id = <provenance_id>
);
```

---

## PlanSlice Issues

### Issue: Slice Without Task

**Symptoms**:
- PlanSlice exists (status: `PENDING`)
- No corresponding Task record
- Violation of 1:1 constraint

**Diagnosis**:
```sql
SELECT ps.id, ps.slice_no, ps.status_code, t.id as task_id
FROM ing_plan_slice ps
LEFT JOIN ing_task t ON t.slice_id = ps.id
WHERE ps.plan_id = <plan_id>
  AND t.id IS NULL;
```

**Root Cause**: Transaction failure after slice creation, before task creation

**Solutions**:
```sql
-- Create missing task
INSERT INTO ing_task (
  slice_id, idempotent_key, status_code,
  params_json, created_at
)
SELECT
  ps.id,
  CONCAT('retry-', ps.id),  -- Temporary key
  'QUEUED',
  '{}',
  NOW()
FROM ing_plan_slice ps
WHERE ps.id = <slice_id>
  AND NOT EXISTS (SELECT 1 FROM ing_task WHERE slice_id = ps.id);
```

---

### Issue: Slice Signature Hash Collision

**Symptoms**:
- Different slices have same `slice_signature_hash`
- UNIQUE constraint violation on slice creation

**Diagnosis**:
```sql
SELECT slice_signature_hash, COUNT(*) as count
FROM ing_plan_slice
WHERE plan_id = <plan_id>
GROUP BY slice_signature_hash
HAVING count > 1;
```

**Root Cause**: WindowSpec canonicalization not deterministic

**Solutions**: Report to development team (canonicalizer bug)

---

## Task Issues

### Issue: Task Stuck in RUNNING

**Symptoms**:
- Task status: `RUNNING`
- Lease expired (`leased_until < NOW()`)
- No progress for hours

**Diagnosis**:
```sql
SELECT
  id,
  idempotent_key,
  status_code,
  lease_owner,
  leased_until,
  NOW() as current_time,
  TIMESTAMPDIFF(MINUTE, leased_until, NOW()) as lease_expired_minutes
FROM ing_task
WHERE status_code = 'RUNNING'
  AND leased_until < NOW();
```

**Root Cause**: Worker crashed/killed without releasing lease

**Solutions**:
```sql
-- Release expired lease
UPDATE ing_task
SET status_code = 'QUEUED',
    lease_owner = NULL,
    leased_until = NULL
WHERE id = <task_id>
  AND leased_until < NOW();
```

---

### Issue: Task Idempotency Key Collision

**Symptoms**:
- Cannot create task
- Error: `Duplicate entry for key 'uk_task_idempotent_key'`

**Diagnosis**:
```sql
SELECT id, slice_id, idempotent_key, status_code
FROM ing_task
WHERE idempotent_key = <key>;
```

**Root Cause**: Retry logic attempting to create duplicate task

**Solutions**: Check existing task status; if SUCCEEDED/FAILED, ignore. If QUEUED/RUNNING, reuse.

---

## Lease Issues

### Issue: Lease Never Released

**Symptoms**:
- Task completed (SUCCEEDED/FAILED) but lease still held
- `lease_owner` not NULL
- `leased_until` in future

**Diagnosis**:
```sql
SELECT id, status_code, lease_owner, leased_until
FROM ing_task
WHERE status_code IN ('SUCCEEDED', 'FAILED')
  AND lease_owner IS NOT NULL;
```

**Root Cause**: Lease release logic bug

**Solutions**:
```sql
-- Manually release lease
UPDATE ing_task
SET lease_owner = NULL,
    leased_until = NULL
WHERE status_code IN ('SUCCEEDED', 'FAILED')
  AND lease_owner IS NOT NULL;
```

---

## Outbox Issues

### Issue: Outbox Message Accumulation

**Symptoms**:
- `ing_outbox_message` table growing
- Messages stuck in `PENDING` status
- Events not published to MQ

**Diagnosis**:
```sql
SELECT
  status,
  COUNT(*) as count,
  MIN(created_at) as oldest_message
FROM ing_outbox_message
GROUP BY status;
```

**Root Cause**: Outbox relay not running or MQ connection failure

**Solutions**:
```bash
# Check outbox relay logs
grep "OutboxRelay" application.log | tail -20

# Restart outbox relay (if using scheduled task)
# Check Spring @Scheduled annotation on OutboxRelay
```

---

### Issue: Outbox Message Failed Publishing

**Symptoms**:
- Messages status: `FAILED`
- Error in `error_message` column

**Diagnosis**:
```sql
SELECT id, event_type, status, error_message, retry_count
FROM ing_outbox_message
WHERE status = 'FAILED'
ORDER BY created_at DESC
LIMIT 10;
```

**Root Cause**: MQ reject (e.g., invalid payload, queue not found)

**Solutions**:
```sql
-- Retry failed messages
UPDATE ing_outbox_message
SET status = 'PENDING',
    retry_count = retry_count + 1
WHERE status = 'FAILED'
  AND retry_count < 3;
```

---

## Configuration Issues

### Issue: Configuration Not Found

**Symptoms**:
- Task fails with `ConfigurationNotFoundException`
- Error: `"No window_offset config for provenance X, operation Y"`

**Diagnosis**:
```sql
-- Check if config exists
SELECT provenance_id, operation_type_code, window_mode_code
FROM reg_prov_window_offset_cfg
WHERE provenance_id = <provenance_id>
  AND (operation_type_code = '<operation>' OR operation_type_code = 'ALL');
```

**Root Cause**: Missing config row in registry

**Solutions**:
```sql
-- Create default config
INSERT INTO reg_prov_window_offset_cfg (
  provenance_id, operation_type_code, window_mode_code,
  window_size_value, window_size_unit
)
VALUES (
  <provenance_id>, 'ALL', 'SLIDING',
  7, 'DAYS'
);
```

---

### Issue: Wrong Config Precedence

**Symptoms**:
- Specific operation config ignored
- Uses 'ALL' config instead

**Diagnosis**:
```sql
-- Check both specific and 'ALL' configs
SELECT operation_type_code, window_mode_code, created_at
FROM reg_prov_window_offset_cfg
WHERE provenance_id = <provenance_id>
  AND operation_type_code IN ('<specific_op>', 'ALL')
ORDER BY operation_type_code DESC;  -- Specific should come first
```

**Root Cause**: Config resolution logic bug (should prefer specific over 'ALL')

**Solutions**: Verify ConfigResolver uses: `specific operation_type > 'ALL'` precedence

---

## Expression Issues

### Issue: Expression Field Not Found

**Symptoms**:
- Task fails: `ExpressionFieldNotFoundException`
- Error: `"Field 'publication_date' not found"`

**Diagnosis**:
```sql
-- Check field dictionary
SELECT id, field_key, field_name, field_type_code
FROM reg_expr_field_dict
WHERE field_key = 'publication_date';
```

**Root Cause**: Missing field definition in registry

**Solutions**:
```sql
-- Add missing field
INSERT INTO reg_expr_field_dict (field_key, field_name, field_type_code)
VALUES ('publication_date', 'Publication Date', 'DATE');
```

---

### Issue: Expression Render Rule Not Found

**Symptoms**:
- Expression field exists but rendering fails
- Error: `"No render rule for capability RANGE"`

**Diagnosis**:
```sql
-- Check render rule exists
SELECT rr.id, rr.template, rr.emission_mode_code, rr.params
FROM reg_expr_field_dict f
JOIN reg_expr_capability c ON f.id = c.field_id
JOIN reg_prov_expr_render_rule rr ON c.id = rr.capability_id
WHERE f.field_key = 'publication_date'
  AND c.operation_code = 'RANGE'
  AND rr.provenance_id = <provenance_id>;
```

**Root Cause**: Missing render rule for this provenance + capability

**Solutions**:
```sql
-- Add render rule
INSERT INTO reg_prov_expr_render_rule (
  provenance_id, capability_id, emission_mode_code, params
)
VALUES (
  <provenance_id>,
  <capability_id>,
  'PARAMS',
  '{"from":"{{from}}", "to":"{{to}}", "datetype":"edat"}'
);
```

---

## Diagnostic Commands

### Database Queries

**Check Plan Distribution**:
```sql
SELECT status_code, COUNT(*)
FROM ing_plan
GROUP BY status_code;
```

**Find Expired Leases**:
```sql
SELECT id, idempotent_key, lease_owner, leased_until
FROM ing_task
WHERE status_code = 'RUNNING'
  AND leased_until < NOW();
```

**Check Outbox Backlog**:
```sql
SELECT status, COUNT(*), MIN(created_at) as oldest
FROM ing_outbox_message
GROUP BY status;
```

**Find Missing Slices**:
```sql
SELECT p.id, p.plan_key, COUNT(ps.id) as slice_count
FROM ing_plan p
LEFT JOIN ing_plan_slice ps ON p.id = ps.plan_id
WHERE p.status_code = 'READY'
GROUP BY p.id
HAVING slice_count = 0;
```

### Logs

**Filter by Plan Key**:
```bash
grep "plan_key=harvest-pubmed-20241102" application.log
```

**Watch Task Execution**:
```bash
tail -f application.log | grep --color=always "Task.*SUCCEEDED\|Task.*FAILED"
```

**Check Lease Acquisition**:
```bash
grep "Lease acquired" application.log | tail -20
```

---

## Best Practices

1. **Check Temporal Validity**: Always verify `created_at`, `leased_until` timestamps
2. **Respect State Machines**: Never manually set invalid state transitions
3. **Monitor Outbox**: Watch for `PENDING` message accumulation
4. **Verify 1:1 Constraints**: Each Slice must have exactly ONE Task
5. **Use Idempotency Keys**: Never create duplicate tasks with same `idempotent_key`

---

## Summary

**Most Common Issues**:
- Expired leases (task stuck RUNNING)
- Missing config (7-table structure)
- Outbox accumulation (relay not running)
- Expression render rule missing (4-table structure)

**Key Tables**:
- `ing_plan`, `ing_plan_slice`, `ing_task` (ingest entities)
- `ing_outbox_message` (event publishing)
- `reg_prov_*_cfg` (7 config tables)
- `reg_expr_*` (4 expression tables)

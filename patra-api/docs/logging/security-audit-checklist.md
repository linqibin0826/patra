# Logging Security Audit Checklist

**Version**: 1.0
**Last Updated**: 2025-10-17
**Audit Frequency**: Monthly
**Compliance**: GDPR, HIPAA, PCI-DSS

---

## Overview

This checklist ensures the Papertrace logging system maintains compliance with security and privacy requirements by verifying sensitive data sanitization, audit logging, and access controls.

---

## Monthly Audit Checklist

### 1. Sensitive Data Sanitization Verification

**Requirement**: FR-008, SC-006 - Zero sensitive data in logs

#### 1.1 Automated PII Scanning

- [ ] **Run automated PII scanner on production logs** (last 24 hours)
  ```bash
  ./scripts/scan-logs-for-pii.sh --env production --last-24h
  ```

- [ ] **Verify zero violations detected**
  - Expected result: `0 violations found`
  - If violations found: Escalate immediately to security team

- [ ] **Review sanitization coverage report**
  ```bash
  cat /tmp/pii-scan-report-$(date +%Y%m%d).txt
  ```

#### 1.2 Manual Sampling Verification

- [ ] **Sample 1000 random log lines from each service**
  ```bash
  for service in registry ingest gateway egress-gateway; do
    shuf -n 1000 /var/log/patra/patra-$service.log > /tmp/sample-$service.txt
  done
  ```

- [ ] **Check for common PII patterns**
  - [ ] No email addresses: `grep -E "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}" /tmp/sample-*.txt`
  - [ ] No phone numbers: `grep -E "\+?[0-9]{1,3}[-. ()]?[0-9]{1,4}[-. ()]?[0-9]{1,4}[-. ()]?[0-9]{1,9}" /tmp/sample-*.txt`
  - [ ] No SSN: `grep -E "[0-9]{3}-[0-9]{2}-[0-9]{4}" /tmp/sample-*.txt`
  - [ ] No credit cards: `grep -E "[0-9]{4}[-\s]?[0-9]{4}[-\s]?[0-9]{4}[-\s]?[0-9]{4}" /tmp/sample-*.txt`

- [ ] **Expected**: All searches return only `***REDACTED***` or no matches

#### 1.3 Sanitization Unit Test Verification

- [ ] **Run DefaultLogSanitizerTest**
  ```bash
  mvn test -pl patra-common -Dtest=DefaultLogSanitizerTest
  ```

- [ ] **Verify all 32 tests pass**
  - Test coverage: Passwords, API keys, emails, phones, CC, SSN
  - JSON sanitization (nested objects, arrays)
  - Edge cases (long strings, special characters)

#### 1.4 Real-World Pattern Testing

- [ ] **Test sanitization with actual production data samples**
  ```bash
  # Get recent DTO from production (anonymized)
  curl http://api-staging.patra.com/api/test/sample-dto > /tmp/test-dto.json

  # Verify sanitization
  java -cp patra-common.jar com.patra.common.logging.sanitizer.DefaultLogSanitizerTest /tmp/test-dto.json
  ```

- [ ] **Confirm all sensitive fields redacted**

---

### 2. Audit Logging Completeness

**Requirement**: SC-008 - 100% coverage for API/DB/Auth events

#### 2.1 Authentication Events

- [ ] **Verify login success events logged**
  ```bash
  grep "AUTH_SUCCESS" /var/log/patra/*.log | wc -l
  # Compare with auth metrics: should match
  ```

- [ ] **Verify login failure events logged**
  ```bash
  grep "AUTH_FAILURE" /var/log/patra/*.log | head -5
  # Check format: includes username, IP, reason
  ```

- [ ] **No passwords in auth logs**
  ```bash
  grep -i "password" /var/log/patra/*.log | grep -v "***REDACTED***"
  # Expected: No matches
  ```

#### 2.2 API Call Logging

- [ ] **Verify external API calls logged**
  ```bash
  grep "ApiCallLogger" /var/log/patra/patra-egress-gateway.log | head -5
  # Check format: [API_CALL] method=GET url=https://... status=200 duration=523ms
  ```

- [ ] **Sensitive headers sanitized**
  ```bash
  grep "Authorization" /var/log/patra/*.log | grep -v "***REDACTED***"
  # Expected: No matches (all auth headers should be masked)
  ```

#### 2.3 Database Failure Logging

- [ ] **Verify DB errors logged**
  ```bash
  grep "DbFailureLogger" /var/log/patra/*.log | head -5
  # Check format: [DB_FAILURE] operation=SELECT table=users error=Connection timeout
  ```

- [ ] **No SQL injection patterns in logs**
  ```bash
  grep -E "(OR|AND) [0-9]+=|UNION SELECT|DROP TABLE" /var/log/patra/*.log
  # Expected: No matches (sanitized if present)
  ```

---

### 3. Sanitization Rules Effectiveness

#### 3.1 Password Patterns

- [ ] **Test with various formats**
  ```bash
  echo "password=secret123" | java -cp patra-common.jar TestSanitizer
  echo "pwd=mypass" | java -cp patra-common.jar TestSanitizer
  echo "secret=topsecret" | java -cp patra-common.jar TestSanitizer
  ```

- [ ] **Expected output**: `password=***REDACTED***`

#### 3.2 API Key Patterns

- [ ] **Test with various formats**
  ```bash
  echo "apiKey=abc123def456" | java -cp patra-common.jar TestSanitizer
  echo "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" | java -cp patra-common.jar TestSanitizer
  echo "Authorization: Bearer xyz123" | java -cp patra-common.jar TestSanitizer
  ```

- [ ] **Expected output**: All tokens show `***REDACTED***`

#### 3.3 JSON Sanitization

- [ ] **Test nested JSON objects**
  ```json
  {
    "user": {
      "name": "John",
      "password": "secret",
      "credentials": {
        "apiKey": "abc123"
      }
    }
  }
  ```

- [ ] **Verify nested passwords/keys redacted**

---

### 4. Access Controls

#### 4.1 Log File Permissions

- [ ] **Verify log file permissions are restrictive**
  ```bash
  ls -l /var/log/patra/*.log
  # Expected: -rw-r----- (640) or -rw------- (600)
  ```

- [ ] **Verify log directory ownership**
  ```bash
  ls -ld /var/log/patra
  # Expected: drwxr-x--- patra-svc patra-svc
  ```

- [ ] **No world-readable logs**
  ```bash
  find /var/log/patra -type f -perm /o=r
  # Expected: No results
  ```

#### 4.2 Log Aggregation Security

- [ ] **Verify TLS for log shipping**
  ```bash
  # Check Filebeat/Fluentd config
  grep "ssl.enabled: true" /etc/filebeat/filebeat.yml
  ```

- [ ] **Verify log aggregation authentication**
  ```bash
  # Check credentials are not in config files
  grep -i "password" /etc/filebeat/filebeat.yml
  # Expected: References to secrets manager, not plain text
  ```

---

### 5. Compliance Verification

#### 5.1 GDPR Compliance

- [ ] **No EU citizen PII in logs without explicit consent**
- [ ] **Data retention policies enforced** (logs deleted after 90 days)
  ```bash
  find /var/log/patra -name "*.log" -mtime +90
  # Expected: Empty (all old logs deleted)
  ```

- [ ] **Right to erasure**: Process exists for removing user data from logs
  - [ ] Document procedure exists: `docs/compliance/gdpr-log-erasure.md`

#### 5.2 HIPAA Compliance (if applicable)

- [ ] **No PHI in logs** (Protected Health Information)
  - Medical record numbers
  - Health insurance IDs
  - Device identifiers
  - Biometric data

- [ ] **Access logs maintained** for compliance audit trail
  ```bash
  # Check access logs exist
  ls -l /var/log/audit/patra-log-access.log
  ```

#### 5.3 PCI-DSS Compliance (if applicable)

- [ ] **No credit card numbers in logs**
- [ ] **No CVV codes in logs**
- [ ] **Payment transaction logs encrypted at rest**
  ```bash
  lsblk -f | grep /var/log
  # Verify: Encrypted volume
  ```

---

### 6. Incident Response Readiness

#### 6.1 Security Event Detection

- [ ] **Alert rules configured for security events**
  - Failed authentication attempts (>5/hour/user)
  - Authorization failures (>10/5min/service)
  - Suspicious API key usage patterns

- [ ] **Test alerts are firing**
  ```bash
  # Trigger test alert
  ./scripts/test-security-alerts.sh
  ```

- [ ] **Verify alert delivery** (Slack, PagerDuty, Email)

#### 6.2 Forensic Readiness

- [ ] **Log integrity verification enabled**
  ```bash
  # Check log signing/hashing
  sha256sum /var/log/patra/*.log > /var/log/audit/checksums.txt
  ```

- [ ] **Tamper detection working**
  ```bash
  # Verify checksums
  sha256sum -c /var/log/audit/checksums.txt
  ```

- [ ] **Log backup strategy tested**
  ```bash
  # Verify last backup timestamp
  aws s3 ls s3://patra-log-archives/ | tail -1
  # Should be within last 24 hours
  ```

---

### 7. Code Review Verification

#### 7.1 Static Analysis

- [ ] **Run SpotBugs for logging violations**
  ```bash
  mvn spotbugs:check
  # Verify: No findings related to logging sensitive data
  ```

- [ ] **Run ArchUnit tests for logging architecture**
  ```bash
  mvn test -Dtest=LoggingArchitectureTest
  # Verify: All tests pass
  ```

#### 7.2 Dependency Scanning

- [ ] **No vulnerable logging dependencies**
  ```bash
  mvn dependency:tree | grep -E "(logback|slf4j)"
  # Check versions against CVE database
  ```

- [ ] **Log4j vulnerability check** (Log4Shell CVE-2021-44228)
  ```bash
  mvn dependency:tree | grep log4j
  # Expected: No log4j dependencies (using Logback)
  ```

---

### 8. Documentation Review

- [ ] **Logging guidelines up to date**
  - [ ] `docs/logging/log-level-guidelines.md`
  - [ ] `docs/logging/sensitive-data-handling.md`
  - [ ] `docs/logging/operations-guide.md`

- [ ] **Developer training completed**
  - [ ] All developers completed logging security training
  - [ ] Training records maintained

- [ ] **Security runbooks current**
  - [ ] Incident response procedures
  - [ ] Breach notification procedures
  - [ ] Log forensics procedures

---

## Audit Report Template

### Executive Summary

- **Audit Date**: YYYY-MM-DD
- **Auditor**: [Name]
- **Services Audited**: [List services]
- **Overall Status**: PASS / FAIL / NEEDS REVIEW

### Findings Summary

| Category | Pass | Fail | Needs Review |
|----------|------|------|--------------|
| PII Sanitization | ☑ | ☐ | ☐ |
| Audit Logging | ☑ | ☐ | ☐ |
| Access Controls | ☑ | ☐ | ☐ |
| Compliance | ☐ | ☐ | ☑ |
| Incident Response | ☑ | ☐ | ☐ |

### Critical Issues

1. **[ISSUE-001]**: [Description]
   - **Severity**: Critical / High / Medium / Low
   - **Impact**: [Description]
   - **Remediation**: [Steps]
   - **Due Date**: [Date]

### Recommendations

1. [Recommendation 1]
2. [Recommendation 2]

### Sign-Off

- **Security Team**: [Name, Date]
- **DevOps Lead**: [Name, Date]
- **Compliance Officer**: [Name, Date]

---

## Quick Reference

### Severity Levels

- **Critical**: Sensitive data exposed in logs, immediate action required
- **High**: Incomplete audit logging, fix within 7 days
- **Medium**: Minor sanitization gaps, fix within 30 days
- **Low**: Documentation or process improvements, fix within 90 days

### Escalation Path

1. **Critical findings**: Immediate Slack alert to #security-incidents
2. **High findings**: Email to security@patra.com within 24 hours
3. **Medium/Low findings**: Add to security backlog, review in sprint planning

### Useful Commands

```bash
# Quick PII scan
grep -rE "(password|token|apiKey|ssn|credit)" /var/log/patra/ | grep -v "REDACTED"

# Check log permissions
find /var/log/patra -type f ! -perm 640

# Verify sanitization coverage
mvn test -pl patra-common -Dtest=DefaultLogSanitizerTest

# Run full security audit
./scripts/security-audit-full.sh
```

---

**Document Owner**: Security Team
**Review Cycle**: Quarterly
**Last Audit**: 2025-10-17
**Next Audit Due**: 2025-11-17

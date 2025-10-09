# Suggested Commands

## Build Commands

### Full Repository Compilation
```bash
./mvnw -q -DskipTests compile
```
Compile all modules without running tests. Use this for quick compilation checks.

### Full Repository Build with Tests
```bash
./mvnw clean install
```
Full build including tests for all modules.

### Single Module Build
```bash
cd patra-registry
./mvnw clean test
```
Build and test a specific module.

### Skip Tests Compilation
```bash
./mvnw -q -DskipTests clean package
```
Package without running tests (for local verification).

## Testing Commands

### Run All Tests
```bash
./mvnw test
```

### Run Specific Module Tests
```bash
./mvnw -pl patra-registry test
```

### Run with Coverage
```bash
./mvnw test jacoco:report
```

## Infrastructure Commands

### Start Local Infrastructure
```bash
cd docker/compose
docker compose up -d
```
Starts MySQL, Redis, Elasticsearch, Nacos, SkyWalking, XXL-Job.

### Stop Local Infrastructure
```bash
cd docker/compose
docker compose down
```

### View Infrastructure Logs
```bash
cd docker/compose
docker compose logs -f [service-name]
```

## Development Workflow Commands

### After Code Changes (Quick Check)
```bash
./mvnw -q -DskipTests compile
```

### Before Committing
```bash
./mvnw clean test
```

### Check for Dependency Issues
```bash
./mvnw dependency:tree
```

### Clean Build Artifacts
```bash
./mvnw clean
```

## Service-Specific Commands

### Run Registry Service Locally
```bash
cd patra-registry/patra-registry-boot
./mvnw spring-boot:run
```

### Run Ingest Service Locally
```bash
cd patra-ingest/patra-ingest-boot
./mvnw spring-boot:run
```

## Git Commands (macOS)

### Standard Git Operations
```bash
git status
git add .
git commit -m "message"
git push
```

### Check Recent Commits
```bash
git log --oneline -10
```

## Troubleshooting

### Check Java Version
```bash
java -version
# Should be Java 21
```

### Check Maven Version
```bash
./mvnw -version
```

### Clean Maven Cache (if build issues)
```bash
rm -rf ~/.m2/repository/com/papertrace
./mvnw clean install -U
```

## macOS-Specific Notes
- Use `brew` for package management
- Python command is `python3` not `python`
- Use `open` command to open files/applications
- Use `mdfind` for fast file search (Spotlight CLI)
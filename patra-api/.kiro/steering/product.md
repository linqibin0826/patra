# Product Overview

## Papertrace - Medical Literature Data Platform

Papertrace is a SpringCloud microservices platform designed for collecting, processing, and managing medical literature data from multiple open sources.

### Core Purpose
1. **Unified Collection**: Collect from 10+ medical literature sources (PubMed, EPMC, etc.)
2. **SSOT Management**: Single source of truth for configuration, dictionaries, and metadata
3. **Data Processing**: Raw literature data parsing, cleaning, and standardization
4. **Future Goals**: Search capabilities and intelligent analysis

### Current Phase: Reliable Data Landing
Focus on reliable data landing - ensuring the complete flow: **Collection → Parsing/Cleaning → Storage**

### Key Business Flows
1. **Admin-triggered Collection**: Manual collection via web interface
2. **Scheduled Collection**: Automated collection via XXL-Job scheduler
3. **Data Processing**: Parse and clean collected literature data with idempotency
4. **Configuration Management**: Manage data source configurations and business rules

### Architecture Approach
- **Hexagonal Architecture + DDD**: Clean separation of concerns with domain-driven design
- **Microservices**: Independent, scalable services with clear boundaries
- **Event-Driven**: Asynchronous communication between services for eventual consistency

### Key Principles
- **Idempotency**: All collection/parsing/cleaning processes must be re-entrant
- **Observability**: Full tracing with SkyWalking for critical paths
- **Security**: No hardcoded credentials, parameterized SQL only
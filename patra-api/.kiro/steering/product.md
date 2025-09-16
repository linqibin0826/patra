# Product Overview

Papertrace is a medical literature data collection and management platform built with Spring Cloud microservices architecture. The system implements hexagonal architecture combined with Domain-Driven Design (DDD) principles.

## Core Purpose

The platform manages data collection from 10+ open medical literature sources (PubMed, EPMC, etc.) through:
- Administrative web interface operations
- Scheduled automated tasks
- Unified data ingestion and parsing
- Reliable data persistence with basic cleaning
- Support for future search and intelligent analysis

## Current Phase Goal

**Data Landing**: Establish unified access to various platforms, parse raw literature data, ensure reliable database storage, and complete basic data cleaning to support downstream search and intelligent analysis capabilities.

## Key Business Domains

- **Registry Service**: Single Source of Truth (SSOT) management for dictionaries and configuration data
- **Ingest Service**: Data collection and processing from external literature sources
- **Gateway**: API gateway and routing management

## Architecture Principles

- Hexagonal Architecture (Ports & Adapters) with clear dependency directions
- Domain-Driven Design (DDD) layering
- Microservices with Spring Cloud
- Clean separation between business logic and technical infrastructure
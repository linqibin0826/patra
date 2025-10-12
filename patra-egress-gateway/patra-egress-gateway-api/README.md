# patra-egress-gateway-api

## Overview
- Consumer-facing contracts for the Egress Gateway. Provides the internal RPC interface, DTOs, and Feign client for invoking `POST /api/egress/call`.

## Contents
- Endpoint interface: `patra-egress-gateway/patra-egress-gateway-api/src/main/java/com/patra/egress/api/endpoint/EgressEndpoint.java:1`
- Feign client: `patra-egress-gateway/patra-egress-gateway-api/src/main/java/com/patra/egress/api/client/EgressGatewayClient.java:1`
- DTOs: request/response/envelope, resilience config, retry advice, rate limit info.

## Quickstart (Feign)
```java
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;

ExternalCallRequestDTO req = new ExternalCallRequestDTO(
    "https://api.example.com/data",
    "GET",
    Map.of("Accept", "application/json"),
    null,
    null
);
ExternalCallResponseDTO res = egressGatewayClient.call(req);
System.out.println("status=" + res.envelope().statusCode());
```

## Related Docs
- API contract: `docs/contracts/api/egress-gateway.md:1`
- Service README: `patra-egress-gateway/README.md:1`

## Notes
- Avoid duplicating contract details here; keep this README concise and link to the central contract.

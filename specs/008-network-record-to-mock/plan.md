# Implementation Plan: Network Record To Mock

## Summary

Add a runtime-side `network.recordToMock` endpoint/tool. The OkHttp interceptor records safe response body previews via `Response.peekBody`, and `NetworkController` can convert a selected history record into a normal static mock rule.

## Technical Context

| Area | Decision |
| --- | --- |
| Body capture | OkHttp `Response.peekBody` with existing size/redaction policy |
| Raw body storage | Do not store hidden raw bodies in MVP |
| Source selection | `recordId` first; fallback to latest record matching `sourceMatch` |
| Target matcher | Explicit `targetMatch` or derived from captured method/exact URL/protocol metadata |
| Cleanup | Reuse normal network rule cleanup registry |

## Flow

1. App makes a real network call.
2. Runtime records method, URL, status, metadata, and safe response body preview.
3. Agent calls `network.recordToMock`.
4. Runtime selects the record, creates a `NetworkMockRequest`, installs it through the normal mock path, and returns rule metadata.

## Risks

- Streaming or binary responses should not be captured as text mocks.
- Redaction can intentionally replace sensitive fields, so generated mocks are safe but may not be byte-for-byte backend fixtures.
- Very large responses should remain capped by the existing redaction/preview policy.

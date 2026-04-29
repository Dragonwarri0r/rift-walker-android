# Implementation Plan: GraphQL And gRPC Network Matching

**Branch**: `007-graphql-grpc-network`  
**Date**: 2026-04-27  
**Spec**: `specs/007-graphql-grpc-network/spec.md`

## Summary

Extend the existing OkHttp-based network control layer with protocol-aware request metadata extraction. Keep response mocking, mutation, failure, assertions, and history paths unchanged; matching becomes more expressive while remaining schema-driven and auditable.

## Technical Context

| Area | Decision |
| --- | --- |
| Runtime integration | Existing `NetworkControlInterceptor` |
| GraphQL parsing | JSON body parse for `operationName`, `query`, `variables` |
| GraphQL variable matching | Top-level exact JSON value equality |
| gRPC matching | URL path + `content-type` containing `grpc` |
| Protobuf decoding | Deferred until descriptor/serializer registry exists |

## Implementation Notes

- Do not consume unsafe request bodies just for protocol matching; reuse existing guarded request-body preview.
- GraphQL matching should work when request body is readable and JSON.
- gRPC matching should not require body inspection.
- Add protocol metadata to network records so reports can explain why a rule matched.

## Risks

- GraphQL multipart uploads and persisted queries may need extra parsing later.
- gRPC true mock responses need framed protobuf bodies and status trailers; MVP only provides coarse OkHttp-level mock/fail matching.

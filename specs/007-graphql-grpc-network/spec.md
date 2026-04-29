# Feature Specification: GraphQL And gRPC Network Matching

**Feature Branch**: `007-graphql-grpc-network`  
**Created**: 2026-04-27  
**Status**: Draft  
**Input**: Extend network control beyond plain REST/JSON so AI agents can target GraphQL operations and gRPC unary calls.

## User Scenarios & Testing

### User Story 1 - Match GraphQL Operations (Priority: P1)

As an AI agent, I want to mock or mutate a GraphQL operation by operation name and variables so that apps using a single `/graphql` endpoint can still cover specific branches.

**Acceptance Scenarios**:

1. **Given** a request body contains `operationName=GetProfile`, **When** a mock rule matches `graphqlOperationName=GetProfile`, **Then** the mock response is returned.
2. **Given** a rule includes `graphqlVariables`, **When** the request variables do not match, **Then** the rule is not consumed.
3. **Given** a GraphQL request is recorded, **When** `network.history` includes bodies, **Then** operation metadata is visible alongside the record.

### User Story 2 - Match gRPC Unary Calls By Service And Method (Priority: P2)

As an AI agent, I want to match gRPC calls by service and method path so that I can install coarse mock/failure rules before descriptor-aware protobuf support exists.

**Acceptance Scenarios**:

1. **Given** a request path is `/pkg.ProfileService/GetProfile`, **When** a rule matches `grpcService=pkg.ProfileService` and `grpcMethod=GetProfile`, **Then** the rule is applied.
2. **Given** a gRPC matcher is used on a non-gRPC request, **When** content type does not contain `grpc`, **Then** the rule does not match.

## Requirements

- **FR-001**: `NetworkMatcher` MUST support GraphQL operation name matching.
- **FR-002**: `NetworkMatcher` MUST support GraphQL query regex matching.
- **FR-003**: `NetworkMatcher` MUST support top-level GraphQL variables matching.
- **FR-004**: `NetworkRecord` SHOULD expose protocol metadata for GraphQL and gRPC calls.
- **FR-005**: `NetworkMatcher` MUST support gRPC service and method path matching.
- **FR-006**: gRPC protobuf body decoding and mutation MAY be deferred to a later descriptor registry feature.

## Key Entities

- **GraphQLRequestMetadata**: operation name, inferred operation type, query text, variables.
- **GrpcRequestMetadata**: service and method inferred from URL path.
- **NetworkMatcher**: Extended matching model for REST, GraphQL, and gRPC.

## Success Criteria

- **SC-001**: Unit tests prove GraphQL operation-name mock matching.
- **SC-002**: Unit tests prove GraphQL variable matching.
- **SC-003**: Unit tests prove gRPC service/method matching.
- **SC-004**: Existing runtime, daemon, sample, release safety verification commands pass.

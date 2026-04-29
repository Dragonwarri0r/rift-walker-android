# 007 GraphQL And gRPC Network Matching Agent Test

## Goal

Verify protocol-aware network matching beyond REST: GraphQL operation/variables and
gRPC service/method matching must work without breaking existing network history behavior.

## Agent Flow

1. Confirm matcher fields are accepted by the runtime.

```json
{
  "tool": "network.mock",
  "arguments": {
    "match": {
      "method": "POST",
      "urlRegex": ".*/graphql",
      "contentTypeContains": "json",
      "graphqlOperationName": "GetProfile",
      "graphqlVariables": {"userId":"current"}
    },
    "response": {
      "status": 200,
      "headers": {"content-type":"application/json"},
      "body": {"data":{"profile":{"name":"GraphQL Agent"}}}
    },
    "times": 1
  }
}
```

Expected evidence:

- A `ruleId` is returned.
- `capabilities.list` includes network tools; if a dedicated matcher capability exists, record it.

2. Trigger a GraphQL request.

Use the sample app if it has a GraphQL trigger. Otherwise run this as a unit/integration test
against an instrumented OkHttp client that uses `AiDebugNetwork.interceptor()`:

```json
{
  "operationName": "GetProfile",
  "query": "query GetProfile($userId: ID!) { profile(id: $userId) { name } }",
  "variables": {"userId":"current"}
}
```

Expected evidence from `network.history` with `includeBodies=true`:

- A record has `protocol == "graphql"`.
- `graphqlOperationName == "GetProfile"`.
- The mock rule id appears in `matchedRuleIds`.
- Response body is the mocked GraphQL body.

3. Verify variable mismatch does not consume the rule.

Install the same rule with `times: 1`, trigger `variables: {"userId":"other"}`, then inspect:

- The rule should not appear in `matchedRuleIds`.
- A later matching request with `userId == "current"` should still match.

4. Verify inferred operation name.

Install a rule with `graphqlOperationName: "GetProfile"` and send a body that omits
`operationName` but includes `query: "query GetProfile { ... }"`.

Expected evidence:

- The rule matches.
- History records `graphqlOperationName == "GetProfile"`.

5. Verify gRPC coarse matching.

Install:

```json
{
  "tool": "network.fail",
  "arguments": {
    "match": {
      "contentTypeContains": "grpc",
      "grpcService": "com.example.ProfileService",
      "grpcMethod": "GetProfile"
    },
    "failure": {"type":"disconnect"},
    "times": 1
  }
}
```

Trigger a POST request whose URL path is `/com.example.ProfileService/GetProfile`
and whose content type contains `grpc`.

Expected evidence:

- History has `protocol == "grpc"`.
- `grpcService == "com.example.ProfileService"`.
- `grpcMethod == "GetProfile"`.
- Non-gRPC content type does not match the gRPC rule.

## Failure Triage

- Rule installs but never matches: inspect request content type and serialized body.
- GraphQL history lacks metadata: request classification is broken, even if normal REST matching works.
- gRPC rule matches non-gRPC traffic: matcher is too broad and must enforce content type.

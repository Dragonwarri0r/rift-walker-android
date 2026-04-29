# Network Matcher Extensions

Existing REST fields continue to work:

```json
{
  "method": "POST",
  "urlRegex": ".*/graphql",
  "headers": {},
  "bodyContains": "GetProfile"
}
```

## GraphQL

```json
{
  "method": "POST",
  "urlRegex": ".*/graphql",
  "graphqlOperationName": "GetProfile",
  "graphqlQueryRegex": "query\\s+GetProfile",
  "graphqlVariables": {
    "userId": "current"
  }
}
```

Matching behavior:

- `graphqlOperationName` checks the JSON body `operationName`; if absent, the runtime tries to infer it from the `query` string.
- `graphqlQueryRegex` checks the raw GraphQL `query` string.
- `graphqlVariables` performs top-level exact JSON equality against the request `variables` object.

## gRPC

```json
{
  "grpcService": "com.example.ProfileService",
  "grpcMethod": "GetProfile"
}
```

Matching behavior:

- Requires request `content-type` to contain `grpc`.
- Infers service/method from the request URL path: `/com.example.ProfileService/GetProfile`.
- Protobuf decode and field mutation are not part of this MVP.

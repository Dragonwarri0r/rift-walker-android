# Quickstart: GraphQL And gRPC Network Matching

## GraphQL Mock

```json
{
  "match": {
    "method": "POST",
    "urlRegex": ".*/graphql",
    "graphqlOperationName": "GetProfile",
    "graphqlVariables": {"userId": "current"}
  },
  "response": {
    "status": 200,
    "body": {
      "data": {
        "profile": {
          "name": "Ada",
          "isVip": true
        }
      }
    }
  },
  "times": 1
}
```

## GraphQL Mutation

```json
{
  "match": {
    "method": "POST",
    "urlRegex": ".*/graphql",
    "graphqlOperationName": "GetProfile"
  },
  "patch": [
    {
      "op": "replace",
      "path": "$.data.profile.isVip",
      "value": false
    }
  ]
}
```

## gRPC Coarse Mock

```json
{
  "match": {
    "grpcService": "com.example.ProfileService",
    "grpcMethod": "GetProfile"
  },
  "response": {
    "status": 200,
    "headers": {
      "content-type": "application/grpc"
    },
    "bodyText": ""
  }
}
```

Descriptor-aware protobuf body mutation is intentionally left for the next protobuf registry slice.

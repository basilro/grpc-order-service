# gRPC 주문 관리 서비스

gRPC 기반 주문 관리 시스템

## API 문서

### 1. CreateOrder (Unary RPC)

단일 주문을 생성합니다.

**요청:**
```protobuf
message CreateOrderRequest {
  string customer_id = 1;
  repeated OrderItem items = 2;
  string shipping_address = 3;
  PaymentInfo payment_info = 4;
}
```

**응답:**
```protobuf
message OrderResponse {
  string order_id = 1;
  OrderStatus status = 2;
  double total_amount = 3;
  int64 created_at = 4;
  string message = 5;
}
```

### 2. TrackOrder (Server Streaming RPC)

주문 상태를 실시간으로 추적합니다.

**요청:**
```protobuf
message TrackOrderRequest {
  string order_id = 1;
}
```

**응답 스트림:**
```protobuf
message OrderStatusUpdate {
  string order_id = 1;
  OrderStatus status = 2;
  string location = 3;
  int64 timestamp = 4;
  string description = 5;
}
```

### 3. BatchCreateOrders (Client Streaming RPC)

여러 주문을 일괄로 생성합니다.

**요청 스트림:** `CreateOrderRequest`

**응답:**
```protobuf
message BatchOrderResponse {
  int32 total_orders = 1;
  int32 successful_orders = 2;
  int32 failed_orders = 3;
  repeated string order_ids = 4;
}
```

### 4. ProcessOrders (Bidirectional Streaming RPC)

실시간 피드백과 함께 주문을 처리합니다.

**요청 스트림:**
```protobuf
message OrderProcessRequest {
  string order_id = 1;
  ProcessAction action = 2;
  string notes = 3;
}
```

**응답 스트림:**
```protobuf
message OrderProcessResponse {
  string order_id = 1;
  bool success = 2;
  string message = 3;
  OrderStatus new_status = 4;
}
```

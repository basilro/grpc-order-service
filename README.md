# gRPC Order Service

[![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=java)](https://www.oracle.com/java/)
[![gRPC](https://img.shields.io/badge/gRPC-1.59.0-00ADD8?style=flat-square&logo=grpc)](https://grpc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A?style=flat-square&logo=gradle)](https://gradle.org/)

Advanced gRPC-based Order Management System demonstrating all four RPC patterns, interceptors, and production-ready error handling.

## 🎯 Features

### gRPC Communication Patterns
- **Unary RPC**: Single request/response for order creation and retrieval
- **Server Streaming RPC**: Real-time order status tracking
- **Client Streaming RPC**: Batch order processing
- **Bidirectional Streaming RPC**: Real-time order processing with instant feedback

### Advanced Features
- 🔐 **Authentication Interceptor**: Token-based authentication
- 📝 **Logging Interceptor**: Comprehensive request/response logging
- ⚠️ **Error Handling**: Proper gRPC status codes and error messages
- 🎯 **Type Safety**: Protocol Buffers for type-safe communication
- ⚡ **Performance**: Efficient binary serialization with Protobuf

## 📁 Project Structure

```
grpc-order-service/
├── src/main/
│   ├── proto/
│   │   └── order_service.proto          # Protocol Buffers definition
│   └── java/com/example/grpc/
│       ├── server/
│       │   ├── OrderServiceImpl.java    # gRPC service implementation
│       │   ├── OrderServer.java         # Server startup
│       │   └── interceptor/
│       │       ├── LoggingInterceptor.java
│       │       └── AuthInterceptor.java
│       └── client/
│           └── OrderClient.java         # Client examples
├── build.gradle                         # Gradle build configuration
└── README.md
```

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Gradle 8.x

### Build

```bash
./gradlew clean build
```

### Run Server

```bash
./gradlew run --args="server"
# Or run directly
java -cp build/libs/grpc-order-service-1.0.0.jar com.example.grpc.server.OrderServer
```

Server will start on port **9090**.

### Run Client

```bash
./gradlew run --args="client"
# Or run directly
java -cp build/libs/grpc-order-service-1.0.0.jar com.example.grpc.client.OrderClient
```

## 📖 API Documentation

### 1. CreateOrder (Unary RPC)

Create a single order.

**Request:**
```protobuf
message CreateOrderRequest {
  string customer_id = 1;
  repeated OrderItem items = 2;
  string shipping_address = 3;
  PaymentInfo payment_info = 4;
}
```

**Response:**
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

Track order status in real-time.

**Request:**
```protobuf
message TrackOrderRequest {
  string order_id = 1;
}
```

**Response Stream:**
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

Create multiple orders in batch.

**Request Stream:** `CreateOrderRequest`

**Response:**
```protobuf
message BatchOrderResponse {
  int32 total_orders = 1;
  int32 successful_orders = 2;
  int32 failed_orders = 3;
  repeated string order_ids = 4;
}
```

### 4. ProcessOrders (Bidirectional Streaming RPC)

Process orders with real-time feedback.

**Request Stream:**
```protobuf
message OrderProcessRequest {
  string order_id = 1;
  ProcessAction action = 2;
  string notes = 3;
}
```

**Response Stream:**
```protobuf
message OrderProcessResponse {
  string order_id = 1;
  bool success = 2;
  string message = 3;
  OrderStatus new_status = 4;
}
```

## 🔧 Technical Details

### Interceptors

#### AuthInterceptor
- Validates authorization tokens in request headers
- Returns `UNAUTHENTICATED` status for invalid/missing tokens
- Token format: `Bearer <token>` (minimum 20 characters)

#### LoggingInterceptor
- Logs all gRPC method calls
- Tracks request duration
- Logs status codes and error messages

### Error Handling

The service implements proper gRPC status codes:

- `INVALID_ARGUMENT`: Invalid request parameters
- `NOT_FOUND`: Order not found
- `UNAUTHENTICATED`: Missing or invalid auth token
- `INTERNAL`: Server-side errors

### Order Status Flow

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
                                         ↓
                                    CANCELLED
                                         ↓
                                     REFUNDED
```

## 💡 Usage Examples

### Java Client Example

```java
// Create channel and stub
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

OrderServiceGrpc.OrderServiceBlockingStub stub = 
    OrderServiceGrpc.newBlockingStub(channel);

// Create order
CreateOrderRequest request = CreateOrderRequest.newBuilder()
    .setCustomerId("customer-001")
    .addItems(OrderItem.newBuilder()
        .setProductId("prod-001")
        .setProductName("Laptop")
        .setQuantity(1)
        .setUnitPrice(1299.99)
        .build())
    .setShippingAddress("123 Main St")
    .build();

OrderResponse response = stub.createOrder(request);
System.out.println("Order ID: " + response.getOrderId());
```

### Adding Authentication Header

```java
Metadata headers = new Metadata();
Metadata.Key<String> authKey = 
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
headers.put(authKey, "Bearer your-token-here-1234567890");

OrderServiceGrpc.OrderServiceBlockingStub authenticatedStub = 
    MetadataUtils.attachHeaders(stub, headers);
```

## 🧪 Testing

```bash
./gradlew test
```

## 📊 Performance Considerations

- **Binary Protocol**: Protobuf provides efficient serialization
- **HTTP/2**: Multiplexing and header compression
- **Streaming**: Reduced latency for real-time data
- **Connection Pooling**: Reuse channels for multiple calls

## 🔐 Security Best Practices

1. **Use TLS in production**: Replace `usePlaintext()` with proper SSL/TLS
2. **Implement proper authentication**: Use JWT or OAuth2 tokens
3. **Validate all inputs**: Check for SQL injection, XSS, etc.
4. **Rate limiting**: Implement request throttling
5. **Audit logging**: Log all sensitive operations

## 📝 License

MIT License - feel free to use this project for learning and development.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📧 Contact

For questions or feedback, please open an issue on GitHub.

---

**Built with ❤️ using gRPC and Protocol Buffers**

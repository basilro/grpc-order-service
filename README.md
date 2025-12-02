# gRPC 주문 관리 서비스

[![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=java)](https://www.oracle.com/java/)
[![gRPC](https://img.shields.io/badge/gRPC-1.59.0-00ADD8?style=flat-square&logo=grpc)](https://grpc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-8.5-02303A?style=flat-square&logo=gradle)](https://gradle.org/)

4가지 RPC 패턴, 인터셉터, 프로덕션 수준의 에러 핸들링을 구현한 고급 gRPC 기반 주문 관리 시스템

## 🎯 주요 기능

### gRPC 통신 패턴
- **Unary RPC**: 주문 생성 및 조회를 위한 단일 요청/응답
- **Server Streaming RPC**: 실시간 주문 상태 추적
- **Client Streaming RPC**: 대량 주문 일괄 처리
- **Bidirectional Streaming RPC**: 실시간 양방향 주문 처리

### 고급 기능
- 🔐 **인증 인터셉터**: 토큰 기반 인증 처리
- 📝 **로깅 인터셉터**: 요청/응답 전체 로깅
- ⚠️ **에러 핸들링**: 적절한 gRPC 상태 코드 및 에러 메시지
- 🎯 **타입 안정성**: Protocol Buffers를 통한 타입 안전 통신
- ⚡ **성능**: Protobuf 바이너리 직렬화로 효율적인 통신

## 📁 프로젝트 구조

```
grpc-order-service/
├── src/main/
│   ├── proto/
│   │   └── order_service.proto          # Protocol Buffers 정의
│   └── java/com/example/grpc/
│       ├── server/
│       │   ├── OrderServiceImpl.java    # gRPC 서비스 구현
│       │   ├── OrderServer.java         # 서버 시작
│       │   └── interceptor/
│       │       ├── LoggingInterceptor.java
│       │       └── AuthInterceptor.java
│       └── client/
│           └── OrderClient.java         # 클라이언트 예제
├── build.gradle                         # Gradle 빌드 설정
└── README.md
```

## 🚀 시작하기

### 사전 요구사항
- Java 17 이상
- Gradle 8.x

### 빌드

```bash
./gradlew clean build
```

### 서버 실행

```bash
./gradlew run --args="server"
# 또는 직접 실행
java -cp build/libs/grpc-order-service-1.0.0.jar com.example.grpc.server.OrderServer
```

서버는 **9090** 포트에서 시작됩니다.

### 클라이언트 실행

```bash
./gradlew run --args="client"
# 또는 직접 실행
java -cp build/libs/grpc-order-service-1.0.0.jar com.example.grpc.client.OrderClient
```

## 📖 API 문서

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

## 🔧 기술 상세

### 인터셉터

#### AuthInterceptor (인증 인터셉터)
- 요청 헤더의 인증 토큰 검증
- 유효하지 않거나 누락된 토큰에 대해 `UNAUTHENTICATED` 상태 반환
- 토큰 형식: `Bearer <token>` (최소 20자)

#### LoggingInterceptor (로깅 인터셉터)
- 모든 gRPC 메서드 호출 로깅
- 요청 처리 시간 추적
- 상태 코드 및 에러 메시지 로깅

### 에러 핸들링

적절한 gRPC 상태 코드를 구현했습니다:

- `INVALID_ARGUMENT`: 잘못된 요청 파라미터
- `NOT_FOUND`: 주문을 찾을 수 없음
- `UNAUTHENTICATED`: 인증 토큰 누락 또는 유효하지 않음
- `INTERNAL`: 서버 내부 에러

### 주문 상태 흐름

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
                                         ↓
                                    CANCELLED
                                         ↓
                                     REFUNDED
```

## 💡 사용 예제

### Java 클라이언트 예제

```java
// 채널과 스텁 생성
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

OrderServiceGrpc.OrderServiceBlockingStub stub = 
    OrderServiceGrpc.newBlockingStub(channel);

// 주문 생성
CreateOrderRequest request = CreateOrderRequest.newBuilder()
    .setCustomerId("customer-001")
    .addItems(OrderItem.newBuilder()
        .setProductId("prod-001")
        .setProductName("노트북")
        .setQuantity(1)
        .setUnitPrice(1299.99)
        .build())
    .setShippingAddress("서울시 강남구")
    .build();

OrderResponse response = stub.createOrder(request);
System.out.println("주문 ID: " + response.getOrderId());
```

### 인증 헤더 추가

```java
Metadata headers = new Metadata();
Metadata.Key<String> authKey = 
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
headers.put(authKey, "Bearer your-token-here-1234567890");

OrderServiceGrpc.OrderServiceBlockingStub authenticatedStub = 
    MetadataUtils.attachHeaders(stub, headers);
```

## 🧪 테스트

```bash
./gradlew test
```

## 📊 성능 고려사항

- **바이너리 프로토콜**: Protobuf가 효율적인 직렬화 제공
- **HTTP/2**: 멀티플렉싱 및 헤더 압축
- **스트리밍**: 실시간 데이터를 위한 지연시간 감소
- **연결 풀링**: 여러 호출에 채널 재사용

## 🔐 보안 모범 사례

1. **프로덕션에서 TLS 사용**: `usePlaintext()`를 적절한 SSL/TLS로 교체
2. **적절한 인증 구현**: JWT 또는 OAuth2 토큰 사용
3. **모든 입력 검증**: SQL 인젝션, XSS 등 체크
4. **요청 제한**: 요청 쓰로틀링 구현
5. **감사 로깅**: 모든 민감한 작업 로깅

## 🎓 학습 포인트

이 프로젝트를 통해 다음을 배울 수 있습니다:

### gRPC 핵심 개념
- ✅ 4가지 RPC 통신 패턴 (Unary, Server Streaming, Client Streaming, Bidirectional)
- ✅ Protocol Buffers 스키마 설계 및 코드 생성
- ✅ gRPC 서비스 인터페이스 정의 및 구현

### 고급 기능
- ✅ Interceptor를 활용한 횡단 관심사 처리 (인증, 로깅)
- ✅ 스트리밍 RPC의 실전 활용 사례
- ✅ 적절한 에러 핸들링 및 상태 코드 사용
- ✅ 비동기 프로그래밍 패턴 (StreamObserver, CountDownLatch)

### 프로덕션 준비
- ✅ 동시성 처리 (ConcurrentHashMap, ScheduledExecutorService)
- ✅ 리소스 관리 (채널 종료, 스케줄러 정리)
- ✅ 로깅 및 모니터링 베스트 프랙티스

## 🚀 확장 아이디어

1. **데이터베이스 연동**: MongoDB, PostgreSQL 등과 연동
2. **메시지 큐**: Kafka, RabbitMQ로 이벤트 기반 아키텍처 구현
3. **서비스 메시 통합**: Istio, Linkerd와 통합
4. **모니터링**: Prometheus, Grafana 메트릭 추가
5. **분산 트레이싱**: Jaeger, Zipkin 연동
6. **부하 테스트**: Gatling, JMeter로 성능 측정

## 📝 라이선스

MIT License - 학습 및 개발 목적으로 자유롭게 사용하세요.

## 🤝 기여

Pull Request를 환영합니다!

## 📧 문의

질문이나 피드백이 있으시면 GitHub 이슈를 생성해주세요.

---

**gRPC와 Protocol Buffers로 만들었습니다 ❤️**

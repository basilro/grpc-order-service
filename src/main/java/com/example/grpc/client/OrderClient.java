package com.example.grpc.client;

import com.example.grpc.order.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OrderClient {
    
    private static final Logger logger = Logger.getLogger(OrderClient.class.getName());
    private final ManagedChannel channel;
    private final OrderServiceGrpc.OrderServiceBlockingStub blockingStub;
    private final OrderServiceGrpc.OrderServiceStub asyncStub;
    
    /**
     * gRPC 채널과 스텁을 초기화하고 인증 헤더를 추가합니다
     */
    public OrderClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        
        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(authKey, "Bearer sample-token-12345678901234567890");
        
        this.blockingStub = MetadataUtils.attachHeaders(
            OrderServiceGrpc.newBlockingStub(channel), headers);
        this.asyncStub = MetadataUtils.attachHeaders(
            OrderServiceGrpc.newStub(channel), headers);
    }
    
    /**
     * 채널을 종료하고 리소스를 정리합니다
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    
    /**
     * Unary RPC 예제: 단일 주문을 생성합니다
     */
    public void createOrder() {
        logger.info("주문 생성 중...");
        
        CreateOrderRequest request = CreateOrderRequest.newBuilder()
            .setCustomerId("customer-001")
            .addItems(OrderItem.newBuilder()
                .setProductId("prod-001")
                .setProductName("노트북")
                .setQuantity(1)
                .setUnitPrice(1299.99)
                .build())
            .addItems(OrderItem.newBuilder()
                .setProductId("prod-002")
                .setProductName("마우스")
                .setQuantity(2)
                .setUnitPrice(29.99)
                .build())
            .setShippingAddress("서울시 강남구 테헤란로 123")
            .setPaymentInfo(PaymentInfo.newBuilder()
                .setPaymentMethod("신용카드")
                .setCardNumber("****-****-****-1234")
                .setCvv("***")
                .build())
            .build();
        
        OrderResponse response = blockingStub.createOrder(request);
        logger.info("주문 생성 완료: " + response.getOrderId() + 
                    ", 총액: $" + response.getTotalAmount());
    }
    
    /**
     * Server Streaming RPC 예제: 주문 상태를 실시간으로 추적합니다
     */
    public void trackOrder(String orderId) throws InterruptedException {
        logger.info("주문 추적 중: " + orderId);
        
        TrackOrderRequest request = TrackOrderRequest.newBuilder()
            .setOrderId(orderId)
            .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        
        asyncStub.trackOrder(request, new StreamObserver<OrderStatusUpdate>() {
            @Override
            public void onNext(OrderStatusUpdate update) {
                logger.info(String.format("상태 업데이트: %s - %s (%s)",
                    update.getStatus(),
                    update.getDescription(),
                    update.getLocation()));
            }
            
            @Override
            public void onError(Throwable t) {
                logger.severe("주문 추적 중 오류 발생: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                logger.info("주문 추적 완료");
                latch.countDown();
            }
        });
        
        latch.await(30, TimeUnit.SECONDS);
    }
    
    /**
     * Client Streaming RPC 예제: 여러 주문을 일괄로 생성합니다
     */
    public void batchCreateOrders(int count) throws InterruptedException {
        logger.info(count + "개의 주문을 일괄 생성 중...");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        StreamObserver<BatchOrderResponse> responseObserver = new StreamObserver<BatchOrderResponse>() {
            @Override
            public void onNext(BatchOrderResponse response) {
                logger.info(String.format("일괄 처리 결과: 총 %d건, 성공 %d건, 실패 %d건",
                    response.getTotalOrders(),
                    response.getSuccessfulOrders(),
                    response.getFailedOrders()));
            }
            
            @Override
            public void onError(Throwable t) {
                logger.severe("일괄 생성 중 오류 발생: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                logger.info("일괄 주문 생성 완료");
                latch.countDown();
            }
        };
        
        StreamObserver<CreateOrderRequest> requestObserver = asyncStub.batchCreateOrders(responseObserver);
        
        try {
            for (int i = 0; i < count; i++) {
                CreateOrderRequest request = CreateOrderRequest.newBuilder()
                    .setCustomerId("customer-" + (i + 1))
                    .addItems(OrderItem.newBuilder()
                        .setProductId("prod-" + (i + 1))
                        .setProductName("제품 " + (i + 1))
                        .setQuantity(1)
                        .setUnitPrice(100.0 + i * 10)
                        .build())
                    .setShippingAddress("주소 " + (i + 1))
                    .setPaymentInfo(PaymentInfo.newBuilder()
                        .setPaymentMethod("신용카드")
                        .build())
                    .build();
                
                requestObserver.onNext(request);
                Thread.sleep(100);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        
        requestObserver.onCompleted();
        latch.await(30, TimeUnit.SECONDS);
    }
    
    /**
     * Bidirectional Streaming RPC 예제: 실시간으로 주문을 처리합니다
     */
    public void processOrders(List<String> orderIds) throws InterruptedException {
        logger.info("실시간 주문 처리 중...");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        StreamObserver<OrderProcessResponse> responseObserver = new StreamObserver<OrderProcessResponse>() {
            @Override
            public void onNext(OrderProcessResponse response) {
                logger.info(String.format("처리 결과: 주문 %s - %s (상태: %s)",
                    response.getOrderId(),
                    response.getMessage(),
                    response.getNewStatus()));
            }
            
            @Override
            public void onError(Throwable t) {
                logger.severe("주문 처리 중 오류 발생: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                logger.info("주문 처리 스트림 완료");
                latch.countDown();
            }
        };
        
        StreamObserver<OrderProcessRequest> requestObserver = asyncStub.processOrders(responseObserver);
        
        try {
            ProcessAction[] actions = {ProcessAction.CONFIRM, ProcessAction.SHIP, ProcessAction.DELIVER};
            
            for (int i = 0; i < orderIds.size(); i++) {
                OrderProcessRequest request = OrderProcessRequest.newBuilder()
                    .setOrderId(orderIds.get(i))
                    .setAction(actions[i % actions.length])
                    .setNotes("주문 처리 " + (i + 1))
                    .build();
                
                requestObserver.onNext(request);
                Thread.sleep(500);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        
        requestObserver.onCompleted();
        latch.await(30, TimeUnit.SECONDS);
    }
    
    /**
     * 메인 메서드: 모든 RPC 패턴을 순차적으로 테스트합니다
     */
    public static void main(String[] args) throws Exception {
        OrderClient client = new OrderClient("localhost", 9090);
        
        try {
            // 1. Unary RPC 테스트
            client.createOrder();
            
            // 2. Server Streaming RPC 테스트
            // client.trackOrder("sample-order-id");
            
            // 3. Client Streaming RPC 테스트
            // client.batchCreateOrders(5);
            
            // 4. Bidirectional Streaming RPC 테스트
            // List<String> orderIds = Arrays.asList("order-1", "order-2", "order-3");
            // client.processOrders(orderIds);
            
        } finally {
            client.shutdown();
        }
    }
}

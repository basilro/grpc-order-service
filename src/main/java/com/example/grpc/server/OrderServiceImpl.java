package com.example.grpc.server;

import com.example.grpc.order.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
    
    private static final Logger logger = Logger.getLogger(OrderServiceImpl.class.getName());
    private final ConcurrentHashMap<String, OrderResponse> orders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    /**
     * Unary RPC: 단일 주문을 생성하고 주문 정보를 반환
     */
    @Override
    public void createOrder(CreateOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        try {
            validateCreateOrderRequest(request);
            
            String orderId = UUID.randomUUID().toString();
            double totalAmount = calculateTotalAmount(request.getItemsList());
            
            OrderResponse response = OrderResponse.newBuilder()
                .setOrderId(orderId)
                .setStatus(OrderStatus.PENDING)
                .setTotalAmount(totalAmount)
                .setCreatedAt(System.currentTimeMillis())
                .setMessage("Order created successfully")
                .build();
            
            orders.put(orderId, response);
            logger.info("Created order: " + orderId);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        } catch (Exception e) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to create order")
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }
    
    /**
     * Unary RPC: 주문 ID로 주문 정보를 조회
     */
    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        String orderId = request.getOrderId();
        OrderResponse order = orders.get(orderId);
        
        if (order == null) {
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("Order not found: " + orderId)
                    .asRuntimeException()
            );
            return;
        }
        
        responseObserver.onNext(order);
        responseObserver.onCompleted();
    }
    
    /**
     * Server Streaming RPC: 주문 상태를 실시간으로 스트리밍
     * 주문 생성부터 배송 완료까지의 상태 변화를 클라이언트에게 전송
     */
    @Override
    public void trackOrder(TrackOrderRequest request, StreamObserver<OrderStatusUpdate> responseObserver) {
        String orderId = request.getOrderId();
        
        if (!orders.containsKey(orderId)) {
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("Order not found: " + orderId)
                    .asRuntimeException()
            );
            return;
        }
        
        OrderStatus[] statusFlow = {
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED
        };
        
        String[] locations = {
            "Order confirmed at warehouse",
            "Processing at distribution center",
            "Shipped from Seoul",
            "Delivered to customer"
        };
        
        for (int i = 0; i < statusFlow.length; i++) {
            final int index = i;
            scheduler.schedule(() -> {
                OrderStatusUpdate update = OrderStatusUpdate.newBuilder()
                    .setOrderId(orderId)
                    .setStatus(statusFlow[index])
                    .setLocation(locations[index])
                    .setTimestamp(System.currentTimeMillis())
                    .setDescription("Order status updated to " + statusFlow[index].name())
                    .build();
                
                responseObserver.onNext(update);
                
                if (index == statusFlow.length - 1) {
                    responseObserver.onCompleted();
                }
            }, i * 2, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Client Streaming RPC: 클라이언트로부터 여러 주문을 받아 일괄 처리
     * 모든 주문을 받은 후 처리 결과를 한 번에 반환
     */
    @Override
    public StreamObserver<CreateOrderRequest> batchCreateOrders(
            StreamObserver<BatchOrderResponse> responseObserver) {
        
        return new StreamObserver<CreateOrderRequest>() {
            private final List<String> orderIds = new ArrayList<>();
            private int successCount = 0;
            private int failCount = 0;
            
            @Override
            public void onNext(CreateOrderRequest request) {
                try {
                    validateCreateOrderRequest(request);
                    String orderId = UUID.randomUUID().toString();
                    double totalAmount = calculateTotalAmount(request.getItemsList());
                    
                    OrderResponse order = OrderResponse.newBuilder()
                        .setOrderId(orderId)
                        .setStatus(OrderStatus.PENDING)
                        .setTotalAmount(totalAmount)
                        .setCreatedAt(System.currentTimeMillis())
                        .setMessage("Batch order created")
                        .build();
                    
                    orders.put(orderId, order);
                    orderIds.add(orderId);
                    successCount++;
                    logger.info("Batch created order: " + orderId);
                } catch (Exception e) {
                    failCount++;
                    logger.warning("Failed to create batch order: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(Throwable t) {
                logger.severe("Error in batch order creation: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                BatchOrderResponse response = BatchOrderResponse.newBuilder()
                    .setTotalOrders(successCount + failCount)
                    .setSuccessfulOrders(successCount)
                    .setFailedOrders(failCount)
                    .addAllOrderIds(orderIds)
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                logger.info(String.format("Batch processing completed: %d success, %d failed",
                    successCount, failCount));
            }
        };
    }
    
    /**
     * Bidirectional Streaming RPC: 클라이언트와 서버가 동시에 주문 처리 요청과 응답을 스트리밍
     * 실시간으로 주문 상태를 변경하고 즉시 결과를 반환
     */
    @Override
    public StreamObserver<OrderProcessRequest> processOrders(
            StreamObserver<OrderProcessResponse> responseObserver) {
        
        return new StreamObserver<OrderProcessRequest>() {
            
            @Override
            public void onNext(OrderProcessRequest request) {
                String orderId = request.getOrderId();
                OrderResponse order = orders.get(orderId);
                
                if (order == null) {
                    OrderProcessResponse response = OrderProcessResponse.newBuilder()
                        .setOrderId(orderId)
                        .setSuccess(false)
                        .setMessage("Order not found")
                        .setNewStatus(OrderStatus.PENDING)
                        .build();
                    responseObserver.onNext(response);
                    return;
                }
                
                OrderStatus newStatus = getNewStatus(request.getAction());
                OrderResponse updatedOrder = order.toBuilder()
                    .setStatus(newStatus)
                    .build();
                
                orders.put(orderId, updatedOrder);
                
                OrderProcessResponse response = OrderProcessResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(true)
                    .setMessage("Order processed: " + request.getAction().name())
                    .setNewStatus(newStatus)
                    .build();
                
                responseObserver.onNext(response);
                logger.info(String.format("Processed order %s: %s -> %s",
                    orderId, order.getStatus(), newStatus));
            }
            
            @Override
            public void onError(Throwable t) {
                logger.severe("Error in order processing stream: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                logger.info("Order processing stream completed");
            }
        };
    }
    
    /**
     * 주문 생성 요청의 유효성을 검증
     */
    private void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request.getCustomerId() == null || request.getCustomerId().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request.getItemsList().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        if (request.getShippingAddress() == null || request.getShippingAddress().isEmpty()) {
            throw new IllegalArgumentException("Shipping address is required");
        }
    }
    
    /**
     * 주문 아이템 목록의 총 금액을 계산
     */
    private double calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
            .mapToDouble(item -> item.getUnitPrice() * item.getQuantity())
            .sum();
    }
    
    /**
     * 처리 액션에 따른 새로운 주문 상태를 반환
     */
    private OrderStatus getNewStatus(ProcessAction action) {
        switch (action) {
            case CONFIRM: return OrderStatus.CONFIRMED;
            case SHIP: return OrderStatus.SHIPPED;
            case DELIVER: return OrderStatus.DELIVERED;
            case CANCEL: return OrderStatus.CANCELLED;
            case REFUND: return OrderStatus.REFUNDED;
            default: return OrderStatus.PENDING;
        }
    }
    
    /**
     * 서버 종료 시 스케줄러를 정리
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}

package com.example.grpc.server;

import com.example.grpc.server.interceptor.AuthInterceptor;
import com.example.grpc.server.interceptor.LoggingInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OrderServer {
    
    private static final Logger logger = Logger.getLogger(OrderServer.class.getName());
    private static final int PORT = 9090;
    
    private Server server;
    private OrderServiceImpl orderService;
    
    /**
     * gRPC 서버를 시작하고 인터셉터를 등록
     */
    public void start() throws IOException {
        orderService = new OrderServiceImpl();
        
        server = ServerBuilder.forPort(PORT)
            .addService(orderService)
            .intercept(new LoggingInterceptor())
            .intercept(new AuthInterceptor())
            .build()
            .start();
        
        logger.info("Server started, listening on " + PORT);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server since JVM is shutting down");
            try {
                OrderServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("Server shut down");
        }));
    }
    
    /**
     * gRPC 서버를 정상적으로 종료
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        if (orderService != null) {
            orderService.shutdown();
        }
    }
    
    /**
     * 서버가 종료될 때까지 대기
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    /**
     * 메인 메서드: 서버를 시작하고 실행 상태를 유지
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final OrderServer server = new OrderServer();
        server.start();
        server.blockUntilShutdown();
    }
}

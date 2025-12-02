package com.example.grpc.server.interceptor;

import io.grpc.*;
import java.util.logging.Logger;

public class LoggingInterceptor implements ServerInterceptor {
    
    private static final Logger logger = Logger.getLogger(LoggingInterceptor.class.getName());
    
    /**
     * 모든 gRPC 호출을 가로채서 로깅을 처리합니다
     * 메서드명, 클라이언트 정보, 처리 시간 등을 기록합니다
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startTime = System.currentTimeMillis();
        
        logger.info("gRPC 호출 시작: " + methodName);
        
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long duration = System.currentTimeMillis() - startTime;
                        logger.info(String.format("gRPC 호출 완료: %s [%s] %dms 소요",
                            methodName, status.getCode(), duration));
                        super.close(status, trailers);
                    }
                }, headers)) {
            
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    logger.severe(methodName + " 에러 발생: " + e.getMessage());
                    throw e;
                }
            }
        };
    }
}

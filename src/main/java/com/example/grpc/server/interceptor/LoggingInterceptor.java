package com.example.grpc.server.interceptor;

import io.grpc.*;
import java.util.logging.Logger;

public class LoggingInterceptor implements ServerInterceptor {
    
    private static final Logger logger = Logger.getLogger(LoggingInterceptor.class.getName());
    
    /**
     * 모든 gRPC 호출을 가로채서 로깅 처리
     * 메서드명, 클라이언트 정보, 처리 시간 등을 기록
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startTime = System.currentTimeMillis();
        
        logger.info("gRPC call started: " + methodName);
        
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long duration = System.currentTimeMillis() - startTime;
                        logger.info(String.format("gRPC call completed: %s [%s] in %dms",
                            methodName, status.getCode(), duration));
                        super.close(status, trailers);
                    }
                }, headers)) {
            
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    logger.severe("Error in " + methodName + ": " + e.getMessage());
                    throw e;
                }
            }
        };
    }
}

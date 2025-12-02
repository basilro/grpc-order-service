package com.example.grpc.server.interceptor;

import io.grpc.*;
import java.util.logging.Logger;

public class AuthInterceptor implements ServerInterceptor {
    
    private static final Logger logger = Logger.getLogger(AuthInterceptor.class.getName());
    private static final Metadata.Key<String> AUTH_TOKEN_KEY = 
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    
    /**
     * 인증 토큰을 검증하는 인터셉터
     * 헤더에서 authorization 토큰을 추출하여 유효성을 검사
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String token = headers.get(AUTH_TOKEN_KEY);
        
        if (token == null) {
            logger.warning("Missing authorization token");
            call.close(Status.UNAUTHENTICATED
                .withDescription("Missing authorization token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        
        if (!validateToken(token)) {
            logger.warning("Invalid authorization token: " + token);
            call.close(Status.UNAUTHENTICATED
                .withDescription("Invalid authorization token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        
        logger.info("Authentication successful for token: " + maskToken(token));
        return next.startCall(call, headers);
    }
    
    /**
     * 토큰의 유효성을 검증 (실제 환경에서는 JWT 검증 등 구현)
     */
    private boolean validateToken(String token) {
        // 데모를 위한 간단한 검증 - 실제로는 JWT 검증 등을 수행
        return token.startsWith("Bearer ") && token.length() > 20;
    }
    
    /**
     * 로그에 출력할 때 토큰을 마스킹 처리
     */
    private String maskToken(String token) {
        if (token.length() > 10) {
            return token.substring(0, 10) + "***";
        }
        return "***";
    }
}

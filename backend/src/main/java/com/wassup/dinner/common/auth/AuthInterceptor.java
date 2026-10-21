package com.wassup.dinner.common.auth;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "loginUser";

    private final TokenProvider tokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        LoginUser user = tokenProvider.parse(request.getHeader(HttpHeaders.AUTHORIZATION))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (request.getRequestURI().startsWith("/api/admin") && !user.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        request.setAttribute(ATTR, user);
        return true;
    }
}

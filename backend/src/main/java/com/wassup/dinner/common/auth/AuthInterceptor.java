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
    private final com.wassup.dinner.user.domain.UserRepository userRepository;
    private final com.wassup.dinner.user.domain.AdminAccountRepository adminAccountRepository;

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
        // 운영자 권한별 접근 통제: 조회 전용은 변경 불가, 운영 매니저는 매칭 정책 변경 불가
        if (user.isAdmin() && !"GET".equalsIgnoreCase(request.getMethod())) {
            var role = adminAccountRepository.findById(user.userId())
                    .map(com.wassup.dinner.user.domain.AdminAccount::effectiveRole)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
            String uri = request.getRequestURI();
            if (role == com.wassup.dinner.user.domain.AdminAccount.Role.VIEWER) {
                throw new ApiException(ErrorCode.FORBIDDEN, "조회 전용 계정이라 바꿀 수 없어요.");
            }
            if (role == com.wassup.dinner.user.domain.AdminAccount.Role.OPERATOR && uri.startsWith("/api/admin/policy")) {
                throw new ApiException(ErrorCode.FORBIDDEN, "매칭 정책은 총괄 운영자만 바꿀 수 있어요.");
            }
        }
        // 탈퇴한 참가자 토큰은 더 쓰지 못한다
        if (!user.isAdmin() && userRepository.findById(user.userId())
                .map(u -> u.getStatus() == com.wassup.dinner.user.domain.AccountStatus.WITHDRAWN).orElse(true)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        request.setAttribute(ATTR, user);
        return true;
    }
}

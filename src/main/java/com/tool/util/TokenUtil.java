package com.tool.util;

import com.tool.vo.testVO.ToolUser;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenUtil {

    public static final String TOKEN_COOKIE_NAME = "tool_token";
    public static final String TOKEN_HEADER_NAME = "X-Tool-Token";
    public static final String TOOL_USER_REQUEST_ATTRIBUTE = "toolUser";

    private static final long TOKEN_EXPIRE_MILLIS = 5 * 60 * 1000L;
    private static final ConcurrentHashMap<String, TokenInfo> TOKEN_MAP = new ConcurrentHashMap<String, TokenInfo>();

    private TokenUtil() {
    }

    public static String createToken(ToolUser toolUser) {
        cleanExpiredTokens();
        String token = UUID.randomUUID().toString().replace("-", "");
        TOKEN_MAP.put(token, new TokenInfo(copyUser(toolUser), System.currentTimeMillis() + TOKEN_EXPIRE_MILLIS));
        return token;
    }

    public static boolean validateToken(String token) {
        return getToolUser(token) != null;
    }

    public static ToolUser getToolUser(String token) {
        if (isBlank(token)) {
            return null;
        }
        TokenInfo tokenInfo = TOKEN_MAP.get(token);
        if (tokenInfo == null) {
            return null;
        }
        if (tokenInfo.expireTime < System.currentTimeMillis()) {
            TOKEN_MAP.remove(token);
            return null;
        }
        return copyUser(tokenInfo.toolUser);
    }

    public static ToolUser getToolUser() {
        return getToolUser(getCurrentRequest());
    }

    public static ToolUser getToolUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object user = request.getAttribute(TOOL_USER_REQUEST_ATTRIBUTE);
        if (user instanceof ToolUser) {
            return copyUser((ToolUser) user);
        }
        return getToolUser(resolveToken(request));
    }

    public static String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader(TOKEN_HEADER_NAME);
        if (!isBlank(token)) {
            return token.trim();
        }

        String authorization = request.getHeader("Authorization");
        if (!isBlank(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }

        token = request.getParameter("token");
        if (!isBlank(token)) {
            return token.trim();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private static HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    public static void removeToken(String token) {
        if (!isBlank(token)) {
            TOKEN_MAP.remove(token);
        }
    }

    public static int getTokenExpireSeconds() {
        return (int) (TOKEN_EXPIRE_MILLIS / 1000L);
    }

    public static void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TokenInfo>> iterator = TOKEN_MAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TokenInfo> entry = iterator.next();
            if (entry.getValue().expireTime < now) {
                TOKEN_MAP.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static ToolUser copyUser(ToolUser source) {
        if (source == null) {
            return null;
        }
        ToolUser user = new ToolUser();
        user.setId(source.getId());
        user.setLoginName(source.getLoginName());
        user.setPassword(null);
        return user;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static class TokenInfo {
        private final ToolUser toolUser;
        private final long expireTime;

        private TokenInfo(ToolUser toolUser, long expireTime) {
            this.toolUser = toolUser;
            this.expireTime = expireTime;
        }
    }
}

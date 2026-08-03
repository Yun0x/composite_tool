package com.tool.controller;

import com.tool.config.datasource.DataSourceContextHolder;
import com.tool.mapper.TestMapper;
import com.tool.util.TokenUtil;
import com.tool.vo.testVO.ToolUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PageController implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(PageController.class);

    private final TestMapper testMapper;

    @Value("${page.force-login:true}")
    private boolean forceLogin;

    public PageController(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!forceLogin) {
                    return true;
                }

                String uri = getRequestUri(request);
                if (isPublicPath(uri)) {
                    return true;
                }

                if (needTokenCheck(uri)) {
                    String token = TokenUtil.resolveToken(request);
                    ToolUser toolUser = TokenUtil.getToolUser(token);
                    if (toolUser != null) {
                        request.setAttribute(TokenUtil.TOOL_USER_REQUEST_ATTRIBUTE, toolUser);
                        return true;
                    }

                    if (isApiPath(uri)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":401,\"msg\":\"登录已过期\"}");
                        return false;
                    }

                    response.sendRedirect(request.getContextPath() + "/login.html");
                    return false;
                }

                return true;
            }
        }).addPathPatterns("/**");
    }

    @GetMapping("/")
    public String home() {
        return forceLogin ? "redirect:/login.html" : "redirect:/index.html";
    }

    @PostMapping("/login")
    @ResponseBody
    public Map<String, Object> login(@RequestBody Map<String, String> loginParam, HttpServletResponse response) {
        TokenUtil.cleanExpiredTokens();

        String username = loginParam == null ? null : loginParam.get("username");
        String password = loginParam == null ? null : loginParam.get("password");

        Map<String, Object> result = new HashMap<String, Object>();
        ToolUser toolUser = checkLogin(username, password);
        if (toolUser != null) {
            String token = TokenUtil.createToken(toolUser);
            addTokenCookie(response, token);
            logger.info("用户登录成功：系统账号 {}", username);
            result.put("code", 200);
            result.put("token", token);
            result.put("url", "/page.html");
            result.put("user", buildUserResult(toolUser));
            return result;
        }

        logger.info("method=login loginName={} username={} result=fail", "unknown", username);
        result.put("code", 401);
        result.put("msg", "账号或密码错误");
        return result;
    }

    @PostMapping("/logout")
    @ResponseBody
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        TokenUtil.removeToken(TokenUtil.resolveToken(request));
        clearTokenCookie(response);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("code", 200);
        return result;
    }

    @GetMapping("/me")
    @ResponseBody
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("code", 200);
        result.put("user", buildUserResult(TokenUtil.getToolUser()));
        return result;
    }

    @GetMapping("/page")
    public String page() {
        return "redirect:/page.html";
    }

    @GetMapping("/order")
    public String order() {
        return "redirect:/order.html";
    }

    @GetMapping("/repair")
    public String repair() {
        return "redirect:/repair.html";
    }

    @GetMapping({"/temp/order"})
    public String oldTempOrder() {
        return "redirect:/order.html";
    }

    private ToolUser checkLogin(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            return null;
        }
        DataSourceContextHolder.set("base");
        try {
            return testMapper.checkToolLogin(username, password);
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    private void addTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(TokenUtil.TOKEN_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(TokenUtil.getTokenExpireSeconds());
        response.addCookie(cookie);
    }

    private void clearTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(TokenUtil.TOKEN_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Map<String, Object> buildUserResult(ToolUser toolUser) {
        Map<String, Object> user = new HashMap<String, Object>();
        if (toolUser != null) {
            user.put("id", toolUser.getId());
            user.put("loginName", toolUser.getLoginName());
        }
        return user;
    }

    private String getRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && contextPath.length() > 0 && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isPublicPath(String uri) {
        return "/".equals(uri)
                || "/login".equals(uri)
                || "/login.html".equals(uri);
    }

    private boolean needTokenCheck(String uri) {
        return isPrivatePagePath(uri)
                || isProtectedApiPath(uri)
                || isHtmlPage(uri);
    }

    private boolean isPrivatePagePath(String uri) {
        return "/page".equals(uri)
                || "/page.html".equals(uri)
                || "/order".equals(uri)
                || "/order.html".equals(uri)
                || "/repair".equals(uri)
                || "/repair.html".equals(uri)
                || "/temp/order".equals(uri)
                || uri.startsWith("/temp/order/");
    }

    private boolean isProtectedApiPath(String uri) {
        return "/logout".equals(uri)
                || "/me".equals(uri)
                || uri.startsWith("/api/")
                || "/test/getFirstOrder".equals(uri)
                || "/test/getRepairRecords".equals(uri);
    }

    private boolean isApiPath(String uri) {
        return uri.startsWith("/api/")
                || uri.startsWith("/test/")
                || "/me".equals(uri)
                || "/logout".equals(uri);
    }

    private boolean isHtmlPage(String uri) {
        return uri != null && uri.endsWith(".html");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}

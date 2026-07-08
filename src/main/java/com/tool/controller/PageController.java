package com.tool.controller;

import com.tool.config.datasource.DataSourceContextHolder;
import com.tool.mapper.TestMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class PageController implements WebMvcConfigurer {

    private static final String LOCAL_STORAGE_TOKEN_KEY = "toolLoginToken";
    private static final long TOKEN_EXPIRE_TIME = 5 * 60 * 1000L;
    private static final ConcurrentHashMap<String, Long> TOKEN_EXPIRE_MAP = new ConcurrentHashMap<>();
    private final TestMapper testMapper;

    public PageController(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Value("${page.force-login:true}")
    private boolean forceLogin;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!forceLogin || !"GET".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                String uri = getRequestUri(request);
                if (checkIsForceLogin(uri)) {
                    return true;
                }
                if (isHtmlPage(uri)) {
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
    public Map<String, Object> login(@RequestBody Map<String, String> loginParam) {
        cleanExpiredTokens();

        String username = loginParam == null ? null : loginParam.get("username");
        String password = loginParam == null ? null : loginParam.get("password");

        Map<String, Object> result = new HashMap<>();
        if (checkLogin(username, password)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            TOKEN_EXPIRE_MAP.put(token, System.currentTimeMillis() + TOKEN_EXPIRE_TIME);

            result.put("code", 200);
            result.put("token", token);
            result.put("url", "/temp/order/" + token);
            return result;
        }

        result.put("code", 401);
        result.put("msg", "账号或密码错误");
        return result;
    }

    private boolean checkLogin(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            return false;
        }
        DataSourceContextHolder.set("base");
        try {
            String loginName = testMapper.checkToolLogin(username, password);
            return !isBlank(loginName);
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    @GetMapping({"/temp/order", "/temp/order/"})
    public String tempOrderWithoutToken() {
        return "redirect:/login.html";
    }

    @GetMapping("/temp/order/{token}")
    public ResponseEntity<String> tempOrder(@PathVariable("token") String token) throws IOException {
        cleanExpiredTokens();

        if (!isLegalToken(token)) {
            return redirectToLogin();
        }

        Long expireTime = TOKEN_EXPIRE_MAP.get(token);
        long now = System.currentTimeMillis();
        if (expireTime == null || expireTime < now) {
            TOKEN_EXPIRE_MAP.remove(token);
            return redirectToLogin();
        }

        String html = readOrderHtml();
        html = localStorageCheck(html, token);
        html = expireCheck(html);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(html);
    }

    private ResponseEntity<String> redirectToLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/login.html")
                .build();
    }

    private String readOrderHtml() throws IOException {
        ClassPathResource resource = new ClassPathResource("private/order.html");
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private String localStorageCheck(String html, String token) {
        String script = "\n<script>\n"
                + "(function () {\n"
                + "  var savedToken = null;\n"
                + "  try {\n"
                + "    savedToken = window.localStorage.getItem('" + LOCAL_STORAGE_TOKEN_KEY + "');\n"
                + "  } catch (e) {\n"
                + "    savedToken = null;\n"
                + "  }\n"
                + "  if (!savedToken || savedToken !== '" + token + "') {\n"
                + "    document.documentElement.style.display = 'none';\n"
                + "    window.location.replace('/login.html');\n"
                + "  }\n"
                + "}());\n"
                + "</script>\n";
        String lowerHtml = html.toLowerCase();
        int headEndIndex = lowerHtml.indexOf("</head>");
        if (headEndIndex >= 0) {
            return html.substring(0, headEndIndex) + script + html.substring(headEndIndex);
        }

        int bodyStartIndex = lowerHtml.indexOf("<body");
        if (bodyStartIndex >= 0) {
            int bodyStartEndIndex = lowerHtml.indexOf(">", bodyStartIndex);
            if (bodyStartEndIndex >= 0) {
                return html.substring(0, bodyStartEndIndex + 1)
                        + script
                        + html.substring(bodyStartEndIndex + 1);
            }
        }

        return script + html;
    }

    private String expireCheck(String html) {
        String script = "\n<script>\n"
                + "setTimeout(function () {\n"
                + "  window.location.href = '/login.html';\n"
                + "}, 5 * 60 * 1000);\n"
                + "</script>\n";
        int bodyEndIndex = html.toLowerCase().lastIndexOf("</body>");
        if (bodyEndIndex >= 0) {
            return html.substring(0, bodyEndIndex) + script + html.substring(bodyEndIndex);
        }
        return html + script;
    }

    private boolean isLegalToken(String token) {
        return token != null && token.matches("[0-9a-fA-F]{32}");
    }

    private String getRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && contextPath.length() > 0 && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean checkIsForceLogin(String uri) {
        return "/".equals(uri)
                || "/login.html".equals(uri)
                || "/temp/order".equals(uri)
                || uri.startsWith("/temp/order/");
    }

    private boolean isHtmlPage(String uri) {
        return uri != null && uri.endsWith(".html");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = TOKEN_EXPIRE_MAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < now) {
                TOKEN_EXPIRE_MAP.remove(entry.getKey(), entry.getValue());
            }
        }
    }

}

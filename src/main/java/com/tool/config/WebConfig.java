package com.tool.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
/**
 * @Description：地址映射器
 *
 * @author Lachesism
 * @date 2026-04-06
 */


@Configuration
public class WebConfig implements WebMvcConfigurer {
    public WebConfig() {
        System.out.println("🔥 WebConfig 加载了");
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/contract/file/**")
                .addResourceLocations("file:///D:/contract/");
    }
}
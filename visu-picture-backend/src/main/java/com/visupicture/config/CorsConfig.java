package com.visupicture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 全局跨域配置
 *
 * 【安全说明】原先使用 {@code allowedOriginPatterns("*")} + {@code allowCredentials(true)}：
 * 该组合会「反射」任意请求的 Origin 并返回 {@code Access-Control-Allow-Origin: <你的 Origin>}
 * 与 {@code Access-Control-Allow-Credentials: true}，使任意站点都能以受害者身份读取接口响应。
 * 又因 Sa-Token Cookie 设为 {@code SameSite=None}（Android APP WebView 跨站所需），
 * 浏览器会在跨站请求中自动携带登录 Cookie，二者叠加构成完整的跨站数据窃取 / CSRF 链路。
 *
 * 现改为**显式来源白名单**。白名单通过配置项 cors.allowed-origins 维护（逗号分隔），
 * 生产环境请只填自己的域名；Android APP（Capacitor）来源为 capacitor://localhost 与 https://localhost。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 允许的来源白名单（逗号分隔）。
     *
     * 实际取值来自各环境的 profile 配置：
     *   application-local.yml —— 本地开发（localhost:5173 / 8123）
     *   application-prod.yml  —— 线上部署（https://www.visuhoriz.cn 等）
     * 这里的默认值仅在未配置该项时兜底使用（本地开发场景）。
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8123,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // 覆盖所有请求：仅白名单来源可携带凭证访问
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 【安全修复】显式白名单，禁用通配反射
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 收敛请求头：仅放行业务实际使用的头
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With", "satoken")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }
}

package com.visupicture.manager.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * WebSocket 配置（定义连接）
 *
 * 【安全说明】原先 {@code setAllowedOrigins("*")} 允许任意站点发起握手。
 * WebSocket 不受 CORS 约束，而 Sa-Token Cookie 为 {@code SameSite=None}
 * （Android APP WebView 跨站所需）时会随跨站握手自动携带，构成跨站 WebSocket 劫持（CSWSH）风险：
 * 攻击页面可把受害者拉入协同编辑房间、推送伪造编辑事件。
 * 现改为与 CORS 一致的来源白名单。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 允许的握手来源（与 cors.allowed-origins 保持一致）。
     *
     * 实际取值来自各环境的 profile 配置：
     *   application-local.yml —— 本地开发
     *   application-prod.yml  —— 线上部署
     * 此处默认值仅在未配置该项时兜底使用。
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8123,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)
                // 【安全修复】白名单来源，替代通配 "*"
                .setAllowedOrigins(origins);
    }
}

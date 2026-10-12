package com.visupicture.utils;

import cn.hutool.core.util.StrUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具
 *
 * <p>⚠️ 关于代理头的信任边界：{@code X-Forwarded-For} 是客户端可自行伪造的请求头。
 * 是否可信取决于反向代理如何写入：</p>
 * <ul>
 *   <li>Nginx 使用 {@code proxy_set_header X-Real-IP $remote_addr} 时该头被覆盖，
 *       客户端伪造无效 —— 因此这里<b>优先取 X-Real-IP</b>。</li>
 *   <li>若只有 XFF，取<b>最后一段</b>而非第一段：反代 {@code $proxy_add_x_forwarded_for}
 *       会在末尾追加真实地址，攻击者自行塞入的伪造值只会出现在前面。</li>
 * </ul>
 * <p>两者都没有时回退到 {@code getRemoteAddr()}（TCP 层地址，无法伪造）。</p>
 * <p>注意：本工具用于限流与审计留痕。若限流是强安全要求，
 * 应在反向代理层做真实 IP 覆盖，否则直连后端时 XFF 仍可被绕过。</p>
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 请求头中可能携带客户端地址的字段，按可信度降序
     */
    private static final String[] IP_HEADERS = {
            "X-Real-IP",
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /**
     * 获取客户端 IP（尽力而为，可能受伪造头影响，见类注释）
     *
     * @param request 请求
     * @return IP 字符串；无法获取时返回 "unknown"
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StrUtil.isBlank(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            // X-Forwarded-For 可能是 "client, proxy1, proxy2"，取最后一段（反代追加的真实来源）
            String ip = value;
            if (ip.contains(",")) {
                String[] parts = ip.split(",");
                ip = parts[parts.length - 1];
            }
            ip = ip.trim();
            if (StrUtil.isNotBlank(ip)) {
                // 去掉 IPv6 的端口写法 [::1]:1234 或 IPv4 的 1.2.3.4:56
                return normalize(ip);
            }
        }
        String remote = request.getRemoteAddr();
        return StrUtil.isBlank(remote) ? "unknown" : normalize(remote);
    }

    /**
     * 归一化：去掉端口、去掉 IPv6 映射前缀
     */
    private static String normalize(String ip) {
        if (ip.startsWith("[")) {
            // [::1]:1234 -> ::1
            int end = ip.indexOf(']');
            if (end > 0) {
                ip = ip.substring(1, end);
            }
        } else if (ip.chars().filter(c -> c == ':').count() == 1) {
            // IPv4:port -> IPv4（IPv6 有多个冒号，不能按此规则截断）
            ip = ip.substring(0, ip.indexOf(':'));
        }
        // IPv4 映射的 IPv6：::ffff:127.0.0.1 -> 127.0.0.1
        if (ip.regionMatches(true, 0, "::ffff:", 0, 7)) {
            ip = ip.substring(7);
        }
        return ip;
    }
}
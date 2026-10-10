package com.visupicture.utils;

import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * 外链访问安全校验（防 SSRF）
 *
 * 背景：图片 URL 上传功能会让服务端主动请求用户提供的地址，
 * 若不校验目标地址，攻击者可借此访问内网服务、云元数据（169.254.169.254）等。
 */
@Slf4j
public class SsrfUtils {

    private SsrfUtils() {
    }

    /**
     * 校验外链是否允许服务端访问：解析域名并拒绝一切非公网地址。
     *
     * 拒绝范围：
     * - 回环 / 本机任意地址（127.0.0.0/8、::1、0.0.0.0）
     * - 链路本地（169.254.0.0/16，含云元数据 169.254.169.254、fe80::/10）
     * - 私有网段（10/8、172.16/12、192.168/16、fc00::/7）
     * - CGNAT（100.64.0.0/10）
     * - 组播 / 保留地址
     * - 各类非点分十进制的 IP 字面量写法（十进制、八进制、十六进制）
     *
     * @param fileUrl 待校验的 URL
     */
    public static void validateExternalUrl(String fileUrl) {
        URL url;
        try {
            url = new URL(fileUrl);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 1. 仅允许 http / https
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 2. 拒绝非点分十进制写法的 IP 字面量（如 http://2130706433/ 等价于 127.0.0.1）
        if (looksLikeEncodedIp(host)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址不合法");
        }
        // 3. 解析所有 IP（一个域名可能对应多个 A 记录，需逐个检查）
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法解析该文件地址的主机名");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法解析该文件地址的主机名");
        }
        for (InetAddress address : addresses) {
            if (isForbiddenAddress(address)) {
                log.warn("拦截疑似 SSRF 请求, url={}, resolvedIp={}", fileUrl, address.getHostAddress());
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止访问内网或保留地址");
            }
        }
    }

    /**
     * 判断是否为需要拒绝的地址
     */
    private static boolean isForbiddenAddress(InetAddress address) {
        if (address.isLoopbackAddress()      // 127.0.0.0/8、::1
                || address.isAnyLocalAddress()   // 0.0.0.0、::
                || address.isLinkLocalAddress()  // 169.254.0.0/16（含云元数据）、fe80::/10
                || address.isSiteLocalAddress()  // 10/8、172.16/12、192.168/16
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            // CGNAT 100.64.0.0/10
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
            // 保留段 240.0.0.0/4
            if (b0 >= 240) {
                return true;
            }
            // 0.0.0.0/8
            if (b0 == 0) {
                return true;
            }
        } else if (bytes.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }

    /**
     * 识别各种「非标准写法」的 IP 字面量（十进制整数、八进制、十六进制、带前导零），
     * 这些写法会被底层解析为真实 IP，从而绕过基于字符串的校验。
     */
    private static boolean looksLikeEncodedIp(String host) {
        // 纯数字（十进制/前导零）：如 2130706433、0177.0.0.1
        if (host.matches("^\\d+$")) {
            return true;
        }
        // 十六进制：0x7f000001
        if (host.toLowerCase().startsWith("0x")) {
            return true;
        }
        // 含八进制段的分段写法：0177.0.0.1
        if (host.matches("^0\\d+(\\.\\d+){1,3}$")) {
            return true;
        }
        // 单段数字带点：127.1（等价 127.0.0.1）
        if (host.matches("^\\d+\\.\\d+$")) {
            return true;
        }
        return false;
    }
}

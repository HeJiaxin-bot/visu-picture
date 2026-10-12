package com.visupicture.config;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 包装请求，使 InputStream 可以重复读取
 *
 * <p><b>【编码修复】</b>原实现用 {@code new InputStreamReader(inputStream)} 与
 * {@code body.getBytes()}，两者都<b>依赖 JVM 平台默认字符集</b>。在中文 Windows 上
 * 默认是 GBK，于是：</p>
 * <ol>
 *   <li>读取时把 UTF-8 字节按 GBK 解码 —— 产生乱码字符；</li>
 *   <li>回写时再按 GBK 编码 —— 若该字符无法映射回原字节，就变成 {@code ?}（0x3F）；</li>
 *   <li>Jackson 拿到损坏的字节流，抛出
 *       {@code Invalid UTF-8 middle byte 0x3f}，接口返回「系统错误」。</li>
 * </ol>
 * <p>后果是：凡经过本过滤器的 JSON 请求，只要载荷含中文（或任何非 ASCII），
 * 就可能被静默破坏 —— 例如中文图片名、简介、评论。是否出错取决于字节序列能否
 * 在 GBK 下无损往返，因此表现为<b>时好时坏</b>，极难排查。</p>
 *
 * <p>修复方式：读写两端都<b>显式指定 UTF-8</b>（HTTP JSON 的默认编码），
 * 不再依赖平台默认值。这样在 Windows 开发机、Linux 服务器、容器内的行为完全一致。</p>
 *
 * @author 何佳鑫
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    /**
     * 缓存的请求体（已按 UTF-8 正确解码）
     */
    private final byte[] bodyBytes;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        byte[] raw = new byte[0];
        try (InputStream inputStream = request.getInputStream()) {
            raw = readAllBytes(inputStream);
        } catch (IOException e) {
            log.error("读取请求体失败，将按空请求体继续处理", e);
        }
        this.bodyBytes = raw;
    }

    /**
     * 一次性读完全部字节（不经过字符解码，避免任何字符集转换）
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bodyBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 同步读取场景不需要异步回调；留空是有意为之
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        // 显式 UTF-8：与 HTTP JSON 的实际编码一致，不受平台默认字符集影响
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(bodyBytes), StandardCharsets.UTF_8));
    }

    /**
     * 获取请求体字符串（按 UTF-8 解码）
     */
    public String getBody() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    @Override
    public int getContentLength() {
        return bodyBytes.length;
    }

    @Override
    public long getContentLengthLong() {
        return bodyBytes.length;
    }
}
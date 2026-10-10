package com.visupicture.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.utils.SsrfUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1. 校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 2. 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 3. 校验 URL 的协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址"
        );
        // 4. 【安全修复】SSRF 防护：解析域名并拒绝内网 / 回环 / 云元数据等保留地址。
        //    原实现只校验协议前缀，攻击者可让服务端请求 127.0.0.1、169.254.169.254 等。
        SsrfUtils.validateExternalUrl(fileUrl);

        // 5. 发送 HEAD 请求验证文件是否存在
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    // 禁止跟随重定向：否则可用 302 跳到内网地址绕过第 4 步的校验
                    .setMaxRedirectCount(0)
                    .timeout(5000)
                    .execute();
            // 【安全修复】原实现在此 `if (status != 200) return;` —— 非 200 时直接跳过
            // 后续全部类型/大小校验，而 processFile 仍会无条件下载响应体，形成绕过。
            // 现在无论状态码如何都完成校验，非 200 直接拒绝。
            ThrowUtils.throwIf(httpResponse.getStatus() != HttpStatus.HTTP_OK,
                    ErrorCode.PARAMS_ERROR, "文件地址不可访问（HTTP " + httpResponse.getStatus() + "）");
            // 6. 文件存在，文件类型校验
            String contentType = httpResponse.header("Content-Type");
            // 不为空，才校验是否合法，这样校验规则相对宽松
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6. 文件存在，文件大小校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024;
                    // 上限 10MB：网络抓取的原图普遍大于 2MB，过严会导致抓图大量失败；
                    // 超限图片会由对象存储数据万象自动压缩
                    ThrowUtils.throwIf(contentLength > 10 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 10MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        } finally {
            // 记得释放资源
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }
}

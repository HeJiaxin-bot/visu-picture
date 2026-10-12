package com.visupicture.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 图片内容摘要工具（用于重复图片检测）
 *
 * <p>对图片<b>字节流</b>做 SHA-256，得到内容指纹 {@code picHash}。
 * 字节完全相同的图片（同一文件重复上传、同一直链重复抓取）会得到相同 hash，
 * 据此可在上传阶段拦截重复。</p>
 *
 * <p><b>能力边界（必须知晓）</b>：本方案只识别<b>字节级重复</b>。
 * 以下情况哈希不同、无法识别：</p>
 * <ul>
 *   <li>图片被重新编码（转格式、改质量、加水印、调尺寸）；</li>
 *   <li>同一画面裁剪出的小图；</li>
 *   <li>视觉相似但像素不同的图。</li>
 * </ul>
 * <p>要覆盖这些需要感知哈希（pHash/dHash）或向量检索，属于本工具的职责范围之外。
 * 因此该字段命名为 picHash 而非"相似度"，避免高估其能力。</p>
 */
@Slf4j
public final class PictureHashUtils {

    private PictureHashUtils() {
    }

    /**
     * 计算本地文件的 SHA-256
     *
     * @param file 图片文件
     * @return 64 位小写十六进制字符串
     */
    public static String hashFile(File file) {
        if (file == null || !file.exists()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不存在，无法计算摘要");
        }
        return DigestUtil.sha256Hex(FileUtil.readBytes(file));
    }

    /**
     * 计算远程图片的 SHA-256（流式读取，避免把大图整体读进内存两次）
     *
     * @param fileUrl 图片直链
     * @return 64 位小写十六进制字符串；失败时返回 null（调用方据此跳过去重，不阻断上传）
     */
    public static String hashUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        try (InputStream in = new URL(fileUrl).openStream()) {
            return hashStream(in);
        } catch (Exception e) {
            // 摘要失败不应阻断上传主流程：退化为「不去重」
            log.warn("计算远程图片摘要失败，跳过重复检测，url={}", fileUrl, e);
            return null;
        }
    }

    /**
     * 流式计算 SHA-256
     */
    public static String hashStream(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    /**
     * 计算本地文件的 SHA-256（自行流式读取，避免一次性载入大文件）
     */
    public static String hashFileStream(File file) {
        if (file == null || !file.exists()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不存在，无法计算摘要");
        }
        try (InputStream in = new FileInputStream(file)) {
            return hashStream(in);
        } catch (Exception e) {
            log.warn("计算本地图片摘要失败，跳过重复检测，file={}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
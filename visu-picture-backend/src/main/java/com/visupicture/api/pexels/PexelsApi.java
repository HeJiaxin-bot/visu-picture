package com.visupicture.api.pexels;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pexels 图片搜索 API（https://www.pexels.com/api/）
 * 免费额度：每小时 200 次请求、每月 20000 次，单次最多返回 80 张
 */
@Slf4j
@Component
public class PexelsApi {

    // 读取配置文件
    @Value("${pexels.apiKey}")
    private String apiKey;

    // 图片搜索接口地址
    public static final String SEARCH_URL = "https://api.pexels.com/v1/search";

    /**
     * 按关键词搜索图片，返回高清图片直链列表
     * 使用原图直链追加压缩参数（1920px），画质高清且体积可控（不会超 10MB 上传校验）
     *
     * @param searchText 搜索关键词
     * @param count      需要的图片数量（即 per_page）
     */
    public List<String> searchImages(String searchText, Integer count) {
        // 关键词必须 URL 编码，避免中文乱码
        String fetchUrl = String.format("%s?query=%s&per_page=%d&size=large",
                SEARCH_URL, URLEncoder.encode(searchText, StandardCharsets.UTF_8), count);
        HttpRequest httpRequest = HttpRequest.get(fetchUrl)
                .header("Authorization", apiKey)
                .timeout(10000);
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("Pexels 搜索失败：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Pexels 图片搜索失败");
            }
            JSONObject body = JSONUtil.parseObj(httpResponse.body());
            JSONArray photos = body.getJSONArray("photos");
            List<String> urlList = new ArrayList<>();
            if (photos == null) {
                return urlList;
            }
            for (Object photo : photos) {
                JSONObject src = ((JSONObject) photo).getJSONObject("src");
                if (src == null) {
                    continue;
                }
                String original = src.getStr("original");
                if (StrUtil.isBlank(original)) {
                    continue;
                }
                // 追加 CDN 压缩参数：宽 1920 高清画质，体积通常几百 KB
                urlList.add(original + "?auto=compress&cs=tinysrgb&w=1920");
            }
            return urlList;
        }
    }
}

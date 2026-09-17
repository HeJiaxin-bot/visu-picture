package com.visupicture.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.DeleteObjectsRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.visupicture.config.CosClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种图片的处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 图片处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 1. 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);
        // 2. 缩略图处理，仅对 > 2 KB 的图片生成缩略图
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            // 拼接缩略图的路径；固定用 webp 后缀——URL 抓取的源文件常无后缀，沿用原后缀会生成以点结尾的无效 key，
            // 导致缩略图生成失败并回退成压缩图（webp 原尺寸大图）
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail.webp";
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            // 缩放规则：/thumbnail/<Width>x<Height>>（大于原图宽高则不处理）+ /format/webp 显式转 webp
            // （数据万象输出格式由规则决定、不跟文件名后缀走，不加 format 会内容是 jpg 而后缀是 webp）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>/format/webp", 400, 400));
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 唯一键
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

    /**
     * 按图片访问地址清理对象存储中的文件（异步执行）
     * 数据库中只保存了压缩图与缩略图的访问地址，原图等变体文件与它们共用主文件名，
     * 因此统一按「主文件名前缀」清理，一次带走同一次上传产生的全部文件
     *
     * @param url 图片访问地址（含域名），为空或不是本存储域名时忽略
     */
    @Async
    public void deletePictureFilesByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        String host = cosClientConfig.getHost();
        if (!url.startsWith(host)) {
            return;
        }
        try {
            // 去掉域名与前导斜杠，得到对象键
            String key = StrUtil.removePrefix(url.substring(host.length()), "/");
            String mainName = FileUtil.mainName(key);
            // 仅在能取到「目录 + 主文件名」且带后缀时按前缀清理，防止前缀过短误删其他文件
            if (StrUtil.isBlank(FileUtil.extName(key)) || !StrUtil.contains(mainName, "/")) {
                return;
            }
            // 兼容对象键带 / 不带前导斜杠两种写法
            this.deleteObjectsByPrefix(mainName);
            this.deleteObjectsByPrefix("/" + mainName);
        } catch (Exception e) {
            log.error("清理图片 COS 文件失败, url = {}", url, e);
        }
    }

    /**
     * 按前缀批量删除对象（一次清理原图、压缩图、缩略图等变体文件）
     *
     * @param prefix 对象键前缀
     */
    public void deleteObjectsByPrefix(String prefix) {
        ListObjectsRequest listRequest = new ListObjectsRequest();
        listRequest.setBucketName(cosClientConfig.getBucket());
        listRequest.setPrefix(prefix);
        listRequest.setMaxKeys(1000);
        ObjectListing listing;
        do {
            listing = cosClient.listObjects(listRequest);
            List<DeleteObjectsRequest.KeyVersion> keys = listing.getObjectSummaries().stream()
                    .map(obj -> new DeleteObjectsRequest.KeyVersion(obj.getKey()))
                    .collect(Collectors.toList());
            if (CollUtil.isNotEmpty(keys)) {
                DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(cosClientConfig.getBucket());
                deleteRequest.setKeys(keys);
                cosClient.deleteObjects(deleteRequest);
            }
            listRequest.setMarker(listing.getNextMarker());
        } while (listing.isTruncated());
    }
}

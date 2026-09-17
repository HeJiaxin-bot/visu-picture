package com.visupicture.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
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
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

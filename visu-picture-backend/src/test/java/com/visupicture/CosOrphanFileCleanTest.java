package com.visupicture;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.COSObjectSummary;
import com.visupicture.config.CosClientConfig;
import com.visupicture.manager.CosManager;
import com.visupicture.model.entity.Picture;
import com.visupicture.service.PictureService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一次性任务：清理 COS 中的孤儿文件（数据库不再引用的文件）
 * 预览：mvn test -Dtest=CosOrphanFileCleanTest
 * 删除：mvn test -Dtest=CosOrphanFileCleanTest -Ddelete=true
 */
@SpringBootTest
public class CosOrphanFileCleanTest {

    @Resource
    private PictureService pictureService;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    @Test
    public void cleanOrphanFiles() throws Exception {
        boolean doDelete = Boolean.parseBoolean(System.getProperty("delete", "true"));
        String host = cosClientConfig.getHost();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        // 1. 数据库在用文件 key 集合（未删除图片的原图 + 缩略图）
        Set<String> usedKeys = new HashSet<>();
        for (Picture picture : pictureService.lambdaQuery().list()) {
            addKey(usedKeys, picture.getUrl(), host);
            addKey(usedKeys, picture.getThumbnailUrl(), host);
        }
        // 2. COS 全部文件 key（分页列举，跳过目录占位）
        Set<String> cosKeys = new HashSet<>();
        String marker = null;
        while (true) {
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(cosClientConfig.getBucket());
            request.setMaxKeys(1000);
            if (marker != null) {
                request.setMarker(marker);
            }
            ObjectListing objectListing = cosClient.listObjects(request);
            List<COSObjectSummary> summaries = objectListing.getObjectSummaries();
            for (COSObjectSummary summary : summaries) {
                if (summary.getSize() > 0) {
                    cosKeys.add(summary.getKey());
                }
            }
            if (!objectListing.isTruncated()) {
                break;
            }
            marker = objectListing.getNextMarker();
        }
        // 3. 差集 = 孤儿文件
        Set<String> orphans = cosKeys.stream()
                .filter(key -> !usedKeys.contains(key))
                .collect(Collectors.toSet());

        System.out.println("========== COS 孤儿文件扫描结果 ==========");
        System.out.println("数据库在用文件数: " + usedKeys.size());
        System.out.println("COS 文件总数: " + cosKeys.size());
        System.out.println("孤儿文件数: " + orphans.size());

        // 按目录分组统计
        Map<String, Long> byDir = orphans.stream()
                .collect(Collectors.groupingBy(
                        key -> key.contains("/") ? key.substring(0, key.lastIndexOf('/')) : "(根目录)",
                        LinkedHashMap::new,
                        Collectors.counting()));
        System.out.println("---- 按目录统计 ----");
        byDir.forEach((dir, count) -> System.out.println(dir + " : " + count + " 个"));
        // 完整清单写入文件，避免控制台换行/转义造成误读
        java.nio.file.Files.write(
                java.nio.file.Paths.get("target", "orphan-files.txt"),
                String.join("\n", orphans).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("完整清单已写入 target/orphan-files.txt");

        if (!doDelete) {
            System.out.println("========== 预览模式，未删除任何文件（加 -Ddelete=true 执行删除） ==========");
            return;
        }
        // 4. 执行删除
        System.out.println("========== 开始删除孤儿文件 ==========");
        int deleted = 0;
        for (String key : orphans) {
            cosManager.deleteObject(key);
            deleted++;
            if (deleted % 50 == 0) {
                System.out.println("已删除 " + deleted + " / " + orphans.size());
            }
        }
        System.out.println("清理完成，共删除 " + deleted + " 个孤儿文件");
    }

    private void addKey(Set<String> usedKeys, String url, String host) {
        if (url == null || url.isEmpty()) {
            return;
        }
        // 数据库存的是完整 URL，转为 COS 对象 key；兼容直接存 key 的情况
        if (url.startsWith(host + "/")) {
            usedKeys.add(url.substring(host.length() + 1));
        } else {
            usedKeys.add(url);
        }
    }
}

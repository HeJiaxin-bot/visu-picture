package com.visupicture.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.visupicture.annotation.AuthCheck;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.ResultUtils;
import com.visupicture.constant.UserConstant;
import com.visupicture.mapper.PictureMapper;
import com.visupicture.model.entity.Picture;
import com.visupicture.model.entity.Space;
import com.visupicture.service.PictureService;
import com.visupicture.service.SpaceService;
import com.visupicture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端全局数据看板
 *
 * <p>聚合全站核心指标：总量、今日增量、待审核数、AI 生成占比、
 * 近 7 天上传趋势与空间占用排行。仅管理员可访问。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
public class DashboardController {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    /**
     * 看板总览
     */
    @GetMapping("/overview")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // ---- 总量指标 ----
        overview.put("totalPictures", pictureService.count());
        overview.put("totalUsers", userService.count());
        overview.put("totalSpaces", spaceService.count());

        // ---- 今日增量 ----
        LocalDate today = LocalDate.now();
        overview.put("todayPictures", countPicturesSince(today));
        overview.put("todayUsers", userService.count(
                new QueryWrapper<com.visupicture.model.entity.User>()
                        .ge("createTime", today.atStartOfDay())));

        // ---- 待审核 / AI 生成 ----
        overview.put("pendingReviews", pictureService.count(
                new QueryWrapper<Picture>().eq("reviewStatus", 0)));
        overview.put("aiGeneratedCount", pictureService.count(
                new QueryWrapper<Picture>().eq("isAiGenerated", 1)));

        // ---- 近 7 天上传趋势（补齐空日期，前端直接画折线） ----
        overview.put("uploadTrend", buildUploadTrend());

        // ---- 空间占用排行 Top 5 ----
        List<Space> topSpaces = spaceService.lambdaQuery()
                .orderByDesc(Space::getTotalSize)
                .last("limit 5")
                .list();
        List<Map<String, Object>> topSpaceList = new ArrayList<>();
        for (Space space : topSpaces) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", space.getId());
            item.put("spaceName", space.getSpaceName());
            item.put("totalCount", space.getTotalCount());
            item.put("totalSize", space.getTotalSize());
            topSpaceList.add(item);
        }
        overview.put("topSpaces", topSpaceList);

        return ResultUtils.success(overview);
    }

    /**
     * 统计某日期（含当天）之后新增的图片数
     */
    private long countPicturesSince(LocalDate since) {
        return pictureService.count(new QueryWrapper<Picture>()
                .ge("createTime", since.atStartOfDay()));
    }

    /**
     * 近 7 天上传趋势：SQL 按天聚合 + 内存补齐缺失日期
     */
    private List<Map<String, Object>> buildUploadTrend() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);
        QueryWrapper<Picture> wrapper = new QueryWrapper<Picture>()
                .select("DATE(createTime) as date", "COUNT(*) as count")
                .ge("createTime", sevenDaysAgo.atStartOfDay())
                .groupBy("DATE(createTime)");
        List<Map<String, Object>> rows = pictureMapper.selectMaps(wrapper);
        Map<String, Object> countByDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            countByDate.put(String.valueOf(row.get("date")), row.get("count"));
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString().substring(5)); // MM-dd
            Object count = countByDate.get(date.toString());
            item.put("count", count == null ? 0 : count);
            trend.add(item);
        }
        return trend;
    }
}

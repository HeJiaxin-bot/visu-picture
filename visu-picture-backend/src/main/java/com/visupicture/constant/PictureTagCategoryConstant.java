package com.visupicture.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 图片分类与标签候选常量
 */
public class PictureTagCategoryConstant {

    public static final List<String> TAG_LIST = Arrays.asList(
            // 通用热度
            "热门", "搞笑", "生活", "高清", "4K", "创意", "艺术", "校园", "背景", "简历",
            // 风格
            "简约", "文艺", "小清新", "商务", "扁平", "手绘", "二次元", "可爱", "治愈", "复古", "国潮", "渐变",
            // 主题
            "风景", "人物", "动物", "美食", "旅游", "自然", "科技", "节日", "促销"
    );

    public static final List<String> CATEGORY_LIST = Arrays.asList(
            "模板", "电商", "表情包", "素材", "海报",
            "壁纸", "头像", "插画", "摄影", "UI 设计", "图标", "美食", "旅游", "科技", "节日"
    );
}

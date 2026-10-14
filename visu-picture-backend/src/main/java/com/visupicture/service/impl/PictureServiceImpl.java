package com.visupicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.api.aliyunai.AliYunAiApi;
import com.visupicture.api.aliyunai.model.CreateImageEditTaskRequest;
import com.visupicture.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.visupicture.api.pexels.PexelsApi;
import com.visupicture.constant.PictureTagCategoryConstant;
import com.visupicture.constant.SortFieldConstant;
import com.visupicture.constant.UserConstant;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.manager.CosManager;
import com.visupicture.manager.FileManager;
import com.visupicture.manager.upload.FilePictureUpload;
import com.visupicture.manager.upload.PictureUploadTemplate;
import com.visupicture.manager.upload.UrlPictureUpload;
import com.visupicture.mapper.PictureMapper;
import com.visupicture.model.dto.file.UploadPictureResult;
import com.visupicture.model.dto.picture.*;
import com.visupicture.model.entity.Picture;
import com.visupicture.model.entity.Space;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.PictureReviewStatusEnum;
import com.visupicture.model.vo.PictureVO;
import com.visupicture.model.vo.UserVO;
import com.visupicture.service.NotificationService;
import com.visupicture.service.PictureInteractionService;
import com.visupicture.service.PictureService;
import com.visupicture.service.SpaceService;
import com.visupicture.service.UserService;
import com.visupicture.utils.ColorSimilarUtils;
import com.visupicture.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @description 针对表【picture(图片)】的数据库操作Service实现
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private PexelsApi pexelsApi;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureInteractionService pictureInteractionService;

    /**
     * 是否拦截重复图片上传（同一空间内 picHash 相同即视为重复）。
     * 默认开启；若业务上允许重复（如多份同图但元数据不同），可通过配置关闭。
     */
    @Value("${app.duplicate-picture.reject:true}")
    private boolean rejectDuplicate;

    /**
     * 批量抓取上传任务的进度缓存 key 前缀
     */
    private static final String BATCH_UPLOAD_PROGRESS_KEY = "visupicture:batch:upload:progress:";

    /**
     * 查找同一空间内内容完全相同的图片
     *
     * <p>作用域限定为「同一空间」：不同用户/空间的私有图片互不干扰，
     * 也避免通过"上传能否成功"探测他人是否已上传过某张图（跨空间存在性泄露）。</p>
     *
     * @param picHash 图片内容摘要
     * @param spaceId 空间 id（null 表示公共图库，此时作用域限定为公共图库 + 本人）
     * @param userId  上传者 id
     * @return 已存在的重复图片；无重复返回 null
     */
    private Picture findDuplicatePicture(String picHash, Long spaceId, Long userId) {
        if (StrUtil.isBlank(picHash)) {
            return null;
        }
        return this.lambdaQuery()
                .eq(Picture::getPicHash, picHash)
                .eq(spaceId != null, Picture::getSpaceId, spaceId)
                // 公共图库场景：只在公共图库（spaceId 为空）内比对，且限定本人已上传的图片，
                // 避免泄露"他人是否传过同一张图"
                .isNull(spaceId == null, Picture::getSpaceId)
                .eq(spaceId == null && userId != null, Picture::getUserId, userId)
                .orderByAsc(Picture::getId)
                .last("limit 1")
                .one();
    }

    /**
     * 批量上传中待通知管理员的图片计数（ThreadLocal，仅当前请求线程可见）
     * 为 null 表示非批量场景，逐张实时通知；非 null 时累计，由批量任务结束时汇总成一条通知
     */
    private static final ThreadLocal<int[]> BATCH_AUDIT_COUNTER = new ThreadLocal<>();

    /**
     * 新图片待审核时通知管理员：批量场景累计计数，单张上传实时通知
     */
    private void notifyAdminsForAudit(Picture picture, User loginUser) {
        int[] counter = BATCH_AUDIT_COUNTER.get();
        if (counter != null) {
            counter[0]++;
            return;
        }
        String uploaderName = loginUser != null ? loginUser.getUserName() : null;
        notificationService.sendAuditTaskNotification(
                picture.getId(), picture.getName(), picture.getThumbnailUrl(), uploaderName);
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 改为使用统一的权限校验
//            // 校验是否有空间的权限，仅空间管理员才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 改为使用统一的权限校验
//            // 仅本人或管理员可编辑图片
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间 id
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        picture.setPicColor(uploadPictureResult.getPicColor());
        // 转换为标准颜色
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        // 图片内容摘要：用于重复图片检测（上传模板中对原始输入源计算）
        picture.setPicHash(uploadPictureResult.getPicHash());
        picture.setUserId(loginUser.getId());
        // AI 生成标识（AI 扩图上传时由前端传入）
        picture.setIsAiGenerated(pictureUploadRequest.getIsAiGenerated());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 【重复图片检测】新增图片时，若同一空间内已存在内容完全相同的图片，按配置拦截。
        // 说明：只做"字节级完全相同"的判定（同一文件重复上传），不含视觉相似的近似去重。
        // 更新场景（pictureId != null）跳过检测，否则用户无法保存对已有图片的编辑。
        if (pictureId == null && StrUtil.isNotBlank(picture.getPicHash()) && rejectDuplicate) {
            Picture duplicate = findDuplicatePicture(picture.getPicHash(), spaceId, loginUser.getId());
            if (duplicate != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "检测到重复图片：已存在内容完全相同的图片「" + duplicate.getName() + "」（id="
                                + duplicate.getId() + "），请勿重复上传");
            }
        }
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        // 如果是更新且图片 URL 发生变化，异步清理旧的图片资源（URL 未变时不能清理，否则会误删在用文件）
        if (oldPicture != null && ObjUtil.notEqual(oldPicture.getUrl(), picture.getUrl())) {
            this.clearPictureFile(oldPicture);
        }
        // 新增的待审核图片：通知管理员处理（批量上传场景下聚合为一条，避免刷屏）
        if (pictureId == null && picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.REVIEWING.getValue()) {
            this.notifyAdminsForAudit(picture, loginUser);
        }
        return PictureVO.objToVo(picture);
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        // 填充当前登录用户的点赞/收藏状态（服务端下发，取代前端 localStorage 推断）
        pictureInteractionService.fillInteractionState(
                Collections.singletonList(pictureVO), tryGetLoginUserId(request));
        return pictureVO;
    }

    /**
     * 尽力获取当前登录用户 id；未登录或会话失效时返回 null（详情接口允许匿名访问）
     */
    private Long tryGetLoginUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            User loginUser = userService.getLoginUser(request);
            return loginUser == null ? null : loginUser.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        // 批量填充当前登录用户的点赞/收藏状态（两个 IN 查询完成整页，避免 N+1）
        pictureInteractionService.fillInteractionState(pictureVOList, tryGetLoginUserId(request));
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        boolean notNullSpaceId = pictureQueryRequest.isNotNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.isNotNull(notNullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "startEditTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序（附加以 id 为次级排序：点赞数等排序值相同时保证分页结果稳定，避免重复/遗漏）
        // 【安全修复】sortField 会被字面量拼进 SQL，必须先经白名单校验，否则构成 SQL 注入
        ThrowUtils.throwIf(!SortFieldConstant.isValid(sortField, SortFieldConstant.PICTURE_SORT_FIELDS),
                ErrorCode.PARAMS_ERROR, "排序字段不合法");
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        queryWrapper.orderByDesc("id");
        return queryWrapper;
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 校验审核状态是否重复，已是改状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 5. 站内通知图片作者审核结果（审核拒绝时必须给出理由；发送失败不影响审核流程）
        boolean pass = PictureReviewStatusEnum.PASS.equals(reviewStatusEnum);
        notificationService.sendReviewResultNotification(
                oldPicture.getUserId(), pass, oldPicture.getId(),
                oldPicture.getName(), oldPicture.getThumbnailUrl(), reviewMessage);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 初始化任务进度（值格式：已完成/总数），前端轮询展示
        String progressKey = BATCH_UPLOAD_PROGRESS_KEY + loginUser.getId();
        stringRedisTemplate.opsForValue().set(progressKey, "0/" + count, 10, TimeUnit.MINUTES);
        // 按抓取源收集图片直链列表：pexels = Pexels 高清图库 API；bing（默认）= 必应图片搜索
        String searchSource = StrUtil.blankToDefault(pictureUploadByBatchRequest.getSearchSource(), "bing");
        List<String> fileUrlList = "pexels".equals(searchSource)
                ? pexelsApi.searchImages(searchText, count)
                : this.fetchBingImageUrls(searchText);
        ThrowUtils.throwIf(CollUtil.isEmpty(fileUrlList), ErrorCode.OPERATION_ERROR, "未抓取到图片结果");
        // 遍历图片直链，依次处理上传
        int uploadCount = 0;
        int failCount = 0;
        // 开启批量通知聚合：任务内不逐张通知管理员，结束时汇总为一条
        BATCH_AUDIT_COUNTER.set(new int[]{0});
        try {
            for (String fileUrl : fileUrlList) {
                if (uploadCount >= count) {
                    break;
                }
                // 上传图片
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                pictureUploadRequest.setFileUrl(fileUrl);
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
                try {
                    PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                    log.info("图片上传成功，id = {}", pictureVO.getId());
                    uploadCount++;
                    // AI 配文：自动生成名称、简介、分类、标签并直接落库（失败不影响整体抓取）
                    if (ObjUtil.defaultIfNull(pictureUploadByBatchRequest.getAiEdit(), true)) {
                        try {
                            Picture aiPicture = new Picture();
                            aiPicture.setId(pictureVO.getId());
                            aiPicture.setUrl(pictureVO.getUrl());
                            aiPicture.setThumbnailUrl(pictureVO.getThumbnailUrl());
                            PictureAiEditResult aiResult = this.generateAiEditResult(aiPicture);
                            this.lambdaUpdate()
                                    .eq(Picture::getId, pictureVO.getId())
                                    .set(StrUtil.isNotBlank(aiResult.getName()), Picture::getName, aiResult.getName())
                                    .set(StrUtil.isNotBlank(aiResult.getIntroduction()), Picture::getIntroduction, aiResult.getIntroduction())
                                    .set(StrUtil.isNotBlank(aiResult.getCategory()), Picture::getCategory, aiResult.getCategory())
                                    .set(CollUtil.isNotEmpty(aiResult.getTags()), Picture::getTags, JSONUtil.toJsonStr(aiResult.getTags()))
                                    .update();
                        } catch (Exception e) {
                            log.warn("AI 配文失败，保留默认信息，pictureId = {}", pictureVO.getId(), e);
                        }
                    }
                    // 更新任务进度
                    stringRedisTemplate.opsForValue().set(progressKey, uploadCount + "/" + count, 10, TimeUnit.MINUTES);
                } catch (Exception e) {
                    failCount++;
                    log.error("图片上传失败，url = {}", fileUrl, e);
                }
            }
        } finally {
            // 清理聚合计数并汇总通知（无论成功失败都要清理，避免线程复用污染）
            int[] counter = BATCH_AUDIT_COUNTER.get();
            BATCH_AUDIT_COUNTER.remove();
            if (counter != null && counter[0] > 0) {
                notificationService.sendAuditTaskBatchNotification(counter[0], loginUser.getUserName());
            }
        }
        log.info("批量抓取完成，关键词 = {}，抓取源 = {}，成功 = {}，失败 = {}", searchText, searchSource, uploadCount, failCount);
        return uploadCount;
    }

    /**
     * 抓取必应图片搜索结果，返回高清图直链列表（缩略图参数改写为 w=1200）
     *
     * @param searchText 搜索关键词
     */
    private List<String> fetchBingImageUrls(String searchText) {
        // 关键词必须 URL 编码：否则中文会按 JVM 默认字符集（Windows 下为 GBK）发出，
        // 必应将其解析为乱码词，返回的图片与关键词不匹配
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&first=1&count=35&mmasync=1",
                URLEncoder.encode(searchText, StandardCharsets.UTF_8));
        Document document;
        try {
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        if (CollUtil.isEmpty(imgElementList)) {
            // 兜底：必应改版导致结构变化时，直接从全文查找
            imgElementList = document.select("img.mimg");
        }
        ThrowUtils.throwIf(CollUtil.isEmpty(imgElementList), ErrorCode.OPERATION_ERROR, "未解析到图片结果");
        List<String> urlList = new ArrayList<>();
        for (Element imgElement : imgElementList) {
            // 优先取 src；懒加载占位（base64/空值）时回退 data-src
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl) || fileUrl.startsWith("data:")) {
                fileUrl = imgElement.attr("data-src");
            }
            if (StrUtil.isBlank(fileUrl)) {
                continue;
            }
            // 必应缩略图参数改写：把 w/h 替换为 w=1200，拿到高清画质。
            // 注意：不能砍掉 ? 参数（会返回无参原图，可能超上传大小校验导致抓取失败），
            // 也不能直接用 src（默认 w=245 的 webp 缩略图画质过差）
            boolean isBingImageCdn = fileUrl.contains(".bing.net/") || fileUrl.contains(".bing.com/");
            if (isBingImageCdn && fileUrl.contains("?")) {
                fileUrl = fileUrl.replaceAll("\\?.*$", "?w=1200&c=7");
            }
            urlList.add(fileUrl);
        }
        return urlList;
    }

    /**
     * AI 配文核心逻辑：调用多模态模型生成名称、简介、分类、标签（候选集收敛后返回）
     */
    @Override
    public PictureAiEditResult generateAiEditResult(Picture picture) {
        // 优先使用缩略图，减少图片 token 消耗
        String imageUrl = StrUtil.isNotBlank(picture.getThumbnailUrl()) ? picture.getThumbnailUrl() : picture.getUrl();
        ThrowUtils.throwIf(StrUtil.isBlank(imageUrl), ErrorCode.OPERATION_ERROR, "图片地址为空，无法 AI 配文");
        // 调用多模态模型进行图片理解，约束输出 JSON 并收敛候选范围
        String prompt = String.format(
                "你是一个图片配文助手。请分析这张图片，生成：\n"
                        + "1. name：一个吸引人的图片标题，不超过 12 个字，突出画面亮点\n"
                        + "2. introduction：一句不超过 30 字的中文简介，描述画面内容和用途\n"
                        + "3. category：从候选分类中选择一个最匹配的，只能选一个\n"
                        + "4. tags：从候选标签中选择 2 到 4 个最匹配的\n\n"
                        + "候选分类：%s\n"
                        + "候选标签：%s\n\n"
                        + "只输出如下 JSON，不要输出任何其他文字：\n"
                        + "{\"name\":\"...\",\"introduction\":\"...\",\"category\":\"...\",\"tags\":[\"...\"]}",
                String.join("、", PictureTagCategoryConstant.CATEGORY_LIST),
                String.join("、", PictureTagCategoryConstant.TAG_LIST));
        String content = aliYunAiApi.chatWithImage(imageUrl, prompt);
        PictureAiEditResult result = parseAiEditResult(content);
        // 候选集收敛：超出候选范围的分类、标签直接丢弃，防止脏数据
        if (result.getCategory() != null && !PictureTagCategoryConstant.CATEGORY_LIST.contains(result.getCategory())) {
            result.setCategory(null);
        }
        if (result.getTags() != null) {
            result.setTags(result.getTags().stream()
                    .filter(StrUtil::isNotBlank)
                    .filter(PictureTagCategoryConstant.TAG_LIST::contains)
                    .distinct()
                    .collect(Collectors.toList()));
        }
        return result;
    }

    /**
     * 解析 AI 配文输出为结构化结果（容错：剥离 markdown 代码块、截取 JSON 部分）
     */
    private PictureAiEditResult parseAiEditResult(String content) {
        String json = content;
        Matcher matcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(json);
        if (matcher.find()) {
            json = matcher.group(1);
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        ThrowUtils.throwIf(start < 0 || end <= start, ErrorCode.OPERATION_ERROR, "AI 配文结果解析失败");
        json = json.substring(start, end + 1);
        try {
            return JSONUtil.toBean(json, PictureAiEditResult.class);
        } catch (Exception e) {
            log.error("AI 配文结果解析失败：{}", content, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 配文结果解析失败");
        }
    }

    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断改图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        if (StrUtil.isBlank(pictureUrl)) {
            return;
        }
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 异步清理对象存储中的图片文件与缩略图文件（含原图等变体文件）
        cosManager.deletePictureFilesByUrl(pictureUrl);
        cosManager.deletePictureFilesByUrl(oldPicture.getThumbnailUrl());
    }

    @Override
    public void deletePicturesBySpaceId(long spaceId) {
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return;
        }
        // 先删除数据库记录，再清理对象存储文件（文件清理为异步、失败不影响记录删除）
        List<Long> pictureIds = pictureList.stream().map(Picture::getId).collect(Collectors.toList());
        transactionTemplate.execute(status -> {
            this.removeByIds(pictureIds);
            // 一并清理这批图片的互动数据，避免关联表残留孤儿记录
            pictureIds.forEach(pictureInteractionService::cleanUpByPictureId);
            return true;
        });
        pictureList.forEach(this::clearPictureFile);
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 【安全修复】恢复归属校验：仅注解鉴权不够 ——
        // 鉴权上下文中的 spaceId 由请求体提供，攻击者可用自己的 spaceId 通过注解校验，
        // 却传入他人 pictureId 执行删除。此处按图片真实归属兜底校验。
        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 清理该图片的互动数据（点赞、收藏、评论、浏览历史、分享链接）
            // 放在同一事务内：避免图片已删但关联记录残留，出现指向不存在图片的孤儿数据
            pictureInteractionService.cleanUpByPictureId(pictureId);
            // 更新空间的使用额度，释放额度
            if (oldPicture.getSpaceId() != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 对象存储文件暂不清理：保留至回收站「彻底删除」时再清理，删除的图片可在回收站恢复
    }

    // ==================== 回收站 ====================

    @Override
    public Page<Picture> listMyRecycle(long current, long size, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        long total = baseMapper.countMyRecycled(loginUser.getId());
        Page<Picture> page = new Page<>(current, size, total);
        if (total > 0) {
            long offset = (Math.max(current, 1) - 1) * size;
            page.setRecords(baseMapper.listMyRecycled(loginUser.getId(), offset, size));
        }
        return page;
    }

    @Override
    public void restoreFromRecycle(long pictureId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Picture picture = baseMapper.selectMyRecycledById(pictureId, loginUser.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "回收站中不存在该图片");
        int restored = baseMapper.restoreRecycled(pictureId, loginUser.getId());
        ThrowUtils.throwIf(restored <= 0, ErrorCode.OPERATION_ERROR, "恢复失败，请稍后重试");
        // 归还空间额度（软删除时已释放；空间已被删除时静默跳过）
        if (picture.getSpaceId() != null && picture.getPicSize() != null) {
            spaceService.lambdaUpdate()
                    .eq(Space::getId, picture.getSpaceId())
                    .setSql("totalSize = totalSize + " + picture.getPicSize())
                    .setSql("totalCount = totalCount + 1")
                    .update();
        }
    }

    @Override
    public void deleteFromRecycle(long pictureId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Picture picture = baseMapper.selectMyRecycledById(pictureId, loginUser.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "回收站中不存在该图片");
        int deleted = baseMapper.deleteRecycledPhysically(pictureId, loginUser.getId());
        ThrowUtils.throwIf(deleted <= 0, ErrorCode.OPERATION_ERROR, "删除失败，请稍后重试");
        // 软删除阶段文件被保留以支持恢复，物理删除时才真正清理对象存储文件
        this.clearPictureFile(picture);
    }

    @Override
    public int restoreFromRecycleBatch(List<Long> pictureIds, User loginUser) {
        if (CollUtil.isEmpty(pictureIds)) {
            return 0;
        }
        // 逐张校验归属并恢复：单张失败不影响其余
        int success = 0;
        for (Long id : pictureIds) {
            try {
                this.restoreFromRecycle(id, loginUser);
                success++;
            } catch (Exception e) {
                log.warn("恢复图片失败, pictureId={}", id, e);
            }
        }
        return success;
    }

    @Override
    public int deleteFromRecycleBatch(List<Long> pictureIds, User loginUser) {
        if (CollUtil.isEmpty(pictureIds)) {
            return 0;
        }
        int success = 0;
        for (Long id : pictureIds) {
            try {
                this.deleteFromRecycle(id, loginUser);
                success++;
            } catch (Exception e) {
                log.warn("彻底删除图片失败, pictureId={}", id, e);
            }
        }
        return success;
    }

    // ==================== 热门搜索 ====================

    private static final String SEARCH_HOT_KEY = "picture:search:hot";

    @Override
    public void recordSearchKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        String kw = keyword.trim();
        if (kw.length() > 50) {
            kw = kw.substring(0, 50);
        }
        try {
            stringRedisTemplate.opsForZSet().incrementScore(SEARCH_HOT_KEY, kw, 1);
            // 只保留热度前 100 名，防止 ZSet 无限膨胀
            stringRedisTemplate.opsForZSet().removeRange(SEARCH_HOT_KEY, 0, -101);
        } catch (Exception e) {
            log.warn("记录热门搜索词失败", e);
        }
    }

    @Override
    public List<String> getHotSearchKeywords(int limit) {
        try {
            Set<String> keywords = stringRedisTemplate.opsForZSet()
                    .reverseRange(SEARCH_HOT_KEY, 0, Math.max(1, Math.min(limit, 20)) - 1L);
            return keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
        } catch (Exception e) {
            log.warn("查询热门搜索词失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 【安全修复】恢复归属校验：仅注解鉴权不够 ——
        // 鉴权上下文中的 spaceId 由请求体提供，攻击者可用自己的 spaceId 通过注解校验，
        // 却操作他人 pictureId。此处按图片真实归属再做一次兜底校验。
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 以色搜图的相似度阈值（0~1），低于该值的图片视为颜色不相似，直接过滤
     */
    private static final double COLOR_SIMILARITY_THRESHOLD = 0.5;

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下的所有图片（必须要有主色调）
        // 空间图片同样需要审核：非管理员只能看到审核通过的图片，以及自己上传的图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .and(!userService.isAdmin(loginUser), wrapper -> wrapper
                        .eq(Picture::getReviewStatus, PictureReviewStatusEnum.PASS.getValue())
                        .or()
                        .eq(Picture::getUserId, loginUser.getId()))
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 阈值过滤 + 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                // 过滤掉与目标颜色相似度低于阈值的图片
                .filter(picture -> {
                    String hexColor = picture.getPicColor();
                    if (StrUtil.isBlank(hexColor)) {
                        return false;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    return ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor)
                            >= COLOR_SIMILARITY_THRESHOLD;
                })
                .sorted(Comparator.comparingDouble(picture -> {
                    // 过滤后必然存在主色调
                    Color pictureColor = Color.decode(picture.getPicColor());
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .collect(Collectors.toList());
        // 5. 返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 【安全修复】恢复归属校验：防止用自己的 spaceId 通过注解校验、却对他人私有图片发起扩图（内容泄露）
        checkPictureAuth(loginUser, picture);
        // 校验图片分辨率，阿里云扩图类模型要求输入图片宽高均在 [512, 4096] 像素范围内
        final int MIN_RESOLUTION = 512;
        Integer picWidth = picture.getPicWidth();
        Integer picHeight = picture.getPicHeight();
        ThrowUtils.throwIf(picWidth == null || picHeight == null || picWidth < MIN_RESOLUTION || picHeight < MIN_RESOLUTION, ErrorCode.OPERATION_ERROR,
                StrUtil.format("图片分辨率过小（当前 {}x{}），无法扩图，请使用宽高不小于 {} 像素的图片",
                        picWidth, picHeight, MIN_RESOLUTION));

        // AI 扩图消耗积分：条件更新（积分充足才扣），防止并发透支
        boolean deducted = userService.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, loginUser.getId())
                .ge(User::getPoints, UserConstant.OUT_PAINTING_POINTS)
                .setSql("points = points - " + UserConstant.OUT_PAINTING_POINTS));
        ThrowUtils.throwIf(!deducted, ErrorCode.OPERATION_ERROR,
                StrUtil.format("积分不足，AI 扩图一次需要 {} 积分（可在个人中心每日签到获取积分）", UserConstant.OUT_PAINTING_POINTS));
        try {
            // 扩图统一使用万相通用图像编辑模型（wanx2.1-imageedit，function = expand）：
            // 专用扩图模型 image-out-painting 及其继任 wanx2.1-imageoutpaint 已于 2026-10 实测下架（Model not exist），
            // wan2.7-image-pro 则仅在新 maas 域名提供服务，故收敛为单模型。
            CreateImageEditTaskRequest editTaskRequest = new CreateImageEditTaskRequest();
            CreateImageEditTaskRequest.Input input = new CreateImageEditTaskRequest.Input();
            input.setBaseImageUrl(picture.getUrl());
            // expand 模式下 prompt 必填，未填写时使用默认提示词
            input.setPrompt(StrUtil.blankToDefault(createPictureOutPaintingTaskRequest.getPrompt(),
                    "将图片画布自然扩展，保持原图内容和风格不变"));
            editTaskRequest.setInput(input);
            CreateImageEditTaskRequest.Parameters editParameters = new CreateImageEditTaskRequest.Parameters();
            Float expandScale = createPictureOutPaintingTaskRequest.getExpandScale();
            if (expandScale != null) {
                editParameters.setTopScale(expandScale);
                editParameters.setBottomScale(expandScale);
                editParameters.setLeftScale(expandScale);
                editParameters.setRightScale(expandScale);
            }
            editTaskRequest.setParameters(editParameters);
            return aliYunAiApi.createImageEditTask(editTaskRequest);
        } catch (Exception e) {
            // 任务创建失败，退还积分
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, loginUser.getId())
                    .setSql("points = points + " + UserConstant.OUT_PAINTING_POINTS));
            throw e;
        }
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 图片点赞 / 取消点赞
     *
     * <p>【点赞去重】原实现直接 {@code likeCount = likeCount + 1}，
     * 没有任何"谁赞过"的记录，导致同一用户可无限次点赞把计数刷高
     * （连点、脚本、并发请求都能触发）。</p>
     *
     * <p>现改为：计数是否变更由 {@code picture_like} 表的写入结果决定 ——
     * 唯一索引 {@code uk_pictureId_userId} + {@code INSERT IGNORE} 使重复点赞自然失效，
     * 记录与计数同事务提交。具体逻辑见
     * {@link PictureInteractionService#like(long, long, boolean)}。</p>
     *
     * @param pictureId 图片 id
     * @param userId    当前登录用户 id（由 Controller 保证非空，计数去重必须绑定到具体用户）
     * @param isLike    是否点赞
     * @return 操作后的点赞数
     */
    public int likePicture(long pictureId, long userId, boolean isLike) {
        return pictureInteractionService.like(pictureId, userId, isLike);
    }
}












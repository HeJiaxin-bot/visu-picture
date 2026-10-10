package com.visupicture.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.DeleteRequest;
import com.visupicture.common.ResultUtils;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.manager.auth.annotation.SaSpaceCheckPermission;
import com.visupicture.manager.auth.model.SpaceUserPermissionConstant;
import com.visupicture.model.dto.spaceuser.SpaceUserAddRequest;
import com.visupicture.model.dto.spaceuser.SpaceUserEditRequest;
import com.visupicture.model.dto.spaceuser.SpaceUserQueryRequest;
import com.visupicture.model.entity.Picture;
import com.visupicture.model.entity.Space;
import com.visupicture.model.entity.SpaceUser;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.SpaceTypeEnum;
import com.visupicture.model.vo.SpaceUserVO;
import com.visupicture.service.PictureService;
import com.visupicture.service.SpaceService;
import com.visupicture.service.SpaceUserService;
import com.visupicture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间成员管理
 */
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 添加成员到空间
     */
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 从空间移除成员
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询某个成员在某个空间的信息
     */
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        // 查询数据库
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    /**
     * 查询成员信息列表
     *
     * 【安全修复】必须指定 spaceId：
     * 原实现未要求 spaceId，而鉴权上下文对「所有字段为空」的请求会 fail-open 放行全部权限，
     * 导致任意登录用户提交 {} 即可 dump 全站 space_user 表（含所有团队的空间名、成员与角色）。
     */
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 强制限定查询范围：必须按空间查询，禁止无范围全表查询
        ThrowUtils.throwIf(spaceUserQueryRequest.getSpaceId() == null
                        && spaceUserQueryRequest.getId() == null,
                ErrorCode.PARAMS_ERROR, "必须指定空间 id");
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 编辑成员信息（设置权限）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                               HttpServletRequest request) {
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        // 数据校验
        spaceUserService.validSpaceUser(spaceUser, false);
        // 判断是否存在
        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 退出团队空间（成员本人操作，删除自己的成员关联记录）
     */
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        // 判断空间是否存在
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(ObjUtil.notEqual(space.getSpaceType(), SpaceTypeEnum.TEAM.getValue()),
                ErrorCode.PARAMS_ERROR, "仅团队空间支持退出");
        // 判断是否是团队成员
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, loginUser.getId())
                .one();
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "您不是该团队成员");
        // 创建人退出 = 解散团队：删除空间并清空所有成员关联；普通成员仅删除自己的关联记录
        boolean isOwner = space.getUserId().equals(loginUser.getId());
        Boolean result = transactionTemplate.execute(status -> {
            if (isOwner) {
                // 删除空间内的图片记录，并异步清理对应的图片文件
                List<Picture> pictureList = pictureService.lambdaQuery()
                        .eq(Picture::getSpaceId, spaceId)
                        .list();
                if (CollUtil.isNotEmpty(pictureList)) {
                    List<Long> pictureIds = pictureList.stream()
                            .map(Picture::getId)
                            .collect(Collectors.toList());
                    pictureService.removeByIds(pictureIds);
                    for (Picture picture : pictureList) {
                        // 异步清理 COS 上的原图和缩略图（内部会判断 URL 是否被其他记录复用）
                        pictureService.clearPictureFile(picture);
                    }
                }
                boolean removeSpace = spaceService.removeById(spaceId);
                ThrowUtils.throwIf(!removeSpace, ErrorCode.OPERATION_ERROR, "删除空间失败");
                spaceUserService.lambdaUpdate()
                        .eq(SpaceUser::getSpaceId, spaceId)
                        .remove();
            } else {
                boolean removed = spaceUserService.removeById(spaceUser.getId());
                ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR);
            }
            return true;
        });
        return ResultUtils.success(Boolean.TRUE.equals(result));
    }

    /**
     * 查询我加入的团队空间列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        // 过滤掉空间已被删除的残留成员记录
        List<Long> spaceIds = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(spaceIds)) {
            return ResultUtils.success(new ArrayList<>());
        }
        Set<Long> validSpaceIds = spaceService.listByIds(spaceIds).stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        spaceUserList = spaceUserList.stream()
                .filter(spaceUser -> validSpaceIds.contains(spaceUser.getSpaceId()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}
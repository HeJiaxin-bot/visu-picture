package com.visupicture.controller;

import com.visupicture.annotation.AuditLog;
import com.visupicture.annotation.RateLimit;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.ResultUtils;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.model.dto.picture.PictureShareCreateRequest;
import com.visupicture.model.entity.User;
import com.visupicture.model.vo.PictureShareVO;
import com.visupicture.model.vo.PictureVO;
import com.visupicture.service.PictureShareService;
import com.visupicture.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 图片分享接口（创建/撤销需登录；公开访问链接无需登录）
 */
@RestController
@RequestMapping("/share")
public class PictureShareController {

    @Resource
    private PictureShareService pictureShareService;

    @Resource
    private UserService userService;

    /**
     * 为图片创建分享链接（仅图片所有者或管理员）
     *
     * @param expireDays 有效期天数；为空或 <=0 表示永久有效
     */
    @PostMapping("/create")
    @RateLimit(count = 20, period = 60, dimension = RateLimit.Dimension.USER,
            message = "创建分享链接过于频繁")
    @AuditLog(module = "picture", action = "share_create", targetType = "picture",
            targetIdParam = "shareCreateRequest", description = "创建图片分享链接")
    public BaseResponse<PictureShareVO> createShare(@RequestBody PictureShareCreateRequest shareCreateRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(shareCreateRequest == null || shareCreateRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureShareService.createShare(
                shareCreateRequest.getPictureId(), shareCreateRequest.getExpireDays(), loginUser));
    }

    /**
     * 查询某图片当前的分享链接（仅所有者或管理员）
     *
     * @return 有有效链接时返回链接信息，否则 data 为 null
     */
    @GetMapping("/get")
    public BaseResponse<PictureShareVO> getShareByPictureId(@RequestParam long pictureId,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureShareService.getShareByPictureId(pictureId, loginUser));
    }

    /**
     * 撤销图片的分享链接（仅所有者或管理员）
     */
    @PostMapping("/revoke")
    @AuditLog(module = "picture", action = "share_revoke", targetType = "picture",
            targetIdParam = "pictureId", description = "撤销图片分享链接")
    public BaseResponse<Integer> revokeShare(@RequestBody Map<String, Long> body,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("pictureId") == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureShareService.revokeShare(body.get("pictureId"), loginUser));
    }

    /**
     * 我创建的全部分享链接
     */
    @GetMapping("/my/list")
    public BaseResponse<List<PictureShareVO>> listMyShares(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureShareService.listMyShares(loginUser.getId()));
    }

    /**
     * 公开访问分享的图片（<b>无需登录</b>）
     *
     * <p>这是分享功能的核心入口：持有分享码即可访问，因此：</p>
     * <ul>
     *   <li>只返回图片本身与作者的公开信息，不含空间 id、审核状态、权限列表；</li>
     *   <li>图片未通过审核时拒绝访问，防止绕过审核外泄内容；</li>
     *   <li>加 IP 限流，防止有人用脚本穷举分享码。</li>
     * </ul>
     */
    @GetMapping("/public/{shareCode}")
    @RateLimit(count = 60, period = 60, dimension = RateLimit.Dimension.IP,
            message = "访问过于频繁，请稍后再试")
    public BaseResponse<PictureVO> getSharedPicture(@PathVariable String shareCode) {
        ThrowUtils.throwIf(shareCode == null || shareCode.isEmpty(), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(pictureShareService.getSharedPicture(shareCode));
    }
}
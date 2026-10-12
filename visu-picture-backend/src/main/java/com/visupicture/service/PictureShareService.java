package com.visupicture.service;

import com.visupicture.model.entity.User;
import com.visupicture.model.vo.PictureShareVO;
import com.visupicture.model.vo.PictureVO;

import java.util.List;

/**
 * 图片分享链接服务（公开访问）
 *
 * <p>安全模型：分享码为随机生成的不可枚举字符串（16 位），
 * 知道链接即可访问 —— 这是「分享」的固有语义（类似网盘的分享链接）。
 * 因此必须做到：</p>
 * <ul>
 *   <li>只有图片的<b>所有者或管理员</b>能创建/撤销分享；</li>
 *   <li>撤销后（逻辑删除）链接立即失效；</li>
 *   <li>过期链接不可访问；</li>
 *   <li>公开访问只暴露图片本身与作者公开信息，不泄露空间/权限等内部字段。</li>
 * </ul>
 */
public interface PictureShareService {

    /**
     * 为图片创建分享链接（仅所有者或管理员）
     *
     * <p>同一图片重复创建时复用已有有效链接，避免产生大量等价链接。</p>
     *
     * @param pictureId  图片 id
     * @param expireDays 有效期天数；为空或 <=0 表示永久有效
     * @param loginUser  当前登录用户
     * @return 分享链接信息
     */
    PictureShareVO createShare(long pictureId, Integer expireDays, User loginUser);

    /**
     * 撤销图片的分享链接（仅所有者或管理员）
     *
     * @return 撤销的链接条数
     */
    int revokeShare(long pictureId, User loginUser);

    /**
     * 查询某图片当前有效的分享链接（仅所有者或管理员）
     *
     * @return 有效链接；不存在时返回 null
     */
    PictureShareVO getShareByPictureId(long pictureId, User loginUser);

    /**
     * 查询我创建的全部分享链接
     */
    List<PictureShareVO> listMyShares(long userId);

    /**
     * 通过分享码公开访问图片（<b>无需登录</b>）
     *
     * <p>校验：分享码存在且未撤销、未过期、图片存在且审核通过。
     * 访问成功后累加访问次数（失败不影响返回）。</p>
     *
     * @param shareCode 分享码
     * @return 图片 VO（仅含公开信息）；无效时抛出业务异常
     */
    PictureVO getSharedPicture(String shareCode);

    /**
     * 删除图片时使其所有分享链接失效
     *
     * @return 失效的链接条数
     */
    int invalidateByPictureId(long pictureId);
}
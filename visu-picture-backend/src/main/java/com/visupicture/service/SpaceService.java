package com.visupicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.model.dto.space.SpaceAddRequest;
import com.visupicture.model.dto.space.SpaceQueryRequest;
import com.visupicture.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.visupicture.model.entity.User;
import com.visupicture.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author 何佳鑫
 * @description 针对表【space(空间)】的数据库操作Service
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 校验空间
     *
     * @param space
     * @param add   是否为创建时检验
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类（单条）
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间级别填充空间对象
     *
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间权限
     *
     * @param loginUser
     * @param space
     */
    void checkSpaceAuth(User loginUser, Space space);

    /**
     * 校验用户是否有权使用指定空间级别
     * 普通版：所有用户；专业版：会员；旗舰版：暂不开放自助开通（仅管理员）
     *
     * @param spaceLevel 空间级别
     * @param loginUser  登录用户
     */
    void checkSpaceLevelPermission(Integer spaceLevel, User loginUser);

    /**
     * 清理空间封面文件（删除空间时调用，避免遗留 COS 孤儿文件）
     *
     * @param coverUrl 封面访问地址
     */
    void clearSpaceCoverFile(String coverUrl);

    /**
     * 上传空间封面（仅空间创建者或管理员可用）
     *
     * @param spaceId   空间 id
     * @param file      封面图片文件
     * @param loginUser 登录用户
     * @return 新封面 url
     */
    String uploadSpaceCover(long spaceId, MultipartFile file, User loginUser);
}

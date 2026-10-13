package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会员兑换码
 *
 * <p><b>为什么从 JSON 文件改为数据库表</b></p>
 *
 * <p>原实现把兑换码放在 jar 内的 {@code biz/vipCode.json}，用
 * {@code resourceLoader.getResource("classpath:biz/vipCode.json").getFile()} 读写。
 * 该写法在 fat jar 中<b>必然失败</b>：资源位于 jar 内部，
 * {@code getFile()} 无法把它解析为文件系统路径，会抛
 * {@code FileNotFoundException: ... cannot be resolved to absolute file path
 * because it does not reside in the file system}。
 * 异常被捕获后统一转成"系统繁忙"，导致线上任何兑换都失败
 * （本地 IDE 运行时 classpath 是目录，所以看不出问题）。</p>
 *
 * <p>改为落库后一次性解决四个问题：</p>
 * <ol>
 *   <li>读写走 JDBC，与部署形态（jar / 目录）无关；</li>
 *   <li>已用状态持久化，重新部署不会让已用的码复活；</li>
 *   <li>可用一条原子 {@code UPDATE ... WHERE usedBy IS NULL} 完成占用，
 *       多实例部署同样安全（原 {@code ReentrantLock} 只护单实例）；</li>
 *   <li>码不再随源码提交到公开仓库。</li>
 * </ol>
 *
 * <p><b>未使用的判定</b>：{@code usedBy IS NULL}。
 * 刻意不设 {@code hasUsed} 布尔列 —— 两个字段表达同一状态会产生
 * "已标记使用但没有使用人"这类不一致数据，用一个可空外键即可表达且不会矛盾。</p>
 *
 * @TableName vip_code
 */
@TableName(value = "vip_code")
@Data
public class VipCode implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 兑换码
     */
    private String code;

    /**
     * 使用人用户 id（NULL = 未使用）
     */
    private Long usedBy;

    /**
     * 使用时间（NULL = 未使用）
     */
    private Date usedTime;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}

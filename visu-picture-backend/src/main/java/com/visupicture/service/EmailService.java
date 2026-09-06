package com.visupicture.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务：发送验证码 + 校验验证码（基于 Redis 存储，含频率限制）
 */
@Service
public class EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发件人地址（来自配置 spring.mail.username）
     */
    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 验证码缓存 key，如 email:verify:xxx@qq.com
     */
    private static final String CODE_KEY = "email:verify:";
    /**
     * 发送频率限制 key，60 秒内不能重复发送
     */
    private static final String SEND_LIMIT_KEY = "email:send:";
    /**
     * 验证码有效期（分钟）
     */
    private static final long CODE_EXPIRE_MINUTES = 5;
    /**
     * 发送间隔（秒）
     */
    private static final long SEND_INTERVAL_SECONDS = 60;

    /**
     * 邮箱格式正则
     */
    private static final String EMAIL_REGEX = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";

    /**
     * 校验邮箱格式是否合法
     */
    public void validateEmail(String email) {
        if (StrUtil.isBlank(email) || !email.matches(EMAIL_REGEX)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }
    }

    /**
     * 发送验证码到指定邮箱，成功返回 true（校验失败会抛异常）
     *
     * @param email 目标邮箱
     */
    public void sendVerifyCode(String email) {
        validateEmail(email);
        // 频率限制
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(SEND_LIMIT_KEY + email))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发送过于频繁，请 60 秒后再试");
        }
        // 生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("【视界云图库】注册验证码");
            helper.setText(
                    "<div style='font-family:微软雅黑,sans-serif;font-size:14px'>" +
                            "<h3>【视界云图库】注册验证码</h3>" +
                            "<p>您的注册验证码为：<b style='font-size:20px;color:#3d5af5'>" + code + "</b></p>" +
                            "<p>验证码 " + CODE_EXPIRE_MINUTES + " 分钟内有效，若非本人操作请忽略本邮件。</p>" +
                            "</div>", true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请检查发件邮箱配置或稍后再试");
        }
        // 存储验证码并设置有效期
        stringRedisTemplate.opsForValue().set(CODE_KEY + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        // 记录发送间隔
        stringRedisTemplate.opsForValue().set(SEND_LIMIT_KEY + email, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 校验验证码是否正确，校验通过后立即删除（一次性）
     *
     * @param email 邮箱
     * @param code  用户填写的验证码
     */
    public void verifyCode(String email, String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入验证码");
        }
        String cached = stringRedisTemplate.opsForValue().get(CODE_KEY + email);
        if (StrUtil.isBlank(cached) || !cached.equals(code.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        stringRedisTemplate.delete(CODE_KEY + email);
    }
}
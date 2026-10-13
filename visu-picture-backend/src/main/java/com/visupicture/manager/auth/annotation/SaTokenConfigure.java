package com.visupicture.manager.auth.annotation;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import com.visupicture.manager.auth.SpaceSessionSyncInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Sa-Token 开启注解和配置
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Resource
    private SpaceSessionSyncInterceptor spaceSessionSyncInterceptor;

    // 注册 Sa-Token 拦截器，打开注解式鉴权功能
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先做/再做空间会话自愈，再执行注解鉴权：
        // 登录态存在两套（HttpSession 与 Sa-Token），Sa-Token 侧可能单独失效，
        // 表现为「页面显示已登录，空间内点创建却跳登录页」。
        // 自愈拦截器依据 HttpSession 补建 Sa-Token 会话，
        // 必须排在 SaInterceptor 之前（order 更小），否则注解鉴权会先抛 40100。
        registry.addInterceptor(spaceSessionSyncInterceptor)
                .addPathPatterns("/**")
                .order(-1);
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    @PostConstruct
    public void rewriteSaStrategy() {
        // 重写Sa-Token的注解处理器，增加注解合并功能 
        SaAnnotationStrategy.instance.getAnnotation = (element, annotationClass) -> {
            return AnnotatedElementUtils.getMergedAnnotation(element, annotationClass);
        };
    }
}
package com.visupicture.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.visupicture.api.aliyunai.model.CreateImageEditTaskRequest;
import com.visupicture.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.visupicture.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // AI 配文使用的多模态模型（图片理解）
    // 默认值必须是「真实存在且支持图片输入」的模型：原先默认 qwen3-vl-flash 已下线，
    // 任何未显式配置 aiEditModel 的环境（曾包括线上 profile）都会 404 而功能全废。
    @Value("${aliYunAi.aiEditModel:qwen3-vl-plus}")
    private String aiEditModel;

    // 创建扩图任务地址（wanx2.1-imageedit，function = expand）

    public static final String CREATE_IMAGE_EDIT_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis";

    // OpenAI 兼容对话接口地址（用于图片理解、AI 配文）
    public static final String CHAT_COMPLETIONS_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    /**
     * 创建万相通用图像编辑任务（wanx2.1-imageedit，扩图 function = expand）
     * 任务结果通过 getOutPaintingTask 查询（共用任务查询接口），成功结果在 output.results[0].url
     *
     * @param createImageEditTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createImageEditTask(CreateImageEditTaskRequest createImageEditTaskRequest) {
        if (createImageEditTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 发送请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_IMAGE_EDIT_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createImageEditTaskRequest));
        // 处理响应
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常：{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败，" + errorMessage);
            }
            return createOutPaintingTaskResponse;
        }
    }

    /**
     * 查询创建的任务结果
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 ID 不能为空");
        }
        // 处理响应
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }

    /**
     * 图片理解对话（OpenAI 兼容接口，多模态模型，如 qwen3-vl-plus）
     *
     * @param imageUrl 图片公网可访问地址
     * @param prompt   提示词
     * @return 模型回答文本
     */
    public String chatWithImage(String imageUrl, String prompt) {
        if (StrUtil.isBlank(imageUrl) || StrUtil.isBlank(prompt)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片理解参数为空");
        }
        // 构造多模态消息体
        JSONObject imageUrlObj = new JSONObject();
        imageUrlObj.set("url", imageUrl);
        JSONObject contentImage = new JSONObject();
        contentImage.set("type", "image_url");
        contentImage.set("image_url", imageUrlObj);
        JSONObject contentText = new JSONObject();
        contentText.set("type", "text");
        contentText.set("text", prompt);
        JSONArray content = new JSONArray();
        content.set(contentImage);
        content.set(contentText);
        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", content);
        JSONArray messages = new JSONArray();
        messages.set(message);

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", aiEditModel);
        // 配文是简单任务，关闭思考模式：响应更快、输出 token 更少
        requestBody.set("enable_thinking", false);
        requestBody.set("messages", messages);

        // 发送请求
        try (HttpResponse httpResponse = HttpRequest.post(CHAT_COMPLETIONS_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute()) {
            String responseBody = httpResponse.body();
            if (!httpResponse.isOk()) {
                // 把上游原因带到错误信息里：原先只报笼统的"AI 配文失败"，
                // 导致「模型名不存在」这类配置错误在界面上完全看不出线索。
                String upstream = extractUpstreamError(responseBody);
                log.error("AI 配文请求异常：model={}, status={}, body={}",
                        aiEditModel, httpResponse.getStatus(), responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "AI 配文失败" + (upstream == null ? "" : "：" + upstream));
            }
            JSONObject response = JSONUtil.parseObj(responseBody);
            // 兼容错误响应（如 {"code":"...","message":"..."}）
            if (response.getStr("code") != null) {
                String errorMessage = response.getStr("message");
                log.error("AI 配文请求异常：{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 配文失败，" + errorMessage);
            }
            JSONArray choices = response.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("AI 配文响应异常：{}", responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 配文失败，响应为空");
            }
            String resultContent = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (StrUtil.isBlank(resultContent)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 配文失败，模型未返回内容");
            }
            return resultContent;
        }
    }

    /**
     * 从 DashScope 错误响应中提取可读原因
     * <p>兼容两种格式：OpenAI 风格的 {@code {"error":{"message":"..."}}}
     * 与 DashScope 原生的 {@code {"code":"...","message":"..."}}。</p>
     *
     * @return 可读错误信息；无法解析时返回 null
     */
    private String extractUpstreamError(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                String message = error.getStr("message");
                if (StrUtil.isNotBlank(message)) {
                    // 模型名错误时明确提示配置项，便于直接定位
                    String code = error.getStr("code");
                    return "model_not_found".equals(code) ? message + "（请检查 aliYunAi.aiEditModel 配置）" : message;
                }
            }
            String message = json.getStr("message");
            return StrUtil.isBlank(message) ? null : message;
        } catch (Exception e) {
            // 响应体不是 JSON（如网关返回的 HTML），截断后原样带出，避免信息丢失
            return StrUtil.maxLength(responseBody.replaceAll("\\s+", " ").trim(), 120);
        }
    }
}

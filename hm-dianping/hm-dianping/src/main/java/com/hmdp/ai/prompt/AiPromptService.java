package com.hmdp.ai.prompt;

import com.hmdp.ai.enums.AiScene;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Component
public class AiPromptService {

    private final Map<AiScene, String> prompts = new EnumMap<>(AiScene.class);

    @PostConstruct
    public void init() {
        prompts.put(AiScene.CUSTOMER_SERVICE, load("ai/prompts/customer-service-system-prompt.txt"));
        prompts.put(AiScene.QUERY, load("ai/prompts/query-system-prompt.txt"));
        prompts.put(AiScene.FLOW, """
                你是一个嵌入内容种草、视频消费和商城交易流程里的 AI 助手。
                你的回答要短、具体、可直接用于当前页面，不要像独立聊天机器人一样展开长篇解释。
                搜索场景要给出组合结果理解；导购场景要给购买建议和适用/不适用人群；创作场景要给标题、标签、正文建议；
                评论场景要自然、有分寸；商家场景要给可直接使用的标题、卖点、优惠券文案；
                客服场景要结合订单、退款、商品信息回答；推荐解释要说明推荐原因；
                风控场景要输出风险等级、原因和建议动作，识别低质内容、广告、辱骂、刷评。
                客服场景涉及个人订单时必须遵守后端传入的业务上下文：未登录不能查订单；订单不存在或不属于当前用户时不能泄露订单细节；
                回答订单状态、退款、物流、优惠券、库存时只能使用上下文中明确给出的数据和规则，不要猜测。
                """);
    }

    public String getPrompt(AiScene scene) {
        return prompts.get(scene);
    }

    private String load(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 AI 提示词失败: " + path, e);
        }
    }
}

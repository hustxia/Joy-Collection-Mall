package com.mall.ai.controller;

import com.mall.ai.entity.Faq;
import com.mall.ai.support.FaqHit;
import com.mall.ai.support.FaqRetriever;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部端点（/inner/**，网关不暴露）：FAQ 检索。
 *
 * <p>留给「其它服务想在别处复用同一份客服知识」的场景（如消息模板推荐问题），
 * 也方便冒烟脚本在不起 LLM 的情况下验证检索质量。裸 Map 返回，不套 Result。
 */
@RestController
@RequestMapping("/inner/ai-chat")
public class InnerAiChatController {

    private final FaqRetriever faqRetriever;

    public InnerAiChatController(FaqRetriever faqRetriever) {
        this.faqRetriever = faqRetriever;
    }

    @GetMapping("/faq/search")
    public Map<String, Object> search(@RequestParam("q") String q) {
        FaqHit hit = faqRetriever.search(q);
        Map<String, Object> data = new LinkedHashMap<>();
        if (hit == null) {
            data.put("hit", false);
            return data;
        }
        Faq faq = hit.faq();
        data.put("hit", true);
        data.put("score", Math.round(hit.score() * 100) / 100.0);
        data.put("code", faq.getCode());
        data.put("question", faq.getQuestion());
        data.put("answer", faq.getAnswer());
        data.put("category", faq.getCategory());
        return data;
    }
}

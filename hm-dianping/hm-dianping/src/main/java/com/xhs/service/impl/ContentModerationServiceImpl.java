package com.xhs.service.impl;

import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.service.ContentModerationService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ContentModerationServiceImpl implements ContentModerationService {

    private static final String DEFAULT_SENSITIVE_WORDS =
            "\u52a0\u5fae\u4fe1,\u52a0wx,\u52a0vx,\u5fae\u4fe1\u53f7,v\u4fe1,\u8fd4\u73b0,"
                    + "\u5237\u5355,\u4ee3\u5237,\u535a\u5f69,\u8d4c\u535a,\u88f8\u804a,"
                    + "\u7ea6\u70ae,\u9ec4\u7247,\u50bb\u903c,\u64cd\u4f60,\u53bb\u6b7b";
    private static final Pattern WORD_SPLITTER = Pattern.compile("[,\\uFF0C\\r\\n]+");
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    @Value("${xiaohongshu.moderation.enabled:true}")
    private boolean enabled;

    @Value("${xiaohongshu.moderation.sensitive-words:}")
    private String sensitiveWordsConfig;

    private volatile List<String> sensitiveWords = List.of();

    @PostConstruct
    public void init() {
        sensitiveWords = parseWords(sensitiveWordsConfig);
    }

    @Override
    public void checkText(String scene, String... texts) {
        if (!hitWords(texts).isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    scene + "\u5305\u542b\u654f\u611f\u8bcd\uff0c\u8bf7\u4fee\u6539\u540e\u518d\u63d0\u4ea4");
        }
    }

    @Override
    public List<String> hitWords(String... texts) {
        if (!enabled || sensitiveWords.isEmpty() || texts == null || texts.length == 0) {
            return List.of();
        }
        String normalizedText = normalize(String.join(" ", toSafeList(texts)));
        if (normalizedText.isEmpty()) {
            return List.of();
        }
        List<String> hits = new ArrayList<>();
        for (String word : sensitiveWords) {
            if (normalizedText.contains(word)) {
                hits.add(word);
            }
        }
        return hits;
    }

    private List<String> parseWords(String config) {
        String source = config == null || config.trim().isEmpty() ? DEFAULT_SENSITIVE_WORDS : config;
        Set<String> words = new LinkedHashSet<>();
        for (String raw : WORD_SPLITTER.split(source)) {
            String word = normalize(raw);
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return List.copyOf(words);
    }

    private List<String> toSafeList(String... texts) {
        List<String> result = new ArrayList<>();
        for (String text : texts) {
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return NOISE.matcher(lower).replaceAll("");
    }
}

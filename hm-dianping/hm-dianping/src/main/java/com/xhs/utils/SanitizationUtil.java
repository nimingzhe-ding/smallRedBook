package com.xhs.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 清理工具：防止 XSS 攻击。
 * 使用 Jsoup 白名单过滤，只保留安全的 HTML 标签和属性。
 */
public final class SanitizationUtil {

    private SanitizationUtil() {}

    /**
     * 清理 HTML 内容，保留基本格式标签。
     * 允许: p, br, b, i, u, em, strong, a[href], img[src], ul, ol, li, h1-h6, blockquote
     */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Safelist safelist = Safelist.basicWithImages()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6", "blockquote");
        return Jsoup.clean(html, safelist);
    }

    /**
     * 纯文本模式：移除所有 HTML 标签。
     */
    public static String stripAll(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, Safelist.none());
    }
}

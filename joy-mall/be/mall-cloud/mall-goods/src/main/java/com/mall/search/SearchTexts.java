package com.mall.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索文本工具（V14）：规范化 / 二元切分 / 编辑距离。
 *
 * <p><b>中文分词策略（刻意不做重型实现）：</b>
 * <ol>
 *   <li>先查 {@code mall_search_term} 词表：命中即拿到同义词组，一次搜索可扩成多个词；</li>
 *   <li>长词（≥3 个连续汉字）再做「二元切分」召回（"智能手机" → "智能"/"能手"/"手机"），
 *       覆盖词表未收录的组合；</li>
 *   <li>最后统一走 SQL LIKE 兜底，保证「至少不会因为分词失败而 0 结果」。</li>
 * </ol>
 *
 * <p><b>已知局限（如实记录，不做掩盖）：</b>
 * <ul>
 *   <li>没有真正的词典与 HMM 模型，二元切分会引入少量噪声词（"能手"），靠词长加权与
 *       标题命中加权压住，不保证 100% 精确；</li>
 *   <li>拼音只支持「输入的拼音在词表里」这一条路径（离线环境无中文注音库），
 *       商品标题里没进词表的词搜拼音不命中；补词表即可扩面，无需改代码；</li>
 *   <li>纠错只做「编辑距离 ≤1 + 词表候选」，两个错字以上的输入不纠错，直接走 LIKE。</li>
 * </ul>
 */
public final class SearchTexts {

    private SearchTexts() {
    }

    /** 规范化：去首尾空白、全角转半角、转小写、连续空白折叠为单空格 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastSpace = false;
        for (char ch : raw.toCharArray()) {
            char c = ch;
            if (c == '\u3000') {
                c = ' ';
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 ASCII（！~）→ 半角（!~）
                c = (char) (c - 0xFEE0);
            }
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            sb.append(Character.toLowerCase(c));
            lastSpace = false;
        }
        return sb.toString().trim();
    }

    /** 是否「纯字母数字」输入（拼音/英文场景） */
    public static boolean isAsciiAlnum(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (char c : s.toCharArray()) {
            if (!(Character.isLetterOrDigit(c) && c < 128)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCjk(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    /** 连续汉字段长度 ≥3 时做二元切分；去重后返回 */
    public static List<String> bigrams(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null || s.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder run = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (isCjk(c)) {
                run.append(c);
            } else {
                addBigrams(run.toString(), out);
                run.setLength(0);
            }
        }
        addBigrams(run.toString(), out);
        // 二元片段本身等于整词时无意义，移除
        out.remove(s);
        return new ArrayList<>(out);
    }

    private static void addBigrams(String run, Set<String> out) {
        if (run.length() < 3) {
            return;
        }
        for (int i = 0; i + 1 < run.length(); i++) {
            out.add(run.substring(i, i + 2));
        }
    }

    /** 不区分大小写的包含判断（null 安全） */
    public static boolean containsIgnoreCase(String text, String word) {
        if (text == null || word == null || word.isEmpty()) {
            return false;
        }
        return text.toLowerCase().contains(word.toLowerCase());
    }

    /** 不区分大小写的前缀判断（联想词用） */
    public static boolean startsWithIgnoreCase(String text, String word) {
        if (text == null || word == null || word.isEmpty()) {
            return false;
        }
        return text.toLowerCase().startsWith(word.toLowerCase());
    }

    /**
     * 编辑距离（Levenshtein），带上限提前返回：超过 {@code max} 立刻返回 max+1，
     * 词表上千条时也不会因为逐条全量计算拖慢 suggest。
     */
    public static int editDistance(String a, String b, int max) {
        if (a == null || b == null) {
            return max + 1;
        }
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()] > max ? max + 1 : prev[b.length()];
    }
}

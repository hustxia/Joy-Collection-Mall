package com.mall.merchant.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 商家对账单 CSV 生成（V11）。
 *
 * <p>不引 POI：对账单是给人看、给财务导入的表格，CSV 足够，还能被 Excel/WPS 直接打开。
 * 两个必须做对的细节：
 * <ul>
 *   <li>UTF-8 <b>BOM</b>：没有它 Excel 会把中文读成乱码（实测 Windows 版必现）；</li>
 *   <li>字段转义：逗号、双引号、换行都要引号包裹，否则一列错位全表作废。</li>
 * </ul>
 */
public final class MerchantBillCsv {

    /** Excel 识别 UTF-8 的 BOM */
    public static final String BOM = "\uFEFF";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] HEADERS = {
            "订单号", "结算单ID", "订单实付(元)", "平台抽成(元)", "商家应收(元)", "退款冲减(元)", "是否退款", "入账时间"
    };

    private MerchantBillCsv() {
    }

    /**
     * @param rows 明细行（键与 mall_merchant_bill_detail 列同名，允许缺失）
     * @return 带 BOM 的 CSV 文本
     */
    public static String build(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(String.join(",", HEADERS)).append("\r\n");
        if (rows == null) {
            return sb.toString();
        }
        for (Map<String, Object> row : rows) {
            sb.append(longNumber(row.get("orderNo"))).append(',')
                    .append(longNumber(row.get("settlementId"))).append(',')
                    .append(yuan(row.get("payAmount"))).append(',')
                    .append(yuan(row.get("platformFee"))).append(',')
                    .append(yuan(row.get("goodsAmount"))).append(',')
                    .append(yuan(row.get("refundAmount"))).append(',')
                    .append(escape(Integer.valueOf(1).equals(intOf(row.get("refundFlag"))) ? "是" : "否")).append(',')
                    .append(escape(time(row.get("createTime")))).append("\r\n");
        }
        return sb.toString();
    }

    /** 分 → 元（两位小数，不出现 12.000000001 这种小数尾巴） */
    static String yuan(Object fen) {
        BigDecimal value = fen == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(fen));
        return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 长数字单号按文本输出：Excel 打开 CSV 会把 20 位数字转科学计数并丢精度，用 ="..." 强制文本 */
    static String longNumber(Object value) {
        String text = text(value);
        return text.isEmpty() ? "" : escape("=\"" + text + "\"");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String time(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return TS.format(ldt);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return TS.format(ts.toLocalDateTime());
        }
        return text(value);
    }
}

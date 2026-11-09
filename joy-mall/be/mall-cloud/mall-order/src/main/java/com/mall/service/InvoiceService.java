package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.dto.InvoiceApplyDTO;
import com.mall.dto.InvoicePageDTO;
import com.mall.entity.MallInvoice;
import com.mall.entity.MallOrder;
import com.mall.mapper.MallInvoiceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 发票服务（V12）：申请、开票、红冲。
 *
 * <p>口径：
 * <ul>
 *   <li>只有**已完成(40)**的订单可申请——售后未完成的订单开了票再退款就是红冲地狱；</li>
 *   <li>金额恒取 {@code mall_order.pay_amount}，一单一票（唯一键 order_id+deleted 兜底）；</li>
 *   <li>企业抬头税号必须 15–20 位字母数字，个人抬头不校验；</li>
 *   <li>已开票订单退款 → 自动红冲（由售后域在退款完成时调用），原票作废。</li>
 * </ul>
 */
@Slf4j
@Service
public class InvoiceService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 纳税人识别号：15-20 位大写字母/数字 */
    private static final Pattern TAX_NO = Pattern.compile("^[0-9A-Za-z]{15,20}$");

    private final MallInvoiceMapper invoiceMapper;
    private final MallOrderService mallOrderService;
    private final JdbcTemplate jdbcTemplate;

    public InvoiceService(MallInvoiceMapper invoiceMapper, MallOrderService mallOrderService,
                          JdbcTemplate jdbcTemplate) {
        this.invoiceMapper = invoiceMapper;
        this.mallOrderService = mallOrderService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== C 端 =====

    @Transactional(rollbackFor = Exception.class)
    public MallInvoice apply(Long memberId, InvoiceApplyDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "请选择要开票的订单");
        }
        MallOrder order = mallOrderService.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权为该订单开票");
        }
        if (!Integer.valueOf(40).equals(order.getStatus())) {
            throw new BusinessException(400, "订单完成后才能申请开票");
        }
        Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_invoice WHERE order_id = ? AND deleted = 0", Long.class, order.getId());
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "该订单已申请开票，请勿重复提交");
        }
        int titleType = dto.getTitleType() == null ? MallInvoice.TITLE_PERSONAL : dto.getTitleType();
        if (titleType != MallInvoice.TITLE_PERSONAL && titleType != MallInvoice.TITLE_COMPANY) {
            throw new BusinessException(400, "抬头类型不正确");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(400, "发票抬头不能为空");
        }
        String taxNo = dto.getTaxNo() == null ? null : dto.getTaxNo().trim();
        if (titleType == MallInvoice.TITLE_COMPANY) {
            if (!StringUtils.hasText(taxNo)) {
                throw new BusinessException(400, "企业抬头必须填写纳税人识别号");
            }
            if (!TAX_NO.matcher(taxNo).matches()) {
                throw new BusinessException(400, "纳税人识别号必须为 15-20 位字母或数字");
            }
        } else {
            taxNo = null;
        }
        MallInvoice invoice = new MallInvoice();
        invoice.setInvoiceNo("I" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        invoice.setOrderId(order.getId());
        invoice.setOrderNo(order.getOrderNo());
        invoice.setMemberId(memberId);
        invoice.setMerchantId(order.getMerchantId());
        invoice.setTitleType(titleType);
        invoice.setTitle(dto.getTitle().trim());
        invoice.setTaxNo(taxNo);
        invoice.setEmail(dto.getEmail());
        invoice.setAmount(order.getPayAmount() == null ? 0L : order.getPayAmount());
        invoice.setStatus(MallInvoice.ST_WAIT);
        invoiceMapper.insert(invoice);
        return invoice;
    }

    public Page<Map<String, Object>> pageOfMember(Long memberId, long pageNum, long pageSize) {
        Page<MallInvoice> page = invoiceMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MallInvoice>()
                        .eq(MallInvoice::getMemberId, memberId)
                        .orderByDesc(MallInvoice::getId));
        return toMapPage(page);
    }

    // ===== 后台 =====

    public Page<Map<String, Object>> adminPage(InvoicePageDTO dto) {
        LambdaQueryWrapper<MallInvoice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> w.like(MallInvoice::getOrderNo, kw)
                    .or().like(MallInvoice::getInvoiceNo, kw)
                    .or().like(MallInvoice::getTitle, kw));
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallInvoice::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallInvoice::getId);
        return toMapPage(invoiceMapper.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper));
    }

    /** 开票：0 待开票 → 1 已开票，必须给电子发票文件地址 */
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long id, String fileUrl) {
        MallInvoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new BusinessException(400, "发票申请不存在");
        }
        if (!Integer.valueOf(MallInvoice.ST_WAIT).equals(invoice.getStatus())) {
            throw new BusinessException(400, "该发票已开票或已红冲");
        }
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException(400, "请上传电子发票文件后再开票");
        }
        int affected = jdbcTemplate.update("UPDATE mall_invoice SET status = ?, file_url = ?, issue_time = NOW()"
                + " WHERE id = ? AND status = ?", MallInvoice.ST_ISSUED, fileUrl.trim(), id, MallInvoice.ST_WAIT);
        if (affected == 0) {
            throw new BusinessException(400, "该发票状态已变化，请刷新后重试");
        }
    }

    /** 红冲：1 已开票 → 2 已红冲 */
    @Transactional(rollbackFor = Exception.class)
    public void redFlush(Long id, String remark) {
        MallInvoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new BusinessException(400, "发票申请不存在");
        }
        doRedFlush(invoice, remark);
    }

    /** 按订单红冲（售后域退款完成时回调）：无发票或已红冲时静默跳过 */
    @Transactional(rollbackFor = Exception.class)
    public boolean redFlushByOrder(Long orderId, String remark) {
        List<MallInvoice> list = invoiceMapper.selectList(new LambdaQueryWrapper<MallInvoice>()
                .eq(MallInvoice::getOrderId, orderId)
                .eq(MallInvoice::getStatus, MallInvoice.ST_ISSUED));
        if (list.isEmpty()) {
            return false;
        }
        doRedFlush(list.get(0), remark);
        return true;
    }

    private void doRedFlush(MallInvoice invoice, String remark) {
        int affected = jdbcTemplate.update(
                "UPDATE mall_invoice SET status = ?, red_flush_time = NOW(), remark = ?"
                        + " WHERE id = ? AND status = ?",
                MallInvoice.ST_RED_FLUSHED, remark, invoice.getId(), MallInvoice.ST_ISSUED);
        if (affected == 0) {
            throw new BusinessException(400, "仅已开票的发票可红冲");
        }
        log.info("发票已红冲：invoiceNo={}, orderNo={}", invoice.getInvoiceNo(), invoice.getOrderNo());
    }

    private Page<Map<String, Object>> toMapPage(Page<MallInvoice> page) {
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(i.getId()));
            row.put("invoiceNo", i.getInvoiceNo());
            row.put("orderNo", i.getOrderNo());
            row.put("orderId", String.valueOf(i.getOrderId()));
            row.put("titleType", i.getTitleType());
            row.put("title", i.getTitle());
            row.put("taxNo", i.getTaxNo());
            row.put("email", i.getEmail());
            row.put("amount", i.getAmount());
            row.put("status", i.getStatus());
            row.put("statusText", MallInvoice.statusText(i.getStatus()));
            row.put("fileUrl", i.getFileUrl());
            row.put("issueTime", i.getIssueTime());
            row.put("redFlushTime", i.getRedFlushTime());
            row.put("createTime", i.getCreateTime());
            return row;
        }).toList());
        return result;
    }
}

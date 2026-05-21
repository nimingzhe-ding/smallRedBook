package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.enums.AiScene;
import com.hmdp.ai.service.AiAssistantService;
import com.hmdp.dto.AiCustomerServiceResponse;
import com.hmdp.dto.AiFlowRequest;
import com.hmdp.dto.Result;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.entity.Blog;
import com.hmdp.entity.MallOrder;
import com.hmdp.entity.MallProduct;
import com.hmdp.entity.Voucher;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IMallOrderService;
import com.hmdp.service.IMallProductService;
import com.hmdp.service.NoteService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.MallOrderServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final NoteService noteService;
    private final IMallProductService productService;
    private final IMallOrderService orderService;
    private final IVoucherService voucherService;

    @PostMapping("/customer-service/chat")
    public Result customerService(@RequestBody AiChatRequest request) {
        try {
            return Result.ok(aiAssistantService.customerService(request));
        } catch (RuntimeException e) {
            String sessionId = StrUtil.blankToDefault(request == null ? null : request.getSessionId(), "customer-service");
            String answer = customerServiceFallback(text(request == null ? null : request.getMessage()), null,
                    UserHolder.getUser(), null, null, null);
            return Result.ok(new AiChatResponse(AiScene.CUSTOMER_SERVICE.getCode(), sessionId,
                    answer, java.util.List.of(), null, java.time.LocalDateTime.now()));
        }
    }

    @PostMapping("/query/chat")
    public Result query(@RequestBody AiChatRequest request) {
        return Result.ok(aiAssistantService.query(request));
    }

    @PostMapping("/flow/search")
    public Result flowSearch(@RequestBody AiFlowRequest request) {
        return safeFlow("AI 搜索", request, """
                用户搜索：%s
                请理解用户真实意图，说明应该优先看哪些笔记、商品、商家和话题，并给出一句搜索结果摘要。
                """.formatted(text(request.getQuery())));
    }

    @PostMapping("/flow/shopping-guide")
    public Result shoppingGuide(@RequestBody AiFlowRequest request) {
        MallProduct product = request == null || request.getProductId() == null ? null : productService.getById(request.getProductId());
        return safeFlow("AI 导购", request, """
                用户问题：%s
                商品信息：%s
                请判断是否适合当前问题里的送礼/自用/场景需求，给出适合点、顾虑点和购买建议。
                """.formatted(text(request == null ? null : request.getQuery()), productText(product)));
    }

    @PostMapping("/flow/note-summary")
    public Result noteSummary(@RequestBody AiFlowRequest request) {
        Blog blog = request == null || request.getNoteId() == null ? null : noteService.getNoteEntity(request.getNoteId());
        String title = text(request == null ? null : request.getTitle());
        String content = text(request == null ? null : request.getContent());
        if (blog != null) {
            title = StrUtil.blankToDefault(blog.getTitle(), title);
            content = StrUtil.blankToDefault(blog.getContent(), content);
        }
        return safeFlow("AI 笔记总结", request, """
                笔记标题：%s
                笔记正文：%s
                请自动总结亮点、避雷点、价格信息、适合人群，每项一句话。
                """.formatted(title, content));
    }

    @PostMapping("/flow/compose")
    public Result compose(@RequestBody AiFlowRequest request) {
        return safeFlow("AI 创作助手", request, """
                发布类型：%s
                已有标题：%s
                已有正文：%s
                请生成一个更适合内容种草平台的标题、5个标签和一段可直接使用的正文。
                """.formatted(text(request == null ? null : request.getScenario()), text(request == null ? null : request.getTitle()), text(request == null ? null : request.getContent())));
    }

    @PostMapping("/flow/comment")
    public Result comment(@RequestBody AiFlowRequest request) {
        return safeFlow("AI 评论助手", request, """
                当前内容：%s
                评论/回复目标：%s
                请生成3条自然、不夸张、不冒犯的评论或回复，控制在30字以内。
                """.formatted(text(request == null ? null : request.getContent()), text(request == null ? null : request.getQuery())));
    }

    @PostMapping("/flow/merchant-copy")
    public Result merchantCopy(@RequestBody AiFlowRequest request) {
        return safeFlow("AI 商家助手", request, """
                商品/活动信息：%s
                请生成商品标题、3条卖点和一条优惠券文案，语气真实克制，不要虚假承诺。
                """.formatted(text(request == null ? null : request.getContent())));
    }

    @PostMapping("/flow/customer-service")
    public Result customerServiceFlow(@RequestBody AiFlowRequest request) {
        CustomerServiceContext context = buildCustomerServiceContext(request);
        String question = text(request == null ? null : request.getQuery());
        String ruleAnswer = customerServiceFallback(question, context);
        if (context.shouldAnswerByRule(question)) {
            return Result.ok(customerServiceResponse(context, ruleAnswer, "rule", java.util.List.of("rule:customer-service")));
        }
        return Result.ok(customerServiceAiResponse(context, request, """
                用户咨询：%s
                当前登录用户：%s
                当前页面场景：%s
                订单信息：%s
                商品信息：%s
                优惠券信息：%s
                安全要求：
                1. 未登录时不得查询或猜测订单信息。
                2. 订单不存在或不属于当前用户时，只能提示用户核对订单，不能输出任何订单细节。
                3. 只允许基于上方业务上下文回答，不要编造物流单号、退款进度、库存或优惠券。
                请按客服口吻回答，涉及退款、物流、库存、支付、取消时说明当前系统状态、可操作按钮和下一步建议。
                """.formatted(question, context.user == null ? "未登录" : context.user.getId(), text(request == null ? null : request.getScenario()),
                orderText(context.order), productText(context.product), voucherText(context.voucher)),
                ruleAnswer));
    }

    @PostMapping("/flow/recommend-reason")
    public Result recommendReason(@RequestBody AiFlowRequest request) {
        Blog blog = request == null || request.getNoteId() == null ? null : noteService.getNoteEntity(request.getNoteId());
        return safeFlow("AI 推荐解释", request, """
                推荐内容：%s
                用户当前意图：%s
                请用一句话解释为什么推荐这条内容，避免暴露隐私和复杂算法。
                """.formatted(blogText(blog), text(request == null ? null : request.getQuery())));
    }

    @PostMapping("/flow/risk-check")
    public Result riskCheck(@RequestBody AiFlowRequest request) {
        String content = text(request == null ? null : request.getContent());
        String fallback = ruleRiskResult(content);
        return safeFlow("AI 风控", request, """
                待审核内容：%s
                请识别低质内容、广告、辱骂、刷评风险，输出：风险等级、原因、建议动作。
                """.formatted(content), fallback);
    }

    @DeleteMapping("/session/{scene}/{sessionId}")
    public Result clearConversation(@PathVariable("scene") String scene, @PathVariable("sessionId") String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "sessionId 不能为空");
        }
        aiAssistantService.clearConversation(AiScene.fromCode(scene), sessionId);
        return Result.ok();
    }

    private Result safeFlow(String sceneName, AiFlowRequest request, String message) {
        return safeFlow(sceneName, request, message, sceneName + "暂时不可用，先按页面已有信息继续操作。");
    }

    private Result safeFlow(String sceneName, AiFlowRequest request, String message, String fallback) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setSessionId(StrUtil.blankToDefault(request == null ? null : request.getSessionId(), sceneName));
        chatRequest.setReset(request == null ? null : request.getReset());
        chatRequest.setMessage(message);
        try {
            AiChatResponse response = aiAssistantService.flow(chatRequest);
            return Result.ok(response);
        } catch (RuntimeException e) {
            return Result.ok(new AiChatResponse(AiScene.FLOW.getCode(), chatRequest.getSessionId(),
                    fallback, java.util.List.of(), null, java.time.LocalDateTime.now()));
        }
    }

    private CustomerServiceContext buildCustomerServiceContext(AiFlowRequest request) {
        UserDTO user = UserHolder.getUser();
        boolean orderRequested = request != null && request.getOrderId() != null;
        MallOrder order = orderRequested && user != null ? loadOwnedMallOrder(request, user) : null;
        boolean orderDenied = orderRequested && user != null && order == null;
        if (orderDenied) {
            return new CustomerServiceContext(request, user, null, null, null, true, true);
        }
        MallProduct product = loadCustomerServiceProduct(request, order);
        Voucher voucher = loadCustomerServiceVoucher(request, order, product);
        return new CustomerServiceContext(request, user, order, product, voucher, orderRequested, orderDenied);
    }

    private MallOrder loadOwnedMallOrder(AiFlowRequest request, UserDTO user) {
        if (request == null || request.getOrderId() == null || user == null) {
            return null;
        }
        return orderService.query()
                .eq("id", request.getOrderId())
                .eq("user_id", user.getId())
                .last("limit 1")
                .one();
    }

    private MallProduct loadCustomerServiceProduct(AiFlowRequest request, MallOrder order) {
        Long productId = null;
        if (request != null && request.getProductId() != null) {
            productId = request.getProductId();
        } else if (order != null) {
            productId = order.getProductId();
        }
        return productId == null ? null : productService.getById(productId);
    }

    private Voucher loadCustomerServiceVoucher(AiFlowRequest request, MallOrder order, MallProduct product) {
        if (request != null && request.getVoucherId() != null) {
            return voucherService.getById(request.getVoucherId());
        }
        if (order != null && order.getVoucherId() != null) {
            return voucherService.getById(order.getVoucherId());
        }
        if (product == null) {
            return null;
        }
        return voucherService.query()
                .eq("status", 1)
                .and(wrapper -> wrapper.eq("product_id", product.getId())
                        .or()
                        .eq(product.getMerchantId() != null, "merchant_id", product.getMerchantId()))
                .orderByDesc("actual_value")
                .last("limit 1")
                .one();
    }

    private AiCustomerServiceResponse customerServiceAiResponse(CustomerServiceContext context, AiFlowRequest request,
                                                               String message, String fallback) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setSessionId(StrUtil.blankToDefault(request == null ? null : request.getSessionId(), "AI 客服"));
        chatRequest.setReset(request == null ? null : request.getReset());
        chatRequest.setMessage(message);
        try {
            AiChatResponse aiResponse = aiAssistantService.flow(chatRequest);
            String answer = StrUtil.blankToDefault(aiResponse == null ? null : aiResponse.getAnswer(), fallback);
            List<String> refs = aiResponse == null || aiResponse.getKnowledgeRefs() == null
                    ? java.util.List.of("ai:customer-service")
                    : aiResponse.getKnowledgeRefs();
            return customerServiceResponse(context, answer, "ai", refs);
        } catch (RuntimeException e) {
            return customerServiceResponse(context, fallback, "fallback", java.util.List.of("fallback:customer-service"));
        }
    }

    private AiCustomerServiceResponse customerServiceResponse(CustomerServiceContext context, String answer,
                                                             String source, List<String> refs) {
        String sessionId = StrUtil.blankToDefault(context.request == null ? null : context.request.getSessionId(), "AI 客服");
        Long currentUserId = context.user == null ? null : context.user.getId();
        AiCustomerServiceResponse response = new AiCustomerServiceResponse();
        response.setScene(AiScene.FLOW.getCode());
        response.setSessionId(sessionId);
        response.setAnswer(answer);
        response.setSource(source);
        response.setScenario(context.scenario());
        response.setRequiresLogin(context.requiresLogin());
        response.setKnowledgeRefs(refs == null ? java.util.List.of() : refs);
        response.setCurrentUserId(currentUserId);
        response.setTimestamp(LocalDateTime.now());
        response.setContext(context.toSummary());
        response.setActions(context.actions());
        return response;
    }

    private String customerServiceFallback(String question, CustomerServiceContext context) {
        return customerServiceFallback(question, context.request, context.user, context.order, context.product, context.voucher,
                context.orderRequested, context.orderDenied);
    }

    private String customerServiceFallback(String question, AiFlowRequest request, UserDTO user, MallOrder order,
                                           MallProduct product, Voucher voucher) {
        boolean orderRequested = request != null && request.getOrderId() != null;
        boolean orderDenied = orderRequested && order == null;
        return customerServiceFallback(question, request, user, order, product, voucher, orderRequested, orderDenied);
    }

    private String customerServiceFallback(String question, AiFlowRequest request, UserDTO user, MallOrder order,
                                           MallProduct product, Voucher voucher, boolean orderRequested, boolean orderDenied) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "").toLowerCase();
        if (orderRequested && user == null) {
            return "我可以帮你查订单，但需要先登录当前账号。登录后打开“我的订单”，再输入订单问题，我会结合订单状态、物流和售后信息回答。";
        }
        if (orderDenied) {
            return "没有查到这个订单，或该订单不属于当前登录账号。请确认订单号后，在“我的订单”里重新发起咨询。";
        }
        if (order != null) {
            return orderServiceFallback(normalizedQuestion, order, product, voucher);
        }
        if (user == null && isPersonalOrderQuestion(normalizedQuestion, request)) {
            return "涉及你的订单状态、物流或售后进度时，需要先登录当前账号。登录后从“我的订单”进入咨询，我才能按订单归属查询并回答。";
        }
        if (user != null && isPersonalOrderQuestion(normalizedQuestion, request)) {
            return "我可以帮你查订单状态、退款进度和物流，但需要先确定具体订单。请从“我的订单”里选择一笔订单后再咨询，或在请求里带上 orderId。";
        }
        if (containsAny(normalizedQuestion, "登录", "验证码", "手机")) {
            return "登录方式是手机号验证码登录：先点“登录”，输入手机号获取验证码；验证码会打印在后端日志里，本地调试时复制验证码完成登录。";
        }
        if (containsAny(normalizedQuestion, "人工", "客服", "转人工")) {
            return "当前是本地智能客服模式，暂时没有人工坐席队列。我可以先帮你定位订单、退款、物流、优惠券和商品库存问题；如果涉及订单隐私，请先登录后从“我的订单”里发起咨询。";
        }
        if (containsAny(normalizedQuestion, "退款", "售后", "退货")) {
            return "退款需要先有商城订单。已支付、待发货或已发货订单可以在“我的订单”里申请退款；待支付订单可以直接取消。";
        }
        if (containsAny(normalizedQuestion, "物流", "快递", "发货")) {
            return "物流信息会在商家发货后出现在订单中。如果订单仍是待发货，说明商家还没有填写物流单号。";
        }
        if (containsAny(normalizedQuestion, "地址", "收货", "配送")) {
            return "收货地址在提交订单页维护。下单前可以新增或选择默认地址；订单支付后如需修改地址，建议在商家发货前尽快取消重下或联系商家处理。";
        }
        if (containsAny(normalizedQuestion, "优惠券", "券", "秒杀")) {
            if (voucher != null) {
                return "当前可参考优惠券：" + voucherText(voucher) + "。结算页会按商品、店铺、平台范围校验门槛，满足条件后抵扣。";
            }
            if (product != null) {
                return productServiceFallback(normalizedQuestion, product, null);
            }
            return "平台支持店铺优惠券、商城商品券和秒杀券。秒杀券需要在活动时间内且库存充足时下单，同一用户不能重复抢同一张券。";
        }
        if (containsAny(normalizedQuestion, "库存", "还有", "能买")) {
            if (product != null) {
                return productServiceFallback(normalizedQuestion, product, voucher);
            }
            return "库存需要结合具体商品查询。请从商品详情页发起咨询，或在请求里带上 productId，我会返回当前商品库存和是否还能购买。";
        }
        if (containsAny(normalizedQuestion, "投诉", "举报", "违规")) {
            return "如果遇到虚假商品、异常订单或不合适的笔记内容，可以先保留订单号/笔记信息。评论区支持举报，订单类问题建议从“我的订单”带订单号咨询，系统会避免泄露他人订单信息。";
        }
        if (product != null) {
            return productServiceFallback(normalizedQuestion, product, voucher);
        }
        return "我可以回答登录、订单、支付、退款、物流、优惠券、商品库存等问题。你可以这样问：我的订单怎么退款、物流到哪了、这个商品还有库存吗。";
    }

    private String orderServiceFallback(String question, MallOrder order, MallProduct product, Voucher voucher) {
        String status = mallOrderStatus(order.getStatus());
        StringBuilder answer = new StringBuilder();
        answer.append("这笔订单当前状态是：").append(status).append("。");
        if (containsAny(question, "物流", "快递", "发货", "到哪")) {
            if (StrUtil.isNotBlank(order.getLogisticsNo())) {
                answer.append("物流公司：").append(StrUtil.blankToDefault(order.getLogisticsCompany(), "未填写"))
                        .append("，物流单号：").append(order.getLogisticsNo()).append("。");
            } else if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_SHIP)) {
                answer.append("订单已支付，正在等待商家发货，目前还没有物流单号。");
            } else if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_PAY)) {
                answer.append("订单还未支付，支付后商家才会发货。");
            } else {
                answer.append("当前订单暂无物流单号。");
            }
            return answer.toString();
        }
        if (containsAny(question, "退款", "退货", "售后")) {
            if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_PAY)) {
                return "这笔订单还未支付，不需要退款，可以直接取消订单。";
            }
            if (isStatus(order, MallOrderServiceImpl.STATUS_PAID, MallOrderServiceImpl.STATUS_PENDING_SHIP, MallOrderServiceImpl.STATUS_SHIPPED)) {
                return "这笔订单可以申请退款。请在订单操作里提交退款申请，系统会创建售后单并通知商家处理。";
            }
            if (isStatus(order, MallOrderServiceImpl.STATUS_REFUNDING)) {
                return "这笔订单正在退款处理中，请等待商家审核。";
            }
            if (isStatus(order, MallOrderServiceImpl.STATUS_REFUNDED)) {
                return "这笔订单已经退款完成。";
            }
            return "当前订单状态是“" + status + "”，暂不支持直接申请退款。";
        }
        if (containsAny(question, "支付", "付款")) {
            if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_PAY)) {
                return "这笔订单待支付，点击“去支付”即可完成付款。订单金额为 ¥" + money(order.getTotalAmount()) + "。";
            }
            return "这笔订单当前状态是“" + status + "”，不需要重复支付。";
        }
        if (containsAny(question, "地址", "收货", "配送")) {
            if (StrUtil.isNotBlank(order.getReceiverAddress())) {
                return "这笔订单的收货地址是：" + order.getReceiverAddress() + "。如果商家还没有发货，建议尽快联系商家处理地址变更；已发货后通常需要联系快递处理。";
            }
            return "这笔订单没有保存到完整收货地址，请在订单详情确认是否是旧订单或重新下单。";
        }
        if (containsAny(question, "优惠", "优惠券", "券")) {
            if (voucher != null) {
                return "这笔订单相关优惠可参考：" + voucherText(voucher) + "。实际是否可用以订单结算时的商品、店铺、类目和门槛校验为准。";
            }
            Long discount = order.getDiscountAmount() == null ? 0L : order.getDiscountAmount();
            return discount > 0
                    ? "这笔订单已优惠 ¥" + money(discount) + "。如果还想使用其他优惠，需要取消后重新下单并在结算页选择可用券。"
                    : "这笔订单没有记录优惠抵扣。优惠券通常只能在下单结算前选择，支付后不能补用。";
        }
        if (containsAny(question, "取消")) {
            if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_PAY)) {
                return "这笔订单还未支付，可以直接取消。";
            }
            return "只有待支付订单可以直接取消。当前状态是“" + status + "”，如需售后请申请退款。";
        }
        answer.append("商品：").append(StrUtil.blankToDefault(order.getProductTitle(), "未命名商品"))
                .append("，数量：").append(order.getQuantity() == null ? 1 : order.getQuantity())
                .append("，实付：¥").append(money(order.getTotalAmount())).append("。");
        if (product != null && product.getStock() != null) {
            answer.append("当前商品库存约 ").append(product.getStock()).append(" 件。");
        }
        answer.append("你还可以继续问：怎么退款、物流到哪了、能不能取消。");
        return answer.toString();
    }

    private String productServiceFallback(String question, MallProduct product, Voucher voucher) {
        StringBuilder answer = new StringBuilder();
        answer.append("商品《").append(StrUtil.blankToDefault(product.getTitle(), "未命名商品")).append("》");
        answer.append("当前价格 ¥").append(money(product.getPrice())).append("，库存 ")
                .append(product.getStock() == null ? 0 : product.getStock()).append(" 件。");
        if (containsAny(question, "库存", "还有", "能买")) {
            answer.append(product.getStock() != null && product.getStock() > 0 ? "目前可以购买。" : "当前库存不足，建议稍后再看。");
        } else if (containsAny(question, "优惠", "券", "便宜")) {
            if (voucher != null) {
                answer.append("当前可参考优惠：").append(voucherText(voucher)).append("。");
            } else {
                answer.append("可用优惠券会在商品详情和结算页展示，结算时也支持自动选择最优优惠。");
            }
        } else {
            answer.append("可以结合预算、送礼对象和使用场景继续问我。");
        }
        return answer.toString();
    }

    private boolean containsAny(String value, String... keywords) {
        if (value == null) return false;
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPersonalOrderQuestion(String question, AiFlowRequest request) {
        if (request != null && StrUtil.equalsIgnoreCase(request.getScenario(), "order")) {
            return true;
        }
        return containsAny(question, "我的订单", "订单状态", "查订单", "物流到哪", "快递到哪", "退款进度", "售后进度");
    }

    private boolean isStatus(MallOrder order, int... statuses) {
        if (order == null || order.getStatus() == null) return false;
        for (int status : statuses) {
            if (order.getStatus() == status) {
                return true;
            }
        }
        return false;
    }

    private String mallOrderStatus(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case MallOrderServiceImpl.STATUS_PENDING_PAY -> "待支付";
            case MallOrderServiceImpl.STATUS_PAID -> "已支付";
            case MallOrderServiceImpl.STATUS_PENDING_SHIP -> "待发货";
            case MallOrderServiceImpl.STATUS_SHIPPED -> "已发货";
            case MallOrderServiceImpl.STATUS_COMPLETED -> "已完成";
            case MallOrderServiceImpl.STATUS_CANCELLED -> "已取消";
            case MallOrderServiceImpl.STATUS_REFUNDING -> "退款中";
            case MallOrderServiceImpl.STATUS_REFUNDED -> "已退款";
            default -> "未知";
        };
    }

    private String money(Long cents) {
        if (cents == null) return "0.00";
        return java.math.BigDecimal.valueOf(cents)
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String ruleRiskResult(String content) {
        String lower = content == null ? "" : content.toLowerCase();
        if (StrUtil.isBlank(lower) || lower.length() < 8) {
            return "风险等级：中。原因：内容过短，可能是低质内容。建议动作：提示用户补充真实体验。";
        }
        if (lower.contains("加微信") || lower.contains("返现") || lower.contains("刷单") || lower.contains("兼职")) {
            return "风险等级：高。原因：疑似广告或刷评引流。建议动作：拦截发布并提示修改。";
        }
        if (lower.contains("垃圾") || lower.contains("傻") || lower.contains("滚")) {
            return "风险等级：高。原因：疑似辱骂攻击。建议动作：拦截或进入人工审核。";
        }
        return "风险等级：低。原因：未命中明显广告、辱骂、刷评特征。建议动作：允许继续。";
    }

    private String text(String value) {
        return StrUtil.blankToDefault(StrUtil.trim(value), "未提供");
    }

    private String blogText(Blog blog) {
        if (blog == null) return "未提供";
        return "标题：" + blog.getTitle() + "；正文：" + blog.getContent() + "；点赞：" + blog.getLiked() + "；评论：" + blog.getComments();
    }

    private String productText(MallProduct product) {
        if (product == null) return "未提供";
        return "标题：" + product.getTitle() + "；副标题：" + product.getSubTitle() + "；价格：" + product.getPrice()
                + "；库存：" + product.getStock() + "；销量：" + product.getSold();
    }

    private String voucherText(Voucher voucher) {
        if (voucher == null) return "未提供";
        return "券：" + StrUtil.blankToDefault(voucher.getTitle(), "未命名优惠券")
                + "；门槛：" + money(voucher.getPayValue())
                + "；优惠：" + money(voucher.getActualValue())
                + "；范围：" + StrUtil.blankToDefault(voucher.getScopeType(), "未指定")
                + "；商品ID：" + voucher.getProductId()
                + "；商家ID：" + voucher.getMerchantId();
    }

    private String orderText(MallOrder order) {
        if (order == null) return "未提供";
        return "订单号：" + order.getId() + "；商品：" + order.getProductTitle() + "；状态：" + order.getStatus()
                + "；实付：" + money(order.getTotalAmount()) + "；收货地址：" + order.getReceiverAddress()
                + "；物流：" + order.getLogisticsCompany() + "/" + order.getLogisticsNo();
    }

    private class CustomerServiceContext {
        private final AiFlowRequest request;
        private final UserDTO user;
        private final MallOrder order;
        private final MallProduct product;
        private final Voucher voucher;
        private final boolean orderRequested;
        private final boolean orderDenied;

        private CustomerServiceContext(AiFlowRequest request, UserDTO user, MallOrder order, MallProduct product,
                                       Voucher voucher, boolean orderRequested, boolean orderDenied) {
            this.request = request;
            this.user = user;
            this.order = order;
            this.product = product;
            this.voucher = voucher;
            this.orderRequested = orderRequested;
            this.orderDenied = orderDenied;
        }

        private boolean shouldAnswerByRule(String question) {
            String normalizedQuestion = StrUtil.blankToDefault(question, "").toLowerCase();
            if (orderRequested || orderDenied || isPersonalOrderQuestion(normalizedQuestion, request)) {
                return true;
            }
            if (order != null) {
                return true;
            }
            if (product != null && containsAny(normalizedQuestion, "库存", "还有", "能买", "优惠", "优惠券", "券")) {
                return true;
            }
            return containsAny(normalizedQuestion, "退款", "退货", "售后", "物流", "快递", "发货",
                    "库存", "还有", "能买", "优惠", "优惠券", "券", "满减", "抵扣")
                    || voucher != null && containsAny(normalizedQuestion, "优惠", "优惠券", "券", "满减", "抵扣");
        }

        private String scenario() {
            if (request != null && StrUtil.isNotBlank(request.getScenario())) {
                return request.getScenario();
            }
            if (order != null || orderRequested) {
                return "order";
            }
            if (product != null) {
                return "product";
            }
            return "customer-service";
        }

        private boolean requiresLogin() {
            return user == null && (orderRequested
                    || StrUtil.equalsIgnoreCase(scenario(), "order")
                    || isPersonalOrderQuestion(requestQuestion(), request));
        }

        private AiCustomerServiceResponse.ContextSummary toSummary() {
            AiCustomerServiceResponse.ContextSummary summary = new AiCustomerServiceResponse.ContextSummary();
            summary.setLoggedIn(user != null);
            summary.setPageScenario(scenario());
            summary.setOrderRequested(orderRequested);
            summary.setOrderDenied(orderDenied);
            summary.setOrderId(order == null ? requestOrderId() : order.getId());
            summary.setOrderStatus(order == null ? null : mallOrderStatus(order.getStatus()));
            summary.setProductId(product == null ? requestProductId() : product.getId());
            summary.setProductTitle(product == null ? null : product.getTitle());
            summary.setProductStock(product == null ? null : product.getStock());
            summary.setVoucherId(voucher == null ? requestVoucherId() : voucher.getId());
            summary.setVoucherTitle(voucher == null ? null : voucher.getTitle());
            return summary;
        }

        private List<AiCustomerServiceResponse.Action> actions() {
            List<AiCustomerServiceResponse.Action> actions = new ArrayList<>();
            if (requiresLogin()) {
                actions.add(action("login", "去登录", null, null, null));
                return actions;
            }
            if (orderDenied) {
                actions.add(action("openOrders", "打开我的订单", null, null, null));
                return actions;
            }
            if (order != null) {
                actions.add(action("openOrders", "查看订单", order.getId(), order.getProductId(), order.getVoucherId()));
                if (isStatus(order, MallOrderServiceImpl.STATUS_PENDING_PAY)) {
                    actions.add(action("payOrder", "去支付", order.getId(), order.getProductId(), order.getVoucherId()));
                    actions.add(action("cancelOrder", "取消订单", order.getId(), order.getProductId(), order.getVoucherId()));
                }
                if (isStatus(order, MallOrderServiceImpl.STATUS_PAID, MallOrderServiceImpl.STATUS_PENDING_SHIP, MallOrderServiceImpl.STATUS_SHIPPED)) {
                    actions.add(action("applyRefund", "申请退款", order.getId(), order.getProductId(), order.getVoucherId()));
                }
                if (isStatus(order, MallOrderServiceImpl.STATUS_SHIPPED)) {
                    actions.add(action("viewLogistics", "查看物流", order.getId(), order.getProductId(), order.getVoucherId()));
                }
            } else if (user != null && StrUtil.equalsIgnoreCase(scenario(), "order")) {
                actions.add(action("openOrders", "选择订单", null, null, null));
            }
            if (product != null) {
                actions.add(action("openProduct", "查看商品", null, product.getId(), voucher == null ? null : voucher.getId()));
            }
            return actions;
        }

        private AiCustomerServiceResponse.Action action(String code, String label, Long orderId, Long productId, Long voucherId) {
            AiCustomerServiceResponse.Action action = new AiCustomerServiceResponse.Action();
            action.setCode(code);
            action.setLabel(label);
            action.setOrderId(orderId);
            action.setProductId(productId);
            action.setVoucherId(voucherId);
            return action;
        }

        private Long requestOrderId() {
            return request == null ? null : request.getOrderId();
        }

        private Long requestProductId() {
            return request == null ? null : request.getProductId();
        }

        private Long requestVoucherId() {
            return request == null ? null : request.getVoucherId();
        }

        private String requestQuestion() {
            return StrUtil.blankToDefault(request == null ? null : request.getQuery(), "").toLowerCase();
        }
    }
}

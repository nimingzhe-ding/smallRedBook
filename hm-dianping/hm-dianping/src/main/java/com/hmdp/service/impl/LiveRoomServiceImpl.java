package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.LiveRoom;
import com.hmdp.entity.LiveRoomMessage;
import com.hmdp.entity.LiveRoomProduct;
import com.hmdp.entity.MallProduct;
import com.hmdp.entity.Merchant;
import com.hmdp.enums.ContentType;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.LiveRoomMapper;
import com.hmdp.mapper.LiveRoomMessageMapper;
import com.hmdp.mapper.LiveRoomProductMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ILiveRoomService;
import com.hmdp.service.IMallProductService;
import com.hmdp.service.IMerchantService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 直播间服务。
 * 第一版先打通直播状态、商品橱窗、互动消息和回放转视频内容的主链路。
 */
@Service
public class LiveRoomServiceImpl extends ServiceImpl<LiveRoomMapper, LiveRoom> implements ILiveRoomService {

    public static final int STATUS_PREVIEW = 0;
    public static final int STATUS_LIVING = 1;
    public static final int STATUS_ENDED = 2;

    @Resource
    private IMerchantService merchantService;

    @Resource
    private IMallProductService productService;

    @Resource
    private IBlogService blogService;

    @Resource
    private LiveRoomProductMapper liveRoomProductMapper;

    @Resource
    private LiveRoomMessageMapper liveRoomMessageMapper;

    @Override
    public Result listPublic(Integer status, Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        var wrapper = query();
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.last("ORDER BY CASE status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE 2 END ASC, start_time DESC, create_time DESC");
        Page<LiveRoom> page = wrapper.page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        List<LiveRoom> rooms = attachProducts(page.getRecords());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rooms);
        result.put("total", page.getTotal());
        result.put("hasMore", page.getCurrent() < page.getPages());
        return Result.ok(result);
    }

    @Override
    public Result detail(Long roomId) {
        LiveRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "直播间不存在");
        }
        attachProducts(List.of(room));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("room", room);
        result.put("messages", loadMessages(roomId, 60));
        return Result.ok(result);
    }

    @Override
    public Result listMine(Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        var wrapper = query()
                .eq("merchant_id", merchant.getId())
                .orderByDesc("create_time");
        if (status != null) {
            wrapper.eq("status", status);
        }
        return Result.ok(attachProducts(wrapper.list()));
    }

    @Override
    @Transactional
    public Result createRoom(LiveRoom room) {
        Merchant merchant = currentMerchant();
        UserDTO user = UserHolder.getUser();
        if (merchant == null || user == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        if (room == null || StrUtil.isBlank(room.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "直播标题不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        room.setId(null);
        room.setMerchantId(merchant.getId());
        room.setAnchorUserId(user.getId());
        room.setTitle(StrUtil.sub(room.getTitle().trim(), 0, 80));
        room.setStatus(room.getStatus() == null ? STATUS_PREVIEW : room.getStatus());
        room.setOnlineCount(0);
        room.setLiked(0);
        room.setCreateTime(now);
        room.setUpdateTime(now);
        save(room);
        saveProductIds(room.getId(), room.getProductIds(), merchant.getId());
        return detail(room.getId());
    }

    @Override
    public Result openRoom(Long roomId) {
        LiveRoom room = checkMerchantRoom(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "直播间不存在或无权限");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_LIVING)
                .set("start_time", room.getStartTime() == null ? now : room.getStartTime())
                .set("update_time", now)
                .eq("id", roomId)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "开播失败");
        }
        return detail(roomId);
    }

    @Override
    @Transactional
    public Result closeRoom(Long roomId, String replayVideoUrl) {
        LiveRoom room = checkMerchantRoom(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "直播间不存在或无权限");
        }
        String replay = StrUtil.blankToDefault(StrUtil.trim(replayVideoUrl), room.getReplayVideoUrl());
        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_ENDED)
                .set("replay_video_url", replay)
                .set("end_time", now)
                .set("update_time", now)
                .eq("id", roomId)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "关播失败");
        }
        if (StrUtil.isNotBlank(replay)) {
            bindReplayVideo(room, replay, now);
        }
        return detail(roomId);
    }

    @Override
    @Transactional
    public Result updateProducts(Long roomId, List<LiveRoomProduct> products) {
        LiveRoom room = checkMerchantRoom(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "直播间不存在或无权限");
        }
        liveRoomProductMapper.delete(new LambdaQueryWrapper<LiveRoomProduct>().eq(LiveRoomProduct::getRoomId, roomId));
        saveProducts(roomId, products == null ? List.of() : products, room.getMerchantId());
        return detail(roomId);
    }

    @Override
    public Result sendMessage(Long roomId, LiveRoomMessage message) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        LiveRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "直播间不存在");
        }
        if (room.getStatus() == null || room.getStatus() != STATUS_LIVING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "直播间未开播");
        }
        if (message == null || StrUtil.isBlank(message.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "弹幕内容不能为空");
        }
        LiveRoomMessage saved = new LiveRoomMessage()
                .setRoomId(roomId)
                .setUserId(user.getId())
                .setType(StrUtil.blankToDefault(message.getType(), "danmaku"))
                .setContent(StrUtil.sub(message.getContent().trim(), 0, 80))
                .setLiked(0)
                .setStatus(0)
                .setCreateTime(LocalDateTime.now());
        liveRoomMessageMapper.insert(saved);
        return Result.ok(saved);
    }

    @Override
    public Result messages(Long roomId) {
        return Result.ok(loadMessages(roomId, 80));
    }

    @Override
    public Result likeRoom(Long roomId) {
        boolean updated = update()
                .setSql("liked = IFNULL(liked, 0) + 1")
                .set("update_time", LocalDateTime.now())
                .eq("id", roomId)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "直播间不存在");
        }
        return detail(roomId);
    }

    @Override
    public Result updateOnline(Long roomId, Integer delta) {
        int value = delta == null ? 0 : delta;
        boolean updated = update()
                .setSql("online_count = GREATEST(IFNULL(online_count, 0) + " + value + ", 0)")
                .set("update_time", LocalDateTime.now())
                .eq("id", roomId)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "直播间不存在");
        }
        return detail(roomId);
    }

    @Override
    public boolean canChat(Long roomId) {
        if (roomId == null) {
            return false;
        }
        LiveRoom room = getById(roomId);
        return room != null && room.getStatus() != null && room.getStatus() == STATUS_LIVING;
    }

    private List<LiveRoom> attachProducts(List<LiveRoom> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(LiveRoom::getId).toList();
        List<LiveRoomProduct> relations = liveRoomProductMapper.selectList(
                new LambdaQueryWrapper<LiveRoomProduct>()
                        .in(LiveRoomProduct::getRoomId, roomIds)
                        .orderByAsc(LiveRoomProduct::getSort)
                        .orderByAsc(LiveRoomProduct::getId));
        if (relations.isEmpty()) {
            rooms.forEach(room -> room.setProducts(List.of()));
            return rooms;
        }
        List<Long> productIds = relations.stream().map(LiveRoomProduct::getProductId).distinct().toList();
        Map<Long, MallProduct> productMap = productService.listByIds(productIds).stream()
                .collect(Collectors.toMap(MallProduct::getId, Function.identity(), (first, second) -> first));
        Map<Long, List<LiveRoomProduct>> relationMap = new LinkedHashMap<>();
        for (LiveRoomProduct relation : relations) {
            relation.setProduct(productMap.get(relation.getProductId()));
            relationMap.computeIfAbsent(relation.getRoomId(), key -> new ArrayList<>()).add(relation);
        }
        rooms.forEach(room -> room.setProducts(relationMap.getOrDefault(room.getId(), List.of())));
        return rooms;
    }

    private List<LiveRoomMessage> loadMessages(Long roomId, int limit) {
        return liveRoomMessageMapper.selectList(
                new LambdaQueryWrapper<LiveRoomMessage>()
                        .eq(LiveRoomMessage::getRoomId, roomId)
                        .eq(LiveRoomMessage::getStatus, 0)
                        .orderByDesc(LiveRoomMessage::getCreateTime)
                        .last("limit " + limit));
    }

    private void saveProductIds(Long roomId, List<Long> productIds, Long merchantId) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        List<LiveRoomProduct> products = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            products.add(new LiveRoomProduct()
                    .setProductId(productIds.get(i))
                    .setSort(i)
                    .setExplaining(i == 0));
        }
        saveProducts(roomId, products, merchantId);
    }

    private void saveProducts(Long roomId, List<LiveRoomProduct> products, Long merchantId) {
        LocalDateTime now = LocalDateTime.now();
        int sort = 0;
        for (LiveRoomProduct product : products) {
            if (product.getProductId() == null) {
                continue;
            }
            MallProduct mallProduct = productService.getById(product.getProductId());
            if (mallProduct == null || !merchantId.equals(mallProduct.getMerchantId())) {
                continue;
            }
            product.setId(null);
            product.setRoomId(roomId);
            product.setSort(product.getSort() == null ? sort : product.getSort());
            product.setExplaining(Boolean.TRUE.equals(product.getExplaining()));
            product.setCreateTime(now);
            product.setUpdateTime(now);
            liveRoomProductMapper.insert(product);
            sort++;
        }
    }

    private void bindReplayVideo(LiveRoom room, String replay, LocalDateTime now) {
        Blog blog = room.getBlogId() == null ? null : blogService.getById(room.getBlogId());
        if (blog == null) {
            blog = new Blog()
                    .setUserId(room.getAnchorUserId())
                    .setShopId(null)
                    .setTitle(room.getTitle())
                    .setImages(room.getCoverUrl())
                    .setVideoUrl(replay)
                    .setContent("直播回放：" + room.getTitle())
                    .setLiked(0)
                    .setComments(0)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            blog.setContentType(ContentType.VIDEO.name());
            blogService.save(blog);
            update().set("blog_id", blog.getId()).eq("id", room.getId()).update();
            return;
        }
        blog.setVideoUrl(replay);
        blog.setContentType(ContentType.VIDEO.name());
        blog.setUpdateTime(now);
        blogService.updateById(blog);
    }

    private LiveRoom checkMerchantRoom(Long roomId) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            return null;
        }
        LiveRoom room = getById(roomId);
        if (room == null || !merchant.getId().equals(room.getMerchantId())) {
            return null;
        }
        return room;
    }

    private Merchant currentMerchant() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return null;
        }
        return merchantService.query().eq("user_id", user.getId()).one();
    }
}

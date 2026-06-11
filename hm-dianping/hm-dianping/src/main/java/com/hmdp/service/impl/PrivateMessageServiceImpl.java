package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.PrivateConversationDTO;
import com.hmdp.dto.PrivateConversationRequest;
import com.hmdp.dto.PrivateMessageDTO;
import com.hmdp.dto.PrivateMessageRequest;
import com.hmdp.dto.PrivateMessageUserDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.PrivateConversation;
import com.hmdp.entity.PrivateMessage;
import com.hmdp.entity.PrivateMessageOutbox;
import com.hmdp.entity.User;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.config.RequestKeySupport;
import com.hmdp.mapper.PrivateConversationMapper;
import com.hmdp.mapper.PrivateMessageMapper;
import com.hmdp.mapper.PrivateMessageOutboxMapper;
import com.hmdp.privatemessage.PrivateMessageOutboxStatus;
import com.hmdp.privatemessage.PrivateMessagePushEvent;
import com.hmdp.service.IPrivateMessageService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage>
        implements IPrivateMessageService {

    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final int MAX_SEARCH_SIZE = 10;

    @Resource
    private PrivateConversationMapper conversationMapper;
    @Resource
    private IUserService userService;
    @Resource
    private PrivateMessageOutboxMapper outboxMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result conversations() {
        Long userId = requireUserId();
        List<PrivateConversation> conversations = conversationMapper.selectList(
                new QueryWrapper<PrivateConversation>()
                        .and(wrapper -> wrapper.eq("user_low_id", userId).or().eq("user_high_id", userId))
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 100"));
        Map<Long, User> peerMap = loadPeerUsers(conversations, userId);
        List<PrivateConversationDTO> result = conversations.stream()
                .map(conversation -> toConversationDTO(conversation, userId, peerMap.get(peerId(conversation, userId))))
                .toList();
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result openConversation(PrivateConversationRequest request) {
        Long userId = requireUserId();
        Long peerUserId = request == null ? null : request.getPeerUserId();
        if (peerUserId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "用户ID不能为空");
        }
        if (userId.equals(peerUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能给自己发私信");
        }
        User peer = userService.getById(peerUserId);
        if (peer == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "用户不存在");
        }
        PrivateConversation conversation = findByPair(userId, peerUserId);
        if (conversation == null) {
            conversation = createConversation(userId, peerUserId);
        }
        return Result.ok(toConversationDTO(conversation, userId, peer));
    }

    @Override
    public Result messages(Long conversationId, Long beforeId, Integer limit) {
        Long userId = requireUserId();
        PrivateConversation conversation = requireConversation(conversationId, userId);
        int pageSize = normalizeLimit(limit);
        QueryWrapper<PrivateMessage> wrapper = new QueryWrapper<PrivateMessage>()
                .eq("conversation_id", conversation.getId())
                .lt(beforeId != null && beforeId > 0, "id", beforeId)
                .orderByDesc("id")
                .last("limit " + pageSize);
        List<PrivateMessage> messages = new ArrayList<>(list(wrapper));
        Collections.reverse(messages);
        return Result.ok(messages.stream().map(message -> toMessageDTO(message, userId)).toList());
    }

    @Override
    @Transactional
    public Result sendMessage(Long conversationId, PrivateMessageRequest request) {
        Long userId = requireUserId();
        return Result.ok(doSendMessage(conversationId, userId, request));
    }

    @Override
    @Transactional
    public PrivateMessageDTO sendMessageFromUser(Long conversationId, Long senderId, PrivateMessageRequest request) {
        if (senderId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return doSendMessage(conversationId, senderId, request);
    }

    private PrivateMessageDTO doSendMessage(Long conversationId, Long userId, PrivateMessageRequest request) {
        PrivateConversation conversation = requireConversation(conversationId, userId);
        ensureRequestIdempotent(conversationId, userId, request);
        String content = StrUtil.trim(request == null ? null : request.getContent());
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "消息不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息不能超过500字");
        }
        Long receiverId = peerId(conversation, userId);
        LocalDateTime now = LocalDateTime.now();
        PrivateMessage message = new PrivateMessage()
                .setConversationId(conversation.getId())
                .setSenderId(userId)
                .setReceiverId(receiverId)
                .setContent(content)
                .setReadFlag(false)
                .setCreateTime(now);
        save(message);
        UpdateWrapper<PrivateConversation> update = new UpdateWrapper<PrivateConversation>()
                .eq("id", conversation.getId())
                .set("last_message_id", message.getId())
                .set("last_message_content", content)
                .set("last_message_time", now)
                .set("update_time", now)
                .setSql(receiverId.equals(conversation.getUserLowId())
                        ? "low_unread_count = low_unread_count + 1"
                        : "high_unread_count = high_unread_count + 1");
        conversationMapper.update(null, update);
        PrivateMessageDTO senderView = toMessageDTO(message, userId);
        savePushOutbox(message, request == null ? null : request.getRequestId());
        return senderView;
    }

    private void savePushOutbox(PrivateMessage message, String requestId) {
        LocalDateTime now = LocalDateTime.now();
        String eventKey = "private-message:push:message:" + message.getId();
        PrivateMessagePushEvent event = new PrivateMessagePushEvent()
                .setEventKey(eventKey)
                .setMessageId(message.getId())
                .setConversationId(message.getConversationId())
                .setSenderId(message.getSenderId())
                .setReceiverId(message.getReceiverId())
                .setRequestId(requestId)
                .setCreateTime(now);
        try {
            outboxMapper.insert(new PrivateMessageOutbox()
                    .setEventKey(eventKey)
                    .setMessageId(message.getId())
                    .setConversationId(message.getConversationId())
                    .setSenderId(message.getSenderId())
                    .setReceiverId(message.getReceiverId())
                    .setRequestId(requestId)
                    .setPayload(objectMapper.writeValueAsString(event))
                    .setStatus(PrivateMessageOutboxStatus.PENDING)
                    .setRetryCount(0)
                    .setMaxRetry(12)
                    .setNextRetryTime(now)
                    .setCreateTime(now)
                    .setUpdateTime(now));
        } catch (Exception e) {
            throw new IllegalStateException("Create private message outbox failed, messageId=" + message.getId(), e);
        }
    }

    private void ensureRequestIdempotent(Long conversationId, Long userId, PrivateMessageRequest request) {
        if (request == null || StrUtil.isBlank(request.getRequestId())) {
            return;
        }
        String raw = "private-message:" + conversationId + ":" + userId + ":" + request.getRequestId().trim();
        String key = RedisConstants.IDEMPOTENT_KEY + RequestKeySupport.sha256(raw);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.REPEAT_OPERATION, "Duplicate private message request");
        }
    }

    @Override
    @Transactional
    public Result markRead(Long conversationId) {
        Long userId = requireUserId();
        PrivateConversation conversation = requireConversation(conversationId, userId);
        update()
                .set("read_flag", true)
                .eq("conversation_id", conversation.getId())
                .eq("receiver_id", userId)
                .eq("read_flag", false)
                .update();
        conversationMapper.update(null, new UpdateWrapper<PrivateConversation>()
                .eq("id", conversation.getId())
                .set(userId.equals(conversation.getUserLowId()), "low_unread_count", 0)
                .set(userId.equals(conversation.getUserHighId()), "high_unread_count", 0)
                .set("update_time", conversation.getUpdateTime()));
        return Result.ok();
    }

    @Override
    public Result unreadCount() {
        Long userId = requireUserId();
        List<PrivateConversation> conversations = conversationMapper.selectList(
                new QueryWrapper<PrivateConversation>()
                        .select("user_low_id", "user_high_id", "low_unread_count", "high_unread_count")
                        .and(wrapper -> wrapper.eq("user_low_id", userId).or().eq("user_high_id", userId)));
        long count = conversations.stream()
                .mapToLong(conversation -> unreadCountOf(conversation, userId))
                .sum();
        return Result.ok(Map.of("count", count));
    }

    @Override
    public Result searchUsers(String keyword) {
        Long userId = requireUserId();
        String value = StrUtil.trim(keyword);
        if (StrUtil.isBlank(value)) {
            return Result.ok(List.of());
        }
        QueryWrapper<User> wrapper = new QueryWrapper<User>()
                .select("id", "nick_name", "icon")
                .ne("id", userId)
                .and(query -> {
                    Long exactUserId = parseUserId(value);
                    if (exactUserId != null) {
                        query.eq("id", exactUserId).or();
                    }
                    query.like("nick_name", value);
                })
                .orderByDesc("id")
                .last("limit " + MAX_SEARCH_SIZE);
        List<PrivateMessageUserDTO> users = userService.list(wrapper).stream()
                .map(this::toUserDTO)
                .toList();
        return Result.ok(users);
    }

    private Long requireUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return user.getId();
    }

    private PrivateConversation requireConversation(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "会话ID不能为空");
        }
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "会话不存在");
        }
        if (!isParticipant(conversation, userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该会话");
        }
        return conversation;
    }

    private PrivateConversation createConversation(Long userId, Long peerUserId) {
        Long low = Math.min(userId, peerUserId);
        Long high = Math.max(userId, peerUserId);
        LocalDateTime now = LocalDateTime.now();
        PrivateConversation conversation = new PrivateConversation()
                .setUserLowId(low)
                .setUserHighId(high)
                .setLowUnreadCount(0)
                .setHighUnreadCount(0)
                .setCreateTime(now)
                .setUpdateTime(now);
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException e) {
            PrivateConversation existing = findByPair(userId, peerUserId);
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }

    private PrivateConversation findByPair(Long userId, Long peerUserId) {
        Long low = Math.min(userId, peerUserId);
        Long high = Math.max(userId, peerUserId);
        return conversationMapper.selectOne(new QueryWrapper<PrivateConversation>()
                .eq("user_low_id", low)
                .eq("user_high_id", high)
                .last("limit 1"));
    }

    private Map<Long, User> loadPeerUsers(List<PrivateConversation> conversations, Long userId) {
        List<Long> ids = conversations.stream()
                .map(conversation -> peerId(conversation, userId))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
    }

    private PrivateConversationDTO toConversationDTO(PrivateConversation conversation, Long userId, User peer) {
        PrivateConversationDTO dto = new PrivateConversationDTO();
        Long peerUserId = peerId(conversation, userId);
        dto.setId(conversation.getId());
        dto.setPeerUserId(peerUserId);
        dto.setLastMessageContent(conversation.getLastMessageContent());
        dto.setLastMessageTime(conversation.getLastMessageTime());
        dto.setUnreadCount(unreadCountOf(conversation, userId));
        dto.setUpdatedAt(conversation.getUpdateTime());
        if (peer != null) {
            PrivateMessageUserDTO userDTO = toUserDTO(peer);
            dto.setPeerUser(userDTO);
            dto.setPeerNickName(userDTO.getNickName());
            dto.setPeerIcon(userDTO.getIcon());
        } else {
            dto.setPeerNickName("用户 " + peerUserId);
            dto.setPeerIcon("");
        }
        return dto;
    }

    private PrivateMessageDTO toMessageDTO(PrivateMessage message, Long userId) {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        dto.setReceiverId(message.getReceiverId());
        dto.setContent(message.getContent());
        dto.setReadFlag(message.getReadFlag());
        dto.setIsMe(userId.equals(message.getSenderId()));
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }

    private PrivateMessageUserDTO toUserDTO(User user) {
        PrivateMessageUserDTO dto = new PrivateMessageUserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
    }

    private boolean isParticipant(PrivateConversation conversation, Long userId) {
        return userId.equals(conversation.getUserLowId()) || userId.equals(conversation.getUserHighId());
    }

    private Long peerId(PrivateConversation conversation, Long userId) {
        return userId.equals(conversation.getUserLowId())
                ? conversation.getUserHighId()
                : conversation.getUserLowId();
    }

    private int unreadCountOf(PrivateConversation conversation, Long userId) {
        Integer count = userId.equals(conversation.getUserLowId())
                ? conversation.getLowUnreadCount()
                : conversation.getHighUnreadCount();
        return count == null ? 0 : count;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }

    private Long parseUserId(String value) {
        if (StrUtil.isBlank(value) || !value.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

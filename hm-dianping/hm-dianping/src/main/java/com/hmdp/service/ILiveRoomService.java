package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.LiveRoom;
import com.hmdp.entity.LiveRoomMessage;
import com.hmdp.entity.LiveRoomProduct;

import java.util.List;

public interface ILiveRoomService extends IService<LiveRoom> {
    Result listPublic(Integer status, Integer current);

    Result detail(Long roomId);

    Result listMine(Integer status);

    Result createRoom(LiveRoom room);

    Result openRoom(Long roomId);

    Result closeRoom(Long roomId, String replayVideoUrl);

    Result updateProducts(Long roomId, List<LiveRoomProduct> products);

    Result sendMessage(Long roomId, LiveRoomMessage message);

    Result messages(Long roomId);

    Result likeRoom(Long roomId);

    Result updateOnline(Long roomId, Integer delta);

    boolean canChat(Long roomId);
}

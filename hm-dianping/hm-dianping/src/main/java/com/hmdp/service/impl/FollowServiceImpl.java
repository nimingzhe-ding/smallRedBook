package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.FollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserServiceImpl userService;
    @Resource
    private IUserNotificationService notificationService;
    /**
     * 关注或取关用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断关注还是取关
        if(isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
                User actor = userService.getById(userId);
                notificationService.notifyUser(followUserId, userId, "FOLLOW", "你有新的关注者",
                        (actor == null ? "有用户" : actor.getNickName()) + " 关注了你。", null, null);
            }
        }else{
            //取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //查询是否关注
        long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long followUserId) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //求交集
        String key2 = "follows:" + followUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}


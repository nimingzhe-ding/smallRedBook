package com.xhs.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.dto.LoginFormDTO;
import com.xhs.dto.LoginResponseDTO;
import com.xhs.dto.Result;
import com.xhs.dto.UserDTO;
import com.xhs.entity.User;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.mapper.UserMapper;
import com.xhs.service.IUserService;
import com.xhs.utils.RedisConstants;
import com.xhs.utils.RegexUtils;
import com.xhs.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Value("${xiaohongshu.auth.debug-code-response:true}")
    private boolean debugCodeResponse;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        phone = normalizePhone(phone);
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误！");
        }
        String cooldownKey = RedisConstants.LOGIN_CODE_COOLDOWN_KEY + phone;
        Long ttl = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ttl + " 秒后再获取验证码");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到redis中,set key value EX 120 NX
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", RedisConstants.LOGIN_CODE_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        //5.发送验证码，
        log.debug("假装发送短信验证码成功，验证码：{}", code);
        //6.返回ok
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ttlSeconds", RedisConstants.LOGIN_CODE_TTL * 60);
        result.put("cooldownSeconds", RedisConstants.LOGIN_CODE_COOLDOWN_SECONDS);
        if (debugCodeResponse) {
            result.put("debugCode", code);
        }
        return Result.ok(result);
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (loginForm == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "登录参数不能为空");
        }
        //1.校验手机号
        String phone = normalizePhone(loginForm.getPhone());
        if (RegexUtils.isPhoneInvalid(phone)) {
            //1.2.如果不符合，返回错误信息
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误！");
        }
        String failKey = RedisConstants.LOGIN_FAIL_KEY + phone;
        Long failTtl = stringRedisTemplate.getExpire(failKey, TimeUnit.SECONDS);
        String failCountText = stringRedisTemplate.opsForValue().get(failKey);
        long failCount = parseLong(failCountText);
        if (failTtl != null && failTtl > RedisConstants.LOGIN_FAIL_TTL * 60
                && failCount >= RedisConstants.LOGIN_FAIL_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误次数过多，请 " + failTtl + " 秒后再试");
        }
        //2.校验验证码,从redis进行获取
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        String code = StrUtil.trim(loginForm.getCode());
        if(cacheCode==null || !cacheCode.equals(code)){
            recordLoginFailure(failKey);
            //3.不一致，报错
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误！");
        }
        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if(user==null){
            //6.不存在，创建新用户并保存
           user = creatUserWithPhone(phone);
        }
        //7.保存用户信息到session中
       //注意导入正确的工具包
        //生成一个token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转换为hashmap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //保存到redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);
        //设置token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_COOLDOWN_KEY + phone);
        stringRedisTemplate.delete(failKey);
        //返回token给客户端
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setExpiresInSeconds(RedisConstants.LOGIN_USER_TTL * 60);
        response.setUser(userDTO);
        return Result.ok(response);


    }

    private User creatUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户"+RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }

    private void recordLoginFailure(String failKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count == null) {
            return;
        }
        if (count == 1) {
            stringRedisTemplate.expire(failKey, RedisConstants.LOGIN_FAIL_TTL, TimeUnit.MINUTES);
        }
        if (count >= RedisConstants.LOGIN_FAIL_MAX) {
            stringRedisTemplate.expire(failKey, RedisConstants.LOGIN_FAIL_LOCK_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String normalizePhone(String phone) {
        return StrUtil.trim(phone);
    }

    private long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 登出功能
     * @param token
     * @return
     */
    @Override
    public Result logout(String token) {
        String key = RedisConstants.LOGIN_USER_KEY+token;
        stringRedisTemplate.delete(key);
        UserHolder.removeUser();
        return Result.ok();

    }

    /**
     * 签到功能
     * @return
     */
    @Override
    public Result sign() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok()  ;
    }

    /**
     * 签到统计
     * @return
     */
    @Override
    public Result signCount() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截止今天为止的所有签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(result==null|| result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==null || num==0){
            return Result.ok(0);
        }
        //循环遍历
        int count =0;
        while(true){
            //让这个数字与1做与运算，得到最后一位
            if((num & 1) ==0){
    break;
            }else {
                count++;
            }num>>>=1;//无符号右移
        }
        return Result.ok(count);
    }
}


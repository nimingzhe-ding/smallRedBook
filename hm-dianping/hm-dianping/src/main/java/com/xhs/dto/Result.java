package com.xhs.dto;

import com.xhs.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private Integer code;
    private String errorMsg;
    private Object data;
    private Long total;

    public Result(Boolean success, Integer code, String errorMsg, Object data) {
        this.success = success;
        this.code = code;
        this.errorMsg = errorMsg;
        this.data = data;
    }

    public static Result ok(){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, ErrorCode.BAD_REQUEST.getCode(), errorMsg, null, null);
    }
    public static Result fail(ErrorCode errorCode){
        return new Result(false, errorCode.getCode(), errorCode.getMsg(), null, null);
    }
    public static Result fail(ErrorCode errorCode, String detail){
        return new Result(false, errorCode.getCode(), detail, null, null);
    }
}

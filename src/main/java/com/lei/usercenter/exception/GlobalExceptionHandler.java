package com.lei.usercenter.exception;

import com.lei.usercenter.common.BaseResponse;
import com.lei.usercenter.common.ErrorCode;
import com.lei.usercenter.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        //log.error("捕获到businessException异常: {}", e.getMessage(), e);
        return ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription());
    }


    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        //log.error("捕获到runtimeException异常", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage(), "");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        if (e.getMessage().contains("Invalid character found in the request target")) {
            return ResponseEntity.badRequest()
                    .body("请求参数格式错误，请刷新页面重试");
        }
        return ResponseEntity.badRequest().body("参数错误");
    }
}

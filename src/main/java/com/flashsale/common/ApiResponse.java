package com.flashsale.common;

/**
 * 对外接口统一响应。
 *
 * @param success 请求是否成功
 * @param data 成功时的业务数据
 * @param message 面向调用方的简短说明
 */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "success");
    }

    public static ApiResponse<Void> failure(String message) {
        return new ApiResponse<>(false, null, message);
    }
}

package com.edu.questionbank.dto;

/**
 * 通用 API 回應包裝類
 *
 * @param <T> 回應資料型別
 */
public class ApiResponse<T> {

    private boolean success;
    private String  message;
    private T       data;
    private int     total;  // 可選：供分頁使用

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    public static <T> ApiResponse<T> ok(T data, int total) {
        ApiResponse<T> r = new ApiResponse<>(true, "success", data);
        r.total = total;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // ── Getters & Setters ──────────────────────────────────────

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}

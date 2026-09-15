package com.nexus.campus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.util.TraceIds;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

@Data
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    /**
     * Present only on a server-side failure, and only so that what the user can
     * read, what the header carries and what the log holds are the same string.
     * A success envelope keeps its exact previous shape.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    private ApiResponse() {}

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = "Operation completed successfully.";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.message = message;
        response.data = data;
        return response;
    }

    public static ApiResponse<Void> successMessage(String message) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.code = 200;
        response.message = message;
        return response;
    }

    /**
     * The only way to build a failure envelope: {@code code} is read off the
     * status, so an envelope that disagrees with the HTTP line it travelled on
     * is not expressible. Before this, callers passed a bare int and thirty-three
     * of them passed one that the transport did not match.
     */
    public static <T> ApiResponse<T> error(HttpStatus status, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = status.value();
        response.message = message;
        if (status.is5xxServerError()) {
            response.traceId = TraceIds.current();
        }
        return response;
    }

    /** Build the failure envelope a {@link BusinessException} describes. */
    public static <T> ApiResponse<T> error(BusinessException failure) {
        return error(failure.getStatus(), failure.getMessage());
    }

    /**
     * Transitional. The sixteen controller sites that pass a literal still answer
     * {@code 200} over the wire while claiming otherwise in the body, and deleting
     * this overload before they are migrated would leave the branch uncompilable
     * for several commits. It goes away in the same commit that makes an unmigrated
     * site a compile error, which is the check this whole ticket is built to have.
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        if (code >= 500) {
            response.traceId = TraceIds.current();
        }
        return response;
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, message);
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message);
    }
}

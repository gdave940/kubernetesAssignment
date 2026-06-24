package NAGP.kubernetesAssignment.model;

import lombok.Data;

import java.sql.Timestamp;
import java.time.Instant;

@Data
public class ApiResponse<T> {

    private String message;
    private boolean status;
    private Timestamp timestamp;
    //@JsonRawValue
    private T data;

    public ApiResponse() {
    }

    private ApiResponse(String message, boolean status, Timestamp timestamp, T data) {
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.data = data;
    }

    public ApiResponse<T> successResponse(T data, String message) {
        return new ApiResponse<T>(message, true, Timestamp.from(Instant.now()), data);
    }

    public ApiResponse<T> failureResponse(String message) {
        return new ApiResponse<T>(message, false, Timestamp.from(Instant.now()), null);
    }

}

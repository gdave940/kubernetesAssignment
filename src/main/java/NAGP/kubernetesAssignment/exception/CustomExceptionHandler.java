package NAGP.kubernetesAssignment.exception;

import NAGP.kubernetesAssignment.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class CustomExceptionHandler {

    @ExceptionHandler(value = {ItemNotFoundException.class})
    public ResponseEntity<ApiResponse<Object>> ItemNotFoundException(ItemNotFoundException e) {
        log.error(e.getMessage());
        ApiResponse<Object> apiResponse = new ApiResponse<>();
        return new ResponseEntity<>(apiResponse.failureResponse(e.getMessage()), HttpStatus.NOT_FOUND);
    }

}

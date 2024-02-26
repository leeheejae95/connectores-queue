package com.fastcampus.flow.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class ApplicationException extends RuntimeException{

    private HttpStatus httpStatus;
    private String code;
    private String reason;
}

package com.becho.saas.common;

import org.springframework.http.HttpStatus;

/** Domain exception carrying an HTTP status + machine-readable code. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() { return status; }
  public String getCode() { return code; }

  public static ApiException notFound(String entity) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " not found");
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static ApiException forbidden() {
    return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }
}

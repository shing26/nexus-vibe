package com.nexus.campus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * A crash report sent by the browser reporter (FE-1). Every field is a
 * client-supplied string, so each one is size-capped here before the
 * controller ever sees it: the endpoint is unauthenticated and its only
 * output is a log line, and the caps are what stop a body from becoming
 * the log line.
 */
@Data
public class CrashReportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Which sink caught it: boundary (React), window (window.onerror), promise (unhandledrejection). */
    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "boundary|window|promise")
    private String kind;

    @NotBlank
    @Size(max = 500)
    private String message;

    @Size(max = 4000)
    private String stack;

    @Size(max = 4000)
    private String componentStack;

    @Size(max = 300)
    private String url;
}

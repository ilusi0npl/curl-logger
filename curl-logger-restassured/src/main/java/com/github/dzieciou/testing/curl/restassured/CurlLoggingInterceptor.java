package com.github.dzieciou.testing.curl.restassured;

import com.github.dzieciou.testing.curl.core.Options;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Logs each HTTP request as CURL command in "curl" log.
 */
public class CurlLoggingInterceptor implements HttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger("curl");

  private final Options options;

  private final RestAssuredHttp2Curl http2Curl;

  public CurlLoggingInterceptor(Options options) {
    this.options = options;
    http2Curl = new RestAssuredHttp2Curl(options);
  }

  private static void printStacktrace(StringBuffer sb) {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    for (StackTraceElement traceElement : trace) {
      sb.append("\tat ").append(traceElement).append(System.lineSeparator());
    }
  }

  @Override
  public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
    try {
      String curl = http2Curl.generateCurl(request);
      StringBuffer message = new StringBuffer(curl);
      if (options.canLogStacktrace()) {
        message.append(String.format("%n\tgenerated%n"));
        printStacktrace(message);
      }
      log.debug(message.toString());
    } catch (Exception e) {
      log.warn("Failed to generate CURL command for HTTP request", e);
    }
  }


}

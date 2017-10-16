package com.github.dzieciou.testing.curl.core;

import java.io.IOException;
import org.apache.http.HttpRequest;


public abstract class Http2Curl<RequestT> {

  protected final Options options;

  public Http2Curl(Options options) {
    this.options = options;
  }

  /**
   * Generates single-line CURL command for a given HTTP request.
   *
   * @param request HTTP request
   * @return CURL command
   * @throws Exception if failed to generate CURL command
   */
  public String generateCurl(RequestT request) throws Exception {

    CurlCommand curl = http2curl(request);
    options.getCurlUpdater().ifPresent(updater -> updater.accept(curl));
    return curl
        .asString(options.getTargetPlatform(), options.useShortForm(), options.printMultiliner());
  }

  @SuppressWarnings("deprecation")
  protected abstract CurlCommand http2curl(RequestT request)
      throws NoSuchFieldException, IllegalAccessException, IOException;
}

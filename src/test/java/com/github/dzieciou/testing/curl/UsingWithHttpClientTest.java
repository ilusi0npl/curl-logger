package com.github.dzieciou.testing.curl;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.github.valfirst.slf4jtest.TestLoggerFactory;
import java.io.IOException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.annotations.Test;

public class UsingWithHttpClientTest {

  private static HttpClient createHttpClient() {
    return HttpClientBuilder.create()
        .addInterceptorFirst(new CurlLoggingInterceptor(Options.builder()
            .targetPlatform(Platform.UNIX) // TO ease verifying output curl
            .build()))
        .build();
  }

  @Test(groups = "end-to-end-samples")
  public void testHttp() throws IOException {
    TestLoggerFactory.clearAll();
    HttpGet getRequest = new HttpGet("http://google.com");
    createHttpClient().execute(getRequest);
    assertThat(TestLoggerFactory.getAllLoggingEvents().get(0).getMessage(),
        equalTo("curl 'http://google.com/' --compressed --insecure --verbose"));
  }

  @Test(groups = "end-to-end-samples")
  public void testHttps() throws IOException {
    TestLoggerFactory.clearAll();
    HttpGet getRequest = new HttpGet("https://google.com");
    createHttpClient().execute(getRequest);
    assertThat(TestLoggerFactory.getAllLoggingEvents().get(0).getMessage(),
        equalTo("curl 'https://google.com/' --compressed --insecure --verbose"));
  }

}

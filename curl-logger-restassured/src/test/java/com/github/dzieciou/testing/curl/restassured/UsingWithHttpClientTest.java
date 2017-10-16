package com.github.dzieciou.testing.curl.restassured;


import com.github.dzieciou.testing.curl.core.Options;
import java.io.IOException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.annotations.Test;

public class UsingWithHttpClientTest {

  private static HttpClient createHttpClient() {
    return HttpClientBuilder.create()
        .addInterceptorFirst(new CurlLoggingInterceptor(Options.builder().build()))
        .build();
  }

  @Test(groups = "end-to-end-samples")
  public void test() throws IOException {
    HttpGet getRequest = new HttpGet("http://google.com");
    createHttpClient().execute(getRequest);
  }
}

package com.github.dzieciou.testing.curl.restassured;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.github.dzieciou.testing.curl.core.Options;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.mockserver.client.server.MockServerClient;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CurlLoggingRestAssuredConfigFactoryTest {

  private static final int MOCK_PORT = 9999;
  private static final String MOCK_HOST = "localhost";
  private static final String MOCK_BASE_URI = "http://" + MOCK_HOST;
  private MockServerClient mockServer;

  @BeforeClass
  public void setupMock() {
    mockServer = startClientAndServer(MOCK_PORT);
    mockServer.when(request()).respond(response().withStatusCode(200));
  }


  @Test
  public void shouldIncludeCurlInterceptorWhenCreatingConfig() {
    RestAssuredConfig updatedConfig = CurlLoggingRestAssuredConfigFactory.createConfig();
    AbstractHttpClient updateClientConfig = (AbstractHttpClient) updatedConfig.getHttpClientConfig()
        .httpClientInstance();
    assertThat(updateClientConfig, new ContainsRequestInterceptor(CurlLoggingInterceptor.class));
  }

  @Test
  public void shouldIncludeCurlInterceptorWhenUpdatingExistingConfig() {

    HttpClientConfig httpClientConfig = HttpClientConfig.httpClientConfig()
        .setParam("TestParam", "TestValue")
        .httpClientFactory(
            new HttpClientConfig.HttpClientFactory() {
              @Override
              public HttpClient createHttpClient() {
                DefaultHttpClient client = new DefaultHttpClient();
                client.addRequestInterceptor(new MyRequestInerceptor());
                return client;
              }
            }
        );
    final RestAssuredConfig config = RestAssuredConfig.config()
        .httpClient(httpClientConfig);

    RestAssuredConfig updatedConfig = CurlLoggingRestAssuredConfigFactory
        .updateConfig(config, Options.builder().build());

    // original configuration has not been modified
    assertThat(updatedConfig, not(equalTo(config)));
    AbstractHttpClient clientConfig = (AbstractHttpClient) config.getHttpClientConfig()
        .httpClientInstance();
    assertThat(clientConfig, not(new ContainsRequestInterceptor(CurlLoggingInterceptor.class)));
    assertThat(clientConfig, new ContainsRequestInterceptor(MyRequestInerceptor.class));
    assertThat(updatedConfig.getHttpClientConfig().params().get("TestParam"), equalTo("TestValue"));

    // curl logging interceptor is included
    AbstractHttpClient updateClientConfig = (AbstractHttpClient) updatedConfig.getHttpClientConfig()
        .httpClientInstance();
    assertThat(updateClientConfig, new ContainsRequestInterceptor(CurlLoggingInterceptor.class));

    // original interceptors are preserved in new configuration
    assertThat(updateClientConfig, new ContainsRequestInterceptor(MyRequestInerceptor.class));
    // original parameters are preserved in new configuration
    assertThat(updatedConfig.getHttpClientConfig().params().get("TestParam"), equalTo("TestValue"));

  }


  @Test
  public void shouldSentRequestWhenUsingConfigurationFactory() {
    RestAssured.given()
        .config(CurlLoggingRestAssuredConfigFactory
            .createConfig(Options.builder().useShortForm().build()))
        .baseUri(MOCK_BASE_URI)
        .port(MOCK_PORT)
        .when()
        .get("/anypath2")
        .then()
        .statusCode(200);
  }

  @AfterClass
  public void closeMock() {
    mockServer.stop();
  }


  private static class ContainsRequestInterceptor extends
      TypeSafeDiagnosingMatcher<AbstractHttpClient> {

    private Class<? extends HttpRequestInterceptor> expectedRequestedInterceptor;

    public ContainsRequestInterceptor(
        Class<? extends HttpRequestInterceptor> expectedRequestedInterceptor) {
      this.expectedRequestedInterceptor = expectedRequestedInterceptor;
    }

    @Override
    protected boolean matchesSafely(AbstractHttpClient client, Description mismatchDescription) {
      for (int i = 0; i < client.getRequestInterceptorCount(); i++) {
        if (expectedRequestedInterceptor.isInstance(client.getRequestInterceptor(i))) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {

    }
  }

  private static class MyRequestInerceptor implements HttpRequestInterceptor {

    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext)
        throws HttpException, IOException {

    }
  }

}

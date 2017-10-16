package com.github.dzieciou.testing.curl.core;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.testng.annotations.Test;

public class CurlCommandTest {

  @Test
  public void shouldRespectTargetPlatformInMultilinePrinting() {
    CurlCommand curl = new CurlCommand()
        .setUrl("/requestPath")
        .addHeader("Host", "server.com")
        .addHeader("Other", "other");

    assertThat(curl.asString(Platform.WINDOWS, true, true),
        containsString("^"));

    assertThat(curl.asString(Platform.UNIX, true, true),
        not(containsString("^")));
  }

}

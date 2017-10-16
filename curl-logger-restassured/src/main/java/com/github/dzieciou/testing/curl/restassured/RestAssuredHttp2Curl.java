/*
 * Copyright (C) 2007, 2008 Apple Inc.  All rights reserved.
 * Copyright (C) 2008, 2009 Anthony Ricaud <rik@webkit.org>
 * Copyright (C) 2011 Google Inc. All rights reserved.
 * Copyright (C) 2016 Maciej Gawinecki <mgawinecki@gmail.com>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1.  Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 * 2.  Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 * 3.  Neither the name of Apple Computer, Inc. ("Apple") nor the names of
 *     its contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE AND ITS CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL APPLE OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.dzieciou.testing.curl.restassured;

import com.github.dzieciou.testing.curl.core.CurlCommand;
import com.github.dzieciou.testing.curl.core.Http2Curl;
import com.github.dzieciou.testing.curl.core.Options;
import io.restassured.internal.multipart.RestAssuredMultiPartEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generates CURL command for a given HTTP request.
 */
@SuppressWarnings("deprecation")
public class RestAssuredHttp2Curl extends Http2Curl<HttpRequest> {

  private static final Logger log = LoggerFactory.getLogger(RestAssuredHttp2Curl.class);

  private static final List<String> NON_BINARY_CONTENT_TYPES = Arrays.asList(
      "application/x-www-form-urlencoded", "application/json");

  public RestAssuredHttp2Curl(Options options) {
    super(options);
  }

  private static String getContent(FormBodyPart bodyPart) throws IOException {
    ContentBody content = bodyPart.getBody();
    ByteArrayOutputStream out = new ByteArrayOutputStream((int) content.getContentLength());
    content.writeTo(out);
    return out.toString();
  }

  private static String removeQuotes(String s) {
    return s.replaceAll("^\"|\"$", "");
  }

  private static boolean isBasicAuthentication(Header h) {
    return h.getName().equals("Authorization") && h.getValue().startsWith("Basic");
  }

  @SuppressWarnings("deprecation")
  private static String getOriginalRequestUri(HttpRequest request) {
    if (request instanceof HttpRequestWrapper) {
      return ((HttpRequestWrapper) request).getOriginal().getRequestLine().getUri();
    } else if (request instanceof RequestWrapper) {
      return ((RequestWrapper) request).getOriginal().getRequestLine().getUri();
    } else {
      throw new IllegalArgumentException("Unsupported request class type: " + request.getClass());
    }
  }

  private static String getHost(HttpRequest request) {
    return tryGetHeaderValue(Arrays.asList(request.getAllHeaders()), "Host")
        .orElseGet(() -> URI.create(getOriginalRequestUri(request)).getHost());
  }

  private static boolean isValidUrl(String url) {
    try {
      new URL(url);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }

  private static Optional<String> tryGetHeaderValue(List<Header> headers, String headerName) {
    return headers
        .stream()
        .filter(h -> h.getName().equals(headerName))
        .map(Header::getValue)
        .findFirst();
  }

  private static <T> Object getFieldValue(T obj, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    Field f = getField(obj.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(obj);
  }

  private static Field getField(Class clazz, String fieldName)
      throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class superClass = clazz.getSuperclass();
      if (superClass == null) {
        throw e;
      } else {
        return getField(superClass, fieldName);
      }
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  protected CurlCommand http2curl(HttpRequest request)
      throws NoSuchFieldException, IllegalAccessException, IOException {
    Set<String> ignoredHeaders = new HashSet<>();
    List<Header> headers = Arrays.asList(request.getAllHeaders());

    CurlCommand curl = new CurlCommand();

    String inferredUri = request.getRequestLine().getUri();
    if (!isValidUrl(inferredUri)) { // Missing schema and domain name
      String host = getHost(request);
      String inferredScheme = "http";
      if (host.endsWith(":443")) {
        inferredScheme = "https";
      } else if (request instanceof RequestWrapper) {
        if (getOriginalRequestUri(request).startsWith("https")) {
          // This is for original URL, so if during redirects we go out of HTTPs, this might be a wrong guess
          inferredScheme = "https";
        }
      }

      if ("CONNECT".equals(request.getRequestLine().getMethod())) {
        inferredUri = String.format("%s://%s", inferredScheme, host);
      } else {
        inferredUri =
            String.format("%s://%s/%s", inferredScheme, host, inferredUri)
                .replaceAll("(?<!http(s)?:)//", "/");
      }
    }

    curl.setUrl(inferredUri);

    String inferredMethod = "GET";
    Optional<String> requestContentType = tryGetHeaderValue(headers, "Content-Type");

    if (request instanceof HttpEntityEnclosingRequest) {
      HttpEntityEnclosingRequest requestWithEntity = (HttpEntityEnclosingRequest) request;
      try {
        HttpEntity entity = requestWithEntity.getEntity();
        if (entity != null) {
          if (requestContentType.isPresent()) {
            if (requestContentType.get().startsWith("multipart/form")) {
              ignoredHeaders.add("Content-Type"); // let curl command decide
              ignoredHeaders.add("Content-Length");
              handleMultipartEntity(entity, curl);
            } else if ((requestContentType.get().startsWith("multipart/mixed"))) {
              headers = headers.stream().filter(h -> !h.getName().equals("Content-Type"))
                  .collect(Collectors.toList());
              headers.add(new BasicHeader("Content-Type", "multipart/mixed"));
              ignoredHeaders.add("Content-Length");
              handleMultipartEntity(entity, curl);
            } else {
              String formData = EntityUtils.toString(entity);
              if (NON_BINARY_CONTENT_TYPES.contains(requestContentType.get())) {
                curl.addData(formData);
                ignoredHeaders.add("Content-Length");
                inferredMethod = "POST";
              } else {
                curl.addDataBinary(formData);
                ignoredHeaders.add("Content-Length");
                inferredMethod = "POST";
              }
            }
          }
        }
      } catch (IOException e) {
        log.error("Failed to consume form data (entity) from HTTP request", e);
        throw e;
      }
    }

    if (!request.getRequestLine().getMethod().equals(inferredMethod)) {
      curl.setMethod(request.getRequestLine().getMethod());
    }

    headers = handleAuthenticationHeader(headers, curl);

    List<Header> cookiesHeaders = headers.stream()
        .filter(h -> h.getName().equals("Cookie"))
        .collect(Collectors.toList());
    if (cookiesHeaders.size() == 1) {
      curl.setCookieHeader(cookiesHeaders.get(0).getValue());
      headers = headers.stream().filter(h -> !h.getName().equals("Cookie"))
          .collect(Collectors.toList());
    } else if (cookiesHeaders.size() > 1) {
      // RFC 6265: When the user agent generates an HTTP request, the user agent MUST NOT attach
      // more than one Cookie header field.
      log.warn("More than one Cookie header in HTTP Request not allowed by RFC 6265");
    }

    handleNotIgnoredHeaders(headers, ignoredHeaders, curl);

    curl.setCompressed(true);
    curl.setInsecure(true);
    curl.setVerbose(true);
    return curl;
  }

  private void handleMultipartEntity(HttpEntity entity, CurlCommand curl)
      throws NoSuchFieldException, IllegalAccessException {
    HttpEntity wrappedEntity = (HttpEntity) getFieldValue(entity, "wrappedEntity");
    RestAssuredMultiPartEntity multiPartEntity = (RestAssuredMultiPartEntity) wrappedEntity;
    MultipartEntityBuilder multipartEntityBuilder = (MultipartEntityBuilder) getFieldValue(
        multiPartEntity, "builder");

    @SuppressWarnings("unchecked")
    List<FormBodyPart> bodyParts = (List<FormBodyPart>) getFieldValue(multipartEntityBuilder,
        "bodyParts");

    bodyParts.forEach(p -> handlePart(p, curl));
  }

  private void handlePart(FormBodyPart bodyPart, CurlCommand curl) {
    String contentDisposition = bodyPart.getHeader().getFields().stream()
        .filter(f -> f.getName().equals("Content-Disposition"))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Multipart missing Content-Disposition header"))
        .getBody();

    List<String> elements = Arrays.asList(contentDisposition.split(";"));
    Map<String, String> map = elements.stream().map(s -> s.trim().split("="))
        .collect(Collectors.toMap(a -> a[0], a -> a.length == 2 ? a[1] : ""));

    if (map.containsKey("form-data")) {

      String partName = removeQuotes(map.get("name"));

      StringBuilder partContent = new StringBuilder();
      if (map.get("filename") != null) {
        partContent.append("@").append(removeQuotes(map.get("filename")));
      } else {
        try {
          partContent.append(getContent(bodyPart));
        } catch (IOException e) {
          throw new RuntimeException("Could not read content of the part", e);
        }
      }
      partContent.append(";type=").append(bodyPart.getHeader().getField("Content-Type").getBody());

      curl.addFormPart(partName, partContent.toString());

    } else {
      throw new RuntimeException("Unsupported type " + map.entrySet().stream().findFirst().get());
    }
  }

  private void handleNotIgnoredHeaders(List<Header> headers, Set<String> ignoredHeaders,
      CurlCommand curl) {
    headers
        .stream()
        .filter(h -> !ignoredHeaders.contains(h.getName()))
        .forEach(h -> curl.addHeader(h.getName(), h.getValue()));
  }

  private List<Header> handleAuthenticationHeader(List<Header> headers, CurlCommand curl) {

    List<Header> remainingHeaders = new ArrayList<>(headers);
    Iterator<Header> it = remainingHeaders.iterator();
    while (it.hasNext()) {
      Header h = it.next();
      if (isBasicAuthentication(h)) {
        try {
          String credentials = h.getValue().replaceAll("Basic ", "");
          String decodedCredentials = new String(Base64.getDecoder().decode(credentials));
          String[] userAndPassword = decodedCredentials.split(":", -1);
          curl.setServerAuthentication(userAndPassword[0], userAndPassword[1]);
          it.remove();
          break; // There can be only one authentication headers
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
          log.warn("This is not valid Basic authentication header: {}", h.getValue());
        }
      }
    }
    return remainingHeaders;
  }


}

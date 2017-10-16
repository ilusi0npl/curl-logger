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

package com.github.dzieciou.testing.curl.core;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents curl command and provides a way to serialize it through {@link #asString(Platform,
 * boolean, boolean)} method.
 */
public class CurlCommand {

  private final List<Header> headers = new ArrayList<>();
  private final List<FormPart> formParts = new ArrayList<>();
  private final List<String> datas = new ArrayList<>();
  private final List<String> datasBinary = new ArrayList<>();
  private String url;
  private Optional<String> cookieHeader = Optional.empty();
  private boolean compressed;
  private boolean verbose;
  private boolean insecure;
  private Optional<String> method = Optional.empty();
  private Optional<ServerAuthentication> serverAuthentication = Optional.empty();

  public CurlCommand setUrl(String url) {
    this.url = url;
    return this;
  }

  public CurlCommand addHeader(String name, String value) {
    headers.add(new Header(name, value));
    return this;
  }

  public CurlCommand removeHeader(String name) {
    headers.removeIf(header -> header.name.equals(name));
    return this;
  }

  public CurlCommand addFormPart(String name, String content) {
    formParts.add(new FormPart(name, content));
    return this;
  }

  public CurlCommand addData(String data) {
    datas.add(data);
    return this;
  }

  public CurlCommand addDataBinary(String dataBinary) {
    datasBinary.add(dataBinary);
    return this;
  }

  public CurlCommand setCookieHeader(String cookieHeader) {
    this.cookieHeader = Optional.of(cookieHeader);
    return this;
  }

  public CurlCommand setCompressed(boolean compressed) {
    this.compressed = compressed;
    return this;
  }

  public CurlCommand setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  public CurlCommand setInsecure(boolean insecure) {
    this.insecure = insecure;
    return this;
  }

  public CurlCommand setMethod(String method) {
    this.method = Optional.of(method);
    return this;
  }

  public CurlCommand setServerAuthentication(String user, String password) {
    serverAuthentication = Optional.of(new ServerAuthentication(user, password));
    return this;
  }

  @Override
  public String toString() {
    return asString(Platform.RECOGNIZE_AUTOMATICALLY, false, true);
  }

  public String asString(Platform targetPlatform, boolean useShortForm, boolean printMultiliner) {
    return new Serializer(targetPlatform, useShortForm, printMultiliner).serialize(this);
  }

  public static class Header {

    private final String name;
    private final String value;

    public Header(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }
  }

  public static class FormPart {

    private final String name;
    private final String content;

    public FormPart(String name, String content) {
      this.name = name;
      this.content = content;
    }

    public String getName() {
      return name;
    }

    public String getContent() {
      return content;
    }
  }

  public static class ServerAuthentication {

    private final String user;
    private final String password;

    public ServerAuthentication(String user, String password) {
      this.user = user;
      this.password = password;
    }

    public String getPassword() {
      return password;
    }

    public String getUser() {
      return user;
    }
  }

  private static class Serializer {

    private static final Map<String, String> SHORT_PARAMETER_NAMES = new HashMap<>();

    static {
      SHORT_PARAMETER_NAMES.put("--user", "-u");
      SHORT_PARAMETER_NAMES.put("--data", "-d");
      SHORT_PARAMETER_NAMES.put("--insecure", "-k");
      SHORT_PARAMETER_NAMES.put("--form", "-F");
      SHORT_PARAMETER_NAMES.put("--cookie", "-b");
      SHORT_PARAMETER_NAMES.put("--header", "-H");
      SHORT_PARAMETER_NAMES.put("--request", "-X");
      SHORT_PARAMETER_NAMES.put("--verbose", "-v");
    }

    private final Platform targetPlatform;
    private final boolean useShortForm;
    private final boolean printMultiliner;

    public Serializer(Platform targetPlatform, boolean useShortForm, boolean printMultiliner) {
      this.targetPlatform = targetPlatform;
      this.useShortForm = useShortForm;
      this.printMultiliner = printMultiliner;
    }

    private static String parameterName(String longParameterName, boolean useShortForm) {
      return useShortForm
          ? (SHORT_PARAMETER_NAMES.get(longParameterName) == null ? longParameterName
          : SHORT_PARAMETER_NAMES
              .get(longParameterName))
          : longParameterName;
    }

    private static List<String> line(boolean useShortForm, String longParameterName,
        String... arguments) {
      List<String> line = new ArrayList<>(Arrays.asList(arguments));
      line.add(0, parameterName(longParameterName, useShortForm));
      return line;
    }

    /**
     * Replace quote by double quote (but not by \") because it is recognized by both cmd.exe and MS
     * Crt arguments parser.
     * <p>
     * Replace % by "%" because it could be expanded to an environment variable value. So %% becomes
     * "%""%". Even if an env variable "" (2 doublequotes) is declared, the cmd.exe will not
     * substitute it with its value.
     * <p>
     * Replace each backslash with double backslash to make sure MS Crt arguments parser won't
     * collapse them.
     * <p>
     * Replace new line outside of quotes since cmd.exe doesn't let to do it inside.
     */
    private static String escapeStringWin(String s) {
      return "\""
          + s
          .replaceAll("\"", "\"\"")
          .replaceAll("%", "\"%\"")
          .replaceAll("\\\\", "\\\\")
          .replaceAll("[\r\n]+", "\"^$&\"")
          + "\"";
    }

    private static String escapeStringPosix(String s) {

      if (s.matches("^.*([^\\x20-\\x7E]|\').*$")) {
        // Use ANSI-C quoting syntax.
        String escaped = s
            .replaceAll("\\\\", "\\\\")
            .replaceAll("'", "\\'")
            .replaceAll("\n", "\\n")
            .replaceAll("\r", "\\r");

        escaped = escaped.chars()
            .mapToObj(c -> escapeCharacter((char) c))
            .collect(Collectors.joining());

        return "$\'" + escaped + "'";
      } else {
        // Use single quote syntax.
        return "'" + s + "'";
      }

    }

    private static String escapeCharacter(char c) {
      int code = (int) c;
      String codeAsHex = Integer.toHexString(code);
      if (code < 256) {
        // Add leading zero when needed to not care about the next character.
        return code < 16 ? "\\x0" + codeAsHex : "\\x" + codeAsHex;
      }
      return "\\u" + ("" + codeAsHex).substring(codeAsHex.length(), 4);
    }

    public String serialize(CurlCommand curl) {
      List<List<String>> command = new ArrayList<>();

      command
          .add(line(useShortForm, "curl",
              escapeString(curl.url).replaceAll("[[{}\\\\]]", "\\$&")));

      curl.method.ifPresent(method -> command.add(line(useShortForm, "--request", method)));

      curl.cookieHeader.ifPresent(
          cookieHeader -> command.add(line(useShortForm, "--cookie", escapeString(cookieHeader))));

      curl.headers.forEach(header
          -> command.add(line(useShortForm, "--header",
          escapeString(header.getName() + ": " + header.getValue()))));

      curl.formParts.forEach(formPart
          -> command.add(line(useShortForm, "--form",
          escapeString(formPart.getName() + "=" + formPart.getContent()))));

      curl.datas.forEach(data -> command.add(line(useShortForm, "--data", escapeString(data))));

      curl.datasBinary
          .forEach(data -> command.add(line(useShortForm, "--data-binary", escapeString(data))));

      curl.serverAuthentication.ifPresent(sa
          -> command
          .add(line(useShortForm, "--user", escapeString(sa.getUser() + ":" + sa.getPassword()))));

      if (curl.compressed) {
        command.add(line(useShortForm, "--compressed"));
      }
      if (curl.insecure) {
        command.add(line(useShortForm, "--insecure"));
      }
      if (curl.verbose) {
        command.add(line(useShortForm, "--verbose"));
      }

      return command.stream()
          .map(line -> line.stream().collect(Collectors.joining(" ")))
          .collect(Collectors.joining(chooseJoiningString(printMultiliner)));
    }

    private CharSequence chooseJoiningString(boolean printMultiliner) {
      String commandLineSeparator = targetPlatform.isOsWindows() ? "^" : "\\";
      return printMultiliner
          ? String.format(" %s%s  ", commandLineSeparator, targetPlatform.lineSeparator())
          : " ";
    }

    private String escapeString(String s) {
      // cURL command is expected to run on the same platform that test run
      return targetPlatform.isOsWindows() ? escapeStringWin(s) : escapeStringPosix(s);
    }
  }


}

/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.CaseInsensitiveHeaders;

/**
 * Contains a bunch of useful HTTP headers stuff:
 *
 * <ul>
 *   <li>methods for creating {@link MultiMap} instances</li>
 *   <li>often used Header names</li>
 *   <li>method to create optimized {@link CharSequence} which can be used as header name and value</li>
 * </ul>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */

public interface HttpHeaders {

  /** JVM system property that disables HTTP headers validation, don't use this in production. */
  String DISABLE_HTTP_HEADERS_VALIDATION_PROP_NAME = "vertx.disableHttpHeadersValidation";

  /** Constant that disables HTTP headers validation, this is a constant so the JIT can eliminate validation code. */
  boolean DISABLE_HTTP_HEADERS_VALIDATION = Boolean.getBoolean(DISABLE_HTTP_HEADERS_VALIDATION_PROP_NAME);

  /**
   * Accept header name
   */

  CharSequence ACCEPT = HttpHeaderNames.ACCEPT;

  /**
   * Accept-Charset header name
   */

  CharSequence ACCEPT_CHARSET = HttpHeaderNames.ACCEPT_CHARSET;

  /**
   * Accept-Encoding header name
   */

  CharSequence ACCEPT_ENCODING = HttpHeaderNames.ACCEPT_ENCODING;

  /**
   * Accept-Language header name
   */

  CharSequence ACCEPT_LANGUAGE = HttpHeaderNames.ACCEPT_LANGUAGE;

  /**
   * Accept-Ranges header name
   */

  CharSequence ACCEPT_RANGES = HttpHeaderNames.ACCEPT_RANGES;

  /**
   * Accept-Patch header name
   */

  CharSequence ACCEPT_PATCH = HttpHeaderNames.ACCEPT_PATCH;

  /**
   * Access-Control-Allow-Credentials header name
   */

  CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;

  /**
   * Access-Control-Allow-Headers header name
   */

  CharSequence ACCESS_CONTROL_ALLOW_HEADERS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;

  /**
   * Access-Control-Allow-Methods header name
   */

  CharSequence ACCESS_CONTROL_ALLOW_METHODS = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;

  /**
   * Access-Control-Allow-Origin header name
   */

  CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;

  /**
   * Access-Control-Expose-Headers header name
   */

  CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS;

  /**
   * Access-Control-Max-Age header name
   */

  CharSequence ACCESS_CONTROL_MAX_AGE = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;

  /**
   * Access-Control-Request-Headers header name
   */

  CharSequence ACCESS_CONTROL_REQUEST_HEADERS = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;

  /**
   * Access-Control-Request-Method header name
   */

  CharSequence ACCESS_CONTROL_REQUEST_METHOD = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;

  /**
   *  Age header name
   */

  CharSequence AGE = HttpHeaderNames.AGE;

  /**
   * Allow header name
   */

  CharSequence ALLOW = HttpHeaderNames.ALLOW;

  /**
   * Authorization header name
   */

  CharSequence AUTHORIZATION = HttpHeaderNames.AUTHORIZATION;

  /**
   * Cache-Control header name
   */

  CharSequence CACHE_CONTROL = HttpHeaderNames.CACHE_CONTROL;

  /**
   * Connection header name
   */

  CharSequence CONNECTION = HttpHeaderNames.CONNECTION;

  /**
   * Content-Base header name
   */

  CharSequence CONTENT_BASE = HttpHeaderNames.CONTENT_BASE;

  /**
   * Content-Disposition header name
   */

  CharSequence CONTENT_DISPOSITION = HttpHeaderNames.CONTENT_DISPOSITION;

  /**
   * Content-Encoding header name
   */
  CharSequence CONTENT_ENCODING = HttpHeaderNames.CONTENT_ENCODING;

  /**
   * Content-Language header name
   */
  CharSequence CONTENT_LANGUAGE = HttpHeaderNames.CONTENT_LANGUAGE;

  /**
   * Content-Length header name
   */
  CharSequence CONTENT_LENGTH = HttpHeaderNames.CONTENT_LENGTH;

  /**
   * Content-Location header name
   */
  CharSequence CONTENT_LOCATION = HttpHeaderNames.CONTENT_LOCATION;

  /**
   * Content-Transfer-Encoding header name
   */
  CharSequence CONTENT_TRANSFER_ENCODING = HttpHeaderNames.CONTENT_TRANSFER_ENCODING;

  /**
   * Content-MD5 header name
   */
  CharSequence CONTENT_MD5 = HttpHeaderNames.CONTENT_MD5;

  /**
   * Content-Rage header name
   */
  CharSequence CONTENT_RANGE = HttpHeaderNames.CONTENT_RANGE;

  /**
   * Content-Type header name
   */
  CharSequence CONTENT_TYPE = HttpHeaderNames.CONTENT_TYPE;

  /**
   * Content-Cookie header name
   */
  CharSequence COOKIE = HttpHeaderNames.COOKIE;

  /**
   * Date header name
   */
  CharSequence DATE = HttpHeaderNames.DATE;

  /**
   * Etag header name
   */
  CharSequence ETAG = HttpHeaderNames.ETAG;

  /**
   * Expect header name
   */
  CharSequence EXPECT = HttpHeaderNames.EXPECT;

  /**
   * Expires header name
   */
  CharSequence EXPIRES = HttpHeaderNames.EXPIRES;

  /**
   * From header name
   */
  CharSequence FROM = HttpHeaderNames.FROM;

  /**
   * Host header name
   */
  CharSequence HOST = HttpHeaderNames.HOST;

  /**
   * If-Match header name
   */
  CharSequence IF_MATCH = HttpHeaderNames.IF_MATCH;

  /**
   * If-Modified-Since header name
   */
  CharSequence IF_MODIFIED_SINCE = HttpHeaderNames.IF_MODIFIED_SINCE;

  /**
   * If-None-Match header name
   */
  CharSequence IF_NONE_MATCH = HttpHeaderNames.IF_NONE_MATCH;

  /**
   * Last-Modified header name
   */
  CharSequence LAST_MODIFIED = HttpHeaderNames.LAST_MODIFIED;

  /**
   * Location header name
   */
  CharSequence LOCATION = HttpHeaderNames.LOCATION;

  /**
   * Origin header name
   */
  CharSequence ORIGIN = HttpHeaderNames.ORIGIN;

  /**
   * Proxy-Authenticate header name
   */
  CharSequence PROXY_AUTHENTICATE = HttpHeaderNames.PROXY_AUTHENTICATE;

  /**
   * Proxy-Authorization header name
   */
  CharSequence PROXY_AUTHORIZATION = HttpHeaderNames.PROXY_AUTHORIZATION;

  /**
   * Referer header name
   */
  CharSequence REFERER = HttpHeaderNames.REFERER;

  /**
   * Retry-After header name
   */
  CharSequence RETRY_AFTER = HttpHeaderNames.RETRY_AFTER;

  /**
   * Server header name
   */
  CharSequence SERVER = HttpHeaderNames.SERVER;

  /**
   * Transfer-Encoding header name
   */
  CharSequence TRANSFER_ENCODING = HttpHeaderNames.TRANSFER_ENCODING;

  /**
   * User-Agent header name
   */
  CharSequence USER_AGENT = HttpHeaderNames.USER_AGENT;

  /**
   * Set-Cookie header name
   */
  CharSequence SET_COOKIE = HttpHeaderNames.SET_COOKIE;

  /**
   * application/x-www-form-urlencoded header value
   */
  CharSequence APPLICATION_X_WWW_FORM_URLENCODED = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;

  /**
   * chunked header value
   */
  CharSequence CHUNKED = HttpHeaderValues.CHUNKED;
  /**
   * close header value
   */
  CharSequence CLOSE = HttpHeaderValues.CLOSE;

  /**
   * 100-continue header value
   */
  CharSequence CONTINUE = HttpHeaderValues.CONTINUE;

  /**
   * identity header value
   */
  CharSequence IDENTITY = HttpHeaderValues.IDENTITY;
  /**
   * keep-alive header value
   */
  CharSequence KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;

  /**
   * Upgrade header value
   */
  CharSequence UPGRADE = HttpHeaderValues.UPGRADE;

  /**
   * WebSocket header value
   */
  CharSequence WEBSOCKET = HttpHeaderValues.WEBSOCKET;

  /**
   * deflate,gzip header value
   */
  CharSequence DEFLATE_GZIP = createOptimized("deflate, gzip");

  /**
   * text/html header value
   */
  CharSequence TEXT_HTML = createOptimized("text/html");

  /**
   * GET header value
   */
  CharSequence GET = createOptimized("GET");

  /**
   * Create an optimized {@link CharSequence} which can be used as header name or value.
   * This should be used if you expect to use it multiple times liked for example adding the same header name or value
   * for multiple responses or requests.
   */
  static CharSequence createOptimized(String value) {
    return new AsciiString(value);
  }

  static MultiMap headers() {
    return new CaseInsensitiveHeaders();
  }

  static MultiMap set(String name, String value) {
    return new CaseInsensitiveHeaders().set(name, value);
  }

  static MultiMap set(CharSequence name, CharSequence value) {
    return new CaseInsensitiveHeaders().set(name, value);
  }
}

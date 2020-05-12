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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.impl.ContextInternal;

abstract class Http2ServerStream extends VertxHttp2Stream<Http2ServerConnection> {

  protected final Http2Headers headers;
  protected final HttpMethod method;
  protected final String uri;
  protected final String host;
  protected final Http2ServerResponseImpl response;

  Http2ServerStream(Http2ServerConnection conn,
                    ContextInternal context,
                    String contentEncoding,
                    HttpMethod method,
                    String uri) {
    super(conn, context);

    this.headers = null;
    this.method = method;
    this.uri = uri;
    this.host = null;
    this.response = new Http2ServerResponseImpl(conn, this, true, contentEncoding, null);
  }

  Http2ServerStream(Http2ServerConnection conn, ContextInternal context, Http2Headers headers, String contentEncoding, String serverOrigin) {
    super(conn, context);

    String host = headers.get(":authority") != null ? headers.get(":authority").toString() : null;
    if (host == null) {
      int idx = serverOrigin.indexOf("://");
      host = serverOrigin.substring(idx + 3);
    }

    this.headers = headers;
    this.host = host;
    this.uri = headers.get(":path") != null ? headers.get(":path").toString() : null;
    this.method = headers.get(":method") != null ? HttpMethod.valueOf(headers.get(":method").toString()) : null;
    this.response = new Http2ServerResponseImpl(conn, this, false, contentEncoding, host);
  }


  @Override
  void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
    if (streamPriority != null) {
      priority(streamPriority);
    }
    CharSequence value = headers.get(HttpHeaderNames.EXPECT);
    if (conn.options.isHandle100ContinueAutomatically() &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE))) {
      response.writeContinue();
    }
    dispatch(conn.requestHandler);
  }

  abstract void dispatch(Handler<HttpServerRequest> handler);

  @Override
  void handleWritabilityChanged(boolean writable) {
    if (response != null) {
      response.handlerWritabilityChanged(writable);
    }
  }

  public HttpMethod method() {
    return method;
  }

}

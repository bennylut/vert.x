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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.clientconnection.Endpoint;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class ClientHttpEndpointBase extends Endpoint<HttpClientConnection> {

  private final int port;
  private final String host;

  ClientHttpEndpointBase(int port, String host, Runnable dispose) {
    super(dispose);
    this.port = port;
    this.host = host;
  }

  @Override
  public final void requestConnection(ContextInternal ctx, Handler<AsyncResult<HttpClientConnection>> handler) {
    requestConnection2(ctx, handler);
  }

  protected abstract void requestConnection2(ContextInternal ctx, Handler<AsyncResult<HttpClientConnection>> handler);

  @Override
  protected void dispose() {

  }

  @Override
  public void close(HttpClientConnection connection) {
    connection.close();
  }
}

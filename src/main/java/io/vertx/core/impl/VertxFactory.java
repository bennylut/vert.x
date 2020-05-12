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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.ServiceHelper;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.impl.FileResolver;
import io.vertx.core.net.impl.transport.Transport;

import java.util.Objects;

/**
 * Internal factory for creating vertx instances with SPI services overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxFactory {

  private VertxOptions options;
  private Transport transport;
  private FileResolver fileResolver;

  public VertxFactory(VertxOptions options) {
    this.options = options;
  }

  public VertxFactory() {
    this(new VertxOptions());
  }

  public VertxFactory transport(Transport transport) {
    this.transport = transport;
    return this;
  }


  public Vertx vertx() {
    VertxImpl vertx = new VertxImpl(options, createTransport(), createFileResolver());
    return vertx;
  }


  private Transport createTransport() {
    if (transport == null) {
      transport = Transport.transport(options.getPreferNativeTransport());
    }
    return transport;
  }

  private FileResolver createFileResolver() {
    if (fileResolver == null) {
      fileResolver = new FileResolver(options.getFileSystemOptions());
    }
    return fileResolver;
  }
}

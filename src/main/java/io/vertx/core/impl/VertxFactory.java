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
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Objects;

/**
 * Internal factory for creating vertx instances with SPI services overrides.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxFactory {

  private VertxOptions options;
  private Transport transport;
  private VertxTracer tracer;
  private VertxMetrics metrics;
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

  public VertxFactory tracer(VertxTracer tracer) {
    this.tracer = tracer;
    return this;
  }

  public VertxFactory metrics(VertxMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public Vertx vertx() {
    VertxImpl vertx = new VertxImpl(options, createMetrics(), createTracer(), createTransport(), createFileResolver());
    vertx.init();
    return vertx;
  }


  private Transport createTransport() {
    if (transport == null) {
      transport = Transport.transport(options.getPreferNativeTransport());
    }
    return transport;
  }

  private VertxMetrics createMetrics() {
    if (metrics == null) {
      if (options.getMetricsOptions() != null && options.getMetricsOptions().isEnabled()) {
        VertxMetricsFactory factory = options.getMetricsOptions().getFactory();
        if (factory == null) {
          factory = ServiceHelper.loadFactoryOrNull(VertxMetricsFactory.class);
          if (factory == null) {
            // log.warn("Metrics has been set to enabled but no VertxMetricsFactory found on classpath");
          }
        }
        if (factory != null) {
          metrics = factory.metrics(options);
          Objects.requireNonNull(metrics, "The metric instance created from " + factory + " cannot be null");
        }
      }
    }
    return metrics;
  }

  private VertxTracer createTracer() {
    if (tracer == null) {
      if (options.getTracingOptions() != null && options.getTracingOptions().isEnabled()) {
        VertxTracerFactory factory = options.getTracingOptions().getFactory();
        if (factory == null) {
          factory = ServiceHelper.loadFactoryOrNull(VertxTracerFactory.class);
          if (factory == null) {
            // log.warn("Metrics has been set to enabled but no TracerFactory found on classpath");
          }
        }
        if (factory != null) {
          tracer = factory.tracer(options.getTracingOptions());
          Objects.requireNonNull(tracer, "The tracer instance created from " + factory + " cannot be null");
        }
      }
    }
    return tracer;
  }

  private FileResolver createFileResolver() {
    if (fileResolver == null) {
      fileResolver = new FileResolver(options.getFileSystemOptions());
    }
    return fileResolver;
  }
}

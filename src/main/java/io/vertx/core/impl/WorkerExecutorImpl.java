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


import io.vertx.core.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl implements WorkerExecutorInternal {

  private final ContextInternal ctx;
  private final VertxImpl.SharedWorkerPool pool;
  private boolean closed;

  public WorkerExecutorImpl(ContextInternal ctx, VertxImpl.SharedWorkerPool pool) {
    this.ctx = ctx;
    this.pool = pool;
  }

  @Override
  public Vertx vertx() {
    return ctx.owner();
  }

  @Override
  public WorkerPool getPool() {
    return pool;
  }

  @Override
  public <T> Future< T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    if (closed) {
      throw new IllegalStateException("Worker executor closed");
    }
    ContextInternal context = (ContextInternal) ctx.owner().getOrCreateContext();
    ContextImpl impl = context instanceof ContextImpl.Duplicated ? ((ContextImpl.Duplicated)context).delegate : (ContextImpl) context;
    return ContextImpl.executeBlocking(context, blockingCodeHandler, pool, ordered ? impl.orderedTasks : null);
  }

  public synchronized <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    if (asyncResultHandler != null) {
      fut.onComplete(asyncResultHandler);
    }
  }

  @Override
  public Future<Void> close() {
    PromiseInternal<Void> promise = ctx.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void close(Promise<Void> completion) {
    synchronized (this) {
      if (!closed) {
        closed = true;
        ctx.removeCloseHook(this);
        pool.release();
      }
    }
    completion.complete();
  }
}

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


import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkerContext extends ContextImpl {

  WorkerContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                ClassLoader tccl) {
    super(vertx, internalBlockingPool, workerPool, deployment, tccl);
  }

  @Override
  <T> void execute(T argument, Handler<T> task) {
    execute(this, orderedTasks, argument, task);
  }

  @Override
  public void execute(Runnable task) {
    execute(this, orderedTasks, task);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  private <T> void execute(ContextInternal ctx, TaskQueue queue, Runnable task) {
    queue.execute(() -> {
        ctx.emit(task);
    }, workerPool.executor());
  }

  private <T> void execute(ContextInternal ctx, TaskQueue queue, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    queue.execute(() -> {
        ctx.emit(value, task);
    }, workerPool.executor());
  }

  private <T> void schedule(TaskQueue queue, T argument, Handler<T> task) {
    if (Context.isOnWorkerThread()) {
      task.handle(argument);
    } else {
      queue.execute(() -> {
        task.handle(argument);
      }, workerPool.executor());
    }
  }


  /**
   * {@inheritDoc}
   *
   * <ul>
   *   <li>When the current thread is a worker thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the worker thread for execution</li>
   * </ul>
   */
  @Override
  public <T> void schedule(T argument, Handler<T> task) {
    schedule(orderedTasks, argument, task);
  }

  public ContextInternal duplicate() {
    return new Duplicated(this);
  }

  static class Duplicated extends ContextImpl.Duplicated<WorkerContext> {

    final TaskQueue orderedTasks = new TaskQueue();

    Duplicated(WorkerContext delegate) {
      super(delegate);
    }

    @Override
    <T> void execute(T argument, Handler<T> task) {
      delegate.execute(this, orderedTasks, argument, task);
    }

    @Override
    public void execute(Runnable task) {
      delegate.execute(this, orderedTasks, task);
    }

    @Override
    public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
      return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, delegate.internalOrderedTasks);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, ordered ? orderedTasks : null);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
    }

    @Override
    public <T> void schedule(T argument, Handler<T> task) {
      delegate.schedule(orderedTasks, argument, task);
    }

    @Override
    public boolean isEventLoopContext() {
      return false;
    }

    @Override
    public ContextInternal duplicate() {
      return new Duplicated(delegate);
    }
  }
}

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
package io.vertx.core

import io.netty.channel.EventLoopGroup
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.datagram.DatagramSocketOptions
import io.vertx.core.dns.DnsClient
import io.vertx.core.dns.DnsClientOptions
import io.vertx.core.file.FileSystem
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.impl.ContextInternal
import io.vertx.core.impl.VertxFactory
import io.vertx.core.metrics.Measured
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.spi.VerticleFactory
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * The entry point into the Vert.x Core API.
 *
 *
 * You use an instance of this class for functionality including:
 *
 *  * Creating TCP clients and servers
 *  * Creating HTTP clients and servers
 *  * Creating DNS clients
 *  * Creating Datagram sockets
 *  * Setting and cancelling periodic and one-shot timers
 *  * Getting a reference to the event bus API
 *  * Getting a reference to the file system API
 *  * Getting a reference to the shared data API
 *  * Deploying and undeploying verticles
 *
 *
 *
 * Most functionality in Vert.x core is fairly low level.
 *
 *
 * To create an instance of this class you can use the static factory methods: [.vertx],
 * [.vertx] .
 *
 *
 * Please see the user manual for more detailed usage information.
 *
 * @author [Tim Fox](http://tfox.org)
 */
interface Vertx : Measured {
  /**
   * Gets the current context, or creates one if there isn't one
   *
   * @return The current context (created if didn't exist)
   */
  fun getOrCreateContext() : Context

  /**
   * Create a TCP/SSL server using the specified options
   *
   * @param options  the options to use
   * @return the server
   */
  fun createNetServer(options: NetServerOptions): NetServer

  /**
   * Create a TCP/SSL server using default options
   *
   * @return the server
   */
  fun createNetServer(): NetServer

  /**
   * Create a TCP/SSL client using the specified options
   *
   * @param options  the options to use
   * @return the client
   */
  fun createNetClient(options: NetClientOptions): NetClient

  /**
   * Create a TCP/SSL client using default options
   *
   * @return the client
   */
  fun createNetClient(): NetClient

  /**
   * Create an HTTP/HTTPS server using the specified options
   *
   * @param options  the options to use
   * @return the server
   */
  fun createHttpServer(options: HttpServerOptions): HttpServer

  /**
   * Create an HTTP/HTTPS server using default options
   *
   * @return the server
   */
  fun createHttpServer(): HttpServer

  /**
   * Create a HTTP/HTTPS client using the specified options
   *
   * @param options  the options to use
   * @return the client
   */
  fun createHttpClient(options: HttpClientOptions): HttpClient

  /**
   * Create a HTTP/HTTPS client using default options
   *
   * @return the client
   */
  fun createHttpClient(): HttpClient

  /**
   * Create a datagram socket using the specified options
   *
   * @param options  the options to use
   * @return the socket
   */
  fun createDatagramSocket(options: DatagramSocketOptions): DatagramSocket

  /**
   * Create a datagram socket using default options
   *
   * @return the socket
   */
  fun createDatagramSocket(): DatagramSocket

  /**
   * Get the filesystem object. There is a single instance of FileSystem per Vertx instance.
   *
   * @return the filesystem object
   */
  fun fileSystem(): FileSystem

  /**
   * Create a DNS client to connect to a DNS server at the specified host and port, with the default query timeout (5 seconds)
   *
   *
   *
   * @param port  the port
   * @param host  the host
   * @return the DNS client
   */
  fun createDnsClient(port: Int, host: String): DnsClient

  /**
   * Create a DNS client to connect to the DNS server configured by [VertxOptions.getAddressResolverOptions]
   *
   *
   * DNS client takes the first configured resolver address provided by [DnsResolverProvider.nameServerAddresses]}
   *
   * @return the DNS client
   */
  fun createDnsClient(): DnsClient

  /**
   * Create a DNS client to connect to a DNS server
   *
   * @param options the client options
   * @return the DNS client
   */
  fun createDnsClient(options: DnsClientOptions): DnsClient

  /**
   * Set a one-shot timer to fire after `delay` milliseconds, at which point `handler` will be called with
   * the id of the timer.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @param handler  the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  fun setTimer(delay: Long, handler: Handler<Long>): Long

  /**
   * Returns a one-shot timer as a read stream. The timer will be fired after `delay` milliseconds after
   * the [ReadStream.handler] has been called.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @return the timer stream
   */
  fun timerStream(delay: Long): TimeoutStream

  /**
   * Set a periodic timer to fire every `delay` milliseconds, at which point `handler` will be called with
   * the id of the timer.
   *
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @param handler  the handler that will be called with the timer ID when the timer fires
   * @return the unique ID of the timer
   */
  fun setPeriodic(delay: Long, handler: Handler<Long>): Long

  /**
   * Returns a periodic timer as a read stream. The timer will be fired every `delay` milliseconds after
   * the [ReadStream.handler] has been called.
   *
   * @param delay  the delay in milliseconds, after which the timer will fire
   * @return the periodic stream
   */
  fun periodicStream(delay: Long): TimeoutStream

  /**
   * Cancels the timer with the specified `id`.
   *
   * @param id  The id of the timer to cancel
   * @return true if the timer was successfully cancelled, or false if the timer does not exist.
   */
  fun cancelTimer(id: Long): Boolean

  /**
   * Puts the handler on the event queue for the current context so it will be run asynchronously ASAP after all
   * preceeding events have been handled.
   *
   * @param action - a handler representing the action to execute
   */
  fun runOnContext(action: Handler<Void>)

  /**
   * Stop the the Vertx instance and release any resources held by it.
   *
   *
   * The instance cannot be used after it has been closed.
   *
   *
   * The actual close is asynchronous and may not complete until after the call has returned.
   *
   * @return a future completed with the result
   */
  fun close(): Future<Void>

  /**
   * Like [.close] but the completionHandler will be called when the close is complete
   *
   * @param completionHandler  The handler will be notified when the close is complete.
   */
  fun close(completionHandler: Handler<AsyncResult<Void>>)

  /**
   * Deploy a verticle instance that you have created yourself.
   *
   *
   * Vert.x will assign the verticle a context and start the verticle.
   *
   *
   * The actual deploy happens asynchronously and may not complete until after the call has returned.
   *
   * @param verticle  the verticle instance to deploy.
   * @return a future completed with the result
   */
  fun deployVerticle(verticle: Verticle): Future<String>

  /**
   * Like [.deployVerticle] but the completionHandler will be notified when the deployment is complete.
   *
   *
   * If the deployment is successful the result will contain a string representing the unique deployment ID of the
   * deployment.
   *
   *
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param verticle  the verticle instance to deploy
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  fun deployVerticle(
    verticle: Verticle,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Like [.deployVerticle] but [io.vertx.core.DeploymentOptions] are provided to configure the
   * deployment.
   *
   * @param verticle  the verticle instance to deploy
   * @param options  the deployment options.
   * @return a future completed with the result
   */
  fun deployVerticle(verticle: Verticle, options: DeploymentOptions): Future<String>

  /**
   * Like [.deployVerticle] but [Verticle] instance is created by invoking the
   * default constructor of `verticleClass`.
   * @return a future completed with the result
   */
  fun deployVerticle(
    verticleClass: Class<out Verticle>,
    options: DeploymentOptions
  ): Future<String>

  /**
   * Like [.deployVerticle] but [Verticle] instance is created by invoking the
   * `verticleSupplier`.
   *
   *
   * The supplier will be invoked as many times as [DeploymentOptions.getInstances].
   * It must not return the same instance twice.
   *
   *
   * Note that the supplier will be invoked on the caller thread.
   *
   * @return a future completed with the result
   */
  fun deployVerticle(
    verticleSupplier: Supplier<Verticle>,
    options: DeploymentOptions
  ): Future<String>

  /**
   * Like [.deployVerticle] but [io.vertx.core.DeploymentOptions] are provided to configure the
   * deployment.
   *
   * @param verticle  the verticle instance to deploy
   * @param options  the deployment options.
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  fun deployVerticle(
    verticle: Verticle,
    options: DeploymentOptions,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Like [.deployVerticle] but [Verticle] instance is created by
   * invoking the default constructor of `verticleClass`.
   */
  fun deployVerticle(
    verticleClass: Class<out Verticle>,
    options: DeploymentOptions,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Like [.deployVerticle] but [Verticle] instance is created by
   * invoking the `verticleSupplier`.
   *
   *
   * The supplier will be invoked as many times as [DeploymentOptions.getInstances].
   * It must not return the same instance twice.
   *
   *
   * Note that the supplier will be invoked on the caller thread.
   */
  fun deployVerticle(
    verticleSupplier: Supplier<Verticle>,
    options: DeploymentOptions,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Deploy a verticle instance given a name.
   *
   *
   * Given the name, Vert.x selects a [VerticleFactory] instance to use to instantiate the verticle.
   *
   *
   * For the rules on how factories are selected please consult the user manual.
   *
   * @param name  the name.
   * @return a future completed with the result
   */
  fun deployVerticle(name: String): Future<String>

  /**
   * Like [.deployVerticle] but the completionHandler will be notified when the deployment is complete.
   *
   *
   * If the deployment is successful the result will contain a String representing the unique deployment ID of the
   * deployment.
   *
   *
   * This deployment ID can subsequently be used to undeploy the verticle.
   *
   * @param name  The identifier
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  fun deployVerticle(
    name: String,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Like [.deployVerticle] but [io.vertx.core.DeploymentOptions] are provided to configure the
   * deployment.
   *
   * @param name  the name
   * @param options  the deployment options.
   * @return a future completed with the result
   */
  fun deployVerticle(name: String, options: DeploymentOptions): Future<String>

  /**
   * Like [.deployVerticle] but [io.vertx.core.DeploymentOptions] are provided to configure the
   * deployment.
   *
   * @param name  the name
   * @param options  the deployment options.
   * @param completionHandler  a handler which will be notified when the deployment is complete
   */
  fun deployVerticle(
    name: String,
    options: DeploymentOptions,
    completionHandler: Handler<AsyncResult<String>>
  )

  /**
   * Undeploy a verticle deployment.
   *
   *
   * The actual undeployment happens asynchronously and may not complete until after the method has returned.
   *
   * @param deploymentID  the deployment ID
   * @return a future completed with the result
   */
  fun undeploy(deploymentID: String): Future<Void>

  /**
   * Like [.undeploy] but the completionHandler will be notified when the undeployment is complete.
   *
   * @param deploymentID  the deployment ID
   * @param completionHandler  a handler which will be notified when the undeployment is complete
   */
  fun undeploy(
    deploymentID: String,
    completionHandler: Handler<AsyncResult<Void>>
  )

  /**
   * Return a Set of deployment IDs for the currently deployed deploymentIDs.
   *
   * @return Set of deployment IDs
   */
  fun deploymentIDs(): Set<String>

  /**
   * Register a `VerticleFactory` that can be used for deploying Verticles based on an identifier.
   *
   * @param factory the factory to register
   */
  fun registerVerticleFactory(factory: VerticleFactory)

  /**
   * Unregister a `VerticleFactory`
   *
   * @param factory the factory to unregister
   */
  fun unregisterVerticleFactory(factory: VerticleFactory)

  /**
   * Return the Set of currently registered verticle factories.
   *
   * @return the set of verticle factories
   */
  fun verticleFactories(): Set<VerticleFactory>

  /**
   * Safely execute some blocking code.
   *
   *
   * Executes the blocking code in the handler `blockingCodeHandler` using a thread from the worker pool.
   *
   *
   * When the code is complete the handler `resultHandler` will be called with the result on the original context
   * (e.g. on the original event loop of the caller).
   *
   *
   * A `Future` instance is passed into `blockingCodeHandler`. When the blocking code successfully completes,
   * the handler should call the [Promise.complete] or [Promise.complete] method, or the [Promise.fail]
   * method if it failed.
   *
   *
   * In the `blockingCodeHandler` the current context remains the original context and therefore any task
   * scheduled in the `blockingCodeHandler` will be executed on the this context and not on the worker thread.
   *
   *
   * The blocking code should block for a reasonable amount of time (i.e no more than a few seconds). Long blocking operations
   * or polling operations (i.e a thread that spin in a loop polling events in a blocking fashion) are precluded.
   *
   *
   * When the blocking operation lasts more than the 10 seconds, a message will be printed on the console by the
   * blocked thread checker.
   *
   *
   * Long blocking operations should use a dedicated thread managed by the application, which can interact with
   * verticles using the event-bus or [Context.runOnContext]
   *
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param resultHandler  handler that will be called when the blocking code is complete
   * @param ordered  if true then if executeBlocking is called several times on the same context, the executions
   * for that context will be executed serially, not in parallel. if false then they will be no ordering
   * guarantees
   * @param <T> the type of the result
  </T> */
  fun <T> executeBlocking(
    blockingCodeHandler: Handler<Promise<T>>,
    ordered: Boolean,
    resultHandler: Handler<AsyncResult<T>>
  )

  /**
   * Like [.executeBlocking] called with ordered = true.
   */
  fun <T> executeBlocking(
    blockingCodeHandler: Handler<Promise<T>>,
    resultHandler: Handler<AsyncResult<T>>
  )

  /**
   * Same as [.executeBlocking] but with an `handler` called when the operation completes
   */
  fun <T> executeBlocking(
    blockingCodeHandler: Handler<Promise<T>>,
    ordered: Boolean
  ): Future<T>

  /**
   * Same as [.executeBlocking] but with an `handler` called when the operation completes
   */
  fun <T> executeBlocking(blockingCodeHandler: Handler<Promise<T>>): Future<T>

  /**
   * Return the Netty EventLoopGroup used by Vert.x
   *
   * @return the EventLoopGroup
   */
  fun nettyEventLoopGroup(): EventLoopGroup

  /**
   * Like [.createSharedWorkerExecutor] but with the [VertxOptions.setWorkerPoolSize] `poolSize`.
   */
  fun createSharedWorkerExecutor(name: String): WorkerExecutor

  /**
   * Like [.createSharedWorkerExecutor] but with the [VertxOptions.setMaxWorkerExecuteTime] `maxExecuteTime`.
   */
  fun createSharedWorkerExecutor(name: String, poolSize: Int): WorkerExecutor

  /**
   * Like [.createSharedWorkerExecutor] but with the [ns unit][TimeUnit.NANOSECONDS].
   */
  fun createSharedWorkerExecutor(name: String, poolSize: Int, maxExecuteTime: Long): WorkerExecutor

  /**
   * Create a named worker executor, the executor should be closed when it's not needed anymore to release
   * resources.
   *
   *
   *
   * This method can be called mutiple times with the same `name`. Executors with the same name will share
   * the same worker pool. The worker pool size , max execute time and unit of max execute time are set when the worker pool is created and
   * won't change after.
   *
   *
   *
   * The worker pool is released when all the [WorkerExecutor] sharing the same name are closed.
   *
   * @param name the name of the worker executor
   * @param poolSize the size of the pool
   * @param maxExecuteTime the value of max worker execute time
   * @param maxExecuteTimeUnit the value of unit of max worker execute time
   * @return the named worker executor
   */
  fun createSharedWorkerExecutor(
    name: String,
    poolSize: Int,
    maxExecuteTime: Long,
    maxExecuteTimeUnit: TimeUnit
  ): WorkerExecutor

  /**
   * @return whether the native transport is used
   */
  val isNativeTransportEnabled: Boolean

  /**
   * Set a default exception handler for [Context], set on [Context.exceptionHandler] at creation.
   *
   * @param handler the exception handler
   * @return a reference to this, so the API can be used fluently
   */
  fun exceptionHandler(handler: Handler<Throwable>): Vertx

  /**
   * @return the current default exception handler
   */
  fun exceptionHandler(): Handler<Throwable>

  companion object {
    /**
     * Creates a non clustered instance using the specified options
     *
     * @param options  the options to use
     * @return the instance
     */
    /**
     * Creates a non clustered instance using default options.
     *
     * @return the instance
     */
    @JvmOverloads
    fun vertx(options: VertxOptions = VertxOptions()): Vertx {
      return VertxFactory(options).vertx()
    }

    /**
     * Gets the current context
     *
     * @return The current context or null if no current context
     */
    @JvmStatic
    fun currentContext(): Context {
      return ContextInternal.current()
    }
  }
}

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
package io.vertx.core.http.impl

import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import io.vertx.core.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.core.impl.ContextInternal
import io.vertx.core.impl.PromiseInternal
import io.vertx.core.impl.VertxInternal
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.net.NetSocket
import io.vertx.core.net.ProxyType
import io.vertx.core.net.SocketAddress
import io.vertx.core.net.impl.SSLHelper
import io.vertx.core.net.impl.clientconnection.ConnectionManager
import io.vertx.core.net.impl.clientconnection.Endpoint
import io.vertx.core.net.impl.clientconnection.EndpointProvider
import io.vertx.core.streams.ReadStream
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 *
 * This class is thread-safe.
 *
 * @author [Tim Fox](http://tfox.org)
 */
class HttpClientImpl(val vertx: VertxInternal, options: HttpClientOptions) : HttpClient {

  /**
   * @return the vertx, for use in package related classes only.
   */
  private val channelGroup: ChannelGroup
  val options: HttpClientOptions
  private val context: ContextInternal
  private val webSocketCM: ConnectionManager<EndpointKey, HttpClientConnection>
  private val httpCM: ConnectionManager<EndpointKey, HttpClientConnection>
  private val closeHook: Closeable
  private val proxyType: ProxyType?
  val sslHelper: SSLHelper
  private val keepAlive: Boolean
  private val pipelining: Boolean
  private var timerID: Long = 0
  @Volatile
  private var closed = false
  @Volatile
  private var connectionHandler: Handler<HttpConnection>? = null
  @Volatile
  private var redirectHandler: HttpRedirectHandler = DefaultHttpRedirectHandler(this)


  init {
    this.options = HttpClientOptions(options)
    channelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    var alpnVersions = options.alpnVersions
    if (alpnVersions == null || alpnVersions.isEmpty()) {
      alpnVersions = when (options.protocolVersion) {
        HttpVersion.HTTP_2 -> Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1)
        else -> listOf(options.protocolVersion)
      }
    }
    keepAlive = options.isKeepAlive
    pipelining = options.isPipelining
    sslHelper =
      SSLHelper(options, options.keyCertOptions, options.trustOptions).setApplicationProtocols(alpnVersions)
    sslHelper.validate(vertx)
    context = vertx.getOrCreateContext()
    closeHook = Closeable { completionHandler: Promise<Void?> ->
      this@HttpClientImpl.close()
      completionHandler.handle(Future.succeededFuture())
    }
    check(!(options.protocolVersion == HttpVersion.HTTP_2 && Context.isOnWorkerThread())) { "Cannot use HttpClient with HTTP_2 in a worker" }
    if (context.deploymentID() != null) {
      context.addCloseHook(closeHook)
    }
    check(!(!keepAlive && pipelining)) { "Cannot have pipelining with no keep alive" }
    webSocketCM = webSocketConnectionManager()
    httpCM = httpConnectionManager()
    proxyType = if (options.proxyOptions != null) options.proxyOptions.type else null
    if (options.poolCleanerPeriod > 0 && (options.keepAliveTimeout > 0L || options.http2KeepAliveTimeout > 0L)) {
      timerID = vertx.setTimer(
        options.poolCleanerPeriod.toLong(),
        Handler { id: Long? -> checkExpired() }
      )
    }
  }

  private fun checkExpired() {
    httpCM.forEach(EXPIRED_CHECKER)
    synchronized(this) {
      if (!closed) {
        timerID = vertx.setTimer(
          options.poolCleanerPeriod.toLong(),
          Handler { id: Long? -> checkExpired() }
        )
      }
    }
  }

  private fun httpConnectionManager(): ConnectionManager<EndpointKey, HttpClientConnection> {
    val maxSize = options.maxPoolSize * options.http2MaxPoolSize.toLong()
    val maxPoolSize = Math.max(options.maxPoolSize, options.http2MaxPoolSize)
    return ConnectionManager(EndpointProvider { key: EndpointKey, ctx: ContextInternal?, dispose: Runnable? ->
      val host: String
      val port: Int
      if (key.serverAddr.isInetSocket) {
        host = key.serverAddr.host()
        port = key.serverAddr.port()
      } else {
        host = key.serverAddr.path()
        port = 0
      }
      val connector = HttpChannelConnector(
        this,
        channelGroup,
        ctx,
        options.protocolVersion,
        key.ssl,
        key.peerAddr,
        key.serverAddr
      )
      ClientHttpStreamEndpoint(
        options.maxWaitQueueSize,
        maxSize,
        host,
        port,
        ctx,
        connector,
        dispose
      )
    })
  }

  private fun webSocketConnectionManager(): ConnectionManager<EndpointKey, HttpClientConnection> {
    val maxPoolSize = options.maxPoolSize
    return ConnectionManager(EndpointProvider { key: EndpointKey, ctx: ContextInternal?, dispose: Runnable? ->
      val host: String
      val port: Int
      if (key.serverAddr.isInetSocket) {
        host = key.serverAddr.host()
        port = key.serverAddr.port()
      } else {
        host = key.serverAddr.path()
        port = 0
      }

      val connector = HttpChannelConnector(
        this,
        channelGroup,
        ctx,
        HttpVersion.HTTP_1_1,
        key.ssl,
        key.peerAddr,
        key.serverAddr
      )
      WebSocketEndpoint(port, host, maxPoolSize, connector, dispose)
    })
  }

  private fun getPort(port: Int?): Int {
    return port ?: options.defaultPort
  }

  private fun getHost(host: String?): String {
    return host ?: options.defaultHost
  }

  override fun webSocket(
    connectOptions: WebSocketConnectOptions,
    handler: Handler<AsyncResult<WebSocket>>
  ) {
    webSocket(connectOptions, vertx.promise(handler))
  }

  private fun webSocket(
    connectOptions: WebSocketConnectOptions,
    promise: PromiseInternal<WebSocket>
  ) {
    val port = getPort(connectOptions.port)
    val host = getHost(connectOptions.host)
    val addr = SocketAddress.inetSocketAddress(port, host)
    val key =
      EndpointKey(if (connectOptions.isSsl != null) connectOptions.isSsl else options.isSsl, addr, addr)
    webSocketCM.getConnection(
      promise.future().context() as ContextInternal,
      key
    ) { ar: AsyncResult<HttpClientConnection> ->
      if (ar.succeeded()) {
        val conn = ar.result() as Http1xClientConnection
        conn.toWebSocket(
          connectOptions.uri,
          connectOptions.headers,
          connectOptions.version,
          connectOptions.subProtocols,
          options.maxWebSocketFrameSize,
          promise
        )
      } else {
        promise.fail(ar.cause())
      }
    }
  }

  override fun webSocket(
    port: Int,
    host: String,
    requestURI: String
  ): Future<WebSocket> {
    val promise: Promise<WebSocket> = vertx.promise()
    webSocket(port, host, requestURI, promise)
    return promise.future()
  }

  override fun webSocket(
    host: String,
    requestURI: String
  ): Future<WebSocket> {
    val promise: Promise<WebSocket> = vertx.promise()
    webSocket(host, requestURI, promise)
    return promise.future()
  }

  override fun webSocket(requestURI: String): Future<WebSocket> {
    val promise: Promise<WebSocket> = vertx.promise()
    webSocket(requestURI, promise)
    return promise.future()
  }

  override fun webSocket(options: WebSocketConnectOptions): Future<WebSocket> {
    val promise: Promise<WebSocket> = vertx.promise()
    webSocket(options, promise)
    return promise.future()
  }

  override fun webSocketAbs(
    url: String,
    headers: MultiMap,
    version: WebsocketVersion,
    subProtocols: List<String>
  ): Future<WebSocket> {
    val promise: Promise<WebSocket> = vertx.promise()
    webSocketAbs(url, headers, version, subProtocols, promise)
    return promise.future()
  }

  override fun webSocket(
    port: Int,
    host: String,
    requestURI: String,
    handler: Handler<AsyncResult<WebSocket>>
  ) {
    webSocket(WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port), handler)
  }

  override fun webSocket(
    host: String,
    requestURI: String,
    handler: Handler<AsyncResult<WebSocket>>
  ) {
    webSocket(options.defaultPort, host, requestURI, handler)
  }

  override fun webSocket(
    requestURI: String,
    handler: Handler<AsyncResult<WebSocket>>
  ) {
    webSocket(options.defaultPort, options.defaultHost, requestURI, handler)
  }

  override fun webSocketAbs(
    url: String,
    headers: MultiMap,
    version: WebsocketVersion,
    subProtocols: List<String>,
    handler: Handler<AsyncResult<WebSocket>>
  ) {
    val uri: URI
    uri = try {
      URI(url)
    } catch (e: URISyntaxException) {
      throw IllegalArgumentException(e)
    }
    val scheme = uri.scheme
    require(!("ws" != scheme && "wss" != scheme)) { "Scheme: $scheme" }
    val ssl = scheme.length == 3
    var port = uri.port
    if (port == -1) port = if (ssl) 443 else 80
    val relativeUri = StringBuilder()
    if (uri.rawPath != null) {
      relativeUri.append(uri.rawPath)
    }
    if (uri.rawQuery != null) {
      relativeUri.append('?').append(uri.rawQuery)
    }
    if (uri.rawFragment != null) {
      relativeUri.append('#').append(uri.rawFragment)
    }
    val options = WebSocketConnectOptions()
      .setHost(uri.host)
      .setPort(port).setSsl(ssl)
      .setURI(relativeUri.toString())
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols)
    webSocket(options, handler)
  }

  override fun send(
    options: RequestOptions,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options, (null as Buffer?)!!, responseHandler)
  }

  override fun send(options: RequestOptions): Future<HttpClientResponse> {
    return send(options, (null as Buffer?)!!)
  }

  override fun send(
    options: RequestOptions,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      options.method,
      getHost(options.host),
      getPort(options.port),
      options.uri,
      options.headers,
      options.isSsl,
      options.followRedirects,
      options.timeout,
      body,
      responseHandler
    )
  }

  override fun send(
    options: RequestOptions,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(
      options.method,
      getHost(options.host),
      getPort(options.port),
      options.uri,
      options.headers,
      options.isSsl,
      options.followRedirects,
      options.timeout,
      body
    )
  }

  override fun send(
    options: RequestOptions,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      options.method,
      getHost(options.host),
      getPort(options.port),
      options.uri,
      options.headers,
      options.isSsl,
      options.followRedirects,
      options.timeout,
      body,
      responseHandler
    )
  }

  override fun send(
    options: RequestOptions,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(
      options.method,
      getHost(options.host),
      getPort(options.port),
      options.uri,
      options.headers,
      options.isSsl,
      options.followRedirects,
      options.timeout,
      body
    )
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, port, host, requestURI, headers, (null as Buffer?)!!, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(method, port, host, requestURI, headers, (null as Buffer?)!!)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, port, requestURI, headers, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(method, host, port, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, port, requestURI, headers, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, host, port, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, port, host, requestURI, null as Buffer?, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(method, port, host, requestURI, null as Buffer?)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer?,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, port, requestURI, null, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, host, port, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, port, requestURI, null, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer?
  ): Future<HttpClientResponse> {
    return send(method, host, port, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, requestURI, headers, null as Buffer?, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(method, host, requestURI, headers, null as Buffer?)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer?,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, options.defaultPort, requestURI, headers, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer?
  ): Future<HttpClientResponse> {
    return send(method, host, options.defaultPort, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, options.defaultPort, requestURI, headers, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, host, options.defaultPort, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    body: Buffer?,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, options.defaultPort, requestURI, null, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    body: Buffer?
  ): Future<HttpClientResponse> {
    return send(method, host, options.defaultPort, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, options.defaultPort, requestURI, null, null, false, 0, body, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, host, options.defaultPort, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, host, options.defaultPort, requestURI, null, null, false, 0, null, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(method, host, options.defaultPort, requestURI, null, null, false, 0, null)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(method, requestURI, headers, null as Buffer?, responseHandler)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(method, requestURI, headers, null as Buffer?)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap,
    body: Buffer?,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      options.defaultHost,
      options.defaultPort,
      requestURI,
      headers,
      null,
      false,
      0,
      body,
      responseHandler
    )
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap,
    body: Buffer?
  ): Future<HttpClientResponse> {
    return send(method, options.defaultHost, options.defaultPort, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      options.defaultHost,
      options.defaultPort,
      requestURI,
      headers,
      null,
      false,
      0,
      body,
      responseHandler
    )
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, options.defaultHost, options.defaultPort, requestURI, headers, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    body: Buffer?,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      options.defaultHost,
      options.defaultPort,
      requestURI,
      null,
      null,
      false,
      0,
      body,
      responseHandler
    )
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    body: Buffer?
  ): Future<HttpClientResponse> {
    return send(method, options.defaultHost, options.defaultPort, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      options.defaultHost,
      options.defaultPort,
      requestURI,
      null,
      null,
      false,
      0,
      body,
      responseHandler
    )
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(method, options.defaultHost, options.defaultPort, requestURI, null, null, false, 0, body)
  }

  override fun send(
    method: HttpMethod,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      options.defaultHost,
      options.defaultPort,
      requestURI,
      null,
      null,
      false,
      0,
      null,
      responseHandler
    )
  }

  override fun send(
    method: HttpMethod,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(method, options.defaultHost, options.defaultPort, requestURI, null, null, false, 0, null)
  }

  private fun send(
    method: HttpMethod,
    host: String,
    port: Int,
    requestURI: String,
    headers: MultiMap?,
    ssl: Boolean?,
    followRedirects: Boolean,
    timeout: Long,
    body: Any?,
    handler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(
      method,
      host,
      port,
      requestURI,
      headers,
      ssl,
      followRedirects,
      timeout,
      body,
      vertx.promise(handler)
    )
  }

  private fun send(
    method: HttpMethod,
    host: String,
    port: Int,
    requestURI: String,
    headers: MultiMap?,
    ssl: Boolean?,
    followRedirects: Boolean,
    timeout: Long,
    body: Any?
  ): Future<HttpClientResponse> {
    val promise: Promise<HttpClientResponse> = vertx.promise()
    send(method, host, port, requestURI, headers, ssl, followRedirects, timeout, body, promise)
    return promise.future()
  }

  private fun send(
    method: HttpMethod,
    host: String,
    port: Int,
    requestURI: String,
    headers: MultiMap?,
    ssl: Boolean?,
    followRedirects: Boolean,
    timeout: Long,
    body: Any?,
    responsePromise: PromiseInternal<HttpClientResponse>
  ) {
    val req = createRequest(method, null, host, port, ssl, requestURI, headers, responsePromise)
    req.setFollowRedirects(followRedirects)
    if (body is Buffer) {
      req.end(body as Buffer?)
    } else if (body is ReadStream<*>) {
      val stream =
        body as ReadStream<Buffer>
      if (headers == null || !headers.contains(HttpHeaders.CONTENT_LENGTH)) {
        req.isChunked = true
      }
      stream.pipeTo(req)
    } else {
      req.end()
    }
    if (timeout > 0) {
      req.setTimeout(timeout)
    }
  }

  override fun request(method: HttpMethod, requestURI: String): HttpClientRequest {
    return request(method, options.defaultPort, options.defaultHost, requestURI)
  }

  override fun request(
    method: HttpMethod,
    port: Int,
    host: String,
    requestURI: String
  ): HttpClientRequest {
    return createRequest(method, null, host, port, null, requestURI, null)
  }

  override fun request(
    method: HttpMethod,
    serverAddress: SocketAddress,
    port: Int,
    host: String,
    requestURI: String
  ): HttpClientRequest {
    return createRequest(method, serverAddress, host, port, null, requestURI, null)
  }

  override fun request(
    serverAddress: SocketAddress,
    options: RequestOptions
  ): HttpClientRequest {
    return createRequest(
      options.method,
      serverAddress,
      getHost(options.host),
      getPort(options.port),
      options.isSsl,
      options.uri,
      null
    )
  }

  override fun request(options: RequestOptions): HttpClientRequest {
    return createRequest(
      options.method,
      null,
      getHost(options.host),
      getPort(options.port),
      options.isSsl,
      options.uri,
      options.headers
    )
  }

  override fun request(
    method: HttpMethod,
    host: String,
    requestURI: String
  ): HttpClientRequest {
    return request(method, options.defaultPort, host, requestURI)
  }

  override fun get(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, port, host, requestURI, headers, responseHandler)
  }

  override fun get(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.GET, port, host, requestURI, headers)
  }

  override fun get(
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, host, requestURI, headers, responseHandler)
  }

  override fun get(
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.GET, host, requestURI, headers)
  }

  override fun get(
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, requestURI, headers, responseHandler)
  }

  override fun get(
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.GET, requestURI, headers)
  }

  override fun get(
    options: RequestOptions,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.GET), responseHandler)
  }

  override fun get(options: RequestOptions): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.GET))
  }

  override fun get(
    port: Int,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, port, host, requestURI, responseHandler)
  }

  override fun get(
    port: Int,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(HttpMethod.GET, port, host, requestURI)
  }

  override fun get(
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, host, requestURI, responseHandler)
  }

  override fun get(host: String, requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.GET, host, requestURI)
  }

  override fun get(
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.GET, requestURI, responseHandler)
  }

  override fun get(requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.GET, requestURI)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, port, host, requestURI, headers, body, responseHandler)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, port, host, requestURI, headers, body)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, port, host, requestURI, headers, body, responseHandler)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, port, host, requestURI, headers, body)
  }

  override fun post(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, host, requestURI, headers, body, responseHandler)
  }

  override fun post(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, host, requestURI, headers, body)
  }

  override fun post(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, host, requestURI, headers, body, responseHandler)
  }

  override fun post(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, host, requestURI, headers, body)
  }

  override fun post(
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, requestURI, headers, body, responseHandler)
  }

  override fun post(
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, requestURI, headers, body)
  }

  override fun post(
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, requestURI, headers, body, responseHandler)
  }

  override fun post(
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, requestURI, headers, body)
  }

  override fun post(
    options: RequestOptions,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.POST), body, responseHandler)
  }

  override fun post(
    options: RequestOptions,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.POST), body)
  }

  override fun post(
    options: RequestOptions,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.POST), body, responseHandler)
  }

  override fun post(
    options: RequestOptions,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.POST), body)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, port, host, requestURI, body, responseHandler)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, port, host, requestURI, body)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, port, host, requestURI, body, responseHandler)
  }

  override fun post(
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, port, host, requestURI, body)
  }

  override fun post(
    host: String,
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, host, requestURI, body, responseHandler)
  }

  override fun post(
    host: String,
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, host, requestURI, body)
  }

  override fun post(
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, host, requestURI, body, responseHandler)
  }

  override fun post(
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, host, requestURI, body)
  }

  override fun post(
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, requestURI, body, responseHandler)
  }

  override fun post(
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, requestURI, body)
  }

  override fun post(
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.POST, requestURI, body, responseHandler)
  }

  override fun post(
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.POST, requestURI, body)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, port, host, requestURI, headers, body, responseHandler)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, port, host, requestURI, headers, body)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, port, host, requestURI, headers, body, responseHandler)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, port, host, requestURI, headers, body)
  }

  override fun put(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, host, requestURI, headers, body, responseHandler)
  }

  override fun put(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, host, requestURI, headers, body)
  }

  override fun put(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, host, requestURI, headers, body, responseHandler)
  }

  override fun put(
    host: String,
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, host, requestURI, headers, body)
  }

  override fun put(
    requestURI: String,
    headers: MultiMap,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, requestURI, headers, body, responseHandler)
  }

  override fun put(
    requestURI: String,
    headers: MultiMap,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, requestURI, headers, body)
  }

  override fun put(
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, requestURI, headers, body, responseHandler)
  }

  override fun put(
    requestURI: String,
    headers: MultiMap,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, requestURI, headers, body)
  }

  override fun put(
    options: RequestOptions,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.PUT), body, responseHandler)
  }

  override fun put(
    options: RequestOptions,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.PUT), body)
  }

  override fun put(
    options: RequestOptions,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.PUT), body, responseHandler)
  }

  override fun put(
    options: RequestOptions,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.PUT), body)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, port, host, requestURI, body, responseHandler)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, port, host, requestURI, body)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, port, host, requestURI, body, responseHandler)
  }

  override fun put(
    port: Int,
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, port, host, requestURI, body)
  }

  override fun put(
    host: String,
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, host, requestURI, body, responseHandler)
  }

  override fun put(
    host: String,
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, host, requestURI, body)
  }

  override fun put(
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, host, requestURI, body, responseHandler)
  }

  override fun put(
    host: String,
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, host, requestURI, body)
  }

  override fun put(
    requestURI: String,
    body: Buffer,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, requestURI, body, responseHandler)
  }

  override fun put(
    requestURI: String,
    body: Buffer
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, requestURI, body)
  }

  override fun put(
    requestURI: String,
    body: ReadStream<Buffer>,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.PUT, requestURI, body, responseHandler)
  }

  override fun put(
    requestURI: String,
    body: ReadStream<Buffer>
  ): Future<HttpClientResponse> {
    return send(HttpMethod.PUT, requestURI, body)
  }

  override fun head(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, port, host, requestURI, headers, responseHandler)
  }

  override fun head(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, port, host, requestURI, headers)
  }

  override fun head(
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, host, requestURI, headers, responseHandler)
  }

  override fun head(
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, host, requestURI, headers)
  }

  override fun head(
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, requestURI, headers, responseHandler)
  }

  override fun head(
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, requestURI, headers)
  }

  override fun head(
    options: RequestOptions,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.HEAD), responseHandler)
  }

  override fun head(options: RequestOptions): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.HEAD))
  }

  override fun head(
    port: Int,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, port, host, requestURI, responseHandler)
  }

  override fun head(
    port: Int,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, port, host, requestURI)
  }

  override fun head(
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, host, requestURI, responseHandler)
  }

  override fun head(host: String, requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, host, requestURI)
  }

  override fun head(
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.HEAD, requestURI, responseHandler)
  }

  override fun head(requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.HEAD, requestURI)
  }

  override fun options(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, port, host, requestURI, headers, responseHandler)
  }

  override fun options(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, port, host, requestURI, headers)
  }

  override fun options(
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, host, requestURI, headers, responseHandler)
  }

  override fun options(
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, host, requestURI, headers)
  }

  override fun options(
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, requestURI, headers, responseHandler)
  }

  override fun options(
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, requestURI, headers)
  }

  override fun options(
    options: RequestOptions,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.OPTIONS), responseHandler)
  }

  override fun options(options: RequestOptions): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.OPTIONS))
  }

  override fun options(
    port: Int,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, port, host, requestURI, responseHandler)
  }

  override fun options(
    port: Int,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, port, host, requestURI)
  }

  override fun options(
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, host, requestURI, responseHandler)
  }

  override fun options(
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, host, requestURI)
  }

  override fun options(
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.OPTIONS, requestURI, responseHandler)
  }

  override fun options(requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.OPTIONS, requestURI)
  }

  override fun delete(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, port, host, requestURI, responseHandler)
  }

  override fun delete(
    port: Int,
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, port, host, requestURI)
  }

  override fun delete(
    host: String,
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, host, requestURI, responseHandler)
  }

  override fun delete(
    host: String,
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, host, requestURI)
  }

  override fun delete(
    requestURI: String,
    headers: MultiMap,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, requestURI, responseHandler)
  }

  override fun delete(
    requestURI: String,
    headers: MultiMap
  ): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, requestURI)
  }

  override fun delete(
    options: RequestOptions,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(options.setMethod(HttpMethod.DELETE), responseHandler)
  }

  override fun delete(options: RequestOptions): Future<HttpClientResponse> {
    return send(options.setMethod(HttpMethod.DELETE))
  }

  override fun delete(
    port: Int,
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, port, host, requestURI, responseHandler)
  }

  override fun delete(
    port: Int,
    host: String,
    requestURI: String
  ): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, port, host, requestURI)
  }

  override fun delete(
    host: String,
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, host, requestURI, responseHandler)
  }

  override fun delete(host: String, requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, host, requestURI)
  }

  override fun delete(
    requestURI: String,
    responseHandler: Handler<AsyncResult<HttpClientResponse>>
  ) {
    send(HttpMethod.DELETE, requestURI, responseHandler)
  }

  override fun delete(requestURI: String): Future<HttpClientResponse> {
    return send(HttpMethod.DELETE, requestURI)
  }

  override fun close(handler: Handler<AsyncResult<Void>>) {
    val closingCtx = vertx.getOrCreateContext()
    close(closingCtx.promise(handler))
  }

  override fun close(): Future<Void> {
    val closingCtx = vertx.getOrCreateContext()
    val promise: Promise<Void> = closingCtx.promise()
    close(promise)
    return promise.future()
  }

  private fun close(promise: PromiseInternal<Void>) {
    synchronized(this) {
      checkClosed()
      closed = true
      if (timerID >= 0) {
        vertx.cancelTimer(timerID)
        timerID = -1
      }
    }
    if (context.deploymentID() != null) {
      context.removeCloseHook(closeHook)
    }
    webSocketCM.close()
    httpCM.close()
    val fut = channelGroup.close()
    fut.addListener(promise)
  }

  override fun connectionHandler(handler: Handler<HttpConnection>): HttpClient {
    connectionHandler = handler
    return this
  }

  fun connectionHandler(): Handler<HttpConnection>? {
    return connectionHandler
  }

  override fun redirectHandler(handler: HttpRedirectHandler?): HttpClient {
    var handler = handler
    if (handler == null) {
      handler = DefaultHttpRedirectHandler(this)
    }
    redirectHandler = handler
    return this
  }

  override fun redirectHandler(): HttpRedirectHandler {
    return redirectHandler
  }

  fun getConnectionForRequest(
    ctx: ContextInternal,
    peerAddress: SocketAddress?,
    req: HttpClientRequestImpl?,
    netSocketPromise: Promise<NetSocket?>?,
    ssl: Boolean,
    server: SocketAddress?,
    handler: Handler<AsyncResult<HttpClientStream>>?
  ) {
    val key = EndpointKey(ssl, server, peerAddress)
    httpCM.getConnection(
      ctx,
      key
    ) { ar: AsyncResult<HttpClientConnection> ->
      if (ar.succeeded()) {
        ar.result().createStream(ctx, req, netSocketPromise, handler)
      } else {
        ctx.emit(
          Future.failedFuture(ar.cause()),
          handler
        )
      }
    }
  }

  internal fun createRequest(
    method: HttpMethod,
    server: SocketAddress?,
    host: String,
    port: Int,
    ssl: Boolean?,
    requestURI: String,
    headers: MultiMap?,
    responsePromise: PromiseInternal<HttpClientResponse> = vertx.promise()
  ): HttpClientRequestBase {
    var server = server
    var requestURI = requestURI
    var headers = headers
    Objects.requireNonNull(method, "no null method accepted")
    Objects.requireNonNull(host, "no null host accepted")
    Objects.requireNonNull(requestURI, "no null requestURI accepted")
    val useAlpn = options.isUseAlpn
    val useSSL = ssl ?: options.isSsl
    require(!(!useAlpn && useSSL && options.protocolVersion == HttpVersion.HTTP_2)) { "Must enable ALPN when using H2" }
    checkClosed()
    val req: HttpClientRequestBase
    val useProxy = !useSSL && proxyType == ProxyType.HTTP
    if (useProxy) { // If the requestURI is as not absolute URI then we do not recompute one for the proxy
      if (!ABS_URI_START_PATTERN.matcher(requestURI).find()) {
        val defaultPort = 80
        val addPort = if (port != -1 && port != defaultPort) ":$port" else ""
        requestURI = (if (ssl === java.lang.Boolean.TRUE) "https://" else "http://") + host + addPort + requestURI
      }
      val proxyOptions = options.proxyOptions
      if (proxyOptions.username != null && proxyOptions.password != null) {
        if (headers == null) {
          headers = HttpHeaders.headers()
        }
        headers!!.add(
          "Proxy-Authorization", "Basic " + Base64.getEncoder()
            .encodeToString((proxyOptions.username + ":" + proxyOptions.password).toByteArray())
        )
      }
      req = HttpClientRequestImpl(
        this,
        responsePromise,
        useSSL,
        method,
        SocketAddress.inetSocketAddress(proxyOptions.port, proxyOptions.host),
        host,
        port,
        requestURI
      )
    } else {
      if (server == null) {
        server = SocketAddress.inetSocketAddress(port, host)
      }
      req = HttpClientRequestImpl(this, responsePromise, useSSL, method, server, host, port, requestURI)
    }
    if (headers != null) {
      req.headers().setAll(headers)
    }
    return req
  }

  @Synchronized
  private fun checkClosed() {
    check(!closed) { "Client is closed" }
  }

  protected fun finalize() { // Make sure this gets cleaned up if there are no more references to it
// so as not to leave connections and resources dangling until the system is shutdown
// which could make the JVM run out of file handles.
    close(Promise.promise<Void>() as PromiseInternal<Void>)
  }

  companion object {
    // Pattern to check we are not dealing with an absoluate URI
    private val ABS_URI_START_PATTERN =
      Pattern.compile("^\\p{Alpha}[\\p{Alpha}\\p{Digit}+.\\-]*:")
    private val log =
      LoggerFactory.getLogger(HttpClientImpl::class.java)
    private val EXPIRED_CHECKER =
      Consumer { endpoint: Endpoint<HttpClientConnection> -> (endpoint as ClientHttpStreamEndpoint).checkExpired() }
  }


}

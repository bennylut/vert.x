package io.vertx.core.http.impl

import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod

class DefaultHttpRedirectHandler(val client: HttpClientImpl) : HttpRedirectHandler {
  override fun next(resp: HttpClientResponse): HttpClientRequest? {
    val statusCode = resp.statusCode()
    val location = resp.getHeader(HttpHeaders.LOCATION)
    if (location != null && (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308)) {
      var m = resp.request().method()
      if (statusCode == 303) {
        m = HttpMethod.GET
      } else if (m !== HttpMethod.GET && m !== HttpMethod.HEAD) {
        return null
      }

      val uri = HttpUtils.resolveURIReference(resp.request().absoluteURI(), location)
      val ssl: Boolean
      var port = uri.port
      val protocol = uri.scheme
      val chend = protocol[protocol.length - 1]
      if (chend == 'p') {
        ssl = false
        if (port == -1) {
          port = 80
        }
      } else if (chend == 's') {
        ssl = true
        if (port == -1) {
          port = 443
        }
      } else {
        return null
      }

      var requestURI = uri.path
      val query = uri.query
      if (query != null) {
        requestURI += "?$query"
      }
      return client.createRequest(
        m,
        null,
        uri.host,
        port,
        ssl,
        requestURI,
        null
      )
    }
    return null

  }

}

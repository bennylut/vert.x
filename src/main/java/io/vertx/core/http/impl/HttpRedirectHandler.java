package io.vertx.core.http.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

public interface HttpRedirectHandler {
  HttpClientRequest next(HttpClientResponse resp);
}


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

package io.vertx.core.net;

import io.vertx.core.net.impl.SocketAddressImpl;

import java.net.InetSocketAddress;

/**
 * The address of a socket, an inet socket address or a domain socket address.
 * <p/>
 * Use {@link #inetSocketAddress(int, String)} to create an inet socket address and {@link #domainSocketAddress(String)}
 * to create a domain socket address
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SocketAddress {

  /**
   * Create a inet socket address, {@code host} must be non {@code null} and {@code port} must be between {@code 0}
   * and {@code 65536}.
   * <br/>
   * The {@code host} string can be an host name or an host address.
   * <br/>
   * No name resolution will be attempted.
   *
   * @param port the port
   * @param host the host
   * @return the created socket address
   */
  static SocketAddress inetSocketAddress(int port, String host) {
    return new SocketAddressImpl(port, host);
  }

  /**
   * Create a domain socket address from a {@code path}.
   *
   * @param path the address path
   * @return the created socket address
   */
  static SocketAddress domainSocketAddress(String path) {
    return new SocketAddressImpl(path);
  }

  /**
   * Create a inet socket address from a Java {@link InetSocketAddress}.
   * <br/>
   * No name resolution will be attempted.
   *
   * @param address the address
   * @return the created socket address
   */
  static SocketAddress inetSocketAddress(InetSocketAddress address) {
    return new SocketAddressImpl(address);
  }

  /**
   * Returns the host name when available or the IP address in string representation.
   * <br/>
   * Domain socket address returns {@code null}.
   *
   * @return the host address
   */
  String host();

  /**
   * Returns the host name when available or {@code null}
   * <br/>
   * Domain socket address returns {@code null}.
   *
   * @return the host name
   */
  String hostName();

  /**
   * Returns the host IP address when available or {@code null} as a String.
   * <br/>
   * Domain socket address returns {@code null}.
   *
   * @return the host address
   */
  String hostAddress();

  /**
   * @return the address port or {@code -1} for a domain socket
   */
  int port();

  /**
   * @return the domain socket path or {@code null} for a inet socket address.
   */
  String path();

  /**
   * @return {@code true} for an inet socket address
   */
  boolean isInetSocket();

  /**
   * @return {@code true} for an domain socket address
   */
  boolean isDomainSocket();

}

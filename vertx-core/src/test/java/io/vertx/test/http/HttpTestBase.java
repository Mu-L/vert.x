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

package io.vertx.test.http;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.test.proxy.TestProxyBase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTestBase extends VertxTestBase {

  public static final String DEFAULT_HTTP_HOST = "localhost";
  public static final int DEFAULT_HTTP_PORT = Integer.parseInt(System.getProperty("vertx.httpPort", "8080"));
  public static final String DEFAULT_HTTP_HOST_AND_PORT = DEFAULT_HTTP_HOST + ":" +  DEFAULT_HTTP_PORT;
  public static final String DEFAULT_HTTPS_HOST = "localhost";
  public static final int DEFAULT_HTTPS_PORT = Integer.parseInt(System.getProperty("vertx.httpsPort", "4043"));;
  public static final String DEFAULT_HTTPS_HOST_AND_PORT = DEFAULT_HTTPS_HOST + ":" + DEFAULT_HTTPS_PORT;;
  public static final String DEFAULT_TEST_URI = "some-uri";

  protected HttpServer server;
  protected HttpClientAgent client;
  protected TestProxyBase proxy;
  protected SocketAddress testAddress;
  protected RequestOptions requestOptions;
  private File tmp;

  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions();
  }

  public void setUp() throws Exception {
    super.setUp();
    HttpServerOptions baseServerOptions = createBaseServerOptions();
    testAddress = SocketAddress.inetSocketAddress(baseServerOptions.getPort(), baseServerOptions.getHost());
    requestOptions = new RequestOptions()
      .setHost(baseServerOptions.getHost())
      .setPort(baseServerOptions.getPort())
      .setURI(DEFAULT_TEST_URI);
    server = vertx.createHttpServer(baseServerOptions);
    client = vertx.createHttpClient(createBaseClientOptions());
  }

  /**
   * Override to disable domain sockets testing.
   */
  protected void configureDomainSockets() throws Exception {
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", TRANSPORT.implementation().supportsDomainSockets());
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
      requestOptions.setServer(testAddress);
    }
  }

  protected void tearDown() throws Exception {
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
  }

  @SuppressWarnings("unchecked")
  protected <E> Handler<E> noOpHandler() {
    return noOp;
  }

  private static final Handler noOp = e -> {
  };

  protected void startServer() throws Exception {
    startServer(vertx.getOrCreateContext());
  }

  protected void startServer(SocketAddress bindAddress) throws Exception {
    startServer(bindAddress, vertx.getOrCreateContext());
  }

  protected void startServer(HttpServer server) throws Exception {
    startServer(vertx.getOrCreateContext(), server);
  }

  protected void startServer(SocketAddress bindAddress, HttpServer server) throws Exception {
    startServer(bindAddress, vertx.getOrCreateContext(), server);
  }

  protected void startServer(Context context) throws Exception {
    startServer(context, server);
  }

  protected void startServer(SocketAddress bindAddress, Context context) throws Exception {
    startServer(bindAddress, context, server);
  }

  protected void startServer(Context context, HttpServer server) throws Exception {
    startServer(null, context, server);
  }

  protected void startServer(SocketAddress bindAddress, Context context, HttpServer server) throws Exception {
    CompletableFuture<Void> latch = new CompletableFuture<>();
    context.runOnContext(v -> {
      Future<HttpServer> fut;
      if (bindAddress != null) {
        fut = server.listen(bindAddress);
      } else {
        fut = server.listen();
      }
      fut.onComplete(ar -> {
        if (ar.succeeded()) {
          latch.complete(null);
        } else {
          latch.completeExceptionally(ar.cause());
        }
      });
    });
    try {
      latch.get(20, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      } else {
        throw e;
      }
    }
  }

  protected void startProxy(String username, ProxyType proxyType) throws Exception {
    if (proxyType == ProxyType.HTTP) {
      proxy = new HttpProxy();
    } else {
      proxy = new SocksProxy();
    }
    proxy.username(username);
    proxy.start(vertx);
  }

  protected File setupFile(String fileName, String content) throws Exception {
    Path dir = Files.createTempDirectory("vertx");
    File file = new File(dir.toFile(), fileName);
    if (file.exists()) {
      file.delete();
    }
    file.deleteOnExit();
    try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
      out.write(content);
    }
    return file;
  }
}

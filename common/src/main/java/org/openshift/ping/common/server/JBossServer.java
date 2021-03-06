/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.openshift.ping.common.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;
import org.jgroups.JChannel;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class JBossServer extends AbstractServer {
    private static final byte[] RESPONSE_BYTES = "OK".getBytes();

    private HttpServer server;

    public JBossServer(int port) {
        super(port);
    }

    public synchronized boolean start(JChannel channel) throws Exception {
        boolean started = false;
        if (server == null) {
            try {
                InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
                server = HttpServer.create(address, 0);
                server.setExecutor(Executors.newCachedThreadPool());
                server.createContext("/", new Handler(this));
                server.start();
                started = true;
            } catch (Exception e) {
                server = null;
                throw e;
            }
        }
        addChannel(channel);
        return started;
    }

    public synchronized boolean stop(JChannel channel) {
        boolean stopped = false;
        removeChannel(channel);
        if (server != null && !hasChannels()) {
            try {
                server.stop(0);
                stopped = true;
            } finally {
                server = null;
            }
        }
        return stopped;
    }

    private class Handler implements HttpHandler {
        private final Server server;

        private Handler(Server server) {
            this.server = server;
        }

        public void handle(HttpExchange exchange) throws IOException {
            try {
                try {
                    String clusterName = exchange.getRequestHeaders().getFirst(CLUSTER_NAME);
                    JChannel channel = server.getChannel(clusterName);
                    try (InputStream stream = exchange.getRequestBody()) {
                        handlePingRequest(channel, stream);
                    }
                    exchange.sendResponseHeaders(200, RESPONSE_BYTES.length);
                    exchange.getResponseBody().write(RESPONSE_BYTES);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } finally {
                exchange.close();
            }
        }
    }
}

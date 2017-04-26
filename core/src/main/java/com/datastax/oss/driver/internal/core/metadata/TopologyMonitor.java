/*
 * Copyright (C) 2017-2017 DataStax Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.internal.core.metadata;

import com.datastax.oss.driver.api.core.AsyncAutoCloseable;
import com.datastax.oss.driver.api.core.Cluster;
import com.datastax.oss.driver.api.core.addresstranslation.AddressTranslator;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.context.EventBus;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.control.ControlConnection;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Monitors the state of the Cassandra cluster.
 *
 * <p>It can either push {@link TopologyEvent topology events} to the rest of the driver (to do
 * that, retrieve the {@link EventBus}) from the {@link InternalDriverContext}), or receive requests
 * to refresh data about the nodes.
 *
 * <p>The default implementation uses the control connection: {@code TOPOLOGY_CHANGE} and {@code
 * STATUS_CHANGE} events on the connection are converted into {@code TopologyEvent}s, and node
 * refreshes are done with queries to system tables. If you prefer to rely on an external monitoring
 * tool, this can be completely overridden.
 */
public interface TopologyMonitor extends AsyncAutoCloseable {

  /**
   * Triggers the initialization of the monitor.
   *
   * <p>This will be invoked at startup, and is how the driver determines when it is "successfully
   * connected" to the Cassandra cluster. In particular, the initialization of the {@link Cluster}
   * instance depends on the result of this method.
   */
  CompletionStage<Void> init();

  /**
   * Invoked when the drive needs to refresh the information about an existing node. This is called
   * when the node was back and comes back up.
   *
   * <p>This will be invoked directly from a driver's internal thread; if the refresh involves
   * blocking I/O or heavy computations, it should be scheduled on a separate thread.
   *
   * @param node the node to refresh.
   * @return a future that completes with the information. If the monitor can't fulfill the request
   *     at this time, it should reply with {@link Optional#empty()}, and the driver will carry on
   *     with its current information.
   */
  CompletionStage<Optional<NodeInfo>> refreshNode(Node node);

  /**
   * Invoked when the driver needs to get information about a newly discovered node.
   *
   * <p>This will be invoked directly from a driver's internal thread; if the refresh involves
   * blocking I/O or heavy computations, it should be scheduled on a separate thread.
   *
   * @param connectAddress the address that the driver uses to connect to the node. This is the
   *     node's broadcast RPC address, <b>transformed by the {@link AddressTranslator}</b> if one is
   *     configured.
   * @return a future that completes with the information. If the monitor doesn't know any node with
   *     this address, it should reply with {@link Optional#empty()}; the new node will be ignored.
   */
  CompletionStage<Optional<NodeInfo>> getNewNodeInfo(InetSocketAddress connectAddress);

  /**
   * Invoked when the driver needs to refresh information about all the nodes.
   *
   * <p>This will be invoked directly from a driver's internal thread; if the refresh involves
   * blocking I/O or heavy computations, it should be scheduled on a separate thread.
   *
   * <p>The driver calls this at initialization; if that initial call fails, the load balancing
   * policy is not initialized, and the driver is unable to execute queries. You should schedule
   * retries to ensure that the call eventually succeeds (see how the default implementation does it
   * in {@link ControlConnection.SingleThreaded#onSuccessfulReconnect()}).
   *
   * @return a future that completes with the information. We assume that the full node list will
   *     always be returned in a single message (no paging).
   */
  CompletionStage<Iterable<NodeInfo>> refreshNodeList();
}

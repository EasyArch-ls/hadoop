/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.ozone.container.common.states.endpoint;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.DatanodeDetailsProto;
import org.apache.hadoop.ozone.container.common.statemachine
    .EndpointStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Register a container with SCM.
 */
public final class RegisterEndpointTask implements
    Callable<EndpointStateMachine.EndPointStates> {
  static final Logger LOG = LoggerFactory.getLogger(RegisterEndpointTask.class);

  private final EndpointStateMachine rpcEndPoint;
  private final Configuration conf;
  private Future<EndpointStateMachine.EndPointStates> result;
  private DatanodeDetailsProto datanodeDetailsProto;

  /**
   * Creates a register endpoint task.
   *
   * @param rpcEndPoint - endpoint
   * @param conf - conf
   */
  @VisibleForTesting
  public RegisterEndpointTask(EndpointStateMachine rpcEndPoint,
      Configuration conf) {
    this.rpcEndPoint = rpcEndPoint;
    this.conf = conf;

  }

  /**
   * Get the DatanodeDetailsProto Proto.
   *
   * @return DatanodeDetailsProto
   */
  public DatanodeDetailsProto getDatanodeDetailsProto() {
    return datanodeDetailsProto;
  }

  /**
   * Set the contiainerNodeID Proto.
   *
   * @param datanodeDetailsProto - Container Node ID.
   */
  public void setDatanodeDetailsProto(
      DatanodeDetailsProto datanodeDetailsProto) {
    this.datanodeDetailsProto = datanodeDetailsProto;
  }

  /**
   * Computes a result, or throws an exception if unable to do so.
   *
   * @return computed result
   * @throws Exception if unable to compute a result
   */
  @Override
  public EndpointStateMachine.EndPointStates call() throws Exception {

    if (getDatanodeDetailsProto() == null) {
      LOG.error("Container ID proto cannot be null in RegisterEndpoint task, " +
          "shutting down the endpoint.");
      return rpcEndPoint.setState(EndpointStateMachine.EndPointStates.SHUTDOWN);
    }

    rpcEndPoint.lock();
    try {

      // TODO : Add responses to the command Queue.
      rpcEndPoint.getEndPoint().register(datanodeDetailsProto,
          conf.getStrings(ScmConfigKeys.OZONE_SCM_NAMES));
      EndpointStateMachine.EndPointStates nextState =
          rpcEndPoint.getState().getNextState();
      rpcEndPoint.setState(nextState);
      rpcEndPoint.zeroMissedCount();
    } catch (IOException ex) {
      rpcEndPoint.logIfNeeded(ex
      );
    } finally {
      rpcEndPoint.unlock();
    }

    return rpcEndPoint.getState();
  }

  /**
   * Returns a builder class for RegisterEndPoint task.
   *
   * @return Builder.
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Builder class for RegisterEndPoint task.
   */
  public static class Builder {
    private EndpointStateMachine endPointStateMachine;
    private Configuration conf;
    private DatanodeDetails datanodeDetails;

    /**
     * Constructs the builder class.
     */
    public Builder() {
    }

    /**
     * Sets the endpoint state machine.
     *
     * @param rpcEndPoint - Endpoint state machine.
     * @return Builder
     */
    public Builder setEndpointStateMachine(EndpointStateMachine rpcEndPoint) {
      this.endPointStateMachine = rpcEndPoint;
      return this;
    }

    /**
     * Sets the Config.
     *
     * @param config - config
     * @return Builder.
     */
    public Builder setConfig(Configuration config) {
      this.conf = config;
      return this;
    }

    /**
     * Sets the NodeID.
     *
     * @param dnDetails - NodeID proto
     * @return Builder
     */
    public Builder setDatanodeDetails(DatanodeDetails dnDetails) {
      this.datanodeDetails = dnDetails;
      return this;
    }

    public RegisterEndpointTask build() {
      if (endPointStateMachine == null) {
        LOG.error("No endpoint specified.");
        throw new IllegalArgumentException("A valid endpoint state machine is" +
            " needed to construct RegisterEndPoint task");
      }

      if (conf == null) {
        LOG.error("No config specified.");
        throw new IllegalArgumentException("A valid configration is needed to" +
            " construct RegisterEndpoint task");
      }

      if (datanodeDetails == null) {
        LOG.error("No datanode specified.");
        throw new IllegalArgumentException("A vaild Node ID is needed to " +
            "construct RegisterEndpoint task");
      }

      RegisterEndpointTask task = new RegisterEndpointTask(this
          .endPointStateMachine, this.conf);
      task.setDatanodeDetailsProto(datanodeDetails.getProtoBufMessage());
      return task;
    }
  }
}
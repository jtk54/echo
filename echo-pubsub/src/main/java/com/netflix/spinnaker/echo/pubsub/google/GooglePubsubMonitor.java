/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pubsub.google;

import com.netflix.spinnaker.echo.config.google.GooglePubsubProperties;
import com.netflix.spinnaker.echo.pubsub.PollingMonitor;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.model.PubsubType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

/**
 * Monitors Google Cloud Pubsub subscriptions.
 */
@Slf4j
@Service
@Async
@ConditionalOnProperty("pubsub.google.enabled")
public class GooglePubsubMonitor implements PollingMonitor {

  private Long lastPoll;

  @Autowired
  private PubsubSubscribers pubsubSubscribers;

  @Autowired
  private GooglePubsubProperties pubsubProperties;

  @PreDestroy
  private void closeAsyncConnections() {
    log.info("Closing async connections for Google Pubsub subscribers");
    pubsubSubscribers
        .filteredMap(PubsubType.GOOGLE)
        .keySet()
        .parallelStream()
        .forEach(this::closeConnection);
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    // TODO(jacobkiefer): Register Echo as enabled on startup.
    log.info("Starting async connections for Google Pubsub subscribers");
    pubsubSubscribers
        .filteredMap(PubsubType.GOOGLE)
        .keySet()
        .parallelStream()
        .forEach(this::openConnection);
  }

  private void openConnection(String subscription) {
    log.info("Opening async connection to {}", subscription);
    lastPoll = System.currentTimeMillis();

    GooglePubsubSubscriber subscriber = (GooglePubsubSubscriber) pubsubSubscribers.get(subscription);
    subscriber.getSubscriber().startAsync();
  }

  private void closeConnection(String subscription) {
    GooglePubsubSubscriber subscriber = (GooglePubsubSubscriber) pubsubSubscribers.get(subscription);
    subscriber.getSubscriber().stopAsync();
  }

  @Override
  public String getName() {
    return "GooglePubsubMonitor";
  }

  @Override
  public boolean isInService() {
    return true;
  }

  @Override
  public Long getLastPoll() {
    return lastPoll;
  }

  @Override
  public int getPollInterval() {
    return -1;
  }
}

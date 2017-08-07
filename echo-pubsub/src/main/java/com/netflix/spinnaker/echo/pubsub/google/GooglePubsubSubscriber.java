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

import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageCache;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.model.PubsubType;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.threeten.bp.Duration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GooglePubsubSubscriber implements PubsubSubscriber {

  private String name;

  private String project;

  @Getter
  private Subscriber subscriber;

  private Integer ackDeadlineSeconds;

  static private final PubsubType pubsubType = PubsubType.GOOGLE;


  public GooglePubsubSubscriber(String name, String project, Subscriber subscriber, Integer ackDeadlineSeconds) {
    this.name = name;
    this.project = project;
    this.subscriber = subscriber;
    this.ackDeadlineSeconds = ackDeadlineSeconds;
  }

  @Override
  public PubsubType pubsubType() {
    return pubsubType;
  }

  public static GooglePubsubSubscriber buildSubscriber(String name,
                                                       String project,
                                                       String jsonPath,
                                                       Integer ackDeadlineSeconds,
                                                       PubsubMessageCache pubsubMessageCache) {
    Subscriber subscriber;
    GooglePubsubMessageReceiver messageReceiver = new GooglePubsubMessageReceiver(ackDeadlineSeconds, name, pubsubMessageCache);

    if (jsonPath != null && !jsonPath.isEmpty()) {
      Credentials credentials = null;
      try {
        credentials = ServiceAccountCredentials.fromStream(new FileInputStream(jsonPath));
      } catch (IOException e) {
        log.error("Could not import Google pubsub json credentials: {}", e.getMessage());
      }
      subscriber = Subscriber
          .defaultBuilder(SubscriptionName.create(project, name), messageReceiver)
          .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
          .setMaxAckExtensionPeriod(Duration.ofSeconds(0))
          .build();
    } else {
      subscriber = Subscriber.defaultBuilder(SubscriptionName.create(project, name), messageReceiver).build();
    }

    subscriber.addListener(new GooglePubsubFailureHandler(), MoreExecutors.directExecutor());

    return new GooglePubsubSubscriber(name, project, subscriber, ackDeadlineSeconds);
  }

  private static class GooglePubsubMessageReceiver implements MessageReceiver {

    private Integer ackDeadlineSeconds;

    private PubsubMessageCache pubsubMessageCache;

    private String subscriptionName;

    private NodeIdentity identity = new NodeIdentity();

    public GooglePubsubMessageReceiver(Integer ackDeadlineSeconds,
                                       String subscriptionName,
                                       PubsubMessageCache pubsubMessageCache) {
      this.ackDeadlineSeconds = ackDeadlineSeconds;
      this.subscriptionName = subscriptionName;
      this.pubsubMessageCache = pubsubMessageCache;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.getData().toStringUtf8();
      log.debug("Received message with payload: {}", messagePayload);
      String messageKey = pubsubMessageCache.makeKey(messagePayload, pubsubType, subscriptionName);
      // Acquire lock and set a high upper bound on message processing time.
      if (!pubsubMessageCache.acquireMessageLock(messageKey, identity.getIdentity(), 5 * TimeUnit.SECONDS.toMillis(ackDeadlineSeconds))) {
        consumer.nack();
        return;
      }

      consumer.ack();
      postEvent(message);
      // Expire key after max retention time, which is 7 days.
      // See https://cloud.google.com/pubsub/docs/subscriber for details.
      pubsubMessageCache.setMessageHandled(messageKey, identity.getIdentity(), TimeUnit.DAYS.toMillis(7));
    }

    private void postEvent(PubsubMessage message) {
      // TODO(jacobkiefer): Process this event since we're in Echo.
      log.info("Processed Google pubsub event with payload {}", message.getData().toStringUtf8());
    }
  }

  private static class GooglePubsubFailureHandler extends ApiService.Listener {
    @Override
    public void failed(ApiService.State from, Throwable failure) {
      log.error("Google pubsub listener failure caused by {}", failure.getMessage());
    }
  }
}

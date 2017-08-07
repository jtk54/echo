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

package com.netflix.spinnaker.echo.pubsub;

import com.netflix.spinnaker.echo.pubsub.model.PubsubType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Shared cache of received and handled pubsub message to synchronize clients.
 */
@Service
public class PubsubMessageCache {

  @Autowired
  private JedisPool jedisPool;

  private static final String SET_IF_NOT_EXIST = "NX";
  private static final String SET_EXPIRE_TIME_MILLIS = "PX";
  private static final String SUCCESS = "OK";

  public Boolean acquireMessageLock(String messageKey, String identifier, Long ackDeadlineMillis) {
    try (Jedis resource = jedisPool.getResource()) {
      String response = resource.set(messageKey, identifier, SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, ackDeadlineMillis);
      return SUCCESS.equals(response);
    }
  }

  public void setMessageHandled(String messageKey, String identifier, Long retentionDeadlineMillis) {
    try (Jedis resource = jedisPool.getResource()) {
      resource.psetex(messageKey, retentionDeadlineMillis, identifier);
    }
  }

  public String makeKey(String messagePayload, PubsubType pubsubType, String subscription) {
    /*
     * NOTE: hashCode() translates a String into a 32-bit integer. This is relatively small space;
     * however, we are assuming a low message influx -- tens a minute, which translates to ~10^4
     * per day. We persist handled messages for a week maximally, so we assume 7x10^4 messages accrued
     * per week. However -- 7x10^4 / 2^32 ~ 10^-5 which is sufficiently small for a collision probability.
     * We can strengthen the hash function if this becomes an issue.
     */

    String messageHash = messagePayload.hashCode() + "";
    return String.format("%s:echo-pubsub:%s:%s", pubsubType.toString(), subscription, messageHash);
  }
}

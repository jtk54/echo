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

import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.model.PubsubType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PubsubSubscribers {
  private Map<String, PubsubSubscriber> subscriberByName = new HashMap<String, PubsubSubscriber>();

  public void putAll(Map<String, PubsubSubscriber> newEntries) {
    subscriberByName.putAll(newEntries);
  }

  public Map<String, PubsubSubscriber> filteredMap(PubsubType pubsubType) {
    return subscriberByName.entrySet()
        .stream()
        .filter(entry -> entry.getValue().pubsubType().equals(pubsubType))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public PubsubSubscriber get(String topic) {
    return subscriberByName.get(topic);
  }
}

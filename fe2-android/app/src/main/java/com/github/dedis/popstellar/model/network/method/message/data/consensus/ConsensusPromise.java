package com.github.dedis.popstellar.model.network.method.message.data.consensus;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.Objects;
import com.google.gson.annotations.SerializedName;

public final class ConsensusPromise extends Data {

  private final String id;

  @SerializedName("message_id")
  private final String messageId;

  @SerializedName("created_at")
  private final long creation;

  @SerializedName("value")
  private final PromiseValue promiseValue;

  public ConsensusPromise(
      String id,
      String messageId,
      long creation,
      int acceptedTry,
      boolean acceptedValue,
      int promisedTry) {
    this.id = id;
    this.messageId = messageId;
    this.creation = creation;
    this.promiseValue = new PromiseValue(acceptedTry, acceptedValue, promisedTry);
  }

  @Override
  public String getObject() {
    return Objects.CONSENSUS.getObject();
  }

  @Override
  public String getAction() {
    return Action.PROMISE.getAction();
  }

  public String getId() {
    return id;
  }

  public String getMessageId() {
    return messageId;
  }

  public long getCreation() {
    return creation;
  }

  public PromiseValue getPromiseValue() {
    return promiseValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) return false;
    ConsensusPromise that = (ConsensusPromise) o;

    return creation == that.creation
        && java.util.Objects.equals(id, that.id)
        && java.util.Objects.equals(messageId, that.messageId)
        && java.util.Objects.equals(promiseValue, that.promiseValue);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(id, messageId, creation, promiseValue);
  }

  @Override
  public String toString() {
    return String.format(
        "ConsensusPromise{id='%s', message_id='%s', created_at=%s, value=%s}",
        id, messageId, creation, promiseValue);
  }
}

package com.github.dedis.popstellar.model.network.method.message.data.consensus;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.Objects;
import com.google.gson.annotations.SerializedName;

public final class ConsensusLearn extends Data {

  @SerializedName("message_id")
  private final String messageId;

  @SerializedName("created_at")
  private final long creation;

  @SerializedName("value")
  private final LearnValue learnValue;

  public ConsensusLearn(String messageId, long creation, boolean decision) {
    this.messageId = messageId;
    this.creation = creation;
    this.learnValue = new LearnValue(decision);
  }

  public String getMessageId() {
    return messageId;
  }

  public long getCreation() {
    return creation;
  }

  public LearnValue getLearnValue() {
    return learnValue;
  }

  @Override
  public String getObject() {
    return Objects.CONSENSUS.getObject();
  }

  @Override
  public String getAction() {
    return Action.LEARN.getAction();
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(messageId, creation, learnValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConsensusLearn that = (ConsensusLearn) o;

    return creation == that.creation
        && java.util.Objects.equals(messageId, that.messageId)
        && java.util.Objects.equals(learnValue, that.learnValue);
  }

  @Override
  public String toString() {
    return String.format(
        "ConsensusLearn{message_id='%s', created_at=%s, value=%s}",
        messageId, creation, learnValue);
  }
}

package com.github.dedis.popstellar.model.network.method.message.data.consensus;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.Objects;
import com.google.gson.annotations.SerializedName;

public final class ConsensusElectAccept extends Data {

  @SerializedName("instance_id")
  private final String instanceId;

  @SerializedName("message_id")
  private final String messageId;

  private final boolean accept;

  public ConsensusElectAccept(String instanceId, String messageId, boolean accept) {
    this.instanceId = instanceId;
    this.messageId = messageId;
    this.accept = accept;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getMessageId() {
    return messageId;
  }

  public boolean isAccept() {
    return accept;
  }

  @Override
  public String getObject() {
    return Objects.CONSENSUS.getObject();
  }

  @Override
  public String getAction() {
    return Action.ELECT_ACCEPT.getAction();
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(instanceId, messageId, accept);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConsensusElectAccept that = (ConsensusElectAccept) o;

    return accept == that.accept
        && java.util.Objects.equals(instanceId, that.instanceId)
        && java.util.Objects.equals(messageId, that.messageId);
  }

  @Override
  public String toString() {
    return String.format(
        "ConsensusElectAccept{instance_id='%s', message_id='%s', accept=%b}",
        instanceId, messageId, accept);
  }
}

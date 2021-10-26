package com.github.dedis.popstellar.model.network.method.message.data.consensus;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.Objects;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.List;

public class ConsensusPropose extends Data {

  @SerializedName("message_id")
  private final String messageId;

  @SerializedName("created_at")
  private final long creation;

  @SerializedName("value")
  private final ProposeValue proposeValue;

  @SerializedName("acceptor-signature")
  private final List<String> acceptorSignature;

  public ConsensusPropose(
      String messageId,
      long creation,
      int proposedTry,
      boolean proposedValue,
      List<String> acceptorSignature) {
    this.messageId = messageId;
    this.creation = creation;
    this.proposeValue = new ProposeValue(proposedTry, proposedValue);
    this.acceptorSignature = acceptorSignature;
  }

  @Override
  public String getObject() {
    return Objects.CONSENSUS.getObject();
  }

  @Override
  public String getAction() {
    return Action.PROPOSE.getAction();
  }

  public String getMessageId() {
    return messageId;
  }

  public long getCreation() {
    return creation;
  }

  public ProposeValue getProposeValue() {
    return proposeValue;
  }

  public List<String> getAcceptorSignature() {
    return acceptorSignature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConsensusPropose that = (ConsensusPropose) o;

    return creation == that.creation
        && java.util.Objects.equals(messageId, that.messageId)
        && java.util.Objects.equals(proposeValue, that.proposeValue)
        && java.util.Objects.equals(acceptorSignature, that.acceptorSignature);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(messageId, creation, proposeValue, acceptorSignature);
  }

  @Override
  public String toString() {
    return String.format(
        "ConsensusPropose{message_id='%s', created_at=%s, value=%s, acceptor-signature=%s}",
        messageId, creation, proposeValue, Arrays.toString(acceptorSignature.toArray()));
  }
}

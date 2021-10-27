package com.github.dedis.popstellar.model.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ConsensusNode {

  public enum State {
    FAILED,
    WAITING,
    STARTING,
    ACCEPTED
  }

  private final String publicKey;
  // contains messageId of consensus that this node has accepted
  private final Set<String> acceptedMessageIds;
  // list of all consensus that this node has created
  private final List<Consensus> consensuses;

  public ConsensusNode(String publicKey) {
    this.publicKey = publicKey;
    this.consensuses = new ArrayList<>();
    this.acceptedMessageIds = new HashSet<>();
  }

  public String getPublicKey() {
    return publicKey;
  }

  public Set<String> getAcceptedMessageIds() {
    return Collections.unmodifiableSet(acceptedMessageIds);
  }

  public Optional<Consensus> getLastConsensus(String keyId) {
    // get the consensus for the given key id, with the largest creation time
    return consensuses.stream()
        .filter(consensus -> consensus.getKey().getId().equals(keyId))
        .max((c1, c2) -> (int) (c1.getCreation() - c2.getCreation()));
  }

  public State getState(String keyId) {
    Optional<Consensus> lastConsensus = getLastConsensus(keyId);
    if (lastConsensus.isPresent()) {
      Consensus consensus = lastConsensus.get();
      if (consensus.isFailed()) {
        return State.FAILED;
      } else if (consensus.isAccepted()) {
        return State.ACCEPTED;
      } else {
        return State.STARTING;
      }
    } else {
      return State.WAITING;
    }
  }

  /**
   * Add the given consensus to this node. If it was already present, do nothing.
   *
   * @param consensus the consensus to add
   */
  public void addConsensus(Consensus consensus) {
    String messageId = consensus.getMessageId();
    if (consensuses.stream().map(Consensus::getMessageId).noneMatch(messageId::equals)) {
      consensuses.add(consensus);
    }
  }

  /**
   * Add the given messageId to the list of accepted consensus.
   *
   * @param consensusMessageId the messageId of the consensus to add
   */
  public void addMessageIdOfAnAcceptedConsensus(String consensusMessageId) {
    this.acceptedMessageIds.add(consensusMessageId);
  }

  @Override
  public String toString() {
    return String.format(
        "ConsensusNode{publicKey='%s', acceptedMessageIds='%s', consensuses='%s'}",
        publicKey, acceptedMessageIds, consensuses);
  }
}

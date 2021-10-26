package com.github.dedis.popstellar.utility.handler;

import android.util.Log;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.AcceptValue;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusAccept;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusElect;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusElectAccept;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusLearn;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusPrepare;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusPromise;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.ConsensusPropose;
import com.github.dedis.popstellar.model.network.method.message.data.consensus.PromiseValue;
import com.github.dedis.popstellar.model.objects.Consensus;
import com.github.dedis.popstellar.model.objects.Lao;
import com.github.dedis.popstellar.repository.LAORepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ConsensusHandler {

  private ConsensusHandler() {
    throw new IllegalStateException("Utility class");
  }

  public static final String TAG = ConsensusHandler.class.getSimpleName();

  public static boolean handleConsensusMessage(
      LAORepository laoRepository, String channel, Data data, String messageId, String senderPk) {
    Log.d(TAG, "handle Consensus message");

    switch (Objects.requireNonNull(Action.find(data.getAction()))) {
      case ELECT:
        return handleConsensusElect(
            laoRepository, channel, (ConsensusElect) data, messageId, senderPk);
      case ELECT_ACCEPT:
        return handleConsensusElectAccept(
            laoRepository, channel, (ConsensusElectAccept) data, messageId, senderPk);
      case PREPARE:
        return handleConsensusPrepare(laoRepository, channel, (ConsensusPrepare) data);
      case PROMISE:
        return handleConsensusPromise(laoRepository, channel, (ConsensusPromise) data);
      case PROPOSE:
        return handleConsensusPropose(laoRepository, channel, (ConsensusPropose) data);
      case ACCEPT:
        return handleConsensusAccept(laoRepository, channel, (ConsensusAccept) data);
      case LEARN:
        return handleConsensusLearn(laoRepository, channel, (ConsensusLearn) data);
      default:
        return true;
    }
  }

  public static boolean handleConsensusElect(
      LAORepository laoRepository,
      String channel,
      ConsensusElect consensusElect,
      String messageId,
      String senderPk) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Set<String> nodes = new HashSet<>(lao.getWitnesses());
    nodes.add(lao.getOrganizer());

    Consensus consensus =
        new Consensus(
            consensusElect.getCreation(), consensusElect.getKey(), consensusElect.getValue());

    consensus.setMessageId(messageId);
    consensus.setProposer(senderPk);
    consensus.setChannel(channel);
    consensus.setNodes(nodes);

    lao.updateConsensus(consensus);

    return false;
  }

  public static boolean handleConsensusElectAccept(
      LAORepository laoRepository,
      String channel,
      ConsensusElectAccept consensusElectAccept,
      String messageId,
      String senderPk) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusElectAccept.getMessageId());
    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "elect-accept for invalid messageId : " + consensusElectAccept.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();
    consensus.putAcceptorResponse(senderPk, messageId, consensusElectAccept.isAccept());

    boolean isProposer = consensus.getProposer().equals(laoRepository.getPublicKey());

    // If we are the proposer and if it can be accepted => send a prepare msg
    if (isProposer && consensus.canBeAccepted()) {
      int proposedTry = Math.max(consensus.getPromisedTry(), consensus.getProposedTry()) + 1;
      consensus.setProposedTry(proposedTry);
      long currentTime = Instant.now().getEpochSecond();
      Data data = new ConsensusPrepare(consensus.getMessageId(), currentTime, proposedTry);

      laoRepository.sendMessageGeneral(channel, data);
    }

    lao.updateConsensus(consensus);

    return false;
  }

  public static boolean handleConsensusPrepare(
      LAORepository laoRepository, String channel, ConsensusPrepare consensusPrepare) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusPrepare.getMessageId());

    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "prepare for invalid messageId : " + consensusPrepare.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();

    int proposedTry = consensusPrepare.getPrepareValue().getProposedTry();
    if (proposedTry > consensus.getPromisedTry()) {
      consensus.setPromisedTry(proposedTry);
      long currentTime = Instant.now().getEpochSecond();

      // TODO understand what each args should be
      // Data data = new ConsensusPromise("what is id", consensus.getMessageId(), currentTime, 0, false, 0);

      // laoRepository.sendMessageGeneral(channel, data);
    }

    return false;
  }

  public static boolean handleConsensusPromise(
      LAORepository laoRepository, String channel, ConsensusPromise consensusPromise) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusPromise.getMessageId());

    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "promise for invalid messageId : " + consensusPromise.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();

    boolean isProposer = consensus.getProposer().equals(laoRepository.getPublicKey());

    if (isProposer) {
      Set<ConsensusPromise> promises = consensus.getPromises();
      promises.add(consensusPromise);
      int n = consensus.getNodes().size();
      Object proposedValue;
      if (promises.size() > n / 2 + 1) {
        if (promises.stream().allMatch(p -> p.getPromiseValue().getAcceptedTry() == -1)) {
          // TODO check what "the proposer can select its own values" mean
          proposedValue = consensus.getProposedValue();
        } else {
          proposedValue =
              promises.stream()
                  .map(ConsensusPromise::getPromiseValue)
                  .max(Comparator.comparingInt(PromiseValue::getAcceptedTry))
                  .map(PromiseValue::isAcceptedValue)
                  .get();
        }

        // TODO get the signatures => need to store messageId and signatures not just the ConsensusPromise
        List<String> signatures = null;
        consensus.setProposedValue(proposedValue);
        promises.clear();

        long currentTime = Instant.now().getEpochSecond();
        // Data data = new ConsensusPropose(consensus.getMessageId(), currentTime,
        // consensus.getProposedTry(), proposedValue, signatures);

        // laoRepository.sendMessageGeneral(channel, data);

      }
    }

    return false;
  }

  public static boolean handleConsensusPropose(
      LAORepository laoRepository, String channel, ConsensusPropose consensusPropose) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusPropose.getMessageId());

    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "propose for invalid messageId : " + consensusPropose.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();

    int proposedTry = consensusPropose.getProposeValue().getProposedTry();
    boolean proposedValue = consensusPropose.getProposeValue().isProposedValue();
    if (proposedTry >= consensus.getProposedTry()) {
      consensus.setProposedTry(proposedTry);
      consensus.setProposedValue(proposedValue);

      long currentTime = Instant.now().getEpochSecond();
      Data data =
          new ConsensusAccept(consensus.getMessageId(), currentTime, proposedTry, proposedValue);

      laoRepository.sendMessageGeneral(channel, data);
    }

    return false;
  }

  public static boolean handleConsensusAccept(
      LAORepository laoRepository, String channel, ConsensusAccept consensusAccept) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusAccept.getMessageId());

    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "accept for invalid messageId : " + consensusAccept.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();

    boolean isProposer = consensus.getProposer().equals(laoRepository.getPublicKey());

    if (isProposer) {
      Set<ConsensusAccept> accepts = consensus.getAccepts();
      accepts.add(consensusAccept);

      int n = consensus.getNodes().size();

      if (!consensus.isDecided() && accepts.size() > n / 2 + 1) {
        consensus.setDecided(true);

        // Get the value accepted by the majority of the Accept message
        Object acceptedValue = accepts.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        consensus.setDecision(acceptedValue);

        long currentTime = Instant.now().getEpochSecond();

        // TODO check type of decision
        // Data data = new ConsensusLearn(consensus.getMessageId(), currentTime, acceptedValue);

        // laoRepository.sendMessageGeneral(channel, data);

      }
    }

    return false;
  }

  public static boolean handleConsensusLearn(
      LAORepository laoRepository, String channel, ConsensusLearn consensusLearn) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Optional<Consensus> consensusOpt = lao.getConsensus(consensusLearn.getMessageId());

    if (!consensusOpt.isPresent()) {
      Log.w(TAG, "learn for invalid messageId : " + consensusLearn.getMessageId());
      return true;
    }

    Consensus consensus = consensusOpt.get();

    if (!consensus.isDecided()) {
      consensus.setDecided(true);
      consensus.setDecision(consensusLearn.getLearnValue().isDecision());
    }

    lao.updateConsensus(consensus);

    return false;
  }
}

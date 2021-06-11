package com.github.dedis.student20_pop.model.data;

import java.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import com.github.dedis.student20_pop.model.Election;
import com.github.dedis.student20_pop.model.Lao;
import com.github.dedis.student20_pop.model.PendingUpdate;
import com.github.dedis.student20_pop.model.RollCall;
import com.github.dedis.student20_pop.model.event.EventState;
import com.github.dedis.student20_pop.model.network.GenericMessage;
import com.github.dedis.student20_pop.model.network.answer.Answer;
import com.github.dedis.student20_pop.model.network.answer.Error;
import com.github.dedis.student20_pop.model.network.answer.Result;
import com.github.dedis.student20_pop.model.network.method.Broadcast;
import com.github.dedis.student20_pop.model.network.method.Catchup;
import com.github.dedis.student20_pop.model.network.method.Publish;
import com.github.dedis.student20_pop.model.network.method.Subscribe;
import com.github.dedis.student20_pop.model.network.method.Unsubscribe;
import com.github.dedis.student20_pop.model.network.method.message.ElectionResultQuestion;
import com.github.dedis.student20_pop.model.network.method.message.MessageGeneral;
import com.github.dedis.student20_pop.model.network.method.message.PublicKeySignaturePair;
import com.github.dedis.student20_pop.model.network.method.message.data.Data;
import com.github.dedis.student20_pop.model.network.method.message.data.election.CastVote;
import com.github.dedis.student20_pop.model.network.method.message.data.election.ElectionEnd;
import com.github.dedis.student20_pop.model.network.method.message.data.election.ElectionResult;
import com.github.dedis.student20_pop.model.network.method.message.data.election.ElectionSetup;
import com.github.dedis.student20_pop.model.network.method.message.data.lao.CreateLao;
import com.github.dedis.student20_pop.model.network.method.message.data.lao.StateLao;
import com.github.dedis.student20_pop.model.network.method.message.data.lao.UpdateLao;
import com.github.dedis.student20_pop.model.network.method.message.data.message.WitnessMessage;
import com.github.dedis.student20_pop.model.network.method.message.data.rollcall.CloseRollCall;
import com.github.dedis.student20_pop.model.network.method.message.data.rollcall.CreateRollCall;
import com.github.dedis.student20_pop.model.network.method.message.data.rollcall.OpenRollCall;
import com.github.dedis.student20_pop.utility.security.Keys;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;
import com.google.crypto.tink.subtle.Ed25519Verify;
import com.google.gson.Gson;

import com.tinder.scarlet.WebSocket;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LAORepository {

  private static final String TAG = LAORepository.class.getSimpleName();
  private static volatile LAORepository INSTANCE = null;

  private final LAODataSource.Remote mRemoteDataSource;
  private final LAODataSource.Local mLocalDataSource;
  private final AndroidKeysetManager mKeysetManager;
  private final Gson mGson;

  private static final String ABSENT_ELECTION_ERROR_MESSAGE = "the election should be present when receiving a result";

  // A subject that represents unprocessed messages
  private Subject<GenericMessage> unprocessed;

  // State for LAO
  private Map<String, LAOState> laoById;

  // State for Messages
  private Map<String, MessageGeneral> messageById;

  // Outstanding subscribes
  private Map<Integer, String> subscribeRequests;

  private Set<String> subscribedChannels;

  // Outstanding catchups
  private Map<Integer, String> catchupRequests;

  // Outstanding create laos
  private Map<Integer, String> createLaoRequests;

  // Observable for view models that need access to all LAO Names
  private BehaviorSubject<List<Lao>> allLaoSubject;

  // Observable to subscribe to LAOs on reconnection
  private Observable<WebSocket.Event> websocketEvents;

  private Observable<GenericMessage> upstream;

  private LAORepository(
      @NonNull LAODataSource.Remote remoteDataSource,
      @NonNull LAODataSource.Local localDataSource,
      @NonNull AndroidKeysetManager keysetManager,
      @NonNull Gson gson) {
    mRemoteDataSource = remoteDataSource;
    mLocalDataSource = localDataSource;
    mKeysetManager = keysetManager;
    mGson = gson;

    laoById = new HashMap<>();
    messageById = new HashMap<>();
    subscribeRequests = new HashMap<>();
    catchupRequests = new HashMap<>();
    createLaoRequests = new HashMap<>();

    unprocessed = PublishSubject.create();

    allLaoSubject = BehaviorSubject.create();

    upstream = mRemoteDataSource.observeMessage().share();

    subscribedChannels = new HashSet<>();
    websocketEvents = mRemoteDataSource.observeWebsocket();

    // subscribe to incoming messages and the unprocessed message queue
    startSubscription();

    subscribeToWebsocketEvents();
  }

  private void subscribeToWebsocketEvents() {
    websocketEvents
        .subscribeOn(Schedulers.io())
        .filter(event -> event.getClass().equals(WebSocket.Event.OnConnectionOpened.class))
        .subscribe(event -> subscribedChannels.forEach(this::sendSubscribe));
  }

  private void startSubscription() {
    // We add a delay of 5 seconds to unprocessed messages to allow incoming messages to have a
    // higher priority
    Observable.merge(upstream, unprocessed.delay(5, TimeUnit.SECONDS))
        .subscribeOn(Schedulers.newThread())
        .subscribe(this::handleGenericMessage);
  }

  private void handleGenericMessage(GenericMessage genericMessage) {
    Log.d(TAG, "handling generic msg");
    if (genericMessage instanceof Error) {
      Error err = (Error) genericMessage;
      int id = err.getId();
      if (subscribeRequests.containsKey(id)) {
        subscribeRequests.remove(id);
      } else if (catchupRequests.containsKey(id)) {
        catchupRequests.remove(id);
      } else if (createLaoRequests.containsKey(id)) {
        createLaoRequests.remove(id);
      }
      return;
    }

    if (genericMessage instanceof Result) {
      Result result = (Result) genericMessage;

      int id = result.getId();
      Log.d(TAG, "handleGenericMessage: request id " + id);
      if (subscribeRequests.containsKey(id)) {
        String channel = subscribeRequests.get(id);
        subscribeRequests.remove(id);

        Lao lao = new Lao(channel);
        laoById.put(channel, new LAOState(lao));
        allLaoSubject.onNext(
            laoById.entrySet().stream()
                .map(x -> x.getValue().getLao())
                .collect(Collectors.toList()));

        Log.d(TAG, "posted allLaos to `allLaoSubject`");
        sendCatchup(channel);
      } else if (catchupRequests.containsKey(id)) {
        String channel = catchupRequests.get(id);
        catchupRequests.remove(id);

        Log.d(TAG, "got a catchup request in response to request id " + id);
        List<MessageGeneral> messages = result.getMessages().orElse(new ArrayList<>());
        Log.d(TAG, "messages length: " + messages.size());
        for (MessageGeneral msg : messages) {
          boolean enqueue = handleMessage(channel, msg);
          if (enqueue) {
            unprocessed.onNext(genericMessage);
          }
        }
      } else if (createLaoRequests.containsKey(id)) {
        String channel = createLaoRequests.get(id);
        createLaoRequests.remove(id);

        Lao lao = new Lao(channel);
        laoById.put(channel, new LAOState(lao));
        allLaoSubject.onNext(
            laoById.entrySet().stream()
                .map(x -> x.getValue().getLao())
                .collect(Collectors.toList()));
        Log.d(TAG, "createLaoRequest contains this id. posted allLaos to `allLaoSubject`");
        sendSubscribe(channel);
        sendCatchup(channel);
      }

      return;
    }

    Log.d(TAG, "Got a broadcast");

    // We've a Broadcast
    Broadcast broadcast = (Broadcast) genericMessage;
    MessageGeneral message = broadcast.getMessage();
    String channel = broadcast.getChannel();

    Log.d(TAG, "Broadcast channel: " + channel + " message " + message.getMessageId());

    boolean enqueue = handleMessage(channel, message);
    if (enqueue) {
      unprocessed.onNext(genericMessage);
    }
  }

  /**
   * @param channel the channel on which the message was received
   * @param message the message that was received
   * @return true if the message cannot be processed and false otherwise
   */
  private boolean handleMessage(String channel, MessageGeneral message) {
    // Put the message in the state
    messageById.put(message.getMessageId(), message);

    String senderPk = message.getSender();

    Data data = message.getData();
    Log.d(TAG, "data with class: " + data.getClass());
    boolean enqueue = false;
    if (data instanceof CreateLao) {
      enqueue = handleCreateLao(channel, (CreateLao) data);
    } else if (data instanceof UpdateLao) {
      enqueue = handleUpdateLao(channel, message.getMessageId(), (UpdateLao) data);
    } else if (data instanceof ElectionSetup) {
      enqueue = handleElectionSetup(channel, (ElectionSetup) data);
    } else if (data instanceof StateLao) {
      enqueue = handleStateLao(channel, (StateLao) data);
    } else if (data instanceof CreateRollCall) {
      enqueue = handleCreateRollCall(channel, (CreateRollCall) data);
    } else if (data instanceof OpenRollCall) {
      enqueue = handleOpenRollCall(channel, (OpenRollCall) data);
    } else if (data instanceof CloseRollCall) {
      enqueue = handleCloseRollCall(channel, (CloseRollCall) data);
    } else if (data instanceof WitnessMessage) {
      enqueue = handleWitnessMessage(channel, senderPk, (WitnessMessage) data);
    } else if (data instanceof ElectionResult) {
      enqueue = handleElectionResult(channel, (ElectionResult) data);
    } else if (data instanceof ElectionEnd) {
      enqueue = handleElectionEnd(channel);
    } else if (data instanceof  CastVote) {
      enqueue = handleCastVote(channel, (CastVote) data, senderPk, message.getMessageId());
    } else {
      Log.d(TAG, "cannot handle message with data" + data.getClass());
      enqueue = true;
    }

    // Trigger an onNext
    if (!(data instanceof WitnessMessage)) {
      LAOState laoState = laoById.get(channel);
      laoState.publish();
      if (data instanceof StateLao || data instanceof CreateLao) {
        allLaoSubject.onNext(
            laoById.entrySet().stream()
                .map(x -> x.getValue().getLao())
                .collect(Collectors.toList()));
      }
    }
    return enqueue;
  }

  /**
   * Retrieves the Election in a given channel
   * @param channel the channel on which the election was created
   * @return the election corresponding to this channel
   */
  private Election getElectionByChannel(String channel) {
    Lao lao = getLaoByChannel(channel);
    Optional<Election> electionOption = lao.getElection(channel.split("/")[3]);
    if (!electionOption.isPresent()) throw new IllegalArgumentException("the election should be present when receiving a result");
    return electionOption.get();
  }

  /**
   * Retrieves the Lao in a given channel
   * @param channel the channel on which the Lao was created
   * @return the Lao corresponding to this channel
   */
  private Lao getLaoByChannel(String channel) {
    String[] split = channel.split("/");
    return laoById.get("/root/" + split[2]).getLao();
  }

  /**
   * Checks that a given channel corresponds to a LAO channel, i.e /root/laoId
   * @param channel the channel we want to check
   * @return true if the channel is a lao channel, false otherwise
   */
  private boolean isLaoChannel(String channel) {
    return channel.split("/").length == 3;
  }

  private boolean handleElectionEnd(String channel) {
    Lao lao = getLaoByChannel(channel);
    Election election = getElectionByChannel(channel);
    election.setEnded(true);
    lao.updateElection(election.getId(), election);
    return false;
  }

  private boolean handleCastVote(String channel, CastVote data, String senderPk, String messageId) {
    Lao lao = getLaoByChannel(channel);
    Election election = getElectionByChannel(channel);

    //We ignore the vote iff the election is ended and the cast vote message was created after the end timestamp
    if (election.getEndTimestamp() >= data.getCreation() || !election.isEnded()) {
      /* We retrieve previous cast vote message stored for the given sender, and consider the new vote iff its creation
      is after (hence preventing reordering attacks) */
      Optional<String> previousMessageIdOption = election.getMessageMap().entrySet().stream()
              .filter(entry -> senderPk.equals(entry.getValue())).map(Map.Entry::getKey).findFirst();
      //If there is no previous message, or that this message is the youngest of all received messages, then we consider the votes
      if (!previousMessageIdOption.isPresent() ||
              ((CastVote) messageById.get(previousMessageIdOption.get()).getData()).getCreation() <= data.getCreation()) {
        election.putVotesBySender(senderPk, data.getVotes());
        election.putSenderByMessageId(senderPk, messageId);
        lao.updateElection(election.getId(), election);
      }
    }
    return false;
  }

  private boolean handleElectionResult(String channel, ElectionResult data) {
    Lao lao = getLaoByChannel(channel);
    Election election = getElectionByChannel(channel);

    List<ElectionResultQuestion> questions = data.getElectionQuestionResults();
    if (questions.isEmpty()) throw new IllegalArgumentException("the questions results shouldn't be empty");
    election.setResults(questions.get(0).getResults());
    election.setResultsReady(true);
    lao.updateElection(election.getId(), election);
    return false;
  }


  private boolean handleCreateLao(String channel, CreateLao createLao) {
    Lao lao = laoById.get(channel).getLao();

    lao.setName(createLao.getName());
    lao.setCreation(createLao.getCreation());
    lao.setLastModified(createLao.getCreation());
    lao.setOrganizer(createLao.getOrganizer());
    lao.setId(createLao.getId());

    Log.d(
        TAG,
        "Setting name as "
            + createLao.getName()
            + " creation time as "
            + createLao.getCreation()
            + " lao channel is "
            + channel);

    return false;
  }

  private boolean handleUpdateLao(String channel, String messageId, UpdateLao updateLao) {
    Lao lao = laoById.get(channel).getLao();

    if (lao.getLastModified() > updateLao.getLastModified()) {
      // the current state we have is more up to date
      return false;
    }

    // TODO: This assumes that LAOs always have some witnesses to begin with. Our current
    // implementation
    // begins with zero witnesses. As a result, we should update the LAO immediately if the current
    // state has no witnesses and not wait for witness messages
    lao.getPendingUpdates().add(new PendingUpdate(updateLao.getLastModified(), messageId));
    return false;
  }

  private boolean handleStateLao(String channel, StateLao stateLao) {
    Lao lao = laoById.get(channel).getLao();

    Log.d(TAG, "received: " + stateLao.getName());
    if (!messageById.containsKey(stateLao.getModificationId())) {
      // queue it if we haven't received the update message yet
      return true;
    }

    // Verify signatures
    for (PublicKeySignaturePair pair : stateLao.getModificationSignatures()) {
      PublicKeyVerify verifier = new Ed25519Verify(pair.getWitness());
      try {
        verifier.verify(
            pair.getSignature(), Base64.getUrlDecoder().decode(stateLao.getModificationId()));
      } catch (GeneralSecurityException e) {
        Log.d(TAG, "failed to verify witness signature in lao/state_lao");
        return false;
      }
    }

    // TODO: verify if lao/state_lao is consistent with the lao/update message

    lao.setId(stateLao.getId());
    lao.setWitnesses(stateLao.getWitnesses());
    lao.setName(stateLao.getName());
    lao.setLastModified(stateLao.getLastModified());
    lao.setModificationId(stateLao.getModificationId());

    // Now we're going to remove all pending updates which came prior to this state lao
    long targetTime = stateLao.getLastModified();
    lao.getPendingUpdates()
        .removeIf(pendingUpdate -> pendingUpdate.getModificationTime() <= targetTime);

    return false;
  }

  private boolean handleElectionSetup(String channel, ElectionSetup electionSetup) {
    //election setup msg should be sent on an LAO channel
    if (isLaoChannel(channel)) {
      Lao lao = laoById.get(channel).getLao();
      Log.d(TAG, "handleElectionSetup: " + channel + " name " + electionSetup.getName());

      Election election = new Election();
      election.setId(electionSetup.getId());
      election.setName(electionSetup.getName());
      election.setCreation(electionSetup.getCreation());
      election.setChannel(channel + "/" + election.getId());
      election.setElectionQuestions(electionSetup.getQuestions());

      election.setStart(electionSetup.getStartTime());
      election.setEnd(electionSetup.getEndTime());

      //Once the election is created, we subscribe to the election channel
      sendSubscribe(election.getChannel())
              .observeOn(AndroidSchedulers.mainThread())
              .timeout(3, TimeUnit.SECONDS)
              .subscribe(
                      answer -> {
                        if (answer instanceof Result) {
                          Log.d(TAG, "got success result for subscribe to election");
                        } else {
                          Log.d(
                                  TAG,
                                  "got failure result for subscribe to election: "
                                          + ((Error) answer).getError().getDescription());
                        }
                      },
                      throwable -> {
                        Log.d(TAG, "timed out waiting for a response for subscribe to election", throwable);
                      });
      Log.d(TAG, "election id being put is " + election.getId());
      lao.putElection(election);
    }
    return false;
  }

    private boolean handleCreateRollCall(String channel, CreateRollCall createRollCall) {
    Lao lao = laoById.get(channel).getLao();
    Log.d(TAG, "handleCreateRollCall: " + channel + " name " + createRollCall.getName());

    RollCall rollCall = new RollCall();
    rollCall.setId(createRollCall.getId());
    rollCall.setPersistentId(createRollCall.getId());
    rollCall.setCreation(createRollCall.getCreation());
    rollCall.setState(EventState.CREATED);
    rollCall.setStart(createRollCall.getProposedStart());
    rollCall.setEnd(createRollCall.getProposedEnd());
    rollCall.setName(createRollCall.getName());
    rollCall.setLocation(createRollCall.getLocation());

    rollCall.setLocation(createRollCall.getLocation());
    rollCall.setDescription(createRollCall.getDescription().orElse(""));

    lao.updateRollCall(rollCall.getId(), rollCall);

    return false;
  }

  private boolean handleOpenRollCall(String channel, OpenRollCall openRollCall) {
    Lao lao = laoById.get(channel).getLao();
    Log.d(TAG, "handleOpenRollCall: " + channel);
    Log.d(TAG, openRollCall.getOpens());

    String updateId = openRollCall.getUpdateId();
    String opens = openRollCall.getOpens();

    Optional<RollCall> rollCallOptional = lao.getRollCall(opens);
    if (!rollCallOptional.isPresent()) {
      return true;
    }

    RollCall rollCall = rollCallOptional.get();
    rollCall.setStart(openRollCall.getOpenedAt());
    rollCall.setState(EventState.OPENED);
    // We might be opening a closed one
    rollCall.setEnd(0);
    rollCall.setId(updateId);

    lao.updateRollCall(opens, rollCall);
    return false;
  }

  private boolean handleCloseRollCall(String channel, CloseRollCall closeRollCall) {
    Lao lao = laoById.get(channel).getLao();
    Log.d(TAG, "handleCloseRollCall: " + channel);

    String updateId = closeRollCall.getUpdateId();
    String closes = closeRollCall.getCloses();

    Optional<RollCall> rollCallOptional = lao.getRollCall(closes);
    if (!rollCallOptional.isPresent()) {
      return true;
    }

    RollCall rollCall = rollCallOptional.get();
    rollCall.setEnd(closeRollCall.getClosedAt());
    rollCall.setId(updateId);
    rollCall.getAttendees().addAll(closeRollCall.getAttendees());
    rollCall.setState(EventState.CLOSED);

    lao.updateRollCall(closes, rollCall);
    return false;
  }

  private boolean handleWitnessMessage(String channel, String senderPk, WitnessMessage message) {
    String messageId = message.getMessageId();
    String signature = message.getSignature();

    byte[] senderPkBuf = Base64.getUrlDecoder().decode(senderPk);
    byte[] signatureBuf = Base64.getUrlDecoder().decode(signature);

    // Verify signature
    try {
      PublicKeyVerify verifier = new Ed25519Verify(senderPkBuf);
      verifier.verify(signatureBuf, Base64.getUrlDecoder().decode(messageId));
    } catch (GeneralSecurityException e) {
      Log.d(TAG, "failed to verify witness signature " + e.getMessage());
      return false;
    }

    if (messageById.containsKey(messageId)) {
      // Update the message
      MessageGeneral msg = messageById.get(messageId);
      msg.getWitnessSignatures().add(new PublicKeySignaturePair(senderPkBuf, signatureBuf));

      Lao lao = laoById.get(channel).getLao();
      Set<PendingUpdate> pendingUpdates = lao.getPendingUpdates();
      if (pendingUpdates.contains(messageId)) {
        // We're waiting to collect signatures for this one

        // Let's check if we have enough signatures
        Set<String> signaturesCollectedSoFar =
            msg.getWitnessSignatures().stream()
                .map(ob -> Base64.getUrlEncoder().encodeToString(ob.getWitness()))
                .collect(Collectors.toSet());
        if (lao.getWitnesses().equals(signaturesCollectedSoFar)) {

          // We send a state lao if we are the organizer
          try {
            KeysetHandle handle = mKeysetManager.getKeysetHandle().getPublicKeysetHandle();
            String ourKey = Keys.getEncodedKey(handle);
            if (ourKey.equals(lao.getOrganizer())) {
              UpdateLao updateLao = (UpdateLao) msg.getData();
              StateLao stateLao =
                  new StateLao(
                      lao.getId(),
                      updateLao.getName(),
                      lao.getCreation(),
                      updateLao.getLastModified(),
                      lao.getOrganizer(),
                      messageId,
                      updateLao.getWitnesses(),
                      msg.getWitnessSignatures());

              byte[] ourPkBuf = Base64.getUrlDecoder().decode(ourKey);
              PublicKeySign signer =
                  mKeysetManager.getKeysetHandle().getPrimitive(PublicKeySign.class);
              MessageGeneral stateLaoMsg = new MessageGeneral(ourPkBuf, stateLao, signer, mGson);

              sendPublish(channel, stateLaoMsg);
            }
          } catch (GeneralSecurityException e) {
            Log.d(TAG, "failed to get keyset handle: " + e.getMessage());
          } catch (IOException e) {
            Log.d(TAG, "failed to get encoded public key: " + e.getMessage());
          }
        }
      }

      return false;
    }

    return true;
  }

  public static LAORepository getInstance(
      LAODataSource.Remote laoRemoteDataSource,
      LAODataSource.Local localDataSource,
      AndroidKeysetManager keysetManager,
      Gson gson) {
    if (INSTANCE == null) {
      synchronized (LAORepository.class) {
        if (INSTANCE == null) {
          INSTANCE = new LAORepository(laoRemoteDataSource, localDataSource, keysetManager, gson);
        }
      }
    }
    return INSTANCE;
  }

  public static void destroyInstance() {
    INSTANCE = null;
  }

  public Single<Answer> sendCatchup(String channel) {
    int id = mRemoteDataSource.incrementAndGetRequestId();
    Catchup catchup = new Catchup(channel, id);

    catchupRequests.put(id, channel);
    Single<Answer> answer = createSingle(id);
    mRemoteDataSource.sendMessage(catchup);
    return answer;
  }

  public Single<Answer> sendPublish(String channel, MessageGeneral message) {
    int id = mRemoteDataSource.incrementAndGetRequestId();
    Single<Answer> answer = createSingle(id);

    Publish publish = new Publish(channel, id, message);
    if (message.getData() instanceof CreateLao) {
      CreateLao data = (CreateLao) message.getData();
      createLaoRequests.put(id, "/root/" + data.getId());
    }
    // Uncomment to test display without message from Backend

    /*
    else {
      if(message.getData() instanceof ElectionSetup) {
        handleElectionSetup(channel,(ElectionSetup) message.getData());
      }
    }
    */

    mRemoteDataSource.sendMessage(publish);
    return answer;
  }

  public Single<Answer> sendSubscribe(String channel) {

    int id = mRemoteDataSource.incrementAndGetRequestId();

    Subscribe subscribe = new Subscribe(channel, id);

    subscribeRequests.put(id, channel);

    Single<Answer> answer = createSingle(id);
    mRemoteDataSource.sendMessage(subscribe);
    Log.d(TAG, "sending subscribe");

    subscribedChannels.add(channel);

    return answer;
  }

  public Single<Answer> sendUnsubscribe(String channel) {
    int id = mRemoteDataSource.incrementAndGetRequestId();

    Unsubscribe unsubscribe = new Unsubscribe(channel, id);

    Single<Answer> answer = createSingle(id);
    mRemoteDataSource.sendMessage(unsubscribe);
    return answer;
  }

  private Single<Answer> createSingle(int id) {
    Single<Answer> res =
        upstream
            .filter(
                genericMessage -> {
                  if (genericMessage instanceof Answer) {
                    Log.d(TAG, "request id: " + ((Answer) genericMessage).getId());
                  }
                  return genericMessage instanceof Answer
                      && ((Answer) genericMessage).getId() == id;
                })
            .map(
                genericMessage -> {
                  return (Answer) genericMessage;
                })
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .cache();

    return res;
  }

  public Observable<List<Lao>> getAllLaos() {
    return allLaoSubject;
  }

  public Observable<Lao> getLaoObservable(String channel) {
    Log.d(TAG, "LaoIds we have are: " + laoById.keySet());
    return laoById.get(channel).getObservable();
  }
}

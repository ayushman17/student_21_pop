package com.github.dedis.popstellar.utility.handler;

import android.util.Log;

import com.github.dedis.popstellar.model.network.method.message.data.Action;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.method.message.data.rollcall.CloseRollCall;
import com.github.dedis.popstellar.model.network.method.message.data.rollcall.CreateRollCall;
import com.github.dedis.popstellar.model.network.method.message.data.rollcall.OpenRollCall;
import com.github.dedis.popstellar.model.objects.Lao;
import com.github.dedis.popstellar.model.objects.RollCall;
import com.github.dedis.popstellar.model.objects.WitnessMessage;
import com.github.dedis.popstellar.model.objects.event.EventState;
import com.github.dedis.popstellar.repository.LAORepository;
import com.github.dedis.popstellar.utility.error.DataHandlingException;
import com.github.dedis.popstellar.utility.error.InvalidDataException;
import com.github.dedis.popstellar.utility.error.UnhandledDataTypeException;
import com.github.dedis.popstellar.utility.error.UnknownDataActionException;

import java.util.Optional;

/** Roll Call messages handler class */
public final class RollCallHandler {

  public static final String TAG = RollCallHandler.class.getSimpleName();

  private static final String ROLL_CALL_NAME = "Roll Call Name : ";
  private static final String MESSAGE_ID = "Message ID : ";

  private RollCallHandler() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Process a Roll Call message.
   *
   * @param laoRepository the repository to access the LAO of the channel
   * @param channel the channel on which the message was received
   * @param data the data of the message received
   * @param messageId the ID of the message received
   */
  public static void handleRollCallMessage(
      LAORepository laoRepository, String channel, Data data, String messageId)
      throws DataHandlingException {
    Log.d(TAG, "handle Roll Call message id=" + messageId);

    Action action = Action.find(data.getAction());
    if (action == null) throw new UnknownDataActionException(data);

    switch (action) {
      case CREATE:
        handleCreateRollCall(laoRepository, channel, (CreateRollCall) data, messageId);
        break;
      case OPEN:
      case REOPEN:
        handleOpenRollCall(laoRepository, channel, (OpenRollCall) data, messageId);
        break;
      case CLOSE:
        handleCloseRollCall(laoRepository, channel, (CloseRollCall) data, messageId);
        break;
      default:
        throw new UnhandledDataTypeException(data, action.getAction());
    }
  }

  /**
   * Process a CreateRollCall message.
   *
   * @param laoRepository the repository to access the LAO of the channel
   * @param channel the channel on which the message was received
   * @param createRollCall the message that was received
   */
  public static void handleCreateRollCall(
      LAORepository laoRepository,
      String channel,
      CreateRollCall createRollCall,
      String messageId) {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Log.d(TAG, "handleCreateRollCall: " + channel + " name " + createRollCall.getName());

    RollCall rollCall = new RollCall(createRollCall.getId());
    rollCall.setCreation(createRollCall.getCreation());
    rollCall.setState(EventState.CREATED);
    rollCall.setStart(createRollCall.getProposedStart());
    rollCall.setEnd(createRollCall.getProposedEnd());
    rollCall.setName(createRollCall.getName());
    rollCall.setLocation(createRollCall.getLocation());

    rollCall.setLocation(createRollCall.getLocation());
    rollCall.setDescription(createRollCall.getDescription().orElse(""));

    lao.updateRollCall(rollCall.getId(), rollCall);

    lao.updateWitnessMessage(messageId, createRollCallWitnessMessage(messageId, rollCall));
  }

  /**
   * Process an OpenRollCall message.
   *
   * @param laoRepository the repository to access the LAO of the channel
   * @param channel the channel on which the message was received
   * @param openRollCall the message that was received
   */
  public static void handleOpenRollCall(
      LAORepository laoRepository, String channel, OpenRollCall openRollCall, String messageId)
      throws DataHandlingException {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Log.d(TAG, "handleOpenRollCall: " + channel + " msg=" + openRollCall);

    String updateId = openRollCall.getUpdateId();
    String opens = openRollCall.getOpens();

    Optional<RollCall> rollCallOptional = lao.getRollCall(opens);
    if (!rollCallOptional.isPresent()) {
      Log.w(TAG, "Cannot find roll call to open : " + opens);
      throw new InvalidDataException(openRollCall, "open id", opens);
    }

    RollCall rollCall = rollCallOptional.get();
    rollCall.setStart(openRollCall.getOpenedAt());
    rollCall.setState(EventState.OPENED);
    // We might be opening a closed one
    rollCall.setEnd(0);
    rollCall.setId(updateId);

    lao.updateRollCall(opens, rollCall);

    lao.updateWitnessMessage(messageId, openRollCallWitnessMessage(messageId, rollCall));
  }

  /**
   * Process a CloseRollCall message.
   *
   * @param laoRepository the repository to access the LAO of the channel
   * @param channel the channel on which the message was received
   * @param closeRollCall the message that was received
   */
  public static void handleCloseRollCall(
      LAORepository laoRepository, String channel, CloseRollCall closeRollCall, String messageId)
      throws DataHandlingException {
    Lao lao = laoRepository.getLaoByChannel(channel);
    Log.d(TAG, "handleCloseRollCall: " + channel);

    String updateId = closeRollCall.getUpdateId();
    String closes = closeRollCall.getCloses();

    Optional<RollCall> rollCallOptional = lao.getRollCall(closes);
    if (!rollCallOptional.isPresent()) {
      Log.w(TAG, "Cannot find roll call to close : " + closes);
      throw new InvalidDataException(closeRollCall, "close id", closes);
    }

    RollCall rollCall = rollCallOptional.get();
    rollCall.setEnd(closeRollCall.getClosedAt());
    rollCall.setId(updateId);
    rollCall.getAttendees().addAll(closeRollCall.getAttendees());
    rollCall.setState(EventState.CLOSED);

    lao.updateRollCall(closes, rollCall);

    lao.updateWitnessMessage(messageId, closeRollCallWitnessMessage(messageId, rollCall));
  }

  public static WitnessMessage createRollCallWitnessMessage(String messageId, RollCall rollCall) {
    WitnessMessage message = new WitnessMessage(messageId);
    message.setTitle("New Roll Call was created");
    message.setDescription(
        ROLL_CALL_NAME
            + rollCall.getName()
            + "\n"
            + "Roll Call ID : "
            + rollCall.getId()
            + "\n"
            + "Location : "
            + rollCall.getLocation()
            + "\n"
            + MESSAGE_ID
            + messageId);

    return message;
  }

  public static WitnessMessage openRollCallWitnessMessage(String messageId, RollCall rollCall) {
    WitnessMessage message = new WitnessMessage(messageId);
    message.setTitle("A Roll Call was opened");
    message.setDescription(
        ROLL_CALL_NAME
            + rollCall.getName()
            + "\n"
            + "Updated ID : "
            + rollCall.getId()
            + "\n"
            + MESSAGE_ID
            + messageId);

    return message;
  }

  public static WitnessMessage closeRollCallWitnessMessage(String messageId, RollCall rollCall) {
    WitnessMessage message = new WitnessMessage(messageId);
    message.setTitle("A Roll Call was closed");
    message.setDescription(
        ROLL_CALL_NAME
            + rollCall.getName()
            + "\n"
            + "Updated ID : "
            + rollCall.getId()
            + "\n"
            + MESSAGE_ID
            + messageId);

    return message;
  }
}

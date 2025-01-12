package com.github.dedis.popstellar.model.network.method.message.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Enumerates all possible messages actions */
public enum Action {
  CREATE("create"),
  END("end"),
  RESULT("result"),
  SETUP("setup"),
  UPDATE("update_properties"),
  STATE("state"),
  WITNESS("witness"),
  OPEN("open"),
  REOPEN("reopen"),
  CLOSE("close"),
  CAST_VOTE("cast_vote"),
  ELECT("elect"),
  ELECT_ACCEPT("elect-accept"),
  LEARN("learn"),
  ADD("add"),
  ADD_BROADCAST("addBroadcast");

  private static final List<Action> ALL = Collections.unmodifiableList(Arrays.asList(values()));
  private final String action;

  /**
   * Constructor for a message Action
   *
   * @param action the name of the action
   */
  Action(String action) {
    this.action = action;
  }

  /** Returns the name of the Action. */
  public String getAction() {
    return action;
  }

  /**
   * Find a given Action
   *
   * @param searched the searched action
   * @return the corresponding enum action
   */
  public static Action find(String searched) {
    for (Action action : ALL) {
      if (action.getAction().equals(searched)) {
        return action;
      }
    }

    return null;
  }
}

import {
  createSlice, createSelector, PayloadAction, Draft,
} from '@reduxjs/toolkit';
import { REHYDRATE } from 'redux-persist';

import { Hash, Lao, LaoState } from 'model/objects';
import { getKeyPairState } from './KeyPairReducer';
// import laosData from 'res/laoData';

/**
 * Reducer & associated function implementation to store all known LAOs
 * and a reference to the current open one.
 */

interface LaoReducerState {
  byId: Record<string, LaoState>,
  allIds: string[],
  currentId?: string,
}

const initialState: LaoReducerState = {
  byId: {},
  allIds: [],
};

const addLaoReducer = (state: Draft<LaoReducerState>, action: PayloadAction<LaoState>) => {
  const newLao = action.payload;

  if (!(newLao.id in state.byId)) {
    state.byId[newLao.id] = newLao;
    state.allIds.push(newLao.id);
  }
};

const laoReducerPath = 'laos';
const laosSlice = createSlice({
  name: laoReducerPath,
  initialState,
  reducers: {
    // Add a LAO to the list of known LAOs
    addLao: addLaoReducer,

    // Update a LAO
    updateLao: (state: Draft<LaoReducerState>, action: PayloadAction<LaoState>) => {
      const updatedLao = action.payload;

      if (!(updatedLao.id in state.byId)) {
        return;
      }

      state.byId[updatedLao.id] = updatedLao;
    },

    // Remove a LAO to the list of known LAOs
    removeLao: (state, action: PayloadAction<Hash>) => {
      const laoId = action.payload.valueOf();

      if (laoId in state.byId) {
        delete state.byId[laoId];
        state.allIds = state.allIds.filter((id) => id !== laoId);
      }
    },

    // Empty the list of known LAOs ("reset")
    clearAllLaos: (state) => {
      if (state.currentId === undefined) {
        state.byId = {};
        state.allIds = [];
      }
    },

    // Connect to a LAO for a given ID
    // Warning: this action is only accepted if we are not already connected to a LAO
    connectToLao: (state, action: PayloadAction<LaoState>) => {
      addLaoReducer(state, action);

      if (state.currentId === undefined) {
        const lao = action.payload;
        state.currentId = lao.id;
      }
    },

    // Disconnected from the current LAO (idempotent)
    disconnectFromLao: (state) => {
      state.currentId = undefined;
    },

    // Update the last roll call observed in the LAO and for which we have a token
    setLaoLastRollCall: {
      prepare(laoId: Hash | string, rollCallId: Hash | string, hasToken: boolean): any {
        return {
          payload: {
            laoId: laoId.valueOf(),
            rollCallId: rollCallId.valueOf(),
            hasToken: hasToken,
          },
        };
      },
      reducer(state, action: PayloadAction<{
        laoId: string;
        rollCallId: string;
        hasToken: boolean;
      }>) {
        const { laoId, rollCallId, hasToken } = action.payload;

        // Lao not initialized, return
        if (!(laoId in state.byId)) {
          return;
        }

        state.byId[laoId].last_roll_call_id = rollCallId;
        if (hasToken) {
          state.byId[laoId].last_tokenized_roll_call_id = rollCallId;
        }
      },
    },

  },
  extraReducers: (builder) => {
    // this is called by the persistence layer of Redux, upon starting the application
    builder.addCase(REHYDRATE, (state) => ({
      ...state,
      // make sure we always start disconnected
      currentId: undefined,
    }));
  },
});

export const {
  addLao, updateLao, removeLao, clearAllLaos, connectToLao, disconnectFromLao, setLaoLastRollCall,
} = laosSlice.actions;

export const getLaosState = (state: any): LaoReducerState => state[laoReducerPath];

export function makeLao(id: string | undefined = undefined) {
  return createSelector(
    // First input: all LAOs map
    (state) => getLaosState(state).byId,
    // Second input: current LAO id
    (state) => id || getLaosState(state).currentId,
    // Selector: returns a LaoState -- should it return a Lao object?
    (laoMap: Record<string, LaoState>, currentId: string | undefined) : Lao | undefined => {
      if (currentId === undefined || !(currentId in laoMap)) {
        return undefined;
      }

      return Lao.fromState(laoMap[currentId]);
    },
  );
}

export const makeCurrentLao = () => makeLao();

export const makeLaoIdsList = () => createSelector(
  // Input: sorted LAO ids list
  (state) => getLaosState(state).allIds,
  // Selector: returns an array of LaoIDs
  (laoIds: string[]) : string[] => laoIds,
);

export const makeLaosList = () => createSelector(
  // First input: all LAOs map
  (state) => getLaosState(state).byId,
  // Second input: sorted LAO ids list
  (state) => getLaosState(state).allIds,
  // Selector: returns an array of LaoStates -- should it return an array of Lao objects?
  (laoMap: Record<string, LaoState>, laoIds: string[]) : Lao[] => laoIds
    .map((id) => Lao.fromState(laoMap[id])),
);

export const makeLaosMap = () => createSelector(
  // First input: all LAOs map
  (state) => getLaosState(state).byId,
  // Selector: returns an array of LaoStates -- should it return an array of Lao objects?
  (laoMap: Record<string, LaoState>)
  : Record<string, Lao> => Object.keys(laoMap).reduce(
    (acc, id) => {
      acc[id] = Lao.fromState(laoMap[id]);
      return acc;
    }, {} as Record<string, Lao>,
  ),
);

export const makeIsLaoOrganizer = () => createSelector(
  // First input: all LAOs map
  (state) => getLaosState(state).byId,
  // Second input: current LAO id
  (state) => getLaosState(state)?.currentId,
  // Second input: sorted LAO ids list
  (state) => getKeyPairState(state)?.keyPair?.publicKey,
  // Selector: returns an array of LaoStates -- should it return an array of Lao objects?
  (laoMap: Record<string, LaoState>,
    laoId: string | undefined,
    pKey: string | undefined) : boolean => !!laoId && laoMap[laoId]?.organizer === pKey,
);

export default {
  [laoReducerPath]: laosSlice.reducer,
};

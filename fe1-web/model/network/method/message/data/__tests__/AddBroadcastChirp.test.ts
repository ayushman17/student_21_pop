import 'jest-extended';
import '__tests__/utils/matchers';

import { Base64UrlData, Timestamp } from 'model/objects';
import { ProtocolError } from 'model/network/ProtocolError';
import { AddBroadcastChirp } from '../chirp/AddBroadcastChirp';
import { ActionType, ObjectType } from '../MessageData';

const TIMESTAMP = new Timestamp(1609455600); // 1st january 2021
const TEXT = 'test text';
const CHANNEL = '/root/abc/social/def';
const ID = Base64UrlData.encode('message_id');

const sampleAddBroadcastChirp: Partial<AddBroadcastChirp> = {
  object: ObjectType.CHIRP,
  action: ActionType.ADD_BROADCAST,
  id: ID,
  text: TEXT,
  channel: CHANNEL,
  timestamp: TIMESTAMP,
};

const dataAddChirpBroadcast: string = `{"object": "${ObjectType.CHIRP}", "action":"${ActionType.ADD_BROADCAST}", "chirp_id": "${ID}", "text": "${TEXT}", "channel": "${CHANNEL}", "timestamp": ${TIMESTAMP}}`;

describe('AddBroadcastChirp', () => {
  it('should be created correctly from JSON', () => {
    expect(new AddBroadcastChirp(sampleAddBroadcastChirp)).toBeJsonEqual(sampleAddBroadcastChirp);
    const temp = {
      object: ObjectType.CHIRP,
      action: ActionType.ADD_BROADCAST,
      id: ID,
      text: TEXT,
      channel: CHANNEL,
      timestamp: TIMESTAMP,
    };
    expect(new AddBroadcastChirp(temp)).toBeJsonEqual(temp);
  });

  it('should be parsed correctly from JSON', () => {
    const obj = JSON.parse(dataAddChirpBroadcast);
    expect(AddBroadcastChirp.fromJson(obj)).toBeJsonEqual(sampleAddBroadcastChirp);
  });

  it('should throw an error if id is undefined', () => {
    const createWrongObj = () => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const wrongObj = new AddBroadcastChirp({
        object: ObjectType.CHIRP,
        action: ActionType.ADD_BROADCAST,
        text: TEXT,
        channel: CHANNEL,
        timestamp: TIMESTAMP,
      });
    };
    expect(createWrongObj).toThrow(ProtocolError);
  });

  it('should throw an error if text is undefined', () => {
    const createWrongObj = () => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const wrongObj = new AddBroadcastChirp({
        object: ObjectType.CHIRP,
        action: ActionType.ADD_BROADCAST,
        id: ID,
        channel: CHANNEL,
        timestamp: TIMESTAMP,
      });
    };
    expect(createWrongObj).toThrow(ProtocolError);
  });

  it('should throw an error if timestamp is undefined', () => {
    const createWrongObj = () => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const wrongObj = new AddBroadcastChirp({
        object: ObjectType.CHIRP,
        action: ActionType.ADD_BROADCAST,
        id: ID,
        text: TEXT,
        channel: CHANNEL,
      });
    };
    expect(createWrongObj).toThrow(ProtocolError);
  });

  it('should throw an error if channel is undefined', () => {
    const createWrongObj = () => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const wrongObj = new AddBroadcastChirp({
        object: ObjectType.CHIRP,
        action: ActionType.ADD_BROADCAST,
        id: ID,
        text: TEXT,
        timestamp: TIMESTAMP,
      });
    };
    expect(createWrongObj).toThrow(ProtocolError);
  });
});

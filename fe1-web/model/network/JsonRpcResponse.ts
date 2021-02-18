import { ProtocolError } from './ProtocolError';
import { Message } from './method/message';

export const UNDEFINED_ID: number = -1;

interface ErrorObject {
  code: number;
  description: string;
}

export class JsonRpcResponse {
  public readonly result?: number | Message[];

  public readonly error?: ErrorObject;

  public readonly id: number;

  constructor(resp: Partial<JsonRpcResponse>) {
    this.id = resp.id || UNDEFINED_ID;

    if (resp.error !== undefined && resp.result !== undefined) {
      throw new ProtocolError('Unexpected json-rpc answer : both \'error\' and \'result\' are present');
    }

    if (resp.result !== undefined) {
      if (typeof resp.result === 'number' && resp.result) {
        throw new ProtocolError('Unexpected json-rpc answer : non-zero result value');
      }

      this.result = resp.result;
    } else if (resp.error !== undefined) {
      if (resp.error.code >= 0) {
        throw new ProtocolError(`Unexpected json-rpc answer : unexpected error code value '${resp.error.code}'`);
      }

      this.error = resp.error;
    } else {
      throw new ProtocolError('Unexpected json-rpc answer : both \'error\' and \'result\' are absent');
    }
  }

  public static fromJson(jsonString: string): JsonRpcResponse {
    // FIXME add JsonSchema validation to all "fromJson"
    const correctness = true;

    return correctness
      ? new JsonRpcResponse(JSON.parse(jsonString))
      : (() => { throw new ProtocolError('add JsonSchema error message'); })();
  }
}
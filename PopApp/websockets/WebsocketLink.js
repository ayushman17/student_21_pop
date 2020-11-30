import React from 'react';
import { w3cwebsocket as W3CWebSocket } from 'websocket';
import { handleServerAnswer } from './WebsocketAnswer';
import { PendingRequest } from './WebsocketUtils';

const WEBSOCKET_READYSTATE_INTERVAL_MS = 10;
const WEBSOCKET_READYSTATE_MAX_ATTEMPTS = 100;

export default class WebsocketLink {

  static #ws;
  static #pendingQueries;


  static sendRequestToServer(message, retry = false) {
    if (this.#ws == null) WebsocketLink._initWebsocket();

    WebsocketLink._sendMessage(message, retry);
  }


  static getPendingProperties() { return this.#pendingQueries; }


  static _initWebsocket(address = '127.0.0.1', port = '8000') {
    const ws = new W3CWebSocket('ws://' + address + ':' + port);

    ws.onopen = () => { console.log(`initiating web socket : ws://${address}:${port}`); };
    ws.onmessage = (message) => { handleServerAnswer(message) };

    this.#ws = ws;
    this.#pendingQueries = new Map();
  }


  static _waitWebsocketReady(resolveWebsocketReady, rejectWebsocketReady) {

    if (!this.#ws.readyState) {
      let count = 0;

      const id = window.setInterval(() => {
        if (!this.#ws.readyState && count < WEBSOCKET_READYSTATE_MAX_ATTEMPTS) {
          count += 1;
        } else {
          if (count === WEBSOCKET_READYSTATE_MAX_ATTEMPTS)
            rejectWebsocketReady(
                "Maximum waiting time for websocket to be ready reached : " +
                WEBSOCKET_READYSTATE_MAX_ATTEMPTS * WEBSOCKET_READYSTATE_INTERVAL_MS +
                "[ms] (_waitWebsocketReady)"
            );
          else
            resolveWebsocketReady();
          window.clearInterval(id);
        }
      }, WEBSOCKET_READYSTATE_INTERVAL_MS);

    } else {
      resolveWebsocketReady();
    }
  }

  static _sendMessage(message, retry) {

    // Check that the websocket connection is ready
    if (!this.#ws.readyState) {

      let promise = new Promise((resolveWebsocketReady, rejectWebsocketReady) => {
        this._waitWebsocketReady(resolveWebsocketReady, rejectWebsocketReady);
      });

      promise.then(
        () => this._sendMessage(message, retry),
        (error) => console.error("(TODO)", error),
      );

    } else {
      // websocket ready to be used, message can be sent
      if (!retry) this.#pendingQueries.set(message.id, new PendingRequest(message));
      console.log("sending this message ", message);
      this.#ws.send(JSON.stringify(message));
    }
  }
}

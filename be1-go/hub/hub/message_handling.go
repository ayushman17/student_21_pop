package hub

import (
	"encoding/base64"
	"encoding/json"
	jsonrpc "popstellar/message"
	"popstellar/message/answer"
	"popstellar/message/messagedata"
	"popstellar/message/query"
	"popstellar/message/query/method"
	"popstellar/message/query/method/message"
	"popstellar/network/socket"
	"popstellar/validation"

	"golang.org/x/xerrors"
)

// handleRootChannelPublishMesssage handles an incominxg publish message on the root channel.
func (h *Hub) handleRootChannelPublishMesssage(sock socket.Socket, publish method.Publish) error {
	jsonData, err := base64.URLEncoding.DecodeString(publish.Params.Message.Data)
	if err != nil {
		err := xerrors.Errorf("failed to decode message data: %v", err)
		sock.SendError(&publish.ID, err)
		return err
	}

	// validate message data against the json schema
	err = h.schemaValidator.VerifyJSON(jsonData, validation.Data)
	if err != nil {
		err := xerrors.Errorf("failed to validate message against json schema: %v", err)
		sock.SendError(&publish.ID, err)
		return err
	}

	// get object#action
	object, action, err := messagedata.GetObjectAndAction(jsonData)
	if err != nil {
		err := xerrors.Errorf("failed to get object#action: %v", err)
		sock.SendError(&publish.ID, err)
		return err
	}

	// must be "lao#create"
	if object != messagedata.LAOObject || action != messagedata.LAOActionCreate {
		err := answer.NewErrorf(publish.ID, "only lao#create is allowed on root, but found %s#%s", object, action)
		h.log.Err(err)
		sock.SendError(&publish.ID, err)
		return err
	}

	var laoCreate messagedata.LaoCreate

	err = publish.Params.Message.UnmarshalData(&laoCreate)
	if err != nil {
		h.log.Err(err).Msg("failed to unmarshal lao#create")
		sock.SendError(&publish.ID, err)
		return err
	}

	err = laoCreate.Verify()
	if err != nil {
		h.log.Err(err).Msg("invalid lao#create message")
		sock.SendError(&publish.ID, err)
	}

	err = h.createLao(publish, laoCreate, sock)
	if err != nil {
		h.log.Err(err).Msg("failed to create lao")
		sock.SendError(&publish.ID, err)
		return err
	}

	h.inbox.StoreMessage(publish.Params.Message)

	return nil
}

// handleServerCatchup handles an incoming catchup message coming from a server
func (h *Hub) handleServerCatchup(senderSocket socket.Socket, byteMessage []byte) ([]message.Message, int, error) {
	var catchup method.Catchup

	err := json.Unmarshal(byteMessage, &catchup)
	if err != nil {
		return nil, -1, xerrors.Errorf("failed to unmarshal catchup message: %v", err)
	}

	if catchup.Params.Channel != serverComChannel {
		return nil, catchup.ID, xerrors.Errorf("server catchup message can only be sent on /root/serverCom channel")
	}

	messages := h.inbox.GetSortedMessages()
	return messages, catchup.ID, nil
}

// handleAnswer handles the answer to a message sent by the server
func (h *Hub) handleAnswer(senderSocket socket.Socket, byteMessage []byte) error {
	var answer method.Answer
	var result method.Result

	err := json.Unmarshal(byteMessage, &result)
	if err == nil {
		h.log.Info().Msg("result isn't an answer to a catchup, nothing to handle")
		return nil
	}

	err = json.Unmarshal(byteMessage, &answer)
	if err != nil {
		return xerrors.Errorf("failed to unmarshal answer: %v", err)
	}

	h.queries.mu.Lock()

	val := h.queries.state[answer.ID]
	if val == nil {
		h.queries.mu.Unlock()
		return xerrors.Errorf("no query sent with id %v", answer.ID)
	} else if *val {
		h.queries.mu.Unlock()
		return xerrors.Errorf("query %v already got an answer", answer.ID)
	}

	channel := h.queries.queries[answer.ID].Params.Channel
	*h.queries.state[answer.ID] = true
	h.queries.mu.Unlock()

	for msg := range answer.Result {
		publish := method.Publish{
			Base: query.Base{
				JSONRPCBase: jsonrpc.JSONRPCBase{
					JSONRPC: "2.0",
				},
				Method: "publish",
			},

			Params: struct {
				Channel string          "json:\"channel\""
				Message message.Message "json:\"message\""
			}{
				Channel: channel,
				Message: answer.Result[msg],
			},
		}

		err := h.handleDuringCatchup(senderSocket, publish)
		if err != nil {
			h.log.Err(err).Msgf("failed to handle message during catchup: %v", err)
		}
	}
	return nil
}

func (h *Hub) handleDuringCatchup(socket socket.Socket, publish method.Publish) error {

	h.Lock()
	_, stored := h.serverInbox.GetMessage(publish.Params.Message.MessageID)
	if stored {
		return xerrors.Errorf("already stored this message")
	}
	h.serverInbox.StoreMessage(publish)
	h.Unlock()

	if publish.Params.Channel == "/root" {
		err := h.handleRootChannelPublishMesssage(socket, publish)
		if err != nil {
			return xerrors.Errorf("failed to handle root channel message: %v", err)
		}
		return nil
	}

	channel, err := h.getChan(publish.Params.Channel)
	if err != nil {
		return xerrors.Errorf("failed to get channel: %v", err)
	}

	err = channel.Publish(publish, socket)
	if err != nil {
		return xerrors.Errorf("failed to publish: %v", err)
	}

	return nil
}

func (h *Hub) handlePublish(socket socket.Socket, byteMessage []byte) (int, error) {
	var publish method.Publish

	err := json.Unmarshal(byteMessage, &publish)
	if err != nil {
		return -1, xerrors.Errorf("failed to unmarshal publish message: %v", err)
	}

	alreadyReceived := h.broadcastToServers(publish, byteMessage)
	if alreadyReceived {
		h.log.Info().Msg("message was already received")
		return publish.ID, nil
	}

	if publish.Params.Channel == "/root" {
		err := h.handleRootChannelPublishMesssage(socket, publish)
		if err != nil {
			return publish.ID, xerrors.Errorf("failed to handle root channel message: %v", err)
		}
		return publish.ID, nil
	}

	channel, err := h.getChan(publish.Params.Channel)
	if err != nil {
		return publish.ID, xerrors.Errorf("failed to get channel: %v", err)
	}

	err = channel.Publish(publish, socket)
	if err != nil {
		return publish.ID, xerrors.Errorf("failed to publish: %v", err)
	}

	return publish.ID, nil
}

func (h *Hub) handleSubscribe(socket socket.Socket, byteMessage []byte) (int, error) {
	var subscribe method.Subscribe

	err := json.Unmarshal(byteMessage, &subscribe)
	if err != nil {
		return -1, xerrors.Errorf("failed to unmarshal subscribe message: %v", err)
	}

	channel, err := h.getChan(subscribe.Params.Channel)
	if err != nil {
		return subscribe.ID, xerrors.Errorf("failed to get subscribe channel: %v", err)
	}

	err = channel.Subscribe(socket, subscribe)
	if err != nil {
		return subscribe.ID, xerrors.Errorf("failed to publish: %v", err)
	}

	return subscribe.ID, nil
}

func (h *Hub) handleUnsubscribe(socket socket.Socket, byteMessage []byte) (int, error) {
	var unsubscribe method.Unsubscribe

	err := json.Unmarshal(byteMessage, &unsubscribe)
	if err != nil {
		return -1, xerrors.Errorf("failed to unmarshal unsubscribe message: %v", err)
	}

	channel, err := h.getChan(unsubscribe.Params.Channel)
	if err != nil {
		return unsubscribe.ID, xerrors.Errorf("failed to get unsubscribe channel: %v", err)
	}

	err = channel.Unsubscribe(socket.ID(), unsubscribe)
	if err != nil {
		return unsubscribe.ID, xerrors.Errorf("failed to unsubscribe: %v", err)
	}

	return unsubscribe.ID, nil
}

func (h *Hub) handleCatchup(socket socket.Socket, byteMessage []byte) ([]message.Message, int, error) {
	var catchup method.Catchup

	err := json.Unmarshal(byteMessage, &catchup)
	if err != nil {
		return nil, -1, xerrors.Errorf("failed to unmarshal catchup message: %v", err)
	}

	if catchup.Params.Channel == "/root" {
		return h.handleServerCatchup(socket, byteMessage)
	}

	channel, err := h.getChan(catchup.Params.Channel)
	if err != nil {
		return nil, catchup.ID, xerrors.Errorf("failed to get catchup channel: %v", err)
	}

	msg := channel.Catchup(catchup)
	if err != nil {
		return nil, catchup.ID, xerrors.Errorf("failed to catchup: %v", err)
	}

	return msg, catchup.ID, nil
}

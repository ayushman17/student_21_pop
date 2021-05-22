package hub

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"sync"

	"student20_pop/message"
	"student20_pop/validation"

	"go.dedis.ch/kyber/v3"
	"golang.org/x/xerrors"
)

type baseHub struct {
	messageChan chan IncomingMessage

	sync.RWMutex
	channelByID map[string]Channel

	public kyber.Point

	schemaValidator *validation.SchemaValidator
}

// NewBaseHub returns a Base Hub.
func NewBaseHub(public kyber.Point) (*baseHub, error) {

	schemaValidator, err := validation.NewSchemaValidator()
	if err != nil {
		return nil, xerrors.Errorf("failed to create the schema validator: %v", err)
	}

	return &baseHub{
		messageChan:     make(chan IncomingMessage),
		channelByID:     make(map[string]Channel),
		public:          public,
		schemaValidator: schemaValidator,
	}, nil
}

// RemoveClient removes the client from this hub.
func (h *baseHub) RemoveClientSocket(client *ClientSocket) {
	h.RLock()
	defer h.RUnlock()

	for _, channel := range h.channelByID {
		channel.Unsubscribe(client, message.Unsubscribe{})
	}
}

// Recv accepts a message and enques it for processing in the hub.
func (h *baseHub) Recv(msg IncomingMessage) {
	log.Printf("Hub::Recv")
	h.messageChan <- msg
}

func (h *baseHub) Start(done chan struct{}) {
	for {
		select {
		case incomingMessage := <-h.messageChan:
			h.handleIncomingMessage(&incomingMessage)
		case <-done:
			return
		}
	}
}

func (h *baseHub) handleMessageFromClient(incomingMessage *IncomingMessage) {
	client := ClientSocket{
		incomingMessage.Socket,
	}
	byteMessage := incomingMessage.Message

	// Check if the GenericMessage has a field "id"
	genericMsg := &message.GenericMessage{}
	id, ok := genericMsg.UnmarshalID(byteMessage)
	if !ok {
		log.Printf("The message does not have a valid `id` field")
		client.SendError(nil, &message.Error{
			Code:        -1,
			Description: "The message does not have a valid `id` field",
		})
		return
	}

	// Verify the message
	err := h.schemaValidator.VerifyJson(byteMessage, validation.GenericMsgSchema)
	if err != nil {
		log.Printf("failed to verify incoming message: %v", err)
		client.SendError(&id, err)
		return
	}

	// Unmarshal the message
	err = json.Unmarshal(byteMessage, genericMsg)
	if err != nil {
		log.Printf("failed to unmarshal incoming message: %v", err)
		client.SendError(&id, err)
		return
	}

	query := genericMsg.Query

	if query == nil {
		return
	}

	channelID := query.GetChannel()
	log.Printf("channel: %s", channelID)

	if channelID == "/root" {
		if query.Publish == nil {
			log.Printf("only publish is allowed on /root")
			client.SendError(&id, err)
			return
		}

		// Check if the structure of the message is correct
		msg := query.Publish.Params.Message

		// Verify the data
		err := h.schemaValidator.VerifyJson(msg.RawData, validation.DataSchema)
		if err != nil {
			log.Printf("failed to validate the data: %v", err)
			client.SendError(&id, err)
			return
		}

		// Unmarshal the data
		err = query.Publish.Params.Message.VerifyAndUnmarshalData()
		if err != nil {
			log.Printf("failed to verify and unmarshal data: %v", err)
			client.SendError(&id, err)
			return
		}

		if query.Publish.Params.Message.Data.GetAction() == message.DataAction(message.CreateLaoAction) &&
			query.Publish.Params.Message.Data.GetObject() == message.DataObject(message.LaoObject) {
			err := h.createLao(*query.Publish)
			if err != nil {
				log.Printf("failed to create lao: %v", err)
				client.SendError(&id, err)
				return
			}
		} else {
			log.Printf("invalid method: %s", query.GetMethod())
			client.SendError(&id, &message.Error{
				Code:        -1,
				Description: "you may only invoke lao/create on /root",
			})
			return
		}

		status := 0
		result := message.Result{General: &status}
		log.Printf("sending result: %+v", result)
		client.SendResult(id, result)
		return
	}

	if channelID[:6] != "/root/" {
		log.Printf("channel id must begin with /root/")
		client.SendError(&id, &message.Error{
			Code:        -2,
			Description: "channel id must begin with /root/",
		})
		return
	}

	channelID = channelID[6:]
	h.RLock()
	channel, ok := h.channelByID[channelID]
	if !ok {
		log.Printf("invalid channel id: %s", channelID)
		client.SendError(&id, &message.Error{
			Code:        -2,
			Description: fmt.Sprintf("channel with id %s does not exist", channelID),
		})
		return
	}
	h.RUnlock()

	method := query.GetMethod()
	log.Printf("method: %s", method)

	msg := []message.Message{}

	// TODO: use constants
	switch method {
	case "subscribe":
		err = channel.Subscribe(&client, *query.Subscribe)
	case "unsubscribe":
		err = channel.Unsubscribe(&client, *query.Unsubscribe)
	case "publish":
		err = channel.Publish(*query.Publish)
	case "message":
		log.Printf("cannot handle broadcasts right now")
	case "catchup":
		msg = channel.Catchup(*query.Catchup)
		// TODO send catchup response to client
	}

	if err != nil {
		log.Printf("failed to process query: %v", err)
		client.SendError(&id, err)
		return
	}

	result := message.Result{}

	if method == "catchup" {
		result.Catchup = msg
	} else {
		general := 0
		result.General = &general
	}

	client.SendResult(id, result)
}

func (h *baseHub) handleMessageFromWitness(incomingMessage *IncomingMessage) {
	//TODO

}

func (h *baseHub) handleIncomingMessage(incomingMessage *IncomingMessage) {
	log.Printf("Hub::handleMessageFromClient: %s", incomingMessage.Message)

	switch incomingMessage.Socket.socketType {
	case ClientSocketType:
		h.handleMessageFromClient(incomingMessage)
		return
	case WitnessSocketType:
		h.handleMessageFromWitness(incomingMessage)
		return
	default:
		log.Printf("error: invalid socket type")
		return
	}

}

func (h *baseHub) createLao(publish message.Publish) error {
	h.Lock()
	defer h.Unlock()

	data, ok := publish.Params.Message.Data.(*message.CreateLAOData)
	if !ok {
		return &message.Error{
			Code:        -4,
			Description: "failed to cast data to CreateLAOData",
		}
	}

	encodedID := base64.StdEncoding.EncodeToString(data.ID)
	if _, ok := h.channelByID[encodedID]; ok {
		return &message.Error{
			Code:        -3,
			Description: "failed to create lao: another one with the same ID exists",
		}
	}

	laoChannelID := "/root/" + encodedID

	laoCh := laoChannel{
		rollCall:    rollCall{},
		attendees:   make(map[string]struct{}),
		baseChannel: createBaseChannel(h, laoChannelID),
	}

	messageID := base64.StdEncoding.EncodeToString(publish.Params.Message.MessageID)
	laoCh.inbox[messageID] = *publish.Params.Message

	h.channelByID[encodedID] = &laoCh

	return nil
}
package hub

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"student20_pop/message"
	"student20_pop/validation"
	"sync"
)

// baseChannel represent a generic channel and contains all the fields that are
// used in all channels
type baseChannel struct {
	hub *organizerHub

	clientsMu sync.RWMutex
	clients   map[*ClientSocket]struct{}

	inboxMu sync.RWMutex
	inbox   map[string]message.Message

	// /root/<ID>
	channelID   string
	idValidator *validation.IdValidatior

	witnessMu sync.Mutex
	witnesses []message.PublicKey
}

// CreateBaseChannel return an instance of a `baseChannel`
func createBaseChannel(h *organizerHub, channelID string) *baseChannel {
	return &baseChannel{
		hub:         h,
		channelID:   channelID,
		idValidator: validation.NewIdValidator(channelID),
		clients:     make(map[*ClientSocket]struct{}),
		inbox:       make(map[string]message.Message),
	}
}

func (c *baseChannel) Subscribe(client *ClientSocket, msg message.Subscribe) error {
	log.Printf("received a subscribe with id: %d", msg.ID)
	c.clientsMu.Lock()
	defer c.clientsMu.Unlock()

	c.clients[client] = struct{}{}

	return nil
}

func (c *baseChannel) Unsubscribe(client *ClientSocket, msg message.Unsubscribe) error {
	log.Printf("received an unsubscribe with id: %d", msg.ID)

	c.clientsMu.Lock()
	defer c.clientsMu.Unlock()

	if _, ok := c.clients[client]; !ok {
		return &message.Error{
			Code:        -2,
			Description: "client is not subscribed to this channel",
		}
	}

	delete(c.clients, client)
	return nil
}

func (c *baseChannel) Catchup(catchup message.Catchup) []message.Message {
	log.Printf("received a catchup with id: %d", catchup.ID)

	c.inboxMu.RLock()
	defer c.inboxMu.RUnlock()

	result := make([]message.Message, 0, len(c.inbox))
	for _, msg := range c.inbox {
		result = append(result, msg)
	}

	return result
}

func (c *baseChannel) broadcastToAllClients(msg message.Message) {
	c.clientsMu.RLock()
	defer c.clientsMu.RUnlock()

	query := message.Query{
		Broadcast: message.NewBroadcast(c.channelID, &msg),
	}

	buf, err := json.Marshal(query)
	if err != nil {
		log.Fatalf("failed to marshal broadcast query: %v", err)
	}

	for client := range c.clients {
		client.Send(buf)
	}
}

// Verify the if a Publish message is valid
func (c *baseChannel) VerifyPublishMessage(publish message.Publish) error {
	log.Printf("received a publish with id: %d", publish.ID)

	// Check if the structure of the message is correct
	msg := publish.Params.Message

	// Verify the data
	err := c.hub.verifyJson(msg.RawData, DataSchema)
	if err != nil {
		return message.NewError("failed to validate the data", err)
	}

	// Unmarshal the data
	err = msg.VerifyAndUnmarshalData()
	if err != nil {
		// Return a error of type "-4 request data is invalid" for all the verifications and unmarshalling problems of the data
		return &message.Error{
			Code:        -4,
			Description: fmt.Sprintf("failed to verify and unmarshal data: %v", err),
		}
	}

	// Verify all the IDs in the data are correct
	err = c.idValidator.VerifyID(msg.Data)
	if err != nil {
		return message.NewError("failed to verify the ID of the provided data", err)
	}

	msgIDEncoded := base64.URLEncoding.EncodeToString(msg.MessageID)

	// Check if the message already exists
	c.inboxMu.RLock()
	if _, ok := c.inbox[msgIDEncoded]; ok {
		c.inboxMu.RUnlock()
		return &message.Error{
			Code:        -3,
			Description: "message already exists",
		}
	}
	c.inboxMu.RUnlock()

	return nil
}

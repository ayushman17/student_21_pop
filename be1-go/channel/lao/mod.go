package lao

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sort"
	"student20_pop/channel"
	"student20_pop/channel/election"
	"student20_pop/channel/inbox"
	"student20_pop/crypto"
	jsonrpc "student20_pop/message"
	"student20_pop/message/answer"
	"student20_pop/message/messagedata"
	"student20_pop/message/query"
	"student20_pop/message/query/method"
	"student20_pop/message/query/method/message"
	"student20_pop/network/socket"
	"student20_pop/validation"
	"sync"

	"go.dedis.ch/kyber/v3/sign/schnorr"
	"golang.org/x/xerrors"
)

const (
	dbPrepareErr  = "failed to prepare query: %v"
	dbParseRowErr = "failed to parse row: %v"
	dbRowIterErr  = "error in row iteration: %v"
	dbQueryRowErr = "failed to query rows: %v"
)

// Channel ...
type Channel struct {
	sockets channel.Sockets

	inbox *inbox.Inbox

	// /root/<ID>
	channelID string

	witnessMu sync.Mutex
	witnesses []string

	rollCall rollCall

	hub channel.HubThingTheChannelNeeds

	attendees map[string]struct{}
}

// NewChannel ...
func NewChannel(channelID string, hub channel.HubThingTheChannelNeeds, msg message.Message) channel.Channel {
	inbox := inbox.NewInbox(channelID)
	inbox.StoreMessage(msg)

	return &Channel{
		channelID: channelID,
		sockets:   channel.NewSockets(),
		inbox:     inbox,
		hub:       hub,
		rollCall:  rollCall{},
		attendees: make(map[string]struct{}),
	}
}

// Subscribe is used to handle a subscribe message from the client.
func (c *Channel) Subscribe(socket socket.Socket, msg method.Subscribe) error {
	log.Printf("received a subscribe with id: %d", msg.ID)
	c.sockets.Upsert(socket)

	return nil
}

// Unsubscribe is used to handle an unsubscribe message.
func (c *Channel) Unsubscribe(socketID string, msg method.Unsubscribe) error {
	log.Printf("received an unsubscribe with id: %d", msg.ID)

	ok := c.sockets.Delete(socketID)

	if !ok {
		return answer.NewError(-2, "client is not subscribed to this channel")
	}

	return nil
}

// Catchup is used to handle a catchup message.
func (c *Channel) Catchup(catchup method.Catchup) []message.Message {
	log.Printf("received a catchup with id: %d", catchup.ID)

	messages := c.inbox.GetMessages()

	// sort.Slice on messages based on the timestamp
	sort.Slice(messages, func(i, j int) bool {
		return messages[i].StoredTime < messages[j].StoredTime
	})

	result := make([]message.Message, 0, len(messages))

	// iterate and extract the messages[i].message field and
	// append it to the result slice
	for _, msgInfo := range messages {
		result = append(result, msgInfo.Message)
	}

	return result
}

// broadcastToAllClients is a helper message to broadcast a message to all
// subscribers.
func (c *Channel) broadcastToAllClients(msg message.Message) {
	rpcMessage := method.Broadcast{
		Base: query.Base{
			JSONRPCBase: jsonrpc.JSONRPCBase{
				JSONRPC: "2.0",
			},
			Method: "broadcast",
		},
		Params: struct {
			Channel string          `json:"channel"`
			Message message.Message `json:"message"`
		}{
			c.channelID,
			msg,
		},
	}

	buf, err := json.Marshal(&rpcMessage)
	if err != nil {
		log.Printf("failed to marshal broadcast query: %v", err)
	}

	c.sockets.SendToAll(buf)
}

// VerifyPublishMessage checks if a Publish message is valid
func (c *Channel) VerifyPublishMessage(publish method.Publish) error {
	log.Printf("received a publish with id: %d", publish.ID)

	// Check if the structure of the message is correct
	msg := publish.Params.Message

	jsonData, err := base64.URLEncoding.DecodeString(msg.Data)
	if err != nil {
		return xerrors.Errorf("failed to decode message data: %v", err)
	}

	// Verify the data
	err = c.hub.GetSchemaValidator().VerifyJSON(jsonData, validation.Data)
	if err != nil {
		return xerrors.Errorf("failed to verify json schema: %w", err)
	}

	// Check if the message already exists
	if _, ok := c.inbox.GetMessage(msg.MessageID); ok {
		return answer.NewError(-3, "message already exists")
	}

	return nil
}

// rollCallState denotes the state of the roll call.
type rollCallState string

const (
	// Open represents the open roll call state.
	Open rollCallState = "open"

	// Closed represents the closed roll call state.
	Closed rollCallState = "closed"

	// Created represents the created roll call state.
	Created rollCallState = "created"
)

// rollCall represents a roll call.
type rollCall struct {
	state rollCallState
	id    string
}

// Publish handles publish messages for the LAO channel.
func (c *Channel) Publish(publish method.Publish) error {
	err := c.VerifyPublishMessage(publish)
	if err != nil {
		return xerrors.Errorf("failed to verify publish message: %w", err)
	}

	msg := publish.Params.Message

	data := msg.Data

	jsonData, err := base64.URLEncoding.DecodeString(data)
	if err != nil {
		return xerrors.Errorf("failed to decode message data: %v", err)
	}

	object, action, err := messagedata.GetObjectAndAction(jsonData)

	switch object {
	case "lao":
		err = c.processLaoObject(action, msg)
	case "meeting":
		err = c.processMeetingObject(action, msg)
	case "message":
		err = c.processMessageObject(action, msg)
	case "roll_call":
		err = c.processRollCallObject(action, msg)
	case "election":
		err = c.processElectionObject(action, msg)
	}

	if err != nil {
		return xerrors.Errorf("failed to process %q object: %w", object, err)
	}

	c.broadcastToAllClients(msg)
	return nil
}

// processLaoObject processes a LAO object.
func (c *Channel) processLaoObject(action string, msg message.Message) error {
	switch action {
	case "update_properties":
	case "state":
		var laoState messagedata.LaoState

		err := msg.UnmarshalData(&laoState)
		if err != nil {
			return xerrors.Errorf("failed to unmarshal lao#state: %v", err)
		}

		err = c.processLaoState(laoState)
		if err != nil {
			return xerrors.Errorf("failed to process state action: %w", err)
		}
	default:
		return answer.NewInvalidActionError(action)
	}

	c.inbox.StoreMessage(msg)

	return nil
}

// processLaoState processes a lao state action.
func (c *Channel) processLaoState(data messagedata.LaoState) error {
	// Check if we have the update message
	msg, ok := c.inbox.GetMessage(data.ModificationID)

	if !ok {
		return answer.NewErrorf(-4, "cannot find lao/update_properties with ID: %s", data.ModificationID)
	}

	// Check if the signatures are from witnesses we need. We maintain
	// the current state of witnesses for a LAO in the channel instance
	// TODO: threshold signature verification

	c.witnessMu.Lock()
	match := 0
	expected := len(c.witnesses)
	// TODO: O(N^2), O(N) possible
	for i := 0; i < expected; i++ {
		for j := 0; j < len(data.ModificationSignatures); j++ {
			if c.witnesses[i] == data.ModificationSignatures[j].Witness {
				match++
				break
			}
		}
	}
	c.witnessMu.Unlock()

	if match != expected {
		return answer.NewErrorf(-4, "not enough witness signatures provided. Needed %d got %d", expected, match)
	}

	// Check if the signatures match
	for _, pair := range data.ModificationSignatures {
		err := schnorr.VerifyWithChecks(crypto.Suite, []byte(pair.Witness), []byte(data.ModificationID), []byte(pair.Signature))
		if err != nil {
			return answer.NewErrorf(-4, "signature verfication failed for witness: %s", pair.Witness)
		}
	}

	var updateMsgData messagedata.LaoUpdate

	err := msg.UnmarshalData(&updateMsgData)
	if err != nil {
		return &answer.Error{
			Code:        -4,
			Description: fmt.Sprintf("failed to unmarshal message from the inbox: %v", err),
		}
	}

	err = compareLaoUpdateAndState(updateMsgData, data)
	if err != nil {
		return xerrors.Errorf("failed to compare lao/update and existing state: %w", err)
	}

	return nil
}

func compareLaoUpdateAndState(update messagedata.LaoUpdate, state messagedata.LaoState) error {
	if update.LastModified != state.LastModified {
		return answer.NewErrorf(-4, "mismatch between last modified: expected %d got %d", update.LastModified, state.LastModified)
	}

	if update.Name != state.Name {
		return answer.NewErrorf(-4, "mismatch between name: expected %s got %s", update.Name, state.Name)
	}

	M := len(update.Witnesses)
	N := len(state.Witnesses)

	if M != N {
		return answer.NewErrorf(-4, "mismatch between witness count: expected %d got %d", M, N)
	}

	match := 0

	for i := 0; i < M; i++ {
		for j := 0; j < N; j++ {
			if update.Witnesses[i] == state.Witnesses[j] {
				match++
				break
			}
		}
	}

	if match != M {
		return answer.NewErrorf(-4, "mismatch between witness keys: expected %d keys to match but %d matched", M, match)
	}

	return nil
}

// processMeetingObject handles a meeting object.
func (c *Channel) processMeetingObject(action string, msg message.Message) error {

	// Nothing to do ...🤷‍♂️
	switch action {
	case "create":
	case "update_properties":
	case "state":
	}

	c.inbox.StoreMessage(msg)

	return nil
}

// processMessageObject handles a message object.
func (c *Channel) processMessageObject(action string, msg message.Message) error {

	switch action {
	case "witness":
		var witnessData messagedata.MessageWitness

		err := msg.UnmarshalData(&witnessData)
		if err != nil {
			return xerrors.Errorf("failed to unmarshal witness data: %v", err)
		}

		err = schnorr.VerifyWithChecks(crypto.Suite, []byte(msg.Sender), []byte(witnessData.MessageID), []byte(witnessData.Signature))
		if err != nil {
			return answer.NewError(-4, "invalid witness signature")
		}

		err = c.inbox.AddWitnessSignature(witnessData.MessageID, msg.Sender, witnessData.Signature)
		if err != nil {
			return xerrors.Errorf("failed to add witness signature: %w", err)
		}
	default:
		return answer.NewInvalidActionError(action)
	}

	return nil
}

// processRollCallObject handles a roll call object.
func (c *Channel) processRollCallObject(action string, msg message.Message) error {
	sender := msg.Sender

	senderBuf, err := base64.URLEncoding.DecodeString(sender)
	if err != nil {
		return xerrors.Errorf("failed to decode sender key: %v", err)
	}

	// Check if the sender of the roll call message is the organizer
	senderPoint := crypto.Suite.Point()
	err = senderPoint.UnmarshalBinary(senderBuf)
	if err != nil {
		return answer.NewErrorf(-4, "failed to unmarshal public key of the sender: %v", err)
	}

	if !c.hub.GetPubkey().Equal(senderPoint) {
		return answer.NewErrorf(-5, "sender's public key %q does not match the organizer's", msg.Sender)
	}

	switch action {
	case "create":
		var rollCallCreate messagedata.RollCallCreate

		err := msg.UnmarshalData(&rollCallCreate)
		if err != nil {
			return xerrors.Errorf("failed to unmarshal roll call create: %v", err)
		}

		err = c.processCreateRollCall(rollCallCreate)
		if err != nil {
			return xerrors.Errorf("failed to process roll call create: %v", err)
		}

	case "open", "reopen":
		err := c.processOpenRollCall(msg, action)
		if err != nil {
			return xerrors.Errorf("failed to process open roll call: %v", err)
		}

	case "close":
		var rollCallClose messagedata.RollCallClose

		err := msg.UnmarshalData(&rollCallClose)
		if err != nil {
			return xerrors.Errorf("failed to unmarshal roll call close: %v", err)
		}

		err = c.processCloseRollCall(rollCallClose)
		if err != nil {
			return xerrors.Errorf("failed to process close roll call: %v", err)
		}
	default:
		return answer.NewInvalidActionError(action)
	}

	if err != nil {
		return xerrors.Errorf("failed to process roll call action: %s %w", action, err)
	}

	c.inbox.StoreMessage(msg)

	return nil
}

// processElectionObject handles an election object.
func (c *Channel) processElectionObject(action string, msg message.Message) error {
	if action != "setup" {
		return answer.NewErrorf(-4, "invalid action: %s", action)
	}

	senderBuf, err := base64.URLEncoding.DecodeString(msg.Sender)
	if err != nil {
		return xerrors.Errorf("failed to decode sender key: %v", err)
	}

	// Check if the sender of election creation message is the organizer
	senderPoint := crypto.Suite.Point()
	err = senderPoint.UnmarshalBinary(senderBuf)
	if err != nil {
		return answer.NewErrorf(-4, "failed to unmarshal public key of the sender: %v", err)
	}

	if !c.hub.GetPubkey().Equal(senderPoint) {
		return answer.NewError(-5, "The sender of the election setup message has a different public key from the organizer")
	}

	var electionSetup messagedata.ElectionSetup

	err = msg.UnmarshalData(&electionSetup)
	if err != nil {
		return xerrors.Errorf("failed to unmarshal election setup: %v", err)
	}

	err = c.createElection(msg, electionSetup)
	if err != nil {
		return xerrors.Errorf("failed to create election: %w", err)
	}

	log.Printf("Election has created with success")
	return nil
}

// createElection creates an election in the LAO.
func (c *Channel) createElection(msg message.Message, setupMsg messagedata.ElectionSetup) error {

	// Check if the Lao ID of the message corresponds to the channel ID
	channelID := c.channelID[6:]
	if channelID != setupMsg.Lao {
		return answer.NewErrorf(-4, "Lao ID of the message (Lao: %s) is different from the channelID (channel: %s)", setupMsg.Lao, channelID)
	}

	// Compute the new election channel id
	channelPath := "/root/" + setupMsg.Lao + "/" + setupMsg.ID

	// Create the new election channel
	electionCh := election.NewChannel(channelPath, setupMsg.StartTime, setupMsg.EndTime, false, setupMsg.Questions, c.attendees, msg, c.hub)
	// {
	// 	createBaseChannel(organizerHub, channelPath),
	// 	setupMsg.StartTime,
	// 	setupMsg.EndTime,
	// 	false,
	// 	getAllQuestionsForElectionChannel(setupMsg.Questions),
	// 	c.attendees,
	// }

	// Saving the election channel creation message on the lao channel
	c.inbox.StoreMessage(msg)

	// Add the new election channel to the organizerHub
	c.hub.RegisterNewChannel(channelPath, &electionCh)

	return nil
}

// processCreateRollCall processes a roll call creation object.
func (c *Channel) processCreateRollCall(msg messagedata.RollCallCreate) error {
	// Check that the ProposedEnd is greater than the ProposedStart
	if msg.ProposedStart > msg.ProposedEnd {
		return answer.NewErrorf(-4, "The field `proposed_start` is greater than the field `proposed_end`: %d > %d", msg.ProposedStart, msg.ProposedEnd)
	}

	c.rollCall.id = string(msg.ID)
	c.rollCall.state = Created
	return nil
}

// processOpenRollCall processes an open roll call object.
func (c *Channel) processOpenRollCall(msg message.Message, action string) error {
	if action == "open" {
		// If the action is an OpenRollCallAction,
		// the previous roll call action should be a CreateRollCallAction
		if c.rollCall.state != Created {
			return answer.NewError(-1, "The roll call cannot be opened since it does not exist")
		}
	} else {
		// If the action is an RepenRollCallAction,
		// the previous roll call action should be a CloseRollCallAction
		if c.rollCall.state != Closed {
			return answer.NewError(-1, "The roll call cannot be reopened since it has not been closed")
		}
	}

	// Why not messagedata.RollCallReopen ? Maybe we should assume that Reopen
	// message is useless.
	var rollCallOpen messagedata.RollCallOpen

	err := msg.UnmarshalData(&rollCallOpen)
	if err != nil {
		return xerrors.Errorf("failed to unmarshal roll call open: %v", err)
	}

	if !c.rollCall.checkPrevID([]byte(rollCallOpen.Opens)) {
		return answer.NewError(-1, "The field `opens` does not correspond to the id of the previous roll call message")
	}

	c.rollCall.id = string(rollCallOpen.UpdateID)
	c.rollCall.state = Open
	return nil
}

// processCloseRollCall processes a close roll call message.
func (c *Channel) processCloseRollCall(msg messagedata.RollCallClose) error {
	if c.rollCall.state != Open {
		return answer.NewError(-1, "The roll call cannot be closed since it's not open")
	}

	if !c.rollCall.checkPrevID([]byte(msg.Closes)) {
		return answer.NewError(-4, "The field `closes` does not correspond to the id of the previous roll call message")
	}

	c.rollCall.id = msg.UpdateID
	c.rollCall.state = Closed

	var db *sql.DB

	if os.Getenv("HUB_DB") != "" {
		db, err := sql.Open("sqlite3", os.Getenv("HUB_DB"))
		if err != nil {
			log.Printf("error: failed to connect to db: %v", err)
			db = nil
		} else {
			defer db.Close()
		}
	}

	for _, attendee := range msg.Attendees {
		c.attendees[attendee] = struct{}{}

		if db != nil {
			log.Printf("inserting attendee into db")

			err := insertAttendee(db, attendee, c.channelID)
			if err != nil {
				log.Printf("error: failed to insert attendee into db: %v", err)
			}
		}
	}

	return nil
}

func insertAttendee(db *sql.DB, key string, channelID string) error {
	stmt, err := db.Prepare("insert into lao_attendee(attendee_key, lao_channel_id) values(?, ?)")
	if err != nil {
		return xerrors.Errorf("failed to prepare query: %v", err)
	}

	defer stmt.Close()

	_, err = stmt.Exec(key, channelID)
	if err != nil {
		return xerrors.Errorf("failed to exec query: %v", err)
	}

	return nil
}

// checkPrevID is a helper method which validates the roll call ID.
func (r *rollCall) checkPrevID(prevID []byte) bool {
	return string(prevID) == r.id
}

// ---
// DB restore
// ---

// CreateChannelFromDB restores a channel from the db
func CreateChannelFromDB(db *sql.DB, channelID string, hub channel.HubThingTheChannelNeeds) (channel.Channel, error) {
	channel := Channel{
		channelID: channelID,
		sockets:   channel.NewSockets(),
		inbox:     inbox.NewInbox(channelID),
		hub:       hub,
		rollCall:  rollCall{},
		attendees: make(map[string]struct{}),
	}

	attendees, err := getAttendeesChannelFromDB(db, channelID)
	if err != nil {
		return nil, xerrors.Errorf("failed to get attendees: %v", err)
	}

	for _, attendee := range attendees {
		channel.attendees[attendee] = struct{}{}
	}

	witnesses, err := getWitnessChannelFromDB(db, channelID)
	if err != nil {
		return nil, xerrors.Errorf("failed to get witnesses: %v", err)
	}

	channel.witnesses = witnesses

	messages, err := getMessagesChannelFromDB(db, channelID)
	if err != nil {
		return nil, xerrors.Errorf("failed to get messages: %v", err)
	}

	for i := range messages {
		channel.inbox.StoreMessage(messages[i].Message)
	}

	return &channel, nil
}

func getAttendeesChannelFromDB(db *sql.DB, channelID string) ([]string, error) {
	query := `
		SELECT
			attendee_key
		FROM
			lao_attendee
		WHERE
			lao_channel_id = ?`

	stmt, err := db.Prepare(query)
	if err != nil {
		return nil, xerrors.Errorf(dbPrepareErr, err)
	}

	defer stmt.Close()

	rows, err := stmt.Query(channelID)
	if err != nil {
		return nil, xerrors.Errorf(dbQueryRowErr, err)
	}

	defer rows.Close()

	result := make([]string, 0)

	for rows.Next() {
		var attendeeKey string

		err = rows.Scan(&attendeeKey)
		if err != nil {
			return nil, xerrors.Errorf(dbParseRowErr, err)
		}

		result = append(result, attendeeKey)
	}

	err = rows.Err()
	if err != nil {
		return nil, xerrors.Errorf(dbRowIterErr, err)
	}

	return result, nil
}

func getWitnessChannelFromDB(db *sql.DB, channelID string) ([]string, error) {
	query := `
		SELECT
			pub_key
		FROM
			lao_witness
		WHERE
			lao_channel_id = ?`

	stmt, err := db.Prepare(query)
	if err != nil {
		return nil, xerrors.Errorf(dbPrepareErr, err)
	}

	defer stmt.Close()

	rows, err := stmt.Query(channelID)
	if err != nil {
		return nil, xerrors.Errorf(dbQueryRowErr, err)
	}

	defer rows.Close()

	result := make([]string, 0)

	for rows.Next() {
		var pubKey string

		err = rows.Scan(&pubKey)
		if err != nil {
			return nil, xerrors.Errorf(dbParseRowErr, err)
		}

		result = append(result, pubKey)
	}

	err = rows.Err()
	if err != nil {
		return nil, xerrors.Errorf(dbRowIterErr, err)
	}

	return result, nil
}

func getMessagesChannelFromDB(db *sql.DB, channelID string) ([]channel.MessageInfo, error) {
	query := `
		SELECT
			message_id, 
			sender, 
			message_signature, 
			raw_data, 
			message_timestamp
		FROM
			message_info
		WHERE
			lao_channel_id = ?`

	stmt, err := db.Prepare(query)
	if err != nil {
		return nil, xerrors.Errorf(dbPrepareErr, err)
	}

	defer stmt.Close()

	rows, err := stmt.Query(channelID)
	if err != nil {
		return nil, xerrors.Errorf(dbQueryRowErr, err)
	}

	defer rows.Close()

	result := make([]channel.MessageInfo, 0)

	for rows.Next() {
		var messageID string
		var sender string
		var messageSignature string
		var rawData string
		var timestamp int64

		err = rows.Scan(&messageID, &sender, &messageSignature, &rawData, &timestamp)
		if err != nil {
			return nil, xerrors.Errorf(dbParseRowErr, err)
		}

		witnesses, err := getWitnessesMessageFromDB(db, messageID)
		if err != nil {
			return nil, xerrors.Errorf("failed to get witnesses: %v", err)
		}

		messageInfo := channel.MessageInfo{
			Message: message.Message{
				MessageID:         messageID,
				Sender:            sender,
				Signature:         messageSignature,
				WitnessSignatures: witnesses,
				Data:              rawData,
			},
			StoredTime: timestamp,
		}

		log.Printf("Msg load: %+v", messageInfo.Message)

		result = append(result, messageInfo)
	}

	err = rows.Err()
	if err != nil {
		return nil, xerrors.Errorf(dbRowIterErr, err)
	}

	return result, nil
}

func getWitnessesMessageFromDB(db *sql.DB, messageID string) ([]message.WitnessSignature, error) {
	query := `
		SELECT
			pub_key,
			witness_signature
		FROM
			message_witness
		WHERE
			message_id = ?`

	stmt, err := db.Prepare(query)
	if err != nil {
		return nil, xerrors.Errorf(dbPrepareErr, err)
	}

	defer stmt.Close()

	rows, err := stmt.Query(messageID)
	if err != nil {
		return nil, xerrors.Errorf(dbQueryRowErr, err)
	}

	defer rows.Close()

	result := make([]message.WitnessSignature, 0)

	for rows.Next() {
		var pubKey string
		var signature string

		err = rows.Scan(&pubKey, &signature)
		if err != nil {
			return nil, xerrors.Errorf(dbParseRowErr, err)
		}

		result = append(result, message.WitnessSignature{
			Witness:   pubKey,
			Signature: signature,
		})
	}

	err = rows.Err()
	if err != nil {
		return nil, xerrors.Errorf(dbRowIterErr, err)
	}

	return result, nil
}

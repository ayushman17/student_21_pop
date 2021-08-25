package hub

import (
	"database/sql"
	"encoding/base64"
	"log"
	"os"
	"student20_pop/message"
	"sync"
	"time"

	"golang.org/x/xerrors"

	_ "github.com/mattn/go-sqlite3"
)

// inbox represents an in-memory data store to record incoming messages.
type inbox struct {
	mutex     sync.RWMutex
	msgs      map[string]*messageInfo
	channelID string
}

// createInbox creates an instance of inbox.
func createInbox(channelID string) *inbox {
	return &inbox{
		mutex:     sync.RWMutex{},
		msgs:      make(map[string]*messageInfo),
		channelID: channelID,
	}
}

// addWitnessSignature adds a signature of witness to a message of ID `messageID`.
// if the signature was correctly added return true
// otherwise returns false
func (i *inbox) addWitnessSignature(messageID []byte, public message.PublicKey, signature message.Signature) error {
	msg, ok := i.getMessage(messageID)
	if !ok {
		// TODO: We received a witness signature before the message itself.
		// We ignore it for now but it might be worth keeping it until we
		// actually receive the message
		msgIDEncoded := base64.URLEncoding.EncodeToString(messageID)
		log.Printf("failed to find message_id %s for witness message", msgIDEncoded)
		return message.NewErrorf(-4, "failed to find message_id %q for witness message", msgIDEncoded)
	}

	i.mutex.Lock()
	defer i.mutex.Unlock()

	msg.WitnessSignatures = append(msg.WitnessSignatures, message.PublicKeySignaturePair{
		Witness:   public,
		Signature: signature,
	})

	if os.Getenv("HUB_DB") != "" {
		log.Println("adding witness into db")

		msgIDEncoded := base64.URLEncoding.EncodeToString(messageID)

		db, err := sql.Open("sqlite3", os.Getenv("HUB_DB"))
		if err != nil {
			log.Printf("error: failed to open connection: %v", err)
		} else {
			defer db.Close()

			err := addWitnessInDB(db, msgIDEncoded, public, signature)
			if err != nil {
				log.Printf("error: failed to store witness into db: %v", err)
			}
		}
	}

	return nil
}

// storeMessage stores a message inside the inbox
func (i *inbox) storeMessage(msg message.Message) {
	i.mutex.Lock()
	defer i.mutex.Unlock()

	msgIDEncoded := base64.URLEncoding.EncodeToString(msg.MessageID)
	storedTime := message.Timestamp(time.Now().UnixNano())

	messageInfo := &messageInfo{
		message:    &msg,
		storedTime: storedTime,
	}

	i.msgs[msgIDEncoded] = messageInfo

	if os.Getenv("HUB_DB") != "" {
		log.Println("storing message into db")

		err := i.storeMessageInDB(messageInfo)
		if err != nil {
			log.Printf("error: failed to store message into db: %v", err)
		}
	}
}

// getMessage returns the message of messageID if it exists.
func (i *inbox) getMessage(messageID []byte) (*message.Message, bool) {
	msgIDEncoded := base64.URLEncoding.EncodeToString(messageID)

	i.mutex.Lock()
	defer i.mutex.Unlock()
	msgInfo, ok := i.msgs[msgIDEncoded]
	if !ok {
		return nil, false
	}
	return msgInfo.message, true
}

func (i *inbox) storeMessageInDB(messageInfo *messageInfo) error {
	db, err := sql.Open("sqlite3", os.Getenv("HUB_DB"))
	if err != nil {
		return xerrors.Errorf("failed to open connection: %v", err)
	}

	defer db.Close()

	query := `
		INSERT INTO 
			message_info(
				message_id, 
				sender, 
				message_signature, 
				raw_data, 
				message_timestamp, 
				lao_channel_id) 
		VALUES(?, ?, ?, ?, ?, ?)`

	stmt, err := db.Prepare(query)
	if err != nil {
		return xerrors.Errorf("failed to prepare query: %v", err)
	}

	defer stmt.Close()

	msg := messageInfo.message

	_, err = stmt.Exec(msg.MessageID, msg.Sender, msg.Signature, msg.RawData, messageInfo.storedTime, i.channelID)
	if err != nil {
		return xerrors.Errorf("failed to exec query: %v", err)
	}

	for _, pubKeySigPair := range messageInfo.message.WitnessSignatures {
		err = addWitnessInDB(db, string(messageInfo.message.MessageID),
			pubKeySigPair.Witness, pubKeySigPair.Signature)
		if err != nil {
			return xerrors.Errorf("failed to store witness: %v", err)
		}
	}

	return nil
}

func addWitnessInDB(db *sql.DB, messageID string, pubKey message.PublicKey,
	signature message.Signature) error {

	query := `
		INSERT INTO
			message_witness(
				pub_key, 
				witness_signature, 
				message_id) 
		VALUES(?, ?, ?)`

	stmt, err := db.Prepare(query)
	if err != nil {
		return xerrors.Errorf("failed to prepare query: %v", err)
	}

	defer stmt.Close()

	_, err = stmt.Exec(pubKey, signature, messageID)
	if err != nil {
		return xerrors.Errorf("failed to exec query: %v", err)
	}

	return nil
}

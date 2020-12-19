/*interface file for actors*/
package actors

import (
	"log"
	"student20_pop/lib"
	"student20_pop/message"
	"student20_pop/parser"
)

type Actor interface {
	//Public functions
	HandleWholeMessage(msg []byte, userId int) (msgAndChannel []lib.MessageAndChannel, responseToSender []byte)
	GetSubscribers(channel string) []int
	//Private functions
	handleSubscribe(query message.Query, userId int) error
	handleUnsubscribe(query message.Query, userId int) error
	handlePublish(query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
	handleCreateLAO(msg message.Message, canal string, query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
	handleUpdateProperties(msg message.Message, canal string, query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
	handleWitnessMessage(msg message.Message, canal string, query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
	handleLAOState(msg message.Message, canal string, query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
	handleCreateRollCall(mag message.Message, canal string, query message.Query) (msgAndChannel []lib.MessageAndChannel, err error)
}

//TODO cette fonction ferait plus de sens dans le package `message` non ?
func filterAnswers(receivedMsg []byte) (bool, error) {
	genericMsg, err := parser.ParseGenericMessage(receivedMsg)
	if err != nil {
		return false, err
	}

	_, isAnswerMsg := genericMsg["result"]
	if isAnswerMsg {
		log.Printf("an answer has been received, with %v", string(receivedMsg))
		return true, nil
	}

	_, isErrorMsg := genericMsg["error"]
	if isErrorMsg {
		log.Printf("an answer has been received, with %v", string(receivedMsg))
		return true, nil
	}
	return false, nil
}

/* creates a message to publish on a channel from a received message. */
func finalizeHandling(canal string, query message.Query) (message []byte, channel []byte) {
	return parser.ComposeBroadcastMessage(query), []byte(canal)
}

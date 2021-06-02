package hub

import (
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"golang.org/x/xerrors"
	"log"
	"sort"
	"student20_pop/message"
	"sync"
)

type electionChannel struct {
	*baseChannel

	// Starting time of the election
	start message.Timestamp

	// Ending time of the election
	end message.Timestamp

	// True if the election is over and false otherwise
	terminated bool

	// Questions asked to the participants
	//the key will be the string representation of the id of type byte[]
	questions map[string]question
}

type question struct {
	// ID of th question
	id []byte

	// Different options
	ballotOptions []message.BallotOption

	//valid vote mutex
	validVotesMu sync.RWMutex

	// list of all valid votes
	// the key represents the public key of the person casting the vote
	validVotes map[string]validVote

	// Voting method of the election
	method message.VotingMethod
}

type validVote struct {
	// time of the creation of the vote
	voteTime message.Timestamp

	// indexes of the ballot options
	indexes []int
}

func (c *laoChannel) createElection(msg message.Message) error {
	organizerHub := c.hub

	organizerHub.Lock()
	defer organizerHub.Unlock()

	// Check the data
	data, ok := msg.Data.(*message.ElectionSetupData)
	if !ok {
		return &message.Error{
			Code:        -4,
			Description: "failed to cast data to SetupElectionData",
		}
	}

	// Check if the Lao ID of the message corresponds to the channel ID
	encodedLaoID := base64.URLEncoding.EncodeToString(data.LaoID)
	channelID := c.channelID[6:]
	if channelID != encodedLaoID {
		return &message.Error{
			Code:        -4,
			Description: fmt.Sprintf("Lao ID of the message (Lao: %s) is different from the channelID (channel: %s)", encodedLaoID, channelID),
		}
	}

	// Compute the new election channel id
	encodedElectionID := base64.URLEncoding.EncodeToString(data.ID)
	encodedID := encodedLaoID + "/" + encodedElectionID

	// Create the new election channel
	electionCh := electionChannel{
		createBaseChannel(organizerHub, rootPrefix+encodedID),
		data.StartTime,
		data.EndTime,
		false,
		getAllQuestionsForElectionChannel(data.Questions),
	}

	// Add the SetupElection message to the new election channel
	messageID := base64.URLEncoding.EncodeToString(msg.MessageID)
	electionCh.inboxMu.Lock()
	electionCh.inbox[messageID] = msg
	electionCh.inboxMu.Unlock()

	// Add the new election channel to the organizerHub
	organizerHub.channelByID[encodedID] = &electionCh

	return nil
}

func (c *electionChannel) Publish(publish message.Publish) error {
	err := c.baseChannel.VerifyPublishMessage(publish)
	if err != nil {
		return xerrors.Errorf("failed to verify publish message on an election channel: %v", err)
	}

	msg := publish.Params.Message

	data := msg.Data

	object := data.GetObject()

	if object == message.ElectionObject {

		action := message.ElectionAction(data.GetAction())
		switch action {
		case message.CastVoteAction:
			return c.castVoteHelper(publish)
		case message.ElectionEndAction:
			log.Fatal("Not implemented", message.ElectionEndAction)
		case message.ElectionResultAction:
			log.Fatal("Not implemented", message.ElectionResultAction)
		}
	}

	return nil
}

func (c *electionChannel) castVoteHelper(publish message.Publish) error {
	msg := publish.Params.Message

	voteData, ok := msg.Data.(*message.CastVoteData)
	if !ok {
		return &message.Error{
			Code:        -4,
			Description: "failed to cast data to CastVoteData",
		}
	}

	if voteData.CreatedAt > c.end {
		return &message.Error{
			Code:        -4,
			Description: fmt.Sprintf("Vote cast too late, vote casted at %v and election ended at %v", voteData.CreatedAt, c.end),
		}
	}

	//This should update any previously set vote if the message ids are the same
	messageID := base64.URLEncoding.EncodeToString(msg.MessageID)
	c.inboxMu.Lock()
	c.inbox[messageID] = *msg
	c.inboxMu.Unlock()
	for _, q := range voteData.Votes {

		QuestionID := base64.URLEncoding.EncodeToString(q.QuestionID)
		qs, ok := c.questions[QuestionID]

		if !ok {
			return &message.Error{
				Code:        -4,
				Description: "No Question with this ID exists",
			}
		}
		//this is to handle the case when the organizer must handle multiple votes being cast at the same time
		qs.validVotesMu.Lock()
		earlierVote, ok := qs.validVotes[msg.Sender.String()]
		// if the sender didn't previously cast a vote or if the vote is no longer valid update it

		if err := checkMethodProperties(qs.method, len(q.VoteIndexes)); err != nil{
			return err
		}
		if !ok {
			qs.validVotes[msg.Sender.String()] =
				validVote{voteData.CreatedAt,
					q.VoteIndexes}
		} else {
			changeVote(&qs, earlierVote, msg.Sender.String(), voteData.CreatedAt, q.VoteIndexes)
		}
		//other votes can now change the list of valid votes
		qs.validVotesMu.Unlock()
	}

	log.Printf("Vote casted with success")
	return nil
}
func checkMethodProperties(method message.VotingMethod, length int) error {

	if method == "Plurality" && length < 1 {
		return &message.Error{
			Code:        -4,
			Description: "No ballot option was chosen for plurality voting method",
		}
	}
	if method == "Approval" && length != 1 {
		return &message.Error{
			Code:        -4,
			Description: "Cannot choose multiple ballot options on Approval voting method",
		}
	}
	return nil
}

func changeVote(qs *question, earlierVote validVote, sender string, created message.Timestamp, indexes []int) {
	if earlierVote.voteTime > created {
		qs.validVotes[sender] =
			validVote{
				voteTime: created,
				indexes:  indexes,
			}
	}
}

func getAllQuestionsForElectionChannel(questions []message.Question) map[string]question {
	qs := make(map[string]question)
	for _, q := range questions {
		qs[base64.URLEncoding.EncodeToString(q.ID)] = question{
			id:            q.ID,
			ballotOptions: q.BallotOptions,
			validVotesMu:  sync.RWMutex{},
			validVotes:    make(map[string]validVote),
			method:        q.VotingMethod,
		}
	}
	return qs
}

func (c *electionChannel) endElectionHelper(publish message.Publish) error {
	endElectionData, ok := publish.Params.Message.Data.(*message.ElectionEndData)
	if !ok {
		return &message.Error{
			Code:        -4,
			Description: "failed to cast data to ElectionEndData",
		}
	}
	if endElectionData.CreatedAt < c.end{
		return &message.Error{
			Code:        -4,
			Description: "Can't send end election message before the end of the election",
		}
	}
	// since we eliminated (in cast vote) the duplicate votes we are sure that the voter casted one vote for one question
	for _,question := range c.questions{
		hashed,err := sortHashVotes(question.validVotes)
		if err != nil {
			return &message.Error{
				Code:        -4,
				Description: "Error while hashing",
			}
		}
		endElectionData.RegisteredVotes = hashed
	}
	msg := publish.Params.Message
	messageID := base64.URLEncoding.EncodeToString(msg.MessageID)
	c.inboxMu.Lock()
	c.inbox[messageID] = *msg
	c.inboxMu.Unlock()

	return nil
}
func sortHashVotes(votes2 map[string]validVote)([]byte,error) {
	type kv struct {
		voteTime message.Timestamp
		sender   string
	}
	votes := make(map[int]kv)
	i := 0
	for k, v := range votes2 {
		votes[i] = kv{v.voteTime, k}
		i += 1
	}
	sort.Slice(votes,
		func(i int, j int) bool { return votes[i].voteTime < votes[j].voteTime })
	h := sha256.New()
	for _, v := range votes {
		if len(v.sender) == 0 {
			return nil, xerrors.Errorf("empty string to hash()")
		}
		h.Write([]byte(fmt.Sprintf("%d%s", len(v.sender), v.sender)))
	}
	return h.Sum(nil), nil
}

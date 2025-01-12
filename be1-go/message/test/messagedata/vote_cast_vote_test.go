package messagedata

import (
	"encoding/json"
	"os"
	"path/filepath"
	"popstellar/message/messagedata"
	"testing"

	"github.com/stretchr/testify/require"
)

func Test_Vote_Cast_Vote(t *testing.T) {
	file := filepath.Join(relativeExamplePath, "vote_cast_vote.json")

	buf, err := os.ReadFile(file)
	require.NoError(t, err)

	object, action, err := messagedata.GetObjectAndAction(buf)
	require.NoError(t, err)

	require.Equal(t, "election", object)
	require.Equal(t, "cast_vote", action)

	var msg messagedata.VoteCastVote

	err = json.Unmarshal(buf, &msg)
	require.NoError(t, err)

	require.Equal(t, "election", msg.Object)
	require.Equal(t, "cast_vote", msg.Action)
	require.Equal(t, "fzJSZjKf-2cbXH7kds9H8NORuuFIRLkevJlN7qQemjo=", msg.Lao)
	require.Equal(t, "zG1olgFZwA0m3mLyUqeOqrG0MbjtfqShkyZ6hlyx1tg=", msg.Election)
	require.Equal(t, int64(1633098941), msg.CreatedAt)

	require.Len(t, msg.Votes, 1)
	require.Equal(t, "8L2MWJJYNGG57ZOKdbmhHD9AopvBaBN26y1w5jL07ms=", msg.Votes[0].ID)
	require.Equal(t, "2PLwVvqxMqW5hQJXkFpNCvBI9MZwuN8rf66V1hS-iZU=", msg.Votes[0].Question)

	require.Len(t, msg.Votes[0].Vote, 1)
	require.Equal(t, 0, msg.Votes[0].Vote[0])
}

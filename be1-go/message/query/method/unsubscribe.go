package method

import "student20_pop/message/query"

// Unsubscribe ....
type Unsubscribe struct {
	query.Base

	ID int `json:"id"`

	Params struct {
		Channel string `json:"channel"`
	} `json:"params"`
}
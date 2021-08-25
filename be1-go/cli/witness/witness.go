// Package witness contains the entry point for starting the witness
// server.
package witness

import (
	"encoding/base64"
	"fmt"
	"log"
	"net/url"
	"student20_pop/crypto"
	"student20_pop/hub"
	"student20_pop/network"
	"student20_pop/network/socket"
	"sync"

	"github.com/gorilla/websocket"
	"github.com/urfave/cli/v2"
	"golang.org/x/xerrors"
)

// Serve parses the CLI arguments and spawns a hub and a websocket server
// for the witness.
func Serve(cliCtx *cli.Context) error {

	// get command line args which specify public key, organizer address, port for organizer,
	// clients, witnesses, other witness' addresses
	organizerAddress := cliCtx.String("organizer-address")
	clientPort := cliCtx.Int("client-port")
	witnessPort := cliCtx.Int("witness-port")
	otherWitness := cliCtx.StringSlice("other-witness")
	pk := cliCtx.String("public-key")
	if pk == "" {
		return xerrors.Errorf("witness' public key is required")
	}

	// decode public key and unmarshal public key
	pkBuf, err := base64.URLEncoding.DecodeString(pk)
	if err != nil {
		return xerrors.Errorf("failed to base64url decode public key: %v", err)
	}

	point := crypto.Suite.Point()
	err = point.UnmarshalBinary(pkBuf)
	if err != nil {
		return xerrors.Errorf("failed to unmarshal public key: %v", err)
	}

	// create witness hub
	h, err := hub.NewWitnessHub(point)
	if err != nil {
		return xerrors.Errorf("failed create the witness hub: %v", err)
	}

	// launch witness hub
	h.Start()

	// create wait group which waits for goroutines to finish
	wg := &sync.WaitGroup{}
	done := make(chan struct{})

	// connect to organizer's witness endpoint
	err = connectToWitnessSocket(hub.OrganizerHubType, organizerAddress, h, wg, done)
	if err != nil {
		return xerrors.Errorf("failed to connect to organizer: %v", err)
	}

	// connect to other witnesses
	for _, witness := range otherWitness {
		err = connectToWitnessSocket(hub.WitnessHubType, witness, h, wg, done)
		if err != nil {
			return xerrors.Errorf("failed to connect to witness: %v", err)
		}
	}

	// create and serve servers for witnesses and clients
	clientSrv := network.NewServer(h, clientPort, socket.ClientSocketType)
	clientSrv.Start()

	witnessSrv := network.NewServer(h, witnessPort, socket.WitnessSocketType)
	witnessSrv.Start()

	// shut down client server and witness server when ctrl+c received
	err = network.WaitAndShutdownServers(clientSrv, witnessSrv)
	if err != nil {
		return err
	}
	<-witnessSrv.Stopped
	<-clientSrv.Stopped

	// stop the hub
	h.Stop()
	close(done)
	wg.Wait()

	return nil
}

// connectToSocket establishes a connection to another server's witness
// endpoint.
func connectToWitnessSocket(otherHubType hub.HubType, address string, h hub.Hub, wg *sync.WaitGroup, done chan struct{}) error {
	urlString := fmt.Sprintf("ws://%s/%s/witness/", address, otherHubType)
	u, err := url.Parse(urlString)
	if err != nil {
		return xerrors.Errorf("failed to parse connection url %s %v", urlString, err)
	}

	ws, _, err := websocket.DefaultDialer.Dial(u.String(), nil)
	if err != nil {
		return xerrors.Errorf("failed to dial to %s: %v", u.String(), err)
	}

	log.Printf("connected to %s at %s", otherHubType, urlString)

	switch otherHubType {
	case hub.OrganizerHubType:
		organizerSocket := socket.NewOrganizerSocket(h.Receiver(), h.OnSocketClose(), ws, wg, done)
		wg.Add(2)

		go organizerSocket.WritePump()
		go organizerSocket.ReadPump()
	case hub.WitnessHubType:
		witnessSocket := socket.NewWitnessSocket(h.Receiver(), h.OnSocketClose(), ws, wg, done)
		wg.Add(2)

		go witnessSocket.WritePump()
		go witnessSocket.ReadPump()
	default:
		return xerrors.Errorf("invalid other hub type: %v", otherHubType)
	}

	return nil
}

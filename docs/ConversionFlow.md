# Flow for a Conversion between the Client and Watson Assistant (audio channel)

This outlines the back and forth flow between the client and the server for a 'conversation'.  Most of this is focused on an audio client, but it can apply to other types of clients (video, text).

## Question and Answer (high-level)

This is to illustrate a Q&A flow between the client and the server and indicate what the `prompt` value of the response would be.  The `prompt` is used by the client to handle the ability for the user to respond.

* > Please make a reservation (could by one of many types - the flow doesn't care, the actual skill and dialog would)...
*  _> Where? (prompt=true)_
* > At XYZZY...
*  _> What time? (prompt=true)_
* > 8 PM...
*  _> For how many? (prompt=true)_
* > 3 people...
*  _> Okay, you would like a reservation at XYZZY for 3 at 8 PM.  Is that correct?..._
* > Yes ('No' is alternate flow)
*  _> Okay, I've made your reservation.  You are all set._

* > Do you have a pool?
*  _> Yes, there is a pool in the garden area. (prompt=false)_


### 'Prompt' value

#### Server returns: `prompt=false`
 Means the client is **done** once it finishes *playing* the response.
 
#### Server returns: `prompt=true`
 Means the server expects a response from the client - therefore, the client should except user input as soon as the server response has been delivered.
 
 (There are some questions about this.  How do we handle the case were the server response is long (on a *prompt* response) and the user wants to stop it?  To start, we are assuming the user will finish the conversation (or will say stop - which should interrupt it).
 
 
# Winsome API-Server Protocol

This file describes the communication protocol between the `Winsome Server`
and the `Winsome API`. 

Both requests and responses are `UTF-8`-encoded strings
representing `JSON` objects. The request must contain a field `request-code`,
describing the request type, whereas the response must contain a field
`response-code`, describing the server response.

### API Request Codes

The following request codes are valid requests:

- `MULTICAST`: asks for the multicast coordinates,
- `LOGIN`: login request,
- `LOGOUT`: logout request,
- `GET_USERS`: get the users of the Social Network,
- `GET_FOLLOWING`: get the currently followed users,
- `FOLLOW`: follow a user,
- `UNFOLLOW`: unfollow a user,
- `BLOG`: get the posts of a userm
- `POST`: create a new post,
- `FEED`: get all followed user's posts,
- `SHOW_POST`: show a post,
- `DELETE_POST`: delete a post,
- `REWIN_POST`: rewin a post,
- `RATE_POST`: upvote or downvote a post,
- `COMMENT`: add a comment under a post,
- `WALLET`: get the transaction history,
- `WALLET_BTC`: get the total amount of Wincoins accumulated 
    and converted in Bitcoins.

### Server Response Codes

The following response codes are valid responses:

- `SUCCESS`: the operation was successful,
- `MALFORMED_JSON_REQUEST`: the request was malformed,
- `USER_NOT_REGISTERED`: no such user exists,
- `WRONG_PASSW`: user is trying to login with the wrong password,
- `ALREADY_LOGGED`: client or user is already logged in,
- `NO_LOGGED_USER`: client is not logged in,
- `WRONG_USER`: client is logged in on another user,
- `USER_NOT_VISIBLE`: the requester has no common interest with the other user,
- `SELF_FOLLOW`: the user is trying to follow/unfollow themselves,
- `ALREADY_FOLLOWED`: the user already follows another user
- `NOT_FOLLOWING`: the user does not follow another user
- `TEXT_LENGTH`: post title or contents exceed maximum length
- `NO_POST`: no such post exists,
- `NOT_POST_OWNER`: the user is not the owner of the post,
- `POST_OWNER`: the user is the owner of the post,
- `REWIN_ERR`: the user has already rewinned the given post,
- `ALREADY_VOTED`: the user has already rated the given post,
- `WRONG_VOTE_FORMAT`: the vote was in a wrong format,
- `EXCHANGE_RATE_ERROR`: the server could not compure the exchange rate to BTC,
- `FATAL_ERR`: fatal error.

### Structured objects

Structured data is organized in JSON objects.

- `User`:
  - `username`: string, the username of the user
  - `tags`: JSON array of strings, the tags of the user
- `PostHeader`:
  - `id`: integer, the identifier of the given post,
  - `author`: string, the original author of the given post,
  - `title`: string, the title of the given post
  - `rewinner`: optional string, the rewinner who created the given post, if any
- `Post`:
  - `id`: integer, the identifier of the given post,
  - `author`: string, the original author of the given post,
  - `title`: string, the title of the given post
  - `rewinner`: optional string, the rewinner who created the given post, if any,
  - `contents`: string, contents of the given post,
  - `upvotes`: integer, the number of upvotes,
  - `downvotes`: integer, the number of downvotes,
  - `comments`: JSON Array of `Comment`s, the comments under this post
- `Comment`:
  - `author`: string, author of the comment
  - `contents`: string, contents of the comment
- `Transaction`:
  - `increment`: double, the amount of Wincoin in the transaction,
  - `timestamp`, string, the timestamp as obtained from `Instant#toString()`.

## `MULTICAST` request

Client sends a JSON object with the following fields:

- `request-code: MULTICAST`

### Server successful response

Server sends back a JSON object with the following fields:

- `multicast-addr`: the address of the multicast socket
- `mutlicast-port`: the port of the multicast socket

### Server error response

Server may send back any of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed

## `LOGIN` request

Client sends a JSON object with the following fields:

- `request-code: LOGIN`
- `username`: username of the user to login
- `password`: hashed password of the user to login

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: WRONG_PASSW` if the password did not match
- `response-code: ALREADY_LOGGED` if the user was already logged.

## `LOGOUT` request

Client sends a JSON object with the following fields:

- `request-code: LOGOUT`
- `username`: string, username of the currently logged user

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user

## `GET_USERS` request

Client sends a JSON object with the following fields:

- `request-code: GET_USERS`
- `username`: string, username of the currently logged user

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `users`: a JSON Array of `User`s, the visible users

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user

## `GET_FOLLOWING` request

Client sends a JSON object with the following fields:

- `request-code: GET_FOLLOWING`
- `username`: string, username of the currently logged user

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `following`: a JSON Array of `User`s, the followed users

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user

## `FOLLOW` request

Client sends a JSON object with the following fields:

- `request-code: FOLLOW`
- `username`: string, username of the currently logged user
- `to-follow`: string, username of the user to follow

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user
- `response-code: SELF_FOLLOW` if `username = to-follow`
- `response-code: ALREADY_FOLLOWED` if `username` already followed `to-follow`

## `UNFOLLOW` request

Client sends a JSON object with the following fields:

- `request-code: UNFOLLOW`
- `username`: string, username of the currently logged user
- `to-unfollow`: string, username of the user to unfollow

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user
- `response-code: SELF_FOLLOW` if `username = to-unfollow`
- `response-code: ALREADY_FOLLOWED` if `username` did not follow `to-unfollow`

## `BLOG` request

Client sends a JSON object with the following fields:

- `request-code: BLOG`
- `username`: string, username of the currently logged user
- `to-view`: string, username of the user to show

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `posts`: JSON array of `PostHeader`s, the posts of the user `to-view`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user
- `response-code: USER_NOT_VISIBLE` if `username` has no common tags with
    `to-view`.

## `POST` request

Client sends a JSON object with the following fields:

- `request-code: POST`
- `username`: string, username of the currently logged user
- `title`: string, title of the post
- `content`: string, contents of the post

### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `id`: integer, the identifier of the newly created post.

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user
- `response-code: TEXT_LENGTH` if `title` or `content` exceed maximum length.

## `FEED` request

Client sends a JSON object with the following fields:

- `request-code: FEED`
- `username`: string, username of the currently logged user
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `posts`: JSON Array of `PostHeader`s, the posts in the user's feed.

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in
- `response-code: WRONG_USER` if the client was logged on a different user.

## `SHOW_POST` request

Client sends a JSON object with the following fields:

- `request-code: SHOW_POST`
- `username`: string, username of the currently logged user
- `id`: integer, identifier of the post to show
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `post`: `Post`, the requested post.

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: NO_POST` if the post does not exist or the user cannot see it.

## `DELETE_POST` request

Client sends a JSON object with the following fields:

- `request-code: DELETE_POST`
- `username`: string, username of the currently logged user
- `id`: integer, identifier of the post to delete
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: NO_POST` if the post does not exist or the user cannot see it,
- `response-code: NOT_POST_OWNER` if the user is not the owner of the post.

## `REWIN_POST` request

Client sends a JSON object with the following fields:

- `request-code: REWIN_POST`
- `username`: string, username of the currently logged user
- `id`: integer, identifier of the post to delete
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: NO_POST` if the post does not exist or the user cannot see it,
- `response-code: POST_OWNER` if the user is trying to rewin their own post,
- `response-code: NOT_FOLLOWING` if the user cannot interact with the post because
    they are not following the post's owner;
- `response-code: REWIN_ERR` if the user has already rewinned the post.

## `RATE_POST` request

Client sends a JSON object with the following fields:

- `request-code: RATE_POST`
- `username`: string, username of the currently logged user
- `id`: integer, identifier of the post to rate
- `vote`: integer, either `+1` (for upvoting) or `-1` (for downvoting)
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: NO_POST` if the post does not exist or the user cannot see it,
- `response-code: POST_OWNER` if the user is trying to vote their own post,
- `response-code: NOT_FOLLOWING` if the user cannot interact with the post because
    they are not following the post's owner;
- `response-code: ALREADY_VOTED` if the user has already voted the post,
- `response-code: WRONG_VOTE_FORMAT` if the vote was neither `+1` nor `-1`.

## `COMMENT` request

Client sends a JSON object with the following fields:

- `request-code: COMMENT`
- `username`: string, username of the currently logged user
- `id`: integer, identifier of the post to rate
- `comment`: string, the comment
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: NO_POST` if the post does not exist or the user cannot see it,
- `response-code: POST_OWNER` if the user is trying to vote their own post,
- `response-code: NOT_FOLLOWING` if the user cannot interact with the post because
    they are not following the post's owner.

## `WALLET` request

Client sends a JSON object with the following fields:

- `request-code: WALLET`
- `username`: string, username of the currently logged user
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `total`: double, the total amount of Wincoins accumulated by the user,
- `transactions`: JSON Array of `Transaction`s, the transaction history

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user.

## `WALLET_BTC` request

Client sends a JSON object with the following fields:

- `request-code: WALLET_BTC`
- `username`: string, username of the currently logged user
  
### Server successful response

Server sends back a JSON object with the following fields:

- `response-code: SUCCESS`
- `btc-total`: double, the total amount of Bitcoins accumulated by the user.

### Server error response

Server may send back one of these errors:

- `response-code: MALFORMED_JSON_REQUEST` if the request was malformed,
- `response-code: USER_NOT_REGISTERED` if no user with the given username exists,
- `response-code: NOT_LOGGED` if the client was not logged in,
- `response-code: WRONG_USER` if the client was logged on a different user,
- `response-code: EXCHANGE_RATE_ERROR` if the server could not compute the 
    exchange rate from Wincoins to BTC.
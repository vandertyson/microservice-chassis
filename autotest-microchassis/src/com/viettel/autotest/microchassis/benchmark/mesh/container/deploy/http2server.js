const http2 = require('http2')

// create a new server instance
const server = http2.createServer()

// log any error that occurs when running the server
server.on('error', (err) => console.error(err))
const router = (stream, headers) => {
    // first, extract the path and method pseudo headers
    const path = headers[':path']
    const method = headers[':method']

    // we can use a simple if-else ladder to determine the
    // final destination for the request stream and assign
    // it to the `handler` variable
    let handler
    if (path === "/forward") {
        handler = echoHandler
    }
    else if (path === "/ping" && method == 'GET') {
        handler = pingHandler
    }
    else {
        handler = notFoundHandler
    }

    // finally, apply the chosen handler to the request
    handler(stream, headers)
}
const bizHandler = (stream, headers) => {
    console.log({ headers })
    stream.respond({
        ':status': 200
    })
    stream.end('Hello World')
}

const echoHandler = (stream, headers) => {
    let body = '';

  // Handle incoming data
  stream.on('data', (chunk) => {
    body += chunk;
  });

  // Handle end of request
  stream.on('end', () => {
    const now = new Date();

// Format the current time as a string including milliseconds
const formattedTime = `${now.toLocaleTimeString()}.${now.getMilliseconds().toString().padStart(3, '0')}`;

// Log the current time and the variable
console.log(`[${formattedTime}] echo: ${body}`);
    // Echo back the request body
    stream.respond({
      'content-type': 'text/plain',
      ':status': 200
    });
    stream.end(body);
  });
  }


// the pingHandler returns "pong" to let us know that
// the server is up and running
const pingHandler = (stream, headers) => {
    console.log({ headers })
    stream.respond({
        ':status': 200
    })
    stream.end('pong')
}

// in case a route doesn't exist, we want to return a
// 404 status and a message that the path is not found
const notFoundHandler = (stream, headers) => {
    stream.respond({
        'content-type': 'text/plain; charset=utf-8',
        ':status': 200
    })
    stream.end('path not found')
}
// the 'stream' callback is called when a new
// stream is created. Or in other words, every time a
// new request is received
server.on('stream', router);
server.on('session', (session) => {
    console.log(`New connection established: ${session.socket.remoteAddress}:${session.socket.remotePort}`);
  });
// start the server on port 8000
server.listen(9015, () => {
    console.log(`HTTP/2 server listening on port 9015`);
  });
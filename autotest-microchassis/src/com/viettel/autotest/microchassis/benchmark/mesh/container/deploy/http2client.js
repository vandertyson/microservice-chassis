const http2 = require('http2');

// Create a client session
const client = http2.connect('http://localhost:9013');

// Handle connection errors
client.on('error', (err) => console.error('Session Error:', err));

// Function to make a request
function makeRequest(path, reqData) {
  return new Promise((resolve, reject) => {
    const req = client.request({ ':path': path, ':method': 'POST' });
    
    req.setEncoding('utf8');
    
    let data = '';

    req.on('response', (headers, flags) => {
      for (const name in headers) {
        console.log(`${name}: ${headers[name]}`);
      }
    });

    req.on('data', (chunk) => {
      data += chunk;
    });

    req.on('end', () => {
      resolve(data);
    });

    req.on('error', (err) => {
      reject(err);
    });
    req.write(JSON.stringify(reqData), 'utf8');
    req.end();
  });
}

// Example usage
(async () => {
  try {
    const response1 = await makeRequest('/forward');
    console.log('Response 1:', response1);

    const response2 = await makeRequest('/endpoint2');
    console.log('Response 2:', response2);
  } catch (error) {
    console.error('Request failed:', error);
  } finally {
    client.close();
  }
})();
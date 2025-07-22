from hyper import HTTP20Connection
from threading import Thread
from hyper.http20.exceptions import ConnectionError, StreamResetError
import os

import sys, time, datetime, uuid
KB = 1024
MB = KB**2
GB = KB**3
def random_content(length):
    """Generate random content of a specified length in bytes."""
    return os.urandom(length)
print("Python version:", sys.version)
# Define server details

host = '172.20.3.130'
if len(sys.argv) >= 2:
    host = sys.argv[1]
port = 9013
if len(sys.argv) >= 3:
    port = int(sys.argv[2])
n_chunks = 3
if len(sys.argv) >= 4:
    n_chunks = int(sys.argv[3])
path = "/forward"
hops=1
if len(sys.argv) >= 5:
    hops = int(sys.argv[4])-1
shutdown_hop=0
if len(sys.argv) >= 6:
    shutdown_hop = int(sys.argv[5])-1
lastStreamIdIncrement=1
if len(sys.argv) >= 7:
    lastStreamIdIncrement = int(sys.argv[6])
print("argv 6", sys.argv[6], lastStreamIdIncrement)
# Create an HTTP/2 connection
conn = HTTP20Connection(host, port=port)
print("Connect to {}:{}".format(host, port))
def build_headers(headers, ttl=hops):
    headers["ttl"] = str(ttl)
    headers["mess_id"] = str(uuid.uuid4())
    print("Compose headers", headers)
    return headers
# Send a discrete request
def send(path):
    c = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")
    stream_id = conn.request('POST', path, c, headers=build_headers({}))
    response = conn.get_response(stream_id)
    res_body = str(response.read(), 'utf-8')
    print(f"[{stream_id}] Status: {response.status} Res: {res_body} {len(res_body)} bytes/match:{res_body == c}")  # Headers: {response.headers}

def chunk_split(data, chunk_size=MB):
    return [data[i:i+chunk_size] for i in range(0, len(data), chunk_size)]
# Function to send a large file in chunks
def send_chunks(content, chunk_size=KB):
    total_size = len(content)
    chunks = chunk_split(content, chunk_size)
    for i, chunk in enumerate(chunks):
        c = "hello"
        stream_id = conn.request('POST', path, body=c, headers=build_headers({'Content-Type': 'application/octet-stream'}))
#         conn.endheaders(None, False, stream_id)
        print("Getting response for stream_id", stream_id)
        response = conn.get_response(stream_id)
        res_body = str(response.read(), 'utf-8')
        print(f"[{stream_id}] Chunk {i + 1}/{len(chunks)} - Status: {response.status} - Res: {res_body} {len(res_body)} bytes/match:{res_body == c}")
def call_shutdown_notifyStop():
    try:
        stream_id = conn.request('GET', "/shutdown", headers=build_headers(
        {"last-stream-id-increment": str(lastStreamIdIncrement)} if lastStreamIdIncrement > 0 else {},
         shutdown_hop))
        print("Getting response for stream_id", stream_id)
        time.sleep(10)
        response = conn.get_response(stream_id)
        res_body = str(response.read(), 'utf-8')
        print(f"[{stream_id}] Status: {response.status} Res: {len(res_body)} bytes {res_body} ")  # Headers: {response.headers}
    except StreamResetError as e:
        print("GO AWAYed:", e)


# Send a discrete request
send(path)
print("Done send discrete")
# Send the large file in chunks
send_chunks(random_content(n_chunks*KB))
print("Done send chunks")
print("shuting down async")
t = Thread(target=call_shutdown_notifyStop)
t.start()
print("Wait 5s")
time.sleep(5)
send(path)
print("Done send discrete")
print("Wait go away ...")
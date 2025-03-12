import bluetooth
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading
import time

# --- Bluetooth Configuration ---
server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
port = 1
server_sock.bind(("", port))
server_sock.listen(1)
uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

bluetooth.advertise_service(
    server_sock,
    "SampleServer",
    service_id=uuid,
    service_classes=[uuid, bluetooth.SERIAL_PORT_CLASS],
    profiles=[bluetooth.SERIAL_PORT_PROFILE],
)

print("Waiting for Bluetooth connection...")
client_sock, client_info = server_sock.accept()
print("Accepted Bluetooth connection from ", client_info)


# --- HTTP Request Handler ---
class RequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers["Content-Length"])
        post_data = self.rfile.read(content_length)
        message = post_data.decode("utf-8")
        print("Received data from HTTP client:", message)

        # Send data via Bluetooth and wait for confirmation
        try:
            client_sock.send(message.encode("utf-8"))
            print("Sent data via Bluetooth:", message)

            # Wait for a response (True/False) from the Bluetooth client
            start_time = time.time()
            response = ""
            while time.time() - start_time < 5:  # Timeout after 5 seconds
                try:
                    data = client_sock.recv(1024)  # Receive data
                    if data:
                        response = data.decode("utf-8")
                        if response.strip() in ["True", "False"]:
                            break
                except bluetooth.btcommon.BluetoothError as e:
                    if e.errno == 11:  # Resource temporarily unavailable
                        time.sleep(0.1)
                        continue
                    else:
                        raise e

            if response.strip() == "True":
                print("Confirmation received: True")
                self.send_response(200)
                self.send_header("Content-type", "text/plain")
                self.end_headers()
                self.wfile.write(b"Data sent and confirmed.\n")
            elif response.strip() == "False":
                print("Confirmation received: False")
                self.send_response(500)
                self.send_header("Content-type", "text/plain")
                self.end_headers()
                self.wfile.write(b"Data received by BT, but processing failed.\n")
            else:
                print("No confirmation received within timeout.")
                self.send_response(408)
                self.send_header("Content-type", "text/plain")
                self.end_headers()
                self.wfile.write(b"No confirmation from Bluetooth device.\n")
        except OSError as e:
            print("Failed to send/receive data via Bluetooth:", e)
            self.send_response(500)
            self.send_header("Content-type", "text/plain")
            self.end_headers()
            self.wfile.write(b"Bluetooth communication error.\n")
        except Exception as e:
            print("An unexpected error occurred", e)
            self.send_response(500)
            self.send_header("Content-type", "text/plain")
            self.end_headers()
            self.wfile.write(b"An unexpected error occurred.\n")


# --- HTTP Server Thread ---
def start_http_server():
    http_server = HTTPServer(("localhost", 8080), RequestHandler)
    print("Starting HTTP server on port 8080...")
    http_server.serve_forever()


http_thread = threading.Thread(target=start_http_server)
http_thread.daemon = True
http_thread.start()

# --- Main Bluetooth Loop ---
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nProgram terminated by user.")
except OSError:
    print("Bluetooth connection lost.")
finally:
    if "client_sock" in locals():
        client_sock.close()
    server_sock.close()
    print("Sockets closed.")

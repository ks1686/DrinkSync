import bluetooth

# Create a new server socket using RFCOMM protocol
server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
port = 1
server_sock.bind(("", port))
server_sock.listen(1)

# Advertise the server socket using a unique UUID
uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

# Advertise the server socket using the Service Discovery Protocol (SDP)
bluetooth.advertise_service(
    server_sock,
    "SampleServer",
    service_id=uuid,
    service_classes=[uuid, bluetooth.SERIAL_PORT_CLASS],
    profiles=[bluetooth.SERIAL_PORT_PROFILE],
)

# Wait for incoming connections
print("Waiting for connection...")
client_sock, client_info = server_sock.accept()
print("Accepted connection from ", client_info)

# Receive data from the client and send it back
try:
    while True:
        data = client_sock.recv(1024)
        if not data:
            break
        print("Received:", data.decode("utf-8"))  # decode the bytes to a string.
        client_sock.send(
            ("Received: " + data.decode("utf-8")).encode("utf-8")
        )  # echo back the data.
except OSError:
    pass

# Close the client socket``
print("Disconnected.")

# Close the server socket
client_sock.close()
server_sock.close()

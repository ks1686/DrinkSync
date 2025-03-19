import bluetooth


def main():
    server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    server_sock.bind(("", bluetooth.PORT_ANY))
    server_sock.listen(1)
    print("Waiting for an Android phone to connect...")
    client_sock, client_info = server_sock.accept()
    print(f"Connected to: {client_info}")

    # Wait for a sync message from the Android phone
    sync_message = client_sock.recv(1024).decode("utf-8")
    print(f"Received: {sync_message}")

    if sync_message == "Sync":
        # Send a confirmation message back to the Android phone
        client_sock.send("Sync Confirmed")

    client_sock.close()
    server_sock.close()


if __name__ == "__main__":
    main()

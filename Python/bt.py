import bluetooth


def main():
    server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    server_sock.bind(("", bluetooth.PORT_ANY))
    server_sock.listen(1)
    print("Waiting for an Android phone to connect...")
    client_sock, client_info = server_sock.accept()
    print(f"Connected to: {client_info}")

    # You could do more work here once connected

    client_sock.close()
    server_sock.close()


if __name__ == "__main__":
    main()

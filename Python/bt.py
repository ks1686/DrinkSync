import bluetooth


def send_message(message):
    """
    Sends a message via Bluetooth to the target device.

    Args:
        message (str): The message to send.
    """
    target_address = "08:8B:C8:32:4F:5F"
    service_uuid = "c7506ec6-09d3-4979-9db3-3b85acad20fd"  # same as the Android side

    service_matches = bluetooth.find_service(uuid=service_uuid, address=target_address)

    if len(service_matches) == 0:
        print("Could not find the DrinkSync service.")
        return False
    else:
        first_match = service_matches[0]
        port = first_match["port"]
        host = first_match["host"]

        try:
            sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            sock.connect((host, port))

            # Send the message
            sock.send(message)
            data = sock.recv(1024)
            print("Received:", data.decode())
            sock.close()
            return True
        except Exception as e:
            print(f"Error sending message: {e}")
            return False

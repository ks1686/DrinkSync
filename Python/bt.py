import bluetooth
import uuid

target_address = "08:8B:C8:32:4F:5F"
service_uuid = "c0de2023-cafe-babe-cafe-2023c0debabe"  # same as the Android side

service_matches = bluetooth.find_service(uuid=service_uuid, address=target_address)

if len(service_matches) == 0:
    print("Could not find the DrinkSync service.")
else:
    first_match = service_matches[0]
    port = first_match["port"]
    host = first_match["host"]

    sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    sock.connect((host, port))
    sock.send("Hello phone!")
    data = sock.recv(1024)
    print("Received:", data.decode())
    sock.close()

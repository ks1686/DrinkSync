import bluetooth
import sys


# take message to send from first argument when calling the script
message = sys.argv[1]

target_address = "08:8B:C8:32:4F:5F"
service_uuid = "c7506ec6-09d3-4979-9db3-3b85acad20fd"  # same as the Android side

service_matches = bluetooth.find_service(uuid=service_uuid, address=target_address)

if len(service_matches) == 0:
    print("Could not find the DrinkSync service.")
else:
    first_match = service_matches[0]
    port = first_match["port"]
    host = first_match["host"]

    sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    sock.connect((host, port))

    #

    sock.send("Hello phone!")
    data = sock.recv(1024)
    print("Received:", data.decode())
    sock.close()

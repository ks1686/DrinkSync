# view for changes in weight_data.txt and uses bt.py to send the data to the phone

import os
import time

# Path to the file that contains the weight data
weight_data_path = "Python/weight_data.txt"

# Path to the Python script that sends the data to the phone
bt_script_path = "Python/bt.py"

# Continuously monitor the weight data file for changes
while True:
    # Get the last modified time of the weight data file
    last_modified = os.path.getmtime(weight_data_path)

    # Wait for a change in the file
    while os.path.getmtime(weight_data_path) == last_modified:
        time.sleep(1)

    # When the file changes, run the Bluetooth script to send the data to the phone
    # The weight data is passed as an argument to the script
    # send each single data point to the phone at a time
    with open(weight_data_path, "r") as file:
        for line in file:
            os.system(f"python {bt_script_path} {line.strip()}")

            # delete the sent data point
            with open(weight_data_path, "r") as f:
                lines = f.readlines()

                # debug print statement
                print(f"Sent: {line.strip()}")

            with open(weight_data_path, "w") as f:
                for line in lines[1:]:
                    f.write(line)

                # debug print statement
                print("Data point deleted")

    # Wait for the next change in the file
    time.sleep(1)  # wait for 1 second before checking for changes again

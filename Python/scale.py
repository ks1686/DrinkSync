import time
import sys
import numpy as np
import RPi.GPIO as GPIO
from hx711 import HX711
from bt import send_message  # Import the send_message function


def cleanAndExit():
    print("\nCleaning up...")
    GPIO.cleanup()
    print("Bye!")
    sys.exit()


# Initialize HX711 on GPIO 5 (DT) and 6 (SCK)
hx = HX711(5, 6)

# Set byte order and bit order
hx.set_reading_format("MSB", "MSB")


# Function to stabilize tare
def stable_tare(samples=20):
    readings = []
    print("Taring... Please wait.")
    for _ in range(samples):
        readings.append(hx.get_weight(5))
        time.sleep(0.1)
    avg_tare = np.median(readings)
    hx.set_offset(avg_tare)
    print(f"Tare complete. Offset set to: {avg_tare}")


# Function to get stable weight reading
def get_filtered_weight(samples=15):
    readings = [hx.get_weight(5) for _ in range(samples)]
    return np.median(readings)  # Median helps remove noise


# Reset and Tare
hx.reset()
stable_tare()

referenceUnit = 425.37  # Adjust this based on calibration
hx.set_reference_unit(referenceUnit)

print("\nTare done! Ready to take readings...")


def take_reading():
    """
    Takes a 3-second average reading from the scale and sends it as a message using bt.py.
    """
    try:
        start_time = time.time()
        readings = []

        # Collect readings for 3 seconds
        while time.time() - start_time < 3:
            readings.append(hx.get_weight(5))
            time.sleep(0.1)  # Small delay to avoid excessive sampling

        # Calculate the average weight
        average_weight = np.mean(readings)

        # Send the average weight as a message using the send_message function
        if send_message(f"Average weight: {average_weight:.2f} grams"):
            print(
                f"Message sent successfully: Average weight: {average_weight:.2f} grams"
            )
        else:
            print("Failed to send the message.")

        print(f"Raw Values: {readings}")  # Debugging info
        print(f"Average Weight Sent: {average_weight:.2f} grams\n")

        hx.power_down()
        hx.power_up()
        time.sleep(0.5)

    except Exception as e:
        print(f"Error during reading: {e}")
        cleanAndExit()

# # loop to test readings
# try:
#     while True:
#         take_reading()
#         time.sleep(1)
# except (KeyboardInterrupt, SystemExit):
#     print("Exiting...")
#     cleanAndExit()

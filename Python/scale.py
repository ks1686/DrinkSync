import time
import sys
import numpy as np
import RPi.GPIO as GPIO
from hx711 import HX711

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

# ** Set the reference unit ** 

### Steps to calibrate ###
# 1. Measure empty scale weight (tare)
# 2. Measure known weight (e.g. 500g)
# 3. Calculate the reference unit using the formula:
#    referenceUnit = filtered_weight / known_weight
# 4. Set the reference unit in the code

referenceUnit = 723.75  # Adjust this based on calibration
hx.set_reference_unit(referenceUnit)

print("\nTare done! Place your weight now...")

# Where the magic happens
try:
    while True:
        raw_values = [hx.get_weight(5) for _ in range(10)]  # Collect multiple readings
        median_weight = np.median(raw_values)  # Use median to reduce noise
        
        print(f"Raw Values: {raw_values}")  # Debugging info
        print(f"Filtered Weight: {median_weight:.2f} grams\n")

        hx.power_down()
        hx.power_up()
        time.sleep(0.5)

except (KeyboardInterrupt, SystemExit):
    cleanAndExit()

import time
import sys
import numpy as np
import RPi.GPIO as GPIO
from hx711 import HX711
# Assuming bt.py is in the same directory or accessible via PYTHONPATH
from bt import send_message
import json  # Needed for reading/writing config file
import os  # Needed for checking if config file exists

# --- Configuration ---
CONFIG_FILE = "scale_config.json"  # File to store/load scale settings
DEFAULT_REFERENCE_UNIT = 425.37  # Adjust this based on your initial calibration
STABLE_TARE_SAMPLES = 20  # Samples for the initial tare process
GET_WEIGHT_SAMPLES = 5  # Samples per single weight reading (used in tare and take_reading)
TAKE_READING_DURATION_S = 3  # Duration to average readings over in take_reading
TAKE_READING_SAMPLE_DELAY = 0.1  # Delay between samples within take_reading
DOUT_PIN = 5  # Data pin
PD_SCK_PIN = 6  # Clock pin

# --- Global HX711 Object ---
# This object will be initialized once when the script loads
# and configured either via tare or loaded config.
hx = None


# --- Function Definitions ---

def cleanAndExit():
    """Cleans up GPIO resources and exits."""
    print("\nCleaning up GPIO...")
    GPIO.cleanup()
    print("Bye!")
    sys.exit()


def stable_tare(hx_instance, samples=STABLE_TARE_SAMPLES):
    """
Performs tare measurement, sets the offset on the hx_instance,
and returns the calculated offset value.
    """
    if not hx_instance:
        print("Error: HX711 instance not provided for tare.")
        return None  # Indicate failure

    readings = []
    print("Taring... Please ensure scale is empty and stable.")
    # Power cycle before tare might help stability
    hx_instance.power_down()
    hx_instance.power_up()
    time.sleep(0.5)

    for i in range(samples):
        try:
            # Use get_raw_data_mean for tare to get value before offset subtraction
            raw_reading = hx_instance.get_raw_data_mean(times=GET_WEIGHT_SAMPLES)
            if raw_reading is not False:  # Check for valid reading
                readings.append(raw_reading)
                print(f"  Tare sample {i + 1}/{samples}: {raw_reading}")
            else:
                print(f"  Warning: Got invalid raw reading during tare sample {i + 1}")
            time.sleep(0.1)
        except Exception as e:
            print(f"  Error getting raw data during tare: {e}")

    if not readings:
        print("ERROR: Could not get any valid readings during tare. Cannot set offset.")
        # Consider raising an error or returning a specific failure value
        return None
    else:
        # Use median for robustness against outliers
        avg_tare_offset = np.median(readings)

    hx_instance.set_offset(avg_tare_offset)
    print(f"Tare complete. Offset set to: {avg_tare_offset}")
    time.sleep(0.5)  # Short delay after setting offset
    return avg_tare_offset  # Return the calculated offset


# Note: get_filtered_weight function removed as its logic is incorporated
# directly into stable_tare (using get_raw_data_mean) and take_reading (using get_weight)

def take_reading():
    """
Takes readings for a specified duration using the globally configured 'hx' object,
calculates the average weight, sends it via Bluetooth, and returns the weight.
    """
    global hx  # Access the global hx object
    if not hx:
        print("Error: Scale (hx) not initialized. Cannot take reading.")
        return None  # Indicate failure

    try:
        start_time = time.time()
        readings = []
        print(f"Taking reading for {TAKE_READING_DURATION_S} seconds...")

        # Power cycle before reading might improve consistency
        hx.power_down()
        hx.power_up()
        time.sleep(0.1)  # Allow time for power up

        # Collect readings for the specified duration
        while time.time() - start_time < TAKE_READING_DURATION_S:
            try:
                # get_weight uses the offset and reference unit already set in 'hx'
                val = hx.get_weight(GET_WEIGHT_SAMPLES)
                readings.append(val)
                # print(f"  Raw reading: {val:.2f}") # Uncomment for detailed debug
                time.sleep(TAKE_READING_SAMPLE_DELAY)  # Small delay
            except Exception as e:
                print(f"  Warning: Error during individual weight reading: {e}")

        if not readings:
            print("Error: No valid readings collected.")
            # Optional: power down hx here if desired after failed reading
            # hx.power_down()
            return None

        # Calculate the average weight using median for noise reduction
        average_weight = np.median(readings)

        # Prepare message
        message = f"Average weight: {average_weight:.2f} grams"

        # Send the average weight as a message
        if send_message(message):
            print(f"Message sent successfully: {message}")
        else:
            print(f"Failed to send the message: {message}")

        # Debugging info
        # print(f"Raw Values Collected: {[f'{r:.2f}' for r in readings]}")
        print(f"Calculated Average Weight: {average_weight:.2f} grams\n")

        # Power down the sensor to save power until the next reading
        # It will be powered up at the start of the next take_reading call
        hx.power_down()

        return average_weight  # Return the calculated weight

    except Exception as e:
        print(f"Error during take_reading: {e}")
        # Attempt to power down even on error
        try:
            if hx: hx.power_down()
        except:
            pass  # Ignore errors during power down in cleanup
        # Consider calling cleanAndExit() or raising the exception
        return None  # Indicate failure


# --- Initialization Code (Runs ONCE when script is imported or executed) ---

print("--- Initializing Scale ---")
config_loaded_successfully = False
loaded_offset = None
loaded_reference_unit = None

# 1. Check for and load existing configuration file
if os.path.exists(CONFIG_FILE):
    print(f"Found configuration file: {CONFIG_FILE}")
    try:
        with open(CONFIG_FILE, 'r') as f:
            config_data = json.load(f)
            # Validate required keys exist
            if 'offset' in config_data and 'referenceUnit' in config_data:
                loaded_offset = config_data['offset']
                loaded_reference_unit = config_data['referenceUnit']
                print("Successfully loaded configuration:")
                print(f"  Offset: {loaded_offset}")
                print(f"  Reference Unit: {loaded_reference_unit}")
                config_loaded_successfully = True
            else:
                print("Warning: Config file is missing 'offset' or 'referenceUnit'.")
    except json.JSONDecodeError:
        print(f"Warning: Config file '{CONFIG_FILE}' contains invalid JSON.")
    except Exception as e:
        print(f"Warning: Error reading config file '{CONFIG_FILE}'. Error: {e}")

    if not config_loaded_successfully:
        print("Ignoring invalid or incomplete config file. Will perform tare.")
        # Optional: you could attempt to delete the bad file here
        # try: os.remove(CONFIG_FILE) except OSError: pass

# 2. Initialize the HX711 Sensor
try:
    # Optional: Set GPIO mode if not done elsewhere (e.g., in your main script)
    # GPIO.setmode(GPIO.BCM) # Or GPIO.BOARD

    hx = HX711(DOUT_PIN, PD_SCK_PIN)
    # Set byte order and bit order (MUST be done before reading/setting offset)
    hx.set_reading_format("MSB", "MSB")
    print("HX711 sensor initialized.")

except Exception as e:
    print(f"FATAL ERROR: Failed to initialize HX711 sensor. Error: {e}")
    print("Check GPIO connections and permissions.")
    GPIO.cleanup()  # Attempt basic cleanup
    sys.exit(1)  # Exit script if sensor fails

# 3. Configure HX711: Use loaded values or perform tare
if config_loaded_successfully:
    # Apply the loaded settings
    print("Applying loaded offset and reference unit...")
    hx.set_offset(loaded_offset)
    hx.set_reference_unit(loaded_reference_unit)
    # Perform a power cycle after applying settings
    hx.power_down()
    hx.power_up()
    time.sleep(0.5)
    print("Scale configured using saved settings.")
else:
    # Perform initial tare and save the configuration
    print("No valid configuration found. Performing initial tare...")
    hx.reset()  # Reset the chip before taring
    calculated_offset = stable_tare(hx)  # This also sets the offset on hx

    if calculated_offset is not None:
        # Use the default reference unit for the first time
        # (Or implement a calibration step here if needed)
        current_reference_unit = DEFAULT_REFERENCE_UNIT
        hx.set_reference_unit(current_reference_unit)
        print(f"Reference unit set to default: {current_reference_unit}")

        # Save the calculated offset and reference unit to the file
        print(f"Saving configuration to {CONFIG_FILE}...")
        try:
            config_data_to_save = {
                'offset': calculated_offset,
                'referenceUnit': current_reference_unit
            }
            with open(CONFIG_FILE, 'w') as f:
                json.dump(config_data_to_save, f, indent=4)
            print("Configuration saved successfully.")
        except Exception as e:
            print(f"Warning: Failed to save configuration to {CONFIG_FILE}. Error: {e}")
    else:
        print("ERROR: Tare process failed. Scale may not read accurately.")
        # Decide how to proceed - exit or continue with potentially bad readings?
        # For now, we'll try setting a default reference unit anyway
        hx.set_reference_unit(DEFAULT_REFERENCE_UNIT)

print("--- Scale Ready ---")

# --- Example Usage (if running this script directly) ---
if __name__ == "__main__":
    print("\nRunning direct execution test loop...")
    try:
        while True:
            input("Press Enter to take a reading (or Ctrl+C to exit)...")
            weight = take_reading()
            if weight is not None:
                print(f"--> Reading Result: {weight:.2f} grams")
            else:
                print("--> Failed to get reading.")
            time.sleep(1)  # Wait a second before next prompt
    except (KeyboardInterrupt, SystemExit):
        print("\nExit requested.")
    finally:
        # Ensure cleanup is called when the script exits
        cleanAndExit()

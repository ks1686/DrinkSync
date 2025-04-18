import time
import sys
import numpy as np
import RPi.GPIO as GPIO
from hx711 import HX711
# Assuming bt.py is in the same directory or accessible via PYTHONPATH
from bt import send_message
import json  # Needed for reading/writing config file
import os   # Needed for checking if config file exists

# --- Configuration ---
CONFIG_FILE = "scale_config.json"  # File to store/load scale settings
DEFAULT_REFERENCE_UNIT = 425.37  # Adjust this based on your initial calibration
STABLE_TARE_SAMPLES = 20  # Samples for the initial tare process
GET_WEIGHT_SAMPLES = 5   # Samples per single weight reading (used in tare and take_reading)
TAKE_READING_DURATION_S = 3  # Duration to average readings over in take_reading
TAKE_READING_SAMPLE_DELAY = 0.1 # Delay between samples within take_reading
DOUT_PIN = 5  # Data pin
PD_SCK_PIN = 6  # Clock pin

# --- Global HX711 Object ---
hx = None
# --- Global Variable for Initial Max Weight ---
# This will store the first weight measured after configuration (either loaded or tared)
initial_max_weight = None


# --- Function Definitions ---

def cleanAndExit():
    """Cleans up GPIO resources and exits."""
    print("\nCleaning up GPIO...")
    # Optional: Try to power down the HX711 before cleaning GPIO
    try:
        if hx:
            hx.power_down()
    except Exception as e:
        print(f"  Warning: Could not power down HX711 during cleanup: {e}")
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
        return None # Indicate failure

    readings = []
    print("Taring... Please ensure scale is empty and stable.")
    # Power cycle before tare might help stability
    try:
        hx_instance.power_down()
        hx_instance.power_up()
        time.sleep(0.5) # Allow settle time
    except Exception as e:
        print(f"  Warning: Error during power cycle before tare: {e}")
        # Continue anyway, might still work

    for i in range(samples):
        try:
            # Use read_average for tare to get value before offset subtraction
            raw_reading = hx_instance.read_average(times=GET_WEIGHT_SAMPLES)
            if raw_reading is not False: # Check for valid reading
                readings.append(raw_reading)
                print(f"  Tare sample {i+1}/{samples}: {raw_reading}")
            else:
                print(f"  Warning: Got invalid raw reading during tare sample {i+1}")
            time.sleep(0.1)
        except Exception as e:
            print(f"  Error getting raw data during tare: {e}")
            # Decide if you want to break or continue after an error
            # break # uncomment to stop taring on first error

    if not readings:
        print("ERROR: Could not get any valid readings during tare. Cannot set offset.")
        # Consider raising an error or returning a specific failure value
        return None
    else:
        # Use median for robustness against outliers
        avg_tare_offset = np.median(readings)

    hx_instance.set_offset(avg_tare_offset)
    print(f"Tare complete. Offset set to: {avg_tare_offset}")
    time.sleep(0.5) # Short delay after setting offset
    return avg_tare_offset # Return the calculated offset


def take_reading():
    """
Takes readings for a specified duration using the globally configured 'hx' object,
calculates the average weight, sends it via Bluetooth, and returns the weight.
    """
    global hx # Access the global hx object
    if not hx:
        print("Error: Scale (hx) not initialized. Cannot take reading.")
        return None # Indicate failure

    try:
        start_time = time.time()
        readings = []
        print(f"Taking reading for {TAKE_READING_DURATION_S} seconds...")

        # Power cycle before reading might improve consistency
        hx.power_down()
        hx.power_up()
        time.sleep(0.1) # Allow time for power up

        # Collect readings for the specified duration
        while time.time() - start_time < TAKE_READING_DURATION_S:
            try:
                # get_weight uses the offset and reference unit already set in 'hx'
                val = hx.get_weight(GET_WEIGHT_SAMPLES)
                # Basic check for unusually large values which might indicate errors
                # Adjust the threshold based on expected weights
                if val is not False and abs(val) < 100000: # Example threshold
                    readings.append(val)
                else:
                    print(f"  Warning: Discarding potentially erroneous reading: {val}")
                # print(f"  Raw reading: {val:.2f}") # Uncomment for detailed debug
                time.sleep(TAKE_READING_SAMPLE_DELAY) # Small delay
            except OverflowError:
                print("  Warning: Overflow error during reading, discarding value.")
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

        # set average_weight to initial minus average_weight
        if initial_max_weight is not None:
            average_weight = initial_max_weight - average_weight

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

        return average_weight # Return the calculated weight

    except Exception as e:
        print(f"Error during take_reading: {e}")
        # Attempt to power down even on error
        try:
            if hx: hx.power_down()
        except:
            pass # Ignore errors during power down in cleanup
        # Consider calling cleanAndExit() or raising the exception
        return None # Indicate failure


# --- Initialization Code (Runs ONCE when script is imported or executed) ---

print("--- Initializing Scale ---")
config_loaded_successfully = False
loaded_offset = None
loaded_reference_unit = None
loaded_initial_max_weight = None # Variable to store loaded max weight

# 1. Check for and load existing configuration file
if os.path.exists(CONFIG_FILE):
    print(f"Found configuration file: {CONFIG_FILE}")
    try:
        with open(CONFIG_FILE, 'r') as f:
            config_data = json.load(f)
            # Validate required keys exist
            if 'offset' in config_data and 'referenceUnit' in config_data and 'initialMaxWeight' in config_data:
                loaded_offset = config_data['offset']
                loaded_reference_unit = config_data['referenceUnit']
                # Load the initial max weight if it's a number, otherwise keep None
                if isinstance(config_data['initialMaxWeight'], (int, float)):
                    loaded_initial_max_weight = config_data['initialMaxWeight']
                else:
                    print("Warning: 'initialMaxWeight' in config is not a valid number. Will measure anew if tare is needed.")


                print("Successfully loaded configuration:")
                print(f"  Offset: {loaded_offset}")
                print(f"  Reference Unit: {loaded_reference_unit}")
                if loaded_initial_max_weight is not None:
                    print(f"  Initial Max Weight: {loaded_initial_max_weight:.2f} grams")
                else:
                    print("  Initial Max Weight: Not found or invalid in config.")
                config_loaded_successfully = True
            else:
                print("Warning: Config file is missing 'offset', 'referenceUnit', or 'initialMaxWeight'.")
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
    # Set GPIO mode BEFORE initializing HX711 if not done elsewhere
    # Choose BCM or BOARD consistently
    # GPIO.setmode(GPIO.BCM) # Example: Use Broadcom pin numbering

    hx = HX711(DOUT_PIN, PD_SCK_PIN)
    # Set byte order and bit order (MUST be done before reading/setting offset/taring)
    hx.set_reading_format("MSB", "MSB")
    print("HX711 sensor initialized.")

except Exception as e:
    print(f"FATAL ERROR: Failed to initialize HX711 sensor. Error: {e}")
    print("Check GPIO connections, permissions, and chosen numbering scheme (BCM/BOARD).")
    GPIO.cleanup() # Attempt basic cleanup
    sys.exit(1) # Exit script if sensor fails

# 3. Configure HX711: Use loaded values or perform tare
if config_loaded_successfully and loaded_offset is not None and loaded_reference_unit is not None:
    # Apply the loaded settings
    print("Applying loaded offset and reference unit...")
    hx.set_offset(loaded_offset)
    hx.set_reference_unit(loaded_reference_unit)
    # Assign the loaded max weight to the global variable
    initial_max_weight = loaded_initial_max_weight
    # Perform a power cycle after applying settings might be good practice
    hx.power_down()
    hx.power_up()
    time.sleep(0.5)
    print("Scale configured using saved settings.")
    if initial_max_weight is not None:
         print(f"Using saved Initial Max Weight: {initial_max_weight:.2f} grams")
    else:
         print("Warning: Could not use saved Initial Max Weight (missing or invalid).")

else:
    # Perform initial tare and save the configuration
    print("No valid configuration found or loaded. Performing initial tare...")
    hx.reset() # Reset the chip before taring
    calculated_offset = stable_tare(hx) # This also sets the offset on hx

    if calculated_offset is not None:
        # Use the default reference unit for the first time
        # (Or implement a calibration step here if needed)
        current_reference_unit = DEFAULT_REFERENCE_UNIT
        hx.set_reference_unit(current_reference_unit)
        print(f"Reference unit set to default: {current_reference_unit}")

        # --- TAKE THE FIRST MEASUREMENT (Initial Max Weight) ---
        print("\nTaking initial 'max' measurement...")
        print("Ensure the item representing the maximum weight is on the scale NOW.")
        time.sleep(5) # Give user a moment

        first_measurement_val = None
        try:
            # Power cycle before critical measurement
            hx.power_down()
            hx.power_up()
            time.sleep(0.5) # Allow settle time
            # Get a single, averaged reading using the new settings
            first_measurement_val = hx.get_weight(GET_WEIGHT_SAMPLES)
            # Check if the reading is valid

            if first_measurement_val is not False:
                initial_max_weight = first_measurement_val # Store globally
                print(f"Initial 'max' weight measured: {initial_max_weight:.2f} grams")
            else:
                print("Warning: Failed to get valid initial 'max' weight reading.")
                initial_max_weight = None # Explicitly set to None on failure

        except Exception as e:
            print(f"ERROR: Could not take initial 'max' weight measurement: {e}")
            initial_max_weight = None # Set to None on error

        # --- Save Configuration (including the initial max weight) ---
        print(f"Saving configuration to {CONFIG_FILE}...")
        try:
            config_data_to_save = {
                'offset': calculated_offset,
                'referenceUnit': current_reference_unit,
                'initialMaxWeight': initial_max_weight # Save the measured value (or None if failed)
            }
            with open(CONFIG_FILE, 'w') as f:
                json.dump(config_data_to_save, f, indent=4)
            print("Configuration saved successfully.")
        except Exception as e:
            print(f"Warning: Failed to save configuration to {CONFIG_FILE}. Error: {e}")

        # Power down after initial measurement if desired
        hx.power_down()

    else:
        print("ERROR: Tare process failed. Scale may not read accurately.")
        # Decide how to proceed - exit or continue with potentially bad readings?
        # For now, we'll try setting a default reference unit anyway but skip max reading/saving
        hx.set_reference_unit(DEFAULT_REFERENCE_UNIT)
        initial_max_weight = None # Ensure it's None if tare failed

# Final check after initialization logic
print("\n--- Scale Ready ---")
if initial_max_weight is not None:
    print(f"Current Initial Max Weight set to: {initial_max_weight:.2f} grams")
else:
    print("Initial Max Weight is not set (check logs for errors).")

# --- Example Usage (if running this script directly) ---
if __name__ == "__main__":
    print("\nRunning direct execution test loop...")
    try:
        while True:
            input("Press Enter to take a reading (or Ctrl+C to exit)...")
            weight = take_reading()
            if weight is not None:
                print(f"--> Reading Result: {weight:.2f} grams")
                # Example: Calculate percentage relative to initial max weight
                if initial_max_weight is not None and initial_max_weight != 0:
                    percentage = (weight / initial_max_weight) * 100
                    print(f"--> Approximately {percentage:.1f}% of initial max weight.")
                elif initial_max_weight == 0:
                     print("--> Cannot calculate percentage, initial max weight is zero.")
                else:
                     print("--> Cannot calculate percentage, initial max weight not set.")

            else:
                print("--> Failed to get reading.")
            # No need for extra sleep if take_reading already powers down
            # time.sleep(1) # Wait a second before next prompt
    except (KeyboardInterrupt, SystemExit):
        print("\nExit requested.")
    finally:
        # Ensure cleanup is called when the script exits
        cleanAndExit()
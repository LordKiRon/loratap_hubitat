# TS130F Dual Curtain Switch - Calibration Guide

## Overview

The TS130F Tuya-based curtain switch requires calibration to accurately track and control curtain position. Calibration teaches the switch the travel limits (fully open and fully closed positions) for your curtain motor.

**Important:** Each gang (endpoint) must be calibrated separately since each curtain may have different travel ranges.

---

## Calibration Procedure

### Step 1: Position the Curtain at the Starting Point

Before starting calibration, move the curtain to one of its extreme positions (fully open or fully closed).

- If the curtain can already reach the desired position, use the **open** or **close** command
- If existing calibration limits prevent reaching the position:
  1. Click **"calibrationStart"** to clear the current limits
  2. Move the curtain to the desired position using the physical buttons
  3. Press the **Pause** button on the switch to stop
  4. Click **"calibrationStop"** to exit calibration mode
  5. Now proceed with the full calibration below

### Step 2: Start Calibration Mode

1. Ensure the curtain is stopped at one extreme position (fully open or fully closed)
2. Click the **"calibrationStart"** command in Hubitat
3. The device enters calibration mode and clears any existing limits

### Step 3: Set the First Limit

1. Use the **physical buttons** on the switch to move the curtain to the opposite extreme
   - If starting from open, move toward closed
   - If starting from closed, move toward open
2. Press the **Pause** button on the switch when the curtain reaches the desired limit position
3. This sets the first travel limit

### Step 4: Set the Second Limit

1. Use the **physical buttons** to move the curtain back to the original starting position
2. Press the **Pause** button when the curtain reaches the desired limit position
3. This sets the second travel limit

### Step 5: Save and Exit Calibration

1. Click the **"calibrationStop"** command in Hubitat
2. The calibration is saved to the device
3. The device state will now show the correct position (0% or 100%)

### Step 6: Verify Calibration

1. Test the **open** and **close** commands - the curtain should move to the correct limits
2. Test **setPosition(50)** - the curtain should stop approximately in the middle
3. Check that the position percentage updates correctly as the curtain moves

---

### Method 2: Manual Time Setting

If you know the exact travel time of your curtain motor, you can set it directly via Preferences.

#### Steps:

1. **Measure travel time**
   - Use a stopwatch to time how long it takes for your curtain to go from fully open to fully closed
   - Note the time in seconds (e.g., 25 seconds)

2. **Set calibration time in Preferences**
   - In Hubitat, go to the child device
   - Scroll down to **Preferences**
   - Enter the time in the **"Calibration Time (seconds)"** field
   - Click **"Save Preferences"**
   - The value will be written to the device

3. **Test the calibration**
   - Try **setPosition(50)** to verify the curtain stops at approximately 50%

---

## Motor Direction Reversal

If your curtain moves in the wrong direction (open command closes, close command opens):

1. Go to the child device in Hubitat
2. Scroll down to **Preferences**
3. Enable **"Reverse Motor Direction"**
4. Click **"Save Preferences"**
5. Test with open/close commands

---

## Troubleshooting

### Calibration doesn't seem to work

1. **Ensure you disabled existing calibration first**
   - Click **calibrationStop** before starting a new calibration
   
2. **Let the motor complete full travel**
   - Do NOT press stop during calibration - let the motor stop by itself
   - The switch needs to measure the complete travel time
   
3. **Try manual method**
   - If automatic calibration fails, measure the time manually and set it via Preferences

### Position reporting is inaccurate

1. **Re-calibrate**
   - The travel time may have changed (motor wear, track friction, etc.)
   
2. **Check for obstructions**
   - Ensure the curtain can move freely through its full range

3. **Adjust calibration time manually**
   - If position is consistently off, try adjusting the calibration time in Preferences
   - Add or subtract a few seconds to fine-tune

### Motor stops before reaching the end

1. **Check physical limits**
   - Ensure the curtain track is clear and the motor can reach both ends
   
2. **Disable calibration and test**
   - Click **calibrationStop** to disable calibration
   - Move the curtain manually to verify full travel is possible

---

## Technical Details

### Tuya-Specific Attributes

| Attribute | Cluster | ID | Type | Description |
|-----------|---------|-----|------|-------------|
| Calibration Mode | 0x0102 | 0xF001 | ENUM8 | 0=start/clear limits, 1=stop/save |
| Motor Reversal | 0x0102 | 0xF002 | ENUM8 | 0=normal, 1=reversed |
| Calibration Time | 0x0102 | 0xF003 | UINT16 | Time in 0.1s units |

### Standard Attributes (Readable)

| Attribute | Cluster | ID | Type | Description |
|-----------|---------|-----|------|-------------|
| Position | 0x0102 | 0x0008 | UINT8 | 0-100% lift percentage |

---

## Tips

- **Calibrate after installation** - Always calibrate when first setting up the device
- **Re-calibrate periodically** - Motor performance can change over time
- **Calibrate each gang separately** - Each curtain may have different travel times
- **Test thoroughly** - After calibration, test various positions (25%, 50%, 75%) to verify accuracy
- **Don't interrupt calibration** - Let the motor complete its full travel during calibration

/**
 * TS130F Curtain Child Driver
 * 
 * This is the child driver for individual curtain endpoints on the TS130F dual gang switch.
 * Each child represents one curtain motor (Gang 1 or Gang 2).
 * All Zigbee communication is handled by the parent driver.
 * 
 * Capabilities:
 * - WindowShade: open, close, setPosition, startPositionChange, stopPositionChange
 * - SwitchLevel: setLevel (maps to position for compatibility)
 * - Actuator: marks device as controllable
 * - Refresh: allows manual state refresh
 * 
 * Custom Commands:
 * - calibrationStart: Begins calibration mode
 * - calibrationStop: Ends calibration mode  
 * - setCalibrationTime: Sets motor travel time
 * - setMotorReversal: Reverses motor direction
 */

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

// ==================== CONSTANTS ====================

// Window Covering Cluster
@Field static final int CLUSTER_WINDOW_COVERING = 0x0102

// Window Covering Attributes
@Field static final int ATTR_POSITION_LIFT_PERCENTAGE = 0x0008    // Current position (0-100%)
@Field static final int ATTR_OPERATIONAL_STATUS = 0x0009          // Movement status

// Tuya-Specific Attributes (Window Covering cluster 0x0102)
@Field static final int ATTR_TUYA_CALIBRATION_MODE = 0xF001       // 0=start/clear, 1=stop/save
@Field static final int ATTR_TUYA_MOTOR_REVERSAL = 0xF002         // 0=normal, 1=reversed
@Field static final int ATTR_TUYA_CALIBRATION_TIME = 0xF003       // Time in 0.1s units

// Operational Status Values
@Field static final int STATUS_STOPPED = 0x00
@Field static final int STATUS_OPENING = 0x01
@Field static final int STATUS_CLOSING = 0x02

// Default Settings
@Field static final boolean DEBUG_DEFAULT = true

// ==================== METADATA ====================

metadata {
    definition(name: "TS130F Curtain Child", namespace: "lkr", author: "Lord KiRon") {
        // Standard Capabilities for Window Coverings
        capability "WindowShade"      // open, close, setPosition, startPositionChange, stopPositionChange
        capability "SwitchLevel"      // setLevel - for compatibility with dimmer-style controls
        capability "Actuator"         // Marks as controllable device
        capability "Refresh"          // Manual refresh
        
        // Calibration Commands
        command "calibrationStart"
        command "calibrationStop"
        
        // Test commands for experimenting with Tuya attributes (commented out - uncomment if needed for debugging)
        // command "testWriteAttribute", [[name:"attribute", type:"NUMBER", description:"Attribute ID (hex as decimal, e.g. 61441 for 0xF001)"], 
        //                                [name:"value", type:"NUMBER", description:"Value to write"],
        //                                [name:"dataType", type:"ENUM", constraints:["UINT8", "UINT16", "ENUM8"], description:"Data type"]]
        // command "testReadAttribute", [[name:"attribute", type:"NUMBER", description:"Attribute ID (hex as decimal, e.g. 61441 for 0xF001)"]]
        
        // Tuya configuration attributes (displayed in Current States)
        attribute "calibrationMode", "enum", ["inactive", "active"]
        attribute "motorDirection", "enum", ["normal", "reversed"]
        attribute "calibrationTime", "number"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: DEBUG_DEFAULT
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
        
        // Configuration Settings (applied on Save)
        input name: "calibrationTime", type: "decimal", title: "Calibration Time (seconds)", 
              description: "Motor travel time from fully open to fully closed (supports 0.1s precision, e.g. 25.5). Set to 0 to clear/disable.",
              defaultValue: null, range: "0..300"
        input name: "motorReversed", type: "bool", title: "Reverse Motor Direction", 
              description: "Enable only if Open/Close commands physically move curtain in wrong direction.",
              defaultValue: false
    }
}

// ==================== LIFECYCLE METHODS ====================

/**
 * Called when the child device is first created.
 * Sets default preferences and requests current state from device.
 */
void installed() {
    logInfo "Installed"
    
    // Set default preference values
    device.updateSetting("logEnable", [value: DEBUG_DEFAULT, type: "bool"])
    
    // Set initial UI state to unknown until we read from device
    sendEvent(name: "windowShade", value: "unknown")
    sendEvent(name: "position", value: 0, unit: "%")
    sendEvent(name: "level", value: 0, unit: "%")
    sendEvent(name: "calibrationMode", value: "unknown")
    sendEvent(name: "motorDirection", value: "unknown")
    sendEvent(name: "calibrationTime", value: null)
    
    // Read actual state from device after a short delay (allow parent to finish setup)
    // This reads position, calibration mode, motor direction, and calibration time
    runIn(2, "refresh")
}

/**
 * Called when preferences are updated.
 * Applies configuration changes to the device and schedules debug logging timeout.
 */
void updated() {
    logInfo "Preferences updated"
    
    // Schedule debug logging to turn off after 30 minutes
    if (logEnable) runIn(1800, logsOff)
    
    // Apply configuration settings to device
    applyConfigurationSettings()
}

/**
 * Applies configuration preferences to the physical device.
 * Called when preferences are saved.
 */
private void applyConfigurationSettings() {
    // Apply calibration time if set (0 = clear/disable calibration)
    if (calibrationTime != null) {
        BigDecimal calTime = calibrationTime as BigDecimal
        if (calTime >= 0) {
            logInfo "Applying calibration time: ${calTime}s"
            parent.componentSetCalibrationTime(device, calTime)
        }
    }
    
    // Apply motor reversal setting
    if (motorReversed != null) {
        logInfo "Applying motor reversal: ${motorReversed}"
        parent.componentSetMotorReversal(device, motorReversed as Boolean)
    }
}

/**
 * Disables debug logging - called automatically after timeout.
 */
void logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ==================== WINDOWSHADE CAPABILITY ====================

/**
 * Opens the curtain fully.
 * WindowShade capability required method.
 */
void open() {
    logDebug "Command: open()"
    parent.componentOpen(device)
}

/**
 * Closes the curtain fully.
 * WindowShade capability required method.
 */
void close() {
    logDebug "Command: close()"
    parent.componentClose(device)
}

/**
 * Stops any current curtain movement.
 * WindowShade capability required method (via stopPositionChange).
 */
void stop() {
    logDebug "Command: stop()"
    parent.componentStop(device)
}

/**
 * Sets the curtain to a specific position.
 * WindowShade capability required method.
 * User sends: 0=open, 100=closed
 * Device expects: 0=closed, 100=open
 * So we invert before sending
 * @param position Target position 0-100
 */
void setPosition(position) {
    if (position == null) {
        log.warn "setPosition called with null position"
        return
    }
    
    Integer userPos = position as Integer
    
    // Clamp to valid range
    userPos = Math.max(0, Math.min(100, userPos))
    
    // Invert: user 0=open,100=closed -> device 0=closed,100=open
    Integer devicePos = 100 - userPos
    
    logDebug "Command: setPosition(${userPos}) -> sending ${devicePos} to device"
    parent.componentSetPosition(device, devicePos)
}

/**
 * Starts continuous position change in specified direction.
 * WindowShade capability required method.
 * @param direction "open" or "close"
 */
void startPositionChange(String direction) {
    logDebug "Command: startPositionChange(${direction})"
    if (direction == "open") {
        open()
    } else if (direction == "close") {
        close()
    } else {
        log.warn "Unknown direction '${direction}' for startPositionChange"
    }
}

/**
 * Stops continuous position change.
 * WindowShade capability required method.
 */
void stopPositionChange() {
    logDebug "Command: stopPositionChange()"
    stop()
}

// ==================== SWITCHLEVEL CAPABILITY ====================

/**
 * Sets the level (position) of the curtain.
 * SwitchLevel capability required method.
 * Maps directly to setPosition for compatibility with dimmer-style controls.
 * @param level Target level 0-100
 * @param duration Optional transition duration (ignored for curtains)
 */
void setLevel(level, duration = null) {
    logDebug "Command: setLevel(${level}, ${duration})"
    setPosition(level)
}

// ==================== REFRESH CAPABILITY ====================

/**
 * Refreshes the current state from the device.
 * Refresh capability required method.
 */
void refresh() {
    logDebug "Command: refresh()"
    parent.componentRefresh(device)
}

// ==================== CALIBRATION COMMANDS ====================

/**
 * Starts calibration mode.
 * The curtain will need to be moved to both ends to calibrate.
 * See calibration documentation for full procedure.
 */
void calibrationStart() {
    logInfo "Starting calibration mode"
    parent.componentSetCalibration(device, true)
}

/**
 * Stops/exits calibration mode.
 * Call this after calibration is complete.
 */
void calibrationStop() {
    logInfo "Stopping calibration mode"
    parent.componentSetCalibration(device, false)
}

/*
 * Test command for experimenting with Tuya attributes (commented out - uncomment if needed for debugging).
 * Use this to try different attribute/value combinations.
 * 
 * Potential Tuya attributes to try (Window Covering cluster 0x0102):
 * - 0xF000 (61440) = Border/Limit setting (some devices)
 * - 0xF001 (61441) = Calibration mode (1=on, 0=off, or toggle)
 * - 0xF002 (61442) = Motor reversal
 * - 0xF003 (61443) = Calibration time
 * - 0xF004 (61444) = Upper limit (some devices)
 * - 0xF005 (61445) = Lower limit (some devices)
 * - 0xF006 (61446) = Clear limits (some devices)
 * - 0xF010 (61456) = Limit control (ZM85: 0=upper, 1=lower, 2=remove upper, 3=remove lower, 4=remove both)
 * 
 * Data types:
 * - 0x20 (32) = UINT8
 * - 0x21 (33) = UINT16
 * - 0x30 (48) = ENUM8
 *
def testWriteAttribute(BigDecimal attribute, BigDecimal value, String dataType) {
    Integer attrInt = attribute.intValue()
    Integer valInt = value.intValue()
    Integer typeInt
    switch (dataType) {
        case "UINT8":  typeInt = DataType.UINT8; break
        case "UINT16": typeInt = DataType.UINT16; break
        case "ENUM8":  typeInt = DataType.ENUM8; break
        default:
            log.error "${device.displayName}: Unknown data type '${dataType}', aborting"
            return
    }
    log.info "${device.displayName}: TEST - Writing attr 0x${String.format('%04X', attrInt)} = ${valInt} (type ${dataType})"
    parent.componentTestWriteAttribute(device, attrInt, valInt, typeInt)
}

def testReadAttribute(BigDecimal attribute) {
    Integer attrInt = attribute.intValue()
    log.info "${device.displayName}: TEST - Reading attr 0x${String.format('%04X', attrInt)}"
    parent.componentTestReadAttribute(device, attrInt)
}
*/

// ==================== ZIGBEE MESSAGE PARSING ====================

/**
 * Called by parent driver when a Zigbee message for this endpoint arrives.
 * Parses the message and updates device state accordingly.
 * @param descMap Parsed Zigbee message map from parent
 */
void parseZigbeeMessage(Map descMap) {
    logDebug "Parsing: Cluster=${descMap.clusterId ?: descMap.cluster}, Attr=${descMap.attrId}, Value=${descMap.value}"
    
    // Get cluster as integer for comparison
    Integer clusterInt = descMap.clusterInt ?: 
                         (descMap.clusterId ? hubitat.helper.HexUtils.hexStringToInt(descMap.clusterId) : 
                         (descMap.cluster ? hubitat.helper.HexUtils.hexStringToInt(descMap.cluster) : null))
    
    if (clusterInt == null) {
        logDebug "Unknown cluster format: ${descMap}"
        return
    }
    
    // Handle Window Covering cluster messages
    if (clusterInt == CLUSTER_WINDOW_COVERING) {
        parseWindowCoveringCluster(descMap)
    } else {
        // Log unknown cluster messages
        logDebug "Unhandled cluster 0x${String.format('%04X', clusterInt)}: ${descMap}"
    }
}

/**
 * Parses Window Covering cluster (0x0102) messages.
 * Handles both attribute reports and command responses.
 * @param descMap Parsed Zigbee message map
 */
private void parseWindowCoveringCluster(Map descMap) {
    // Check if this is a command response (not an attribute report)
    String command = descMap.command
    if (command == "0B") {
        // Default Response - acknowledgment of a command we sent
        // data[0] = command that was acknowledged, data[1] = status (0x00 = success)
        List data = descMap.data
        if (data && data.size() >= 2) {
            String cmdAcked = data[0]
            String status = data[1]
            if (status == "00") {
                logDebug "Command 0x${cmdAcked} acknowledged successfully"
            } else {
                log.warn "Command 0x${cmdAcked} failed with status 0x${status}"
            }
        }
        return
    }
    
    if (command == "04") {
        // Write Attribute Response
        List data = descMap.data
        if (data && data.size() >= 1) {
            String status = data[0]
            if (status != "00") {
                log.warn "Write attribute failed with status 0x${status}"
            }
        }
        return
    }
    
    // Get attribute ID as integer for attribute reports
    Integer attrInt = descMap.attrInt ?: 
                      (descMap.attrId ? hubitat.helper.HexUtils.hexStringToInt(descMap.attrId) : null)
    
    if (attrInt == null) {
        // Not an attribute report and not a known command response
        logDebug "Unhandled Window Covering message: ${descMap}"
        return
    }
    
    String value = descMap.value
    
    switch (attrInt) {
        case ATTR_POSITION_LIFT_PERCENTAGE:
            // Position report (0-100%)
            parsePositionReport(value)
            break
            
        case ATTR_OPERATIONAL_STATUS:
            // Movement status report
            parseOperationalStatus(value)
            break
            
        case ATTR_TUYA_CALIBRATION_MODE:
            // Calibration mode response
            Integer calMode = hubitat.helper.HexUtils.hexStringToInt(value)
            String calModeStr = (calMode == 0) ? "active" : "inactive"
            logDebug "Calibration Mode = ${calModeStr}"
            sendEvent(name: "calibrationMode", value: calModeStr, descriptionText: "Calibration mode is ${calModeStr}")
            break
            
        case ATTR_TUYA_MOTOR_REVERSAL:
            // Motor reversal response
            Integer reversed = hubitat.helper.HexUtils.hexStringToInt(value)
            String directionStr = (reversed == 0) ? "normal" : "reversed"
            logDebug "Motor Direction = ${directionStr}"
            sendEvent(name: "motorDirection", value: directionStr, descriptionText: "Motor direction is ${directionStr}")
            break
            
        case ATTR_TUYA_CALIBRATION_TIME:
            // Calibration time response (in 0.1s units)
            Integer timeVal = hubitat.helper.HexUtils.hexStringToInt(value)
            BigDecimal seconds = timeVal / 10.0
            logDebug "Calibration Time = ${seconds}s"
            sendEvent(name: "calibrationTime", value: seconds, unit: "s", descriptionText: "Calibration time is ${seconds}s")
            break
            
        default:
            // Log truly unknown attributes
            logDebug "Unknown attribute 0x${String.format('%04X', attrInt)} = ${value}"
    }
}

/**
 * Parses position report and updates device state.
 * Device reports: 0=closed, 100=open (Zigbee standard)
 * We want: 0=open, 100=closed
 * So we invert: pos = 100 - rawPos
 * @param value Hex string position value
 */
private void parsePositionReport(String value) {
    if (value == null) return
    
    Integer rawPos = hubitat.helper.HexUtils.hexStringToInt(value)
    
    // Invert: device 0=closed,100=open -> we want 0=open,100=closed
    Integer pos = 100 - rawPos
    
    // Clamp to valid range
    pos = Math.max(0, Math.min(100, pos))
    
    logInfo "Position ${pos}%"
    
    // Update position and level attributes
    sendEvent(name: "position", value: pos, unit: "%", descriptionText: "Position is ${pos}%")
    sendEvent(name: "level", value: pos, unit: "%")
    
    // Update windowShade state based on position (0=open, 100=closed)
    String shadeState
    if (pos == 0) {
        shadeState = "open"
    } else if (pos == 100) {
        shadeState = "closed"
    } else {
        shadeState = "partially open"
    }
    sendEvent(name: "windowShade", value: shadeState, descriptionText: "Window shade is ${shadeState}")
}

/**
 * Parses operational status (movement) report.
 * @param value Hex string status value
 */
private void parseOperationalStatus(String value) {
    if (value == null) return
    
    Integer status = hubitat.helper.HexUtils.hexStringToInt(value)
    String stateText
    String shadeState
    
    switch (status) {
        case STATUS_STOPPED:
            stateText = "stopped"
            // Don't change windowShade state on stop - let position report handle it
            break
        case STATUS_OPENING:
            stateText = "opening"
            shadeState = "opening"
            break
        case STATUS_CLOSING:
            stateText = "closing"
            shadeState = "closing"
            break
        default:
            stateText = "unknown (${status})"
    }
    
    logInfo "Movement status: ${stateText}"
    
    if (shadeState) {
        sendEvent(name: "windowShade", value: shadeState, descriptionText: "Window shade is ${shadeState}")
    }
}

// ==================== LOGGING HELPERS ====================

/**
 * Logs an info message if description text logging is enabled.
 */
private void logInfo(String msg) {
    if (txtEnable) log.info msg
}

/**
 * Logs a debug message if debug logging is enabled.
 */
private void logDebug(String msg) {
    if (logEnable) log.debug msg
}

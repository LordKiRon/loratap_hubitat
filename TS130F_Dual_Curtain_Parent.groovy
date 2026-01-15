/**
 * TS130F Dual Curtain Switch Parent Driver
 * Device: LoraTap Zigbee Curtain Switch (Gang of 2)
 * Model: TS130F
 * Manufacturer: _TZ3000_esynmmox
 * 
 * This is the parent driver that manages two child devices (one per endpoint/gang).
 * Each child represents one curtain motor. The parent handles all Zigbee communication
 * and routes messages to the appropriate child based on endpoint.
 * 
 * Based on community Zemismart driver by kkossev version 3.5.1
 */

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

// ==================== CONSTANTS ====================

// Zigbee Cluster IDs
@Field static final int CLUSTER_BASIC = 0x0000
@Field static final int CLUSTER_GROUPS = 0x0004
@Field static final int CLUSTER_SCENES = 0x0005
@Field static final int CLUSTER_ON_OFF = 0x0006
@Field static final int CLUSTER_WINDOW_COVERING = 0x0102

// Window Covering Cluster Attributes
@Field static final int ATTR_POSITION_LIFT_PERCENTAGE = 0x0008    // Current position (0-100%)
@Field static final int ATTR_OPERATIONAL_STATUS = 0x0009          // Movement status
@Field static final int ATTR_TUYA_CALIBRATION_MODE = 0xF001       // Tuya-specific: Calibration mode (0=off, 1=on)
@Field static final int ATTR_TUYA_MOTOR_REVERSAL = 0xF002         // Tuya-specific: Motor direction reversal
@Field static final int ATTR_TUYA_CALIBRATION_TIME = 0xF003       // Tuya-specific: Calibration time (in 0.1s units)

// Window Covering Cluster Commands
@Field static final int CMD_UP_OPEN = 0x00                        // Open the covering
@Field static final int CMD_DOWN_CLOSE = 0x01                     // Close the covering
@Field static final int CMD_STOP = 0x02                           // Stop movement
@Field static final int CMD_GO_TO_LIFT_PERCENTAGE = 0x05          // Go to specific position

// Basic Cluster Attributes (for health check/ping)
@Field static final int ATTR_MODEL_IDENTIFIER = 0x0005            // Model name string

// Data Types - using hubitat.zigbee.zcl.DataType constants

// Device Endpoints
@Field static final List<Integer> ENDPOINTS = [1, 2]              // Gang 1 and Gang 2

// Default Settings
@Field static final boolean DEBUG_DEFAULT = true
@Field static final boolean DESCRIPTION_DEFAULT = true

// ==================== METADATA ====================

metadata {
    definition(name: "TS130F Dual Curtain Parent", namespace: "lkr", author: "Lord KiRon") {
        capability "Configuration"      // Allows device configuration
        capability "Refresh"            // Allows manual refresh of device state
        capability "HealthCheck"        // Enables health monitoring (ping)
        
        // Manual command to wipe and recreate children if they get corrupted
        // Warning: This will break existing Rules/Dashboards linked to the children
        command "reinstallChildren"
        
        // Device fingerprint for automatic pairing
        fingerprint profileId: "0104", 
                    inClusters: "0000,0004,0005,0006,0102", 
                    outClusters: "0019,000A", 
                    model: "TS130F", 
                    manufacturer: "_TZ3000_esynmmox", 
                    deviceJoinName: "LoraTap Dual Curtain"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: DEBUG_DEFAULT
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: DESCRIPTION_DEFAULT
    }
}

// ==================== LIFECYCLE METHODS ====================

/**
 * Called when the device is first installed.
 * Sets default preferences and initializes the device.
 */
void installed() {
    logInfo "Installed"
    device.updateSetting("logEnable", [value: DEBUG_DEFAULT, type: "bool"])
    device.updateSetting("txtEnable", [value: DESCRIPTION_DEFAULT, type: "bool"])
    initialize()
}

/**
 * Called when device preferences are updated.
 * Schedules debug logging to turn off after 30 minutes if enabled.
 */
void updated() {
    logInfo "Updated"
    if (logEnable) runIn(1800, logsOff)
    initialize()
}

/**
 * Initializes the device - creates children and sets up health check.
 */
void initialize() {
    logInfo "Initializing..."
    createChildDevices()
    
    // Health Check: Ping every 3 hours, consider offline after 4 hours
    sendEvent(name: "checkInterval", value: 3 * 60 * 60 + 2 * 60, displayed: false, 
              data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    sendEvent(name: "healthStatus", value: "online")
    
    refresh()
}

/**
 * Configures Zigbee reporting for the device.
 * Sets up position reporting for both endpoints.
 */
void configure() {
    logInfo "Configuring Zigbee reporting..."
    
    createChildDevices()
    
    List<String> cmds = []
    
    // Configure reporting for both endpoints
    // Report Position (0x0008) min:1s, max:3600s, change:1%
    ENDPOINTS.each { ep ->
        cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTR_POSITION_LIFT_PERCENTAGE, 
                                          DataType.UINT8, 1, 3600, 1, [destEndpoint: ep])
    }
    
    logDebug "Configure commands: ${cmds}"
    sendZigbeeCommands(cmds)
}

/**
 * Disables debug logging - called automatically after timeout.
 */
void logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ==================== CHILD DEVICE MANAGEMENT ====================

/**
 * Creates child devices for each endpoint if they don't exist.
 * Uses safe mode - won't delete existing children to preserve rules.
 */
private void createChildDevices() {
    ENDPOINTS.each { ep ->
        String childDni = "${device.deviceNetworkId}-${ep}"
        def existingChild = getChildDevice(childDni)
        
        if (!existingChild) {
            logInfo "Creating child device for Endpoint ${ep}"
            try {
                addChildDevice("lkr", "TS130F Curtain Child", childDni,
                    [name: "Curtain ${ep}", 
                     isComponent: true, 
                     label: "Curtain ${ep}"])
            } catch (e) {
                log.error "Failed to create child device: ${e}. Ensure 'TS130F Curtain Child' driver is installed."
            }
        } else {
            logDebug "Child device for Endpoint ${ep} already exists. Skipping creation."
        }
    }
}

/**
 * Removes all child devices.
 * Only called manually via reinstallChildren command.
 */
void removeChildDevices() {
    log.warn "Removing all child devices..."
    getChildDevices().each { child ->
        try {
            deleteChildDevice(child.deviceNetworkId)
            log.warn "Deleted child device: ${child.deviceNetworkId}"
        } catch (Exception e) {
            log.warn "Failed to delete child device ${child.deviceNetworkId}: ${e.message}"
        }
    }
}

/**
 * Command to reinstall all children.
 * Warning: This will break existing rules/dashboards linked to the children.
 */
void reinstallChildren() {
    log.warn "Reinstalling children. Existing rules linking to these devices may break."
    removeChildDevices()
    runIn(1, "createChildDevices")
}

/**
 * Gets the endpoint number from a child device's DNI.
 * @param child The child device
 * @return Integer endpoint number (1 or 2)
 */
private Integer getChildEndpoint(child) {
    return child.deviceNetworkId.split("-")[1] as Integer
}

// ==================== ZIGBEE MESSAGE PARSING ====================

/**
 * Main parse method - receives all Zigbee messages from the hub.
 * Routes messages to appropriate child based on endpoint.
 * @param description Raw Zigbee message string from hub
 */
void parse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    
    // Extract endpoint from message, handling various formats ("01", "1", 1)
    Integer ep = null
    if (descMap?.endpoint) {
        try {
            ep = hubitat.helper.HexUtils.hexStringToInt(descMap.endpoint.toString())
        } catch (e) {
            ep = descMap.endpoint as Integer
        }
    } else if (descMap?.sourceEndpoint) {
        try {
            ep = hubitat.helper.HexUtils.hexStringToInt(descMap.sourceEndpoint.toString())
        } catch (e) {
            ep = descMap.sourceEndpoint as Integer
        }
    }

    // Route to child if endpoint is valid
    if (ep != null && ENDPOINTS.contains(ep)) {
        String childDni = "${device.deviceNetworkId}-${ep}"
        def child = getChildDevice(childDni)
        
        if (child) {
            // Let child handle parsing and logging
            child.parseZigbeeMessage(descMap)
            return
        } else {
            log.warn "Child device not found for endpoint ${ep}"
        }
    }

    // Log unrouted/unknown messages at parent level
    logDebug "Unrouted/Unknown message (EP: ${ep}): ${descMap}"
}

// ==================== PARENT COMMAND HANDLERS (Called by Children) ====================

/**
 * Refreshes the state of a specific child/endpoint.
 * Reads position, calibration mode, and calibration time.
 * @param child The child device requesting refresh
 */
void componentRefresh(child) {
    Integer ep = getChildEndpoint(child)
    logDebug "Refreshing endpoint ${ep}"
    
    List<String> cmds = []
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_POSITION_LIFT_PERCENTAGE, [destEndpoint: ep])
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_MODE, [destEndpoint: ep])
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_MOTOR_REVERSAL, [destEndpoint: ep])
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_TIME, [destEndpoint: ep])
    
    sendZigbeeCommands(cmds)
}

/**
 * Opens the curtain for a specific endpoint.
 * @param child The child device
 */
void componentOpen(child) {
    Integer ep = getChildEndpoint(child)
    if (logEnable) child.log.debug "Opening curtain"
    
    List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, CMD_UP_OPEN, [:], [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Closes the curtain for a specific endpoint.
 * @param child The child device
 */
void componentClose(child) {
    Integer ep = getChildEndpoint(child)
    if (logEnable) child.log.debug "Closing curtain"
    
    List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, CMD_DOWN_CLOSE, [:], [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Stops curtain movement for a specific endpoint.
 * @param child The child device
 */
void componentStop(child) {
    Integer ep = getChildEndpoint(child)
    if (logEnable) child.log.debug "Stopping curtain"
    
    List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, CMD_STOP, [:], [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Sets the curtain position for a specific endpoint.
 * @param child The child device
 * @param position Target position (0-100)
 */
void componentSetPosition(child, Integer position) {
    Integer ep = getChildEndpoint(child)
    if (logEnable) child.log.debug "Setting position to ${position}%"
    
    // Position is sent as single byte (0-100)
    // No read-back needed - device reports position automatically while moving
    String payload = String.format("%02X", position)
    List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, CMD_GO_TO_LIFT_PERCENTAGE, payload, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Sets calibration mode for a specific endpoint.
 * Used to start/stop the calibration process.
 * @param child The child device
 * @param start true = start calibration, false = stop/save calibration
 * Note: TS130F uses value 1 to toggle calibration mode (both start and stop)
 */
void componentSetCalibration(child, Boolean start) {
    Integer ep = getChildEndpoint(child)
    // TS130F calibration mode: 0=start/clear limits, 1=stop/save calibration
    Integer deviceMode = start ? 0 : 1
    child.log.info "Setting calibration mode to ${start ? 'START' : 'STOP'} (value=${deviceMode})"
    
    List<String> cmds = zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_MODE, 
                                                    DataType.ENUM8, deviceMode, [destEndpoint: ep])
    // Read back the attribute to confirm and update state
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_MODE, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Sets the calibration time (motor run time) for a specific endpoint.
 * This defines how long the motor runs from fully open to fully closed.
 * @param child The child device
 * @param seconds Time in seconds (will be converted to 0.1s units)
 */
void componentSetCalibrationTime(child, BigDecimal seconds) {
    Integer ep = getChildEndpoint(child)
    Integer tenthsOfSeconds = (seconds * 10) as Integer
    child.log.info "Setting calibration time to ${seconds}s (${tenthsOfSeconds} tenths)"
    
    List<String> cmds = zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_TIME, 
                                                    DataType.UINT16, tenthsOfSeconds, [destEndpoint: ep])
    // Read back the attribute to confirm and update state
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_CALIBRATION_TIME, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Sets motor reversal for a specific endpoint.
 * @param child The child device
 * @param reversed true = reversed, false = normal
 */
void componentSetMotorReversal(child, Boolean reversed) {
    Integer ep = getChildEndpoint(child)
    Integer value = reversed ? 1 : 0
    child.log.info "Setting motor reversal to ${reversed ? 'ON' : 'OFF'}"
    
    List<String> cmds = zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_MOTOR_REVERSAL, 
                                                    DataType.ENUM8, value, [destEndpoint: ep])
    // Read back the attribute to confirm and update state
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTR_TUYA_MOTOR_REVERSAL, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Test command for experimenting with Tuya attributes.
 * Writes arbitrary attribute values to the Window Covering cluster.
 * @param child The child device
 * @param attribute Attribute ID
 * @param value Value to write
 * @param dataType Zigbee data type (0x20=UINT8, 0x21=UINT16, 0x30=ENUM8)
 */
void componentTestWriteAttribute(child, Integer attribute, Integer value, Integer dataType) {
    Integer ep = getChildEndpoint(child)
    child.log.info "TEST - Writing attr 0x${String.format('%04X', attribute)} = ${value} (type 0x${String.format('%02X', dataType)})"
    
    List<String> cmds = zigbee.writeAttribute(CLUSTER_WINDOW_COVERING, attribute, dataType, value, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

/**
 * Test command for reading Tuya attributes.
 * @param child The child device
 * @param attribute Attribute ID
 */
void componentTestReadAttribute(child, Integer attribute) {
    Integer ep = getChildEndpoint(child)
    child.log.info "TEST - Reading attr 0x${String.format('%04X', attribute)}"
    
    List<String> cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, attribute, [destEndpoint: ep])
    sendZigbeeCommands(cmds)
}

// ==================== ZIGBEE HELPERS ====================

/**
 * Sends a list of Zigbee commands to the hub.
 * @param cmds List of command strings to send
 */
void sendZigbeeCommands(List<String> cmds) {
    if (!cmds || cmds.isEmpty()) {
        logDebug "No commands to send"
        return
    }
    
    // Filter out any null or empty commands
    cmds = cmds.findAll { it != null && it.trim() != "" }
    
    if (cmds.isEmpty()) {
        logDebug "No valid commands to send after filtering"
        return
    }
    
    logDebug "Sending ${cmds.size()} Zigbee command(s): ${cmds}"
    
    // Add delays between commands for reliability
    List<String> delayedCmds = []
    cmds.eachWithIndex { cmd, idx ->
        delayedCmds << cmd
        // Add delay after each command except the last one
        if (idx < cmds.size() - 1) {
            delayedCmds << "delay 100"
        }
    }
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayedCmds, hubitat.device.Protocol.ZIGBEE))
}

// ==================== CAPABILITY IMPLEMENTATIONS ====================

/**
 * HealthCheck capability - pings the device to verify it's online.
 * Reads the model identifier from the Basic cluster.
 */
void ping() {
    logDebug "Pinging device (Basic Cluster)..."
    List<String> cmds = zigbee.readAttribute(CLUSTER_BASIC, ATTR_MODEL_IDENTIFIER, [destEndpoint: 1])
    sendZigbeeCommands(cmds)
}

/**
 * Refresh capability - refreshes state of all children.
 */
void refresh() {
    logInfo "Refreshing all endpoints..."
    getChildDevices().each { child ->
        componentRefresh(child)
    }
}

// ==================== LOGGING HELPERS ====================

/**
 * Logs an info message if description text logging is enabled.
 */
void logInfo(String msg) {
    if (txtEnable) log.info msg
}

/**
 * Logs a debug message if debug logging is enabled.
 */
void logDebug(String msg) {
    if (logEnable) log.debug msg
}


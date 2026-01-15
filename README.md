# loratap_hubitat
Hubitat driver for LoraTap SC420ZB (TS130F Tuya-based) gang of 2 curtain/blinds Zigbee switch .
manufacturer: "_TZ3000_esynmmox"

The code is initially based on "Zemismart Zigbee Blind Driver" "community driver by amosyuen and kkossev.
Removed support of all extra devices, concentrated on this specific one, but added support for gang of two (dual) child switch that was not supported in original driver.

Installation: 
--=You need to install BOTH drivers =--
1. Open Hubitat Web page on "Drivers Code" tab (in DEVELOPERS section of left menu)
2. Click "+Add Driver" button
3. Paste content of TS130F_Curtain_Child.groovy into a text box and click "Save" button
4. Do the same for TS130F_Dual_Curtain_Parent.groovy content
5. Pair the device iof its not paired yet , if it is, go to the device "Device Info" page and in "Type" drop dpwn box select "TS130F Dual Curtain Parent" 
6. You might need to use "Reinstall children" button on Commands page in Parent device driver
7. Calibration of each child is required for proper work, I added calibration instructions in CALIBRATION_GUIDE.md , however not sure how accurate they are, in my case I am getting constant "drift" , so had after calibration to adjust time manually to make sure it operates more or less reliable, maybe I am missing something or its just a nature of such device working with motors that do not have sensors


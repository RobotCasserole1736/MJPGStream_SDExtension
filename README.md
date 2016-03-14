SmartDashboard plugin for viewing two camera streams from MJPG-Streamer.
Switches between streams by setting boolean "useCamera1" from SmartDashboard.
Also includes ability to add crosshair to image from properties window. See
(https://github.com/robotpy/mjpg-streamer) and 
(https://github.com/robotpy/roborio-packages)

To install:
    copy jar file from latest release 
    (https://github.com/RobotCasserole1736/MJPGStream_SDExtension/releases/latest) 
    to $HOME/SmartDashboard/extensions

Video is now recorded in the directory that you start SmartDashboard.  Encoding will not finish properly if SmartDashboard is closed - video stream must end first (network connection lost, camera stream killed, etc).  Encoding is performed using the jcodec library.  Recorded video speed is approximately double the initial speed - little documentation on jcodec means I'm not sure how to fix this.
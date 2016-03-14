package edu.wpi.first.smartdashboard.gui.elements;

import edu.wpi.first.smartdashboard.gui.StaticWidget;
import edu.wpi.first.smartdashboard.properties.BooleanProperty;
import edu.wpi.first.smartdashboard.properties.IntegerProperty;
import edu.wpi.first.smartdashboard.properties.Property;
import edu.wpi.first.smartdashboard.properties.StringProperty;
import edu.wpi.first.wpilibj.networktables.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

import org.jcodec.api.awt.SequenceEncoder8Bit;

/**
 *
 * @author Nick Dunne
 */
public class MJPGStreamerViewerExtension extends StaticWidget {

    /**
	 * 
	 */
	private static final long serialVersionUID = 3280022425547727170L;

	public static final String NAME = "MJPG Streamer Camera Switcher";
    
    private NetworkTable networkTable; 

    private static final int[] START_BYTES = new int[]{0xFF, 0xD8};
    private static final int[] END_BYTES = new int[]{0xFF, 0xD9};

    private boolean ipChanged = true;
    private String ipString = null;
    private double rotateAngleRad = 0;
    private double rotate2AngleRad = 0;
    private int port = 0;
    private int port2 = 0;
    private long lastFPSCheck = 0;
    private int lastFPS = 0;
    private int fpsCounter = 0;
    private boolean useCamera1 = true;
    private int crosshairX = 0;
    private int crosshairY = 0;
    private int crosshairSize = 0;
    private boolean swapCameras = false;
    private Font camFont = new Font("Arial", Font.BOLD, 20);
    private SequenceEncoder8Bit encoder = null;
    public class BGThread extends Thread {

        boolean destroyed = false;

        public BGThread() {
            super("Camera Viewer Background");
        }

        long lastRepaint = 0;
        @Override
        public void run() {
            URLConnection connection = null;
            URLConnection connection2 = null;
            InputStream stream = null;
            InputStream stream2 = null;
            ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
            while (!destroyed) {
                try{
                    System.out.println("Connecting to camera 1");
                    ipChanged = false;
                    URL url = new URL("http://"+ipString+":"+port+"/?action=stream");
                    URL url2 = new URL("http://"+ipString+":"+port2+"/?action=stream");
                    connection = url.openConnection();
                    connection.setReadTimeout(250);
                    stream = connection.getInputStream();
                    System.out.println("Connecting to camera 2");
                    connection2 = url2.openConnection();
                    connection2.setReadTimeout(250);
                    stream2 = connection2.getInputStream();
                    try {
                    	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
                    	String currentTime = LocalDateTime.now().format(formatter);
                    	System.out.println("Creating file " + currentTime + "_camFeed4.mp4");
        				encoder = new SequenceEncoder8Bit(new File(currentTime + "_camFeed.mp4"));
        			} catch (IOException e1) {
        				System.out.println("Error: Encoder could not be created");
        			}
                    networkTable = NetworkTable.getTable("SmartDashboard");

                    while(!destroyed && !ipChanged){
                        while(System.currentTimeMillis()-lastRepaint<10){
                            stream.skip(stream.available());
                            stream2.skip(stream2.available());
                            Thread.sleep(1);
                        }
                        stream.skip(stream.available());
                        stream2.skip(stream2.available());
                        
                        useCamera1 = networkTable.getBoolean("useCamera1", true);
                        if(swapCameras)
                        	useCamera1 = !useCamera1;

                        imageBuffer.reset();
                        for(int i = 0; i<START_BYTES.length;){
                            int b = useCamera1? stream.read() : stream2.read();
                            if(b==START_BYTES[i])
                                i++;
                            else
                                i = 0;
                        }
                        for(int i = 0; i<START_BYTES.length;++i)
                        {
                            imageBuffer.write(START_BYTES[i]);
                        }

                        for(int i = 0; i<END_BYTES.length;){
                            int b = useCamera1? stream.read() : stream2.read();
                            imageBuffer.write(b);
                            if(b==END_BYTES[i])
                                i++;
                            else
                                i = 0;
                        }

                        fpsCounter++;
                        if(System.currentTimeMillis()-lastFPSCheck>500){
                            lastFPSCheck = System.currentTimeMillis();
                            lastFPS = fpsCounter*2;
                            fpsCounter = 0;
                        }

                        lastRepaint = System.currentTimeMillis();
                        ByteArrayInputStream tmpStream = new ByteArrayInputStream(imageBuffer.toByteArray());
                        imageToDraw = ImageIO.read(tmpStream);
                        if(encoder != null)
                        	encoder.encodeImage(imageToDraw);
                        repaint();
                    }

                } catch(Exception e){
                    imageToDraw = null;
                    repaint();
                    System.out.println("Error: Connection to camera failed");
					try {
						if(encoder != null)
						{
							encoder.finish();
							encoder = null;
						}
					} catch (IOException e1) {
						System.out.println("Error: Encoder unable to finish");
					}
                }

                if(!ipChanged){
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {}
                }
            }

        }

        @Override
        public void destroy() {
            destroyed = true;
            try {
            	if(encoder != null)
            	{
            		encoder.finish();
            		encoder = null;
            	}
			} catch (IOException e) {
				System.out.println("Error: Encoder unable to finish");
			}
        }
    }
    private BufferedImage imageToDraw;
    private BGThread bgThread = new BGThread();
    public final StringProperty ipProperty = new StringProperty(this, "Robot IP Address or mDNS name", "roborio-1736-frc.local");
    public final IntegerProperty portProperty = new IntegerProperty(this, "port", 5800);
    public final IntegerProperty port2Property = new IntegerProperty(this, "port 2", 5801);
    public final IntegerProperty rotateProperty = new IntegerProperty(this, "Degrees Rotation", 0);
    public final IntegerProperty rotate2Property = new IntegerProperty(this, "Cam2 Degrees Rotation", 0);
    public final IntegerProperty crosshairXProperty = new IntegerProperty(this, "Crosshair X", 0);
    public final IntegerProperty crosshairYProperty = new IntegerProperty(this, "Crosshair Y", 0);
    public final IntegerProperty crosshairSizeProperty = new IntegerProperty(this, "Crosshair size", 0);
    public final BooleanProperty swapCamerasProperty = new BooleanProperty(this, "Swap Cameras", false);

    @Override
    public void init() {
        setPreferredSize(new Dimension(160, 120));
        ipString = ipProperty.getSaveValue();
        rotateAngleRad = Math.toRadians(rotateProperty.getValue());
        rotate2AngleRad = Math.toRadians(rotate2Property.getValue());
        port = portProperty.getValue();
        port2 = port2Property.getValue();
        crosshairX = crosshairXProperty.getValue();
        crosshairY = crosshairYProperty.getValue();
        crosshairSize = crosshairSizeProperty.getValue();
        swapCameras = swapCamerasProperty.getValue();
        bgThread.start();
        revalidate();
        repaint();
    }

    @Override
    public void propertyChanged(Property property) {
        if (property == ipProperty) {
            ipString = ipProperty.getSaveValue();
            ipChanged = true;
        }
        if (property == rotateProperty) {
            rotateAngleRad = Math.toRadians(rotateProperty.getValue());
        }
        if (property == rotate2Property) {
        	rotate2AngleRad = Math.toRadians(rotate2Property.getValue());
        }
        if (property == portProperty) {
            port = portProperty.getValue();
            ipChanged = true;
        }
        if (property == port2Property) {
        	port2 = port2Property.getValue();
        	ipChanged = true;
        }
        if (property == crosshairXProperty) {
        	crosshairX = crosshairXProperty.getValue();
        }
        if (property == crosshairYProperty) {
        	crosshairY = crosshairYProperty.getValue();
        }
        if (property == crosshairSizeProperty) {
        	crosshairSize = crosshairSizeProperty.getValue();
        }
        if(property == swapCamerasProperty) {
        	swapCameras = swapCamerasProperty.getValue();
        }
    }

    @Override
    public void disconnect() {
			try {
		    	if(encoder != null)
		    	{
		    		encoder.finish();
		    		encoder = null;
		    	}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("Error: Encoder unable to finish");
			}
        bgThread.destroy();
        super.disconnect();
    }

    @Override
    protected void paintComponent(Graphics g) {
        BufferedImage drawnImage = imageToDraw;

        if (drawnImage != null) {
        	double rotate = useCamera1 ? rotateAngleRad : rotate2AngleRad;
            // cast the Graphics context into a Graphics2D
            Graphics2D g2d = (Graphics2D)g;

            // get the existing Graphics transform and copy it so that we can perform scaling and rotation
            AffineTransform origXform = g2d.getTransform();
            AffineTransform newXform = (AffineTransform)(origXform.clone());

            // find the center of the original image
            int origImageWidth = drawnImage.getWidth();
            int origImageHeight = drawnImage.getHeight();
            int imageCenterX = origImageWidth/2;
            int imageCenterY = origImageHeight/2;

            // perform the desired scaling
            double panelWidth = getBounds().width;
            double panelHeight = getBounds().height;
            double panelCenterX = panelWidth/2.0;
            double panelCenterY = panelHeight/2.0;
            double rotatedImageWidth = origImageWidth * Math.abs(Math.cos(rotate)) + origImageHeight * Math.abs(Math.sin(rotate));
            double rotatedImageHeight = origImageWidth * Math.abs(Math.sin(rotate)) + origImageHeight * Math.abs(Math.cos(rotate));

            // compute scaling needed
            double scale = Math.min(panelWidth / rotatedImageWidth, panelHeight / rotatedImageHeight);

            // set the transform before drawing the image
            // 1 - translate the origin to the center of the panel
            // 2 - perform the desired rotation (rotation will be about origin)
            // 3 - perform the desired scaling (will scale centered about origin)
            newXform.translate(panelCenterX,  panelCenterY);
            newXform.rotate(rotate);
            newXform.scale(scale, scale);
            g2d.setTransform(newXform);

            // draw image so that the center of the image is at the "origin"; the transform will take care of the rotation and scaling
            g2d.drawImage(drawnImage, -imageCenterX, -imageCenterY, null);

            // restore the original transform
            g2d.setTransform(origXform);

            g.setColor(Color.PINK);
            g.drawString("FPS: "+lastFPS, 10, 10);
            
            g2d.setStroke(new BasicStroke(8));
            g2d.setFont(camFont);
            if(useCamera1)
            {
            	g2d.setColor(Color.GREEN);
            	g2d.drawString("CAM 1", 80, 20);
            }
            else
            {
            	g2d.setColor(Color.MAGENTA);
            	g2d.drawString("CAM 2", 80, 20);
            }
            
            if(crosshairSize > 0)
            {
            	g2d.setColor(Color.YELLOW);
            	g2d.drawOval(crosshairX, crosshairY, crosshairSize, crosshairSize);
            }
        } else {
            g.setColor(Color.PINK);
            g.fillRect(0, 0, getBounds().width, getBounds().height);
            g.setColor(Color.BLACK);
            g.drawString("NO CONNECTION", 10, 10);
        }
    }
}
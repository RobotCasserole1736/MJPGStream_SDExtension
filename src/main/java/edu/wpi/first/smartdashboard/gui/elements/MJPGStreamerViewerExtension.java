package edu.wpi.first.smartdashboard.gui.elements;

import edu.wpi.first.smartdashboard.gui.StaticWidget;
import edu.wpi.first.smartdashboard.properties.BooleanProperty;
import edu.wpi.first.smartdashboard.properties.IntegerProperty;
import edu.wpi.first.smartdashboard.properties.Property;
import edu.wpi.first.smartdashboard.properties.StringProperty;
import edu.wpi.first.wpilibj.networktables.*;
import edu.wpi.first.wpilibj.tables.ITable;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.jcodec.api.awt.SequenceEncoder8Bit;
import org.jcodec.common.io.IOUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

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
    private boolean processOpenCV = false;
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
                        processOpenCV = networkTable.getBoolean("processOpenCV", false);
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
                        if(processOpenCV)
                            ProcessImageOpenCV(imageToDraw);
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
        loadLibrary();
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
    
    private static void loadLibrary()
    {
        try
        {
            InputStream in = null;
            File fileOut = null;
            in = new FileInputStream("C:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java310.dll");
            fileOut = File.createTempFile("lib",".dll");
            OutputStream out = new FileOutputStream(fileOut);
            IOUtils.copy(in, out);
            in.close();
            out.close();
            System.load(fileOut.toString());
        }
        catch(Exception ex)
        {
            throw new RuntimeException("Failed to load opencv native library", ex);
        }
    }
    
    private void ProcessImageOpenCV(BufferedImage im)
    {
        // Threshold values for HSV image filter
        int HMin = 0;
        int HMax = 255;
        int SMin = 0;
        int SMax = 255;
        int VMin = 0;
        int VMax = 255;
        
        Mat mat = new Mat(im.getHeight(), im.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) im.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        //Convert image to HSV, then split to separate channels
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2HSV);
        Vector<Mat> split = new Vector<Mat>(3);
        Core.split(mat, split);
        Mat mH = split.get(0);
        Mat mS = split.get(1);
        Mat mV = split.get(2);
        //Perform thresholds
        Imgproc.threshold(mH, mH, HMin, HMax, Imgproc.THRESH_BINARY);
        //Imgproc.threshold(mH, mH, HMax, 255, Imgproc.THRESH_BINARY_INV);
        Imgproc.threshold(mS, mS, SMin, SMax, Imgproc.THRESH_BINARY);
        //Imgproc.threshold(mS, mS, SMax, 255, Imgproc.THRESH_BINARY_INV);
        Imgproc.threshold(mV, mV, VMin, VMax, Imgproc.THRESH_BINARY);
        //Imgproc.threshold(mV, mV, VMax, 255, Imgproc.THRESH_BINARY_INV);
        //And the channels back together
        Core.bitwise_and(mH, mS, mS);
        Core.bitwise_and(mS, mV, mV);
        Mat hierarchy = new Mat();
        //Find contours, then decide to shoot based on x/y
        Vector<MatOfPoint> contours = new Vector<MatOfPoint>();
        Imgproc.findContours(mV, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
//      Vector<Double> width = new Vector<Double>();
//      Vector<Double> height= new Vector<Double>();
//      Vector<Double> centerX = new Vector<Double>();
//      Vector<Double> centerY = new Vector<Double>();
//      Vector<Double> area = new Vector<Double>();
        boolean shotAligned = false;
        for(MatOfPoint contour:contours)
        {
//          width.add((double)contour.width());
//          height.add((double)contour.height());
            Moments moments = Imgproc.moments(contour);
            double pointCenterX = moments.get_m10() / moments.get_m00();
            double pointCenterY = moments.get_m01() / moments.get_m00();
//          centerX.add(pointCenterX);
//          centerY.add(pointCenterY);
//          area.add(Imgproc.contourArea(contour));
            double xError = Math.abs(pointCenterX - crosshairX) / crosshairX;
            double yError = Math.abs(pointCenterY - crosshairY) / crosshairY;
            if(xError < 0.1 && yError < 0.1)
                shotAligned = true;
        }
//      ITable targetTable = NetworkTable.getTable("grip").getSubTable("targets");
//      if(targetTable != null)
//      {
//          targetTable.putNumberArray("width", (Double[])width.toArray());
//          targetTable.putNumberArray("height", (Double[])height.toArray());
//          targetTable.putNumberArray("center_x", (Double[])centerX.toArray());
//          targetTable.putNumberArray("center_y", (Double[])centerY.toArray());
//          targetTable.putNumberArray("area", (Double[])area.toArray());
//      }
        
        //if a found contour was close to the expected target center, write true to smartdashboard "shoot"
        networkTable.putBoolean("ShotAligned", shotAligned);
    }
}
package eu.spitfire_project;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

public class THouseView extends JPanel {

	private static final long serialVersionUID = 1L;

	private class Reading {
		public long time;
		public double value;
		public Reading(long time, double value) {
			this.time = time;
			this.value = value;
		}
	}

	private class SensorData {
		public String ipv6Addr = null;
		public String macAddr = null;
		public String FOI = null;
		public TList readings = null;
		public TPoint loc = null;
		public double maxValue, minValue;
		public long timeL, timeR;
		public int senID;

		public SensorData(int senID, String macAddr, String ipv6Addr, TPoint loc, String FOI) {
			this.senID = senID;
			this.ipv6Addr = ipv6Addr;
			this.macAddr = macAddr;
			this.FOI = FOI;
			this.loc = loc;
			this.readings = new TList(numberOfImagesPerDay); //Maximum number of readings
			maxValue = Double.MIN_VALUE;
			minValue = Double.MAX_VALUE;
		}

		private void updateReadings(Reading r) {
			readings.enList(r);
			timeL = ((Reading)(readings.get(0))).time;
			timeR = ((Reading)(readings.get(readings.len()-1))).time;
			if (maxValue < r.value) maxValue = r.value;
			if (minValue > r.value) minValue = r.value;
			while (timeR-timeL >= numberOfImagesPerDay*realTimeTick) {
				readings.remove(0);
				timeL = ((Reading)(readings.get(0))).time;
				timeR = ((Reading)(readings.get(readings.len()-1))).time;
			}
		}
	}

	private class Area {
		public double x1, y1, w, h;
		public Area(double x1, double y1, double w, double h) {
			this.x1 = x1;
			this.y1 = y1;
			this.w = w;
			this.h = h;
		}
	}

	//GUI stuffs
    private JFrame appFrame;
	private JPopupMenu popView = new JPopupMenu();
	private JMenuItem popItmStart = new JMenuItem("Start visualizer");
	private JMenuItem popItmPause = new JMenuItem("Pause visualizer");
	private JMenuItem popItmResume = new JMenuItem("Resume visualizer");

	private BufferedImage tempImg;

	//Core stuffs
	private boolean pause;
	private long startTime;
	private int numberOfImagesPerDay = 96;
	private int realTimeTick = 15; //minutes
	private int simTime, simTimeM, simTimeH, simTimeD;
	private double currentTemperature = 30;
	private double maxTemperature = 40;
	private double maxLux = 1500;
	private double minLux = 0;
    private Random random = new Random();

	//Coordinates of the areas
	Area LivingRoom = new Area(4, 2, 41, 95);
	Area BedRoom = new Area(58, 2, 58, 58);
	Area Kitchen = new Area(58, 62, 58, 37);

	private int simW, simH, areaW, areaH;
	private double ratioW, ratioH;
	int graphW = 10; int graphH = 6;
	int paddingy = 5;
	int senRadius = 2;
	double tempX = 52, tempY = 30;
	SensorData clickNode = null;
	boolean draggingNode = false;
	boolean draggingTemperature = false;

	private TList sensors = null;
	private TList images = null;
	private int imgIndex = 60;

	public THouseView(JFrame appFrame) {
		setBackground(Color.WHITE);

		pause = true;

		//Initialize components
        this.appFrame = appFrame;
		popView.add(popItmStart);
		popView.add(popItmPause);
		popView.add(popItmResume);

		popItmStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { popItmStartClick(evt); } });
		popItmPause.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { popItmPauseClick(evt); } });
		popItmResume.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { popItmResumeClick(evt); } });

		//Resize response
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent evt) {	netViewResized(evt); } });

		//Mouse events for the netView
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent evt) { netViewMousePressed(evt); }
			public void mouseReleased(MouseEvent evt) { netViewMouseReleased(evt); }
			public void mouseClicked(MouseEvent evt) { netViewMouseClicked(evt); }
		});

		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent evt) { netViewMouseDragged(evt); }
			public void mouseMoved(MouseEvent evt) { netViewMouseMoved(evt); }
		});

		//Projection area
		areaW = 120; //1,2m
		areaH = 100; //1m

		//Initialize the sensor list
		sensors = new TList();

		//Fill in the list of the images of the house
		images = new TList();

		final String path = "img/house";

		try {
			// Load all Files as InputStream
			final CodeSource src = THouseView.class.getProtectionDomain()
					.getCodeSource();
			final URL jar = src.getLocation();
			if (new File(jar.getFile()).isFile()) {
				final ZipFile zipFile = new ZipFile(jar.getFile());
				final ZipInputStream zip = new ZipInputStream(jar.openStream());
				ZipEntry entry;

				// Read files as a List
				List<String> fileList = new LinkedList<String>();
				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().startsWith(path) && !entry.isDirectory()) {
						fileList.add(entry.getName());
					}
				}
				zipFile.close();

				// Convert to an Array
				String[] fileArray = new String[fileList.size()];
				for (int i = 0; i < fileList.size(); i++) {
					fileArray[i] = fileList.get(i);
				}

				Arrays.sort(fileArray);

				for (String file : fileArray) {
					BufferedImage img = ImageIO.read(ClassLoader.getSystemResourceAsStream(file));
					images.enList(img);
				}
			}

			// E.g. run in Eclipse
			else {
				File folder = null;
				try {
					folder = new File(ClassLoader.getSystemResource(path).toURI());
				} catch (URISyntaxException e) { }
				File[] listOfFiles = folder.listFiles();
				Arrays.sort(listOfFiles);
				for (int i = 0; i < listOfFiles.length; i++) {
					if (listOfFiles[i].isFile()) {
						File f = new File(folder.getAbsolutePath() + "/" + listOfFiles[i].getName());
						BufferedImage img = ImageIO.read(f);
						images.enList(img);
					}
				}
			}
		} catch (IOException e) {
			// TODO: handle exception
		}

		try {
			tempImg = ImageIO.read(ClassLoader
					.getSystemResourceAsStream("img/temp.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void popItmStartClick(ActionEvent evt) {
        pause = false;

        URL crawRequest;
        try {
            crawRequest = new URL("http://www.coap1.wisebed.itm.uni-luebeck.de:8080/visualizer");
            //crawRequest = new URL("http://localhost:8080/visualizer");
            URLConnection connection = crawRequest.openConnection();
            connection.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write("resumeVisualization");
            writer.flush();
            writer.close();
        } catch (MalformedURLException e) {	e.printStackTrace(); }
        catch (IOException e) {	e.printStackTrace();	}
	}

	public void popItmPauseClick(ActionEvent evt) {
		pause = true;
	}

	public void popItmResumeClick(ActionEvent evt) {
		pause = false;
	}

	public void netViewResized(ComponentEvent evt) {
		resizedResponse();
	}

	public void netViewMouseClicked(MouseEvent evt) {
		if (evt.getButton() == MouseEvent.BUTTON3) {
			popView.show(evt.getComponent(), evt.getX(), evt.getY());
		}
	}

	public void netViewMousePressed(MouseEvent evt) {
		if (evt.getButton() == MouseEvent.BUTTON1) {
			clickNode = getSensorAt(evt.getX(), evt.getY());
			if (clickNode != null)
		  		draggingNode = true;
			double realX = evt.getX()/ratioW;
			double realY = evt.getY()/ratioH;
			double d = (realX-tempX)*(realX-tempX)+(realY-tempY)*(realY-tempY);
			d = Math.sqrt(d);
			if (d < 5) draggingTemperature = true;
		}
	}

	public void netViewMouseReleased(MouseEvent evt) {
		if (draggingNode && clickNode != null) {
			relocateSensor(clickNode, evt.getX(), evt.getY());
			draggingNode = false;
			clickNode = null;
		}
		if (draggingTemperature)
			draggingTemperature = false;
	}

	public void netViewMouseDragged(MouseEvent evt) {
		if (clickNode != null) {
			Graphics gr = getGraphics();
			gr.setXORMode(Color.white);
			gr.setColor(Color.black);
			relocateSensor(clickNode, evt.getX(), evt.getY());
		}
		if (draggingTemperature) {
			tempX = (double)evt.getX()/ratioW;
			tempY = (double)evt.getY()/ratioH;
			repaint();
		}
	}

	public void netViewMouseMoved(MouseEvent evt) {
	}


	//-------------------------Graphic stuff-------------------------
	public void relocateSensor(SensorData sd, int X, int Y) {
		double realX = (double)X/ratioW;
		double realY = (double)Y/ratioH;
		sd.loc.x = realX;
		sd.loc.y = realY;
		repaint();
	}

	public SensorData getSensorAt(int simX, int simY) {
		SensorData sd = null;
		double realX = (double)simX/ratioW;
		double realY = (double)simY/ratioH;
		for (int i=0; i<sensors.len(); i++) {
			SensorData s = (SensorData)sensors.get(i);
			double d = (s.loc.x-realX)*(s.loc.x-realX)+(s.loc.y-realY)*(s.loc.y-realY);
			d = Math.sqrt(d);
			if (d < (graphH+graphW)/4) {
				sd = s;
				break;
			}
		}
		return sd;
	}

	public void resizedResponse() {
		simW = this.getBounds().width;
		simH = this.getBounds().height;
		ratioW = (double)simW/areaW;
		ratioH = (double)simH/areaH;
		repaint();
	}

	public void drawString(Graphics2D gr2d, int x, int y, String st, double fontSize, Color txtColor, Color brdColor) {
		int FontSize = (int)(fontSize*ratioH);
		Font font = new Font("Arial", Font.BOLD, FontSize);
		FontMetrics fm = getFontMetrics(font);
		gr2d.setFont(font);
		int sw = fm.stringWidth(st);
		int X = x-sw/2;
		int Y = y+FontSize;
    gr2d.setColor(brdColor);
    gr2d.drawString(st, X-1, Y-1);
    gr2d.drawString(st, X-1, Y+1);
    gr2d.drawString(st, X+1, Y-1);
    gr2d.drawString(st, X+1, Y+1);
    gr2d.setColor(txtColor);
    gr2d.drawString(st, X, Y);
	}

	public void drawSensor(Graphics2D gr2d, SensorData sd) {
		//simulated coordinate
		int W = (int)(graphW*ratioW);
		int H = (int)(graphH*ratioH);
		int X = (int)(sd.loc.x*ratioW);
		int Y = (int)(sd.loc.y*ratioH);
		int x1 = X-W/2; int y1 = Y-H/2;
		int x2 = x1+W; //int y2 = y1+H;

		//Draw the graph area
		gr2d.setColor(Color.WHITE);
		gr2d.fillRect(x1, y1, W, H);
		gr2d.setColor(Color.BLUE);
		gr2d.drawRect(x1-1, y1-1, W+2, H+2);

		//Draw the sensor readings
		//double vratio = (double)H/(sd.maxValue-sd.minValue);
		int pad = 3;
		double vratio = (double)(H-2*pad)/(maxLux-minLux);
		double tratio = (double)W/(double)(numberOfImagesPerDay*realTimeTick);
		gr2d.setColor(Color.RED);

		//Reading re = (Reading)sd.readings.get(0);
		//long t = sd.timeR-re.time;
		//if (sd.ipv6Addr.indexOf("8e7f")>0)
			//System.out.println("sd.timeR: "+sd.timeR+", re.time: "+re.time+", time distance is "+t);
		if (sd.readings.len() > 2) {
			Reading first = (Reading)sd.readings.get(sd.readings.len()-1);
			int xdraw_old = x2-(int)((sd.timeR-first.time)*tratio);
			//int ydraw_old = y1+H-(int)((first.value-sd.minValue)*vratio);
			double v1 = first.value;
			if (v1 > maxLux) v1 = maxLux-minLux;
			if (v1 < minLux) v1 = 0;
			int ydraw_old = y1+H-(int)(v1*vratio)-pad;
			for (int i=sd.readings.len()-2; i>=1; i--) {
				Reading r = (Reading)sd.readings.get(i);
				int xdraw_new = x2-(int)((sd.timeR-r.time)*tratio);
				//int ydraw_new = y1+H-(int)((r.value-sd.minValue)*vratio);
				double v2 = r.value;
				if (v2 > maxLux) v2 = maxLux-minLux;
				if (v2 < minLux) v2 = 0;
				int ydraw_new = y1+H-(int)(v2*vratio)-pad;
				gr2d.drawLine(xdraw_old, ydraw_old, xdraw_new, ydraw_new);
				xdraw_old = xdraw_new;
				ydraw_old = ydraw_new;
			}
		}

		//Draw other information
		double yFOI = sd.loc.y - graphH/2+0.2;
		drawString(gr2d, (int)(sd.loc.x*ratioW), (int)(yFOI*ratioH), sd.FOI, 1.8, Color.BLUE, Color.WHITE);
		double xIPv6 = sd.loc.x;
		double yIPv6 = sd.loc.y + graphH/2+0.1;
		drawString(gr2d, (int)(xIPv6*ratioW), (int)(yIPv6*ratioH), "IPv6: '..."+
							sd.ipv6Addr.substring(sd.ipv6Addr.length()-5)+"'", 2, Color.YELLOW, Color.BLACK);
	}

	public void drawTemperature(Graphics2D gr2d, double tempX, double tempY) {
		int X = (int)(tempX*ratioW) - tempImg.getWidth()/2;
		int Y = (int)(tempY*ratioH) - tempImg.getHeight()/2;
		gr2d.drawImage(tempImg, X, Y, null);
		gr2d.setColor(Color.RED);
		int curTempX = (int)(tempX*ratioW)-4;
		int curTempY = (int)(tempY*ratioH)+55;
		int drawTempBarHeight = (int)((double)currentTemperature*168/maxTemperature);
		gr2d.fillRect(curTempX, curTempY-drawTempBarHeight, 10, drawTempBarHeight);
		int curTemp = (int)currentTemperature;
		drawString(gr2d, curTempX+5, curTempY+10, String.format("%d", curTemp)+"°C", 2.5, Color.RED, Color.WHITE);
		/*double light = 0;
		if (sensors.len() > 0) {
			SensorData sd = (SensorData)sensors.get(0);
			Reading r = (Reading)sd.readings.get(sd.readings.len()-1);
			light = r.value;
		}
		drawString(gr2d, curTempX+5, curTempY+10, String.format("%.2f", light)+" lux", 2, Color.RED, Color.WHITE);
		*/
	}

	public void drawDateTime(Graphics2D gr2d, double dateX, double dateY, int DD, int HH, int MM) {
		//gr2d.drawImage(dtImg, X, Y, null);
		gr2d.setColor(Color.RED);
		int timex = (int)(dateX*ratioW);
		int timey = (int)((dateY+2.5)*ratioH);
		int datey = (int)((dateY)*ratioH);
		//drawString(gr2d, datex, datey, String.format("TIME: %d:%d", HH, MM), 2, Color.BLACK, new Color(153, 175, 162));
		drawString(gr2d, timex, datey, String.format("DAY - %d", DD), 2, Color.RED, Color.WHITE);
		drawString(gr2d, timex, timey, String.format("TIME - %02d:%02d", HH, MM), 2, Color.RED, Color.WHITE);
	}

	@Override
	public void paintComponent(Graphics g) {

		BufferedImage bufferedImage = (BufferedImage) images.get(imgIndex);
		ImageIcon imageicon = new ImageIcon(bufferedImage);
		Image image = imageicon.getImage();
		super.paintComponent(g);
		if (image != null)
			g.drawImage(image, 0, 0, getWidth(), getHeight(), this);

		//Redraw my stuff
		Graphics2D gr2d = (Graphics2D)g;

		//Draw room boundaries
		//gr2d.setColor(Color.RED);
		//gr2d.drawRect((int)(LivingRoom.x1*ratioW), (int)(LivingRoom.y1*ratioH), (int)(LivingRoom.w*ratioW), (int)(LivingRoom.h*ratioH));
		//gr2d.drawRect((int)(BedRoom.x1*ratioW), (int)(BedRoom.y1*ratioH), (int)(BedRoom.w*ratioW), (int)(BedRoom.h*ratioH));
		//gr2d.drawRect((int)(Kitchen.x1*ratioW), (int)(Kitchen.y1*ratioH), (int)(Kitchen.w*ratioW), (int)(Kitchen.h*ratioH));

		//Draw temperature
		drawTemperature(gr2d, tempX, tempY);

		//Draw date and time
		double dateX = tempX;
		double dateY = 5;
		//drawDateTime(gr2d, dateX, dateY, simTimeD, simTimeH, simTimeM);
		drawDateTime(gr2d, dateX, dateY, simTimeD, simTimeH, simTimeM);

		//Draw sensors
		for (int i=0; i<sensors.len(); i++) {
			SensorData sd = (SensorData)sensors.get(i);
				drawSensor(gr2d, sd);
		}
	}

	private SensorData searchSensor(int sensorID) {
    SensorData rs = null;
    int ind = 0;
    for (; ind<sensors.len(); ind++) {
        SensorData sd = (SensorData)sensors.get(ind);
        if (sd.senID == sensorID) {
            rs = sd;
            break;
        }
    }
    return rs;
}

	private int searchSensorMAC(String mac) {
	    int ind = 0;
	    for (; ind<sensors.len(); ind++) {
	        SensorData sd = (SensorData)sensors.get(ind);
	        if (sd.macAddr.equalsIgnoreCase(mac)) {
	            break;
	        }
	    }
	    return ind;
	}

	//-----------------Methods to update simulation info-----------------------------
	public void startUpdateInfo() {

	}

	public void update(String updateCommand) {
        System.out.println(" ----------- Updating... ------------------------");

        //The update command
        TString command = new TString(updateCommand, '\n');

        //Meta information
        TString parameter = new TString(command.getStrAt(0), '|');
        simTime = Integer.valueOf(parameter.getStrAt(0));
        imgIndex = Integer.valueOf(parameter.getStrAt(1));
        currentTemperature = Double.valueOf(parameter.getStrAt(2));
        System.out.println("simTime: "+simTime+", imgIndex: "+imgIndex+", temp: "+currentTemperature);

        int elapsedTimeS = (int)((double)(System.currentTimeMillis()-startTime)/(double)1000) % 60;
        int elapsedTimeM = (int)((double)(System.currentTimeMillis()-startTime)/(double)1000/(double)60) % 60;
        int elapsedTimeH = (int)((double)(System.currentTimeMillis()-startTime)/(double)1000/(double)60/(double)60) % 24;
        appFrame.setTitle("Elapsed real time <"+elapsedTimeH+":"+elapsedTimeM+":"+elapsedTimeS+
                ">; Elapsed simulation time <"+simTimeD+"-"+simTimeH+":"+simTimeM+">");

        simTimeM = (int)simTime % 60;
        simTimeH = (int)((double)(simTime)/(double)60) % 24;
        simTimeD = (int)((double)(simTime)/(double)60/(double)24) % 24 + 1;

        //Sensor information
        for (int i=1; i<command.len(); i++) {
            parameter = new TString(command.getStrAt(i), '|');
            System.out.println("Parameter: <"+command.getStrAt(i)+">");

            int sensorID = Integer.valueOf(parameter.getStrAt(0));
            String ipv6Addr = parameter.getStrAt(1);
            String macAddr = parameter.getStrAt(2);
            String FOI = parameter.getStrAt(3);
            long ts = Long.valueOf(parameter.getStrAt(4)).longValue();
            double vl = Double.valueOf(parameter.getStrAt(5)).doubleValue();
            SensorData sd = searchSensor(sensorID);
            if (sd == null) {
                //Check if "8e84" is already in the list then delete it
                if ("8e84".equalsIgnoreCase(macAddr)) {
                    int ind = searchSensorMAC(macAddr);
                    if (ind < sensors.len())
                        sensors.remove(ind);
                }

                double x = 0, y = 0;
                if ("8e7f".equalsIgnoreCase(macAddr)) {
                    x = 51.5; y = 55; //Living room top
                } else if ("8ed8".equalsIgnoreCase(macAddr)) {
                    x = 51.5; y = 65; //Living room bottom
                } else if ("a88".equalsIgnoreCase(macAddr)) {
                    x = 51.5; y = 75; //Kitchen left
                } else if ("2304".equalsIgnoreCase(macAddr)) {
                    x = 51.5; y = 85; //Kitcheen right
                } else if ("8e84".equalsIgnoreCase(macAddr)) { //The new sensor
                    System.out.println("New node added");
                    x = BedRoom.x1+random.nextDouble()*(BedRoom.w-2*graphW)+graphW;
                    y = BedRoom.y1+random.nextDouble()*(BedRoom.h-(graphH+3*paddingy))+(graphH+3*paddingy)/2;
                }
                sd = new SensorData(sensorID, macAddr, ipv6Addr, new TPoint(x, y), FOI);
                sd.updateReadings(new Reading(ts, vl));
                sensors.enList(sd);
            } else {
                //Check if this sensor has just been annotated
                if ("Unannotated".equalsIgnoreCase(sd.FOI) &&
                        !"Unannotated".equalsIgnoreCase(FOI)) {
                    Area area;
                    if ("Living-Room".equalsIgnoreCase(FOI)) area = LivingRoom;
                    else
                    if ("Kitchen".equalsIgnoreCase(FOI)) area = Kitchen;
                    else area = BedRoom;
                    sd.loc.x = area.x1+random.nextDouble()*(area.w-2*graphW)+graphW;
                    sd.loc.y = area.y1+random.nextDouble()*(area.h-(graphH+3*paddingy))+(graphH+3*paddingy)/2;
                }
                sd.FOI = FOI;
                sd.updateReadings(new Reading(ts, vl));
            }
           /* System.out.println("sensor "+sd.ipv6Addr.substring(sd.ipv6Addr.length()-5)
                    +": (time-"+ts+", value-"+String.format("%.2f", vl)+")"
                    +", timeL-"+sd.timeL+", timeR-"+sd.timeR+", timeR-timeL:"+(sd.timeR-sd.timeL)
                    +", total:"+(numberOfImagesPerDay*realTimeTick));*/
            //log.debug(String.format(Locale.GERMANY, "%d %.4f", ts, vl));
        }
        repaint();

        System.out.println(" ----------- Updating DONE! ------------------------");
	}
}

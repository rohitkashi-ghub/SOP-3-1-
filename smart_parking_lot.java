package org.fog.test.perfeval;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
public class smart_parking_lot {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int numOfAreas = 4; //no. of fog nodes
	static int numOfcamerasPerArea=8; //no. of cameras connected to each fog node
	static double camera_wait = 5; //how often the cam should take pictures
	private static boolean CLOUD = false; //data going to fog nodes
	public static void main(String[] args) {
		//Pre-defined(Lines 45 to 57
		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "dcns"; // identifier of the application
			FogBroker broker = new FogBroker("broker");
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			createFogDevices(broker.getId(), appId);
			Controller controller = null;
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("c")){ // We later ensure that the name of each camera starts with the letter 'c'
					moduleMapping.addModuleToDevice("take-picture", device.getName());  
					// Since the camera takes a picture, we fix an instance of the module 'take-picture' to each CAMERA
				}
			}
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("a")){ 
					// We later ensure that the name of each fog device starts with the letter 'a'
					moduleMapping.addModuleToDevice("free-space", device.getName());   
		// Since the fog node checks if free space is available, we fix an instance of the module 'free-space' to each fog device
				}
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators); //performs the simulation
			
			controller.submitApplication(application, 
					(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("take-picture", "cloud"); 
				// placing all instances of take-picture module in the Cloud
				moduleMapping.addModuleToDevice("free-space", "cloud"); 
				// placing all instances of free-space module in the Cloud
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Error Occurred");
		}
	}
	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44000, 40000, 100, 10000, 0, 0.01, 1648, 1332);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2000, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // Connection Latency between proxy and cloud
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId){
		FogDevice fog_node = createFogDevice("a-"+id, 2000, 4000, 1000, 10000, 2, 0.0, 107.339, 83.4333); 
		//ensuring fog nodes start with the letter 'a'
		fogDevices.add(fog_node);
		fog_node.setUplinkLatency(2); // connection latency between fog node and proxy  
		for(int i=0;i<numOfcamerasPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice camera_sensor = add_camera_sensor(mobileId, userId, appId, fog_node.getId()); 
			//considering the CAMERA as a fog device. (Everything on iFogSim is a fog device)
			camera_sensor.setUplinkLatency(2); // Connection latency between camera and fog node
			fogDevices.add(camera_sensor);
		}
		fog_node.setParentId(parentId);
		return fog_node;
	}
	
	private static FogDevice add_camera_sensor(String id, int userId, String appId, int parentId){
		FogDevice camera_sensor = createFogDevice("c-"+id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44); // CAMERA starts with 'c'
		camera_sensor.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "camera_sensor", userId, appId, new DeterministicDistribution(camera_wait)); 
		// transmission time of CAMERA: deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL"); 
		//pan-tilt-zoom. Part of the CAMERA's function, and hence considered as an actuator
		actuators.add(ptz);
		sensor.setGatewayDeviceId(camera_sensor.getId());
		sensor.setLatency(1.0);  
		ptz.setGatewayDeviceId(parentId);
		ptz.setLatency(1.0);  
		return camera_sensor;
	}
	//pre-defined. Not required to change it
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);
		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		
		//Adding modules, which are the vertices in the directed graph
		 
		application.addAppModule("take-picture", 10);
		application.addAppModule("free-space", 10);
		application.addAppEdge("camera_sensor", "take-picture", 1000, 500, "camera_sensor", Tuple.UP, AppEdge.SENSOR); 
		// adding edge from camera_sensor to take_picture module 
		application.addAppEdge("take-picture", "free-space", 1000, 500, "slots",Tuple.UP, AppEdge.MODULE);
				
		application.addAppEdge("free-space", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);
		application.addTupleMapping("take-picture", "camera_sensor", "slots",
		new FractionalSelectivity(1.0));
		application.addTupleMapping("free-space", "slots",
		"PTZ_PARAMS", new FractionalSelectivity(1.0));
		final AppLoop loop = new AppLoop(new ArrayList<String>()
		{{add("camera_sensor");
		add("take-picture");add("free-space");
		add("PTZ_CONTROL");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop);}};
		application.setLoops(loops);
		return application;
		}
	}

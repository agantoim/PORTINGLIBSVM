//import driver untuk sensor suhu
import com.virtenio.driver.device.ADT7410;

//import driver untuk sensor tekanan
import com.virtenio.driver.device.MPL115A2;

//import driver untuk sesor humidity
import com.virtenio.driver.device.SHT21;

//import driver interface untuk input output pins
import com.virtenio.driver.gpio.GPIO;

//import driver untuk instansiasi GPIO
import com.virtenio.driver.gpio.NativeGPIO;

//import driver untuk I2C bus yaitu serial bus 2 kabel yang digunakan untuk menyambungkan prangkat kecepatan rendah
import com.virtenio.driver.i2c.I2C;

//import driver untuk instansiasi I2C
import com.virtenio.driver.i2c.NativeI2C;


public class SensingProgram {
	
	private NativeI2C i2c;
	private ADT7410 temperatureSensor;
	private MPL115A2 pressureSensor;
	private SHT21 humiditySensor;
	
	private void init() throws Exception{
		System.out.println("I2C(Init)");
		i2c = NativeI2C.getInstance(1);
		i2c.open(I2C.DATA_RATE_400);

		System.out.println("ADT7410(Init)");
		temperatureSensor = new ADT7410(i2c, ADT7410.ADDR_0, null, null);
		temperatureSensor.open();
		temperatureSensor.setMode(ADT7410.CONFIG_MODE_CONTINUOUS);
		
		System.out.println("GPIO(Init)");
		GPIO resetPin = NativeGPIO.getInstance(24);
		GPIO shutDownPin = NativeGPIO.getInstance(12);

		System.out.println("MPL115A2(Init)");
		pressureSensor = new MPL115A2(i2c, resetPin, shutDownPin);
		pressureSensor.open();
		pressureSensor.setReset(false);
		pressureSensor.setShutdown(false);
		
		System.out.println("SHT21(Init)");
		humiditySensor = new SHT21(i2c);
		humiditySensor.open();
		humiditySensor.setResolution(SHT21.RESOLUTION_RH12_T14);
		humiditySensor.reset();

		System.out.println("Done(Init)");
	}
	
	public void run() throws Exception {
		init();
		int tempRaw;
		int pressurePr;
		int rawRH;
		float celsius = 0;
		float pressure = 0;
		float rh = 0;
		double label = 0.0;
		String value="";
		System.out.println("Label | Temperature | Pressure | Humidity");
		while (true) {
			try {
				// get temp raw
				tempRaw = temperatureSensor.getTemperatureRaw();
				// get real temp in celcius
				celsius = temperatureSensor.getTemperatureCelsius();
				Thread.sleep(1000);

				// start pressure conversion
				pressureSensor.startBothConversion();
				//thread sleep berdasarkan conversion time
				Thread.sleep(MPL115A2.BOTH_CONVERSION_TIME);
				// get raw pressure
				pressurePr = pressureSensor.getPressureRaw();
				// get real pressure
				pressure = pressureSensor.compensate(pressurePr, tempRaw);
				//thread sleep berdasarkan waktu yang ditetapkan (1 Detik) dikurangi conversion time
				Thread.sleep(1000 - MPL115A2.BOTH_CONVERSION_TIME);

				// start humidity relative converstion
				humiditySensor.startRelativeHumidityConversion();
				Thread.sleep(100);
				// get raw RH
				rawRH = humiditySensor.getRelativeHumidityRaw();
				// get converted rh (humidity)
				rh = SHT21.convertRawRHToRHw(rawRH);
				Thread.sleep(1000);
				
				//ubah label manual disini karena sensor tidak mendukung library pengambilan waktu
				//label 1 Siang, label 0 Malam
				label=1.0;

				// temp in celsius
				System.out.println(label + " " + "0:" + celsius + " " + "1:" + pressure + " " + "2:" + rh);
				//set data sekarang berdasarkan apa yang sensor generate
				value=label + " " + "0:" + celsius + " " + "1:" + pressure + " " + "2:" + rh;
				
			} catch (Exception e) {
				System.out.println("SHT21 error");
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		new SensingProgram().run();
	}
}

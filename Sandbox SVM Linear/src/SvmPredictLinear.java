import com.virtenio.driver.device.ADT7410;
import com.virtenio.driver.device.MPL115A2;
import com.virtenio.driver.device.SHT21;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.i2c.I2C;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.misc.StringUtils;
import com.virtenio.vm.Time;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

class model_data {

	// header model
	static final String[] header = { 	"svm_type c_svc",
										"kernel_type linear",
										"nr_class 2",
										"total_sv 2",
										"rho 9.192837537668378",
										"label 0 1",
										"nr_sv 1 1" };
	// koefisien atau label dari SV
	static final double[][] sv_coef = { { 	 0.03815908889120921, 
											-0.03815908889120921   } };

	// index dan value dari SV
	public static final String[] sv_string = { 	"1:27.08160018920898 2:-17.41446685791015 3:82.02032470703125",
												"1:28.2047996520996 2:-22.751708984375 3:77.25958251953125"    };
}

public class SvmPredictLinear {

	private NativeI2C i2c;
	private ADT7410 temperatureSensor;
	private MPL115A2 pressureSensor;
	private SHT21 humiditySensor;

	private void init() throws Exception {
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
		String input="";
		System.out.println("Label | Temperature | Pressure | Humidity");
		while (true) {
			try {
				// get temp raw
				tempRaw = temperatureSensor.getTemperatureRaw();
				// get real temp in celcius
				celsius = temperatureSensor.getTemperatureCelsius();
				Thread.sleep(250);

				// start pressure conversion
				pressureSensor.startBothConversion();
				//thread sleep berdasarkan conversion time
				Thread.sleep(MPL115A2.BOTH_CONVERSION_TIME);
				// get raw pressure
				pressurePr = pressureSensor.getPressureRaw();
				// get real pressure
				pressure = pressureSensor.compensate(pressurePr, tempRaw);
				//thread sleep berdasarkan waktu yang ditetapkan (1 Detik) dikurangi conversion time
				Thread.sleep(250 - MPL115A2.BOTH_CONVERSION_TIME);

				// start humidity relative converstion
				humiditySensor.startRelativeHumidityConversion();
				Thread.sleep(250);
				// get raw RH
				rawRH = humiditySensor.getRelativeHumidityRaw();
				// get converted rh (humidity)
				rh = SHT21.convertRawRHToRHw(rawRH);
				Thread.sleep(250);
				

				// load model header
				svm_model model = svm.svm_load_model();
				
				//ubah label manual disini karena sensor tidak mendukung library pengambilan waktu
				//label 1 Siang, label 0 Malam
				label=1.0;

				// temp in celsius
				System.out.println("Generate data berdasarkan sensing sensor: ");
				System.out.println(label + " " + "1:" + celsius + " " + "2:" + pressure + " " + "3:" + rh);
				//set data sekarang berdasarkan apa yang sensor generate
				System.out.println("Hasil dari prediksi LIBSVM: ");
				input=label + " " + "1:" + celsius + " " + "2:" + pressure + " " + "3:" + rh;
				
				// method predict dengan parameter value dari test data dan model header
				predict(input, model);
			} catch (Exception e) {
				System.out.println("SHT21 error");
			}
		}
	}
	


	public static void main(String[] args) throws Exception {
		new SvmPredictLinear().run();
	}

	/*
	 * Method predict dengan parameter value dari test data dan model header
	 */
	private static void predict(String input, svm_model model) {
		// mengambil value dari svm_type yang berada pada header model
		int svm_type = svm.svm_get_svm_type(model);
		// mengambil jumlah class dari svm_type yang berada pada header model
		int nr_class = svm.svm_get_nr_class(model);

		// inisialisasi label
		int[] labels = new int[nr_class];
		// mengambil label pada model
		svm.svm_get_labels(model, labels);

		// kode untuk mengambil label,index, dan value dari sebuah test_data
		String[] lines = StringUtils.split(input, "\n");
		for (int i = 0; i < lines.length; i++) {
			String line = input;
			if (line == null) {
				break;
			}
			String label = StringUtils.split(line, " ")[0];
			line = line.replace(label + " ", "").trim();
			double target = Double.parseDouble(label);
			String[] lineSplit = StringUtils.split(line, " ");
			svm_node[] x = new svm_node[lineSplit.length];
			for (int j = 0; j < lineSplit.length; j++) {
				String[] dataSplit = StringUtils.split(lineSplit[j], ":");// pemisahan berdasarkan string yang
																			// mengandung ":"
				x[j] = new svm_node();// inisialisasi node baru
				x[j].index = Integer.parseInt(dataSplit[0]);// untuk menampung index atribut
				x[j].value = Double.parseDouble(dataSplit[1]);// untuk menampung value atribut
			}

			double v;
			v = svm.svm_predict(model, x);// prediksi model dan masukan yang sudah di split isinya dari
			System.out.println(v);
		}
	}
}

class svm_parameter implements Cloneable {

	/* svm_type */
	public static final int C_SVC = 0;

	/* kernel_type */
	public static final int LINEAR = 0;

	public int svm_type;
	public int kernel_type;

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
}

class Kernel {

	private svm_node[][] x;
	static double dot(svm_node[] x, svm_node[] y) {
		double sum = 0;
		int xlen = x.length;
		int ylen = y.length;
		int i = 0;
		int j = 0;
		while (i < xlen && j < ylen) {
			if (x[i].index == y[j].index) {
				sum += x[i++].value * y[j++].value;
			} else {
				if (x[i].index > y[j].index) {
					++j;
				} else {
					++i;
				}
			}
		}
		return sum;
	}

	static double k_function(svm_node[] x, svm_node[] y, svm_parameter param) {
		switch (param.kernel_type) {
		case svm_parameter.LINEAR:
			return dot(x, y);
		default:
			return 0; // java
		}
	}
}

class svm {

	//
	// construct and solve various formulations
	//
	public static final int LIBSVM_VERSION = 323;

	// method untuk mengembalikan type svm dari model data
	public static int svm_get_svm_type(svm_model model) {
		return model.param.svm_type;
	}

	// method untuk mengembalikan jumlah class dari model data
	public static int svm_get_nr_class(svm_model model) {
		return model.nr_class;
	}

	// method untuk mengembalikan label dari model data
	public static void svm_get_labels(svm_model model, int[] label) {
		if (model.label != null) {
			for (int i = 0; i < model.nr_class; i++) {
				label[i] = model.label[i];
			}
		}
	}

	public static double svm_predict_values(svm_model model, svm_node[] x, double[] dec_values) {
		int i;
		
			int nr_class = model.nr_class;
			int l = model.l;

			double[] kvalue = new double[l];
			for (i = 0; i < l; i++) {
				kvalue[i] = Kernel.k_function(x, model.SV[i], model.param);
			}

			int[] start = new int[nr_class];
			start[0] = 0;
			for (i = 1; i < nr_class; i++) {
				start[i] = start[i - 1] + model.nSV[i - 1];
			}

			int[] vote = new int[nr_class];
			for (i = 0; i < nr_class; i++) {
				vote[i] = 0;
			}

			int p = 0;
			for (i = 0; i < nr_class; i++) {
				for (int j = i + 1; j < nr_class; j++) {
					double sum = 0;
					int si = start[i];
					int sj = start[j];
					int ci = model.nSV[i];
					int cj = model.nSV[j];

					int k;
					double[] coef1 = model.sv_coef[j - 1];
					double[] coef2 = model.sv_coef[i];
					for (k = 0; k < ci; k++) {
						sum += coef1[si + k] * kvalue[si + k];
					}
					for (k = 0; k < cj; k++) {
						sum += coef2[sj + k] * kvalue[sj + k];
					}
					sum -= model.rho[p];
					dec_values[p] = sum;

					if (dec_values[p] > 0) {
						++vote[i];
					} else {
						++vote[j];
					}
					p++;
				}
			}

			int vote_max_idx = 0;
			for (i = 1; i < nr_class; i++) {
				if (vote[i] > vote[vote_max_idx]) {
					vote_max_idx = i;
				}
			}

			return model.label[vote_max_idx];
		
	}

	public static double svm_predict(svm_model model, svm_node[] x) {
		int nr_class = model.nr_class;
		double[] dec_values;
		dec_values = new double[nr_class * (nr_class - 1) / 2];
		double pred_result = svm_predict_values(model, x, dec_values);
		return pred_result;
	}

	static final String svm_type_table[] = { "c_svc" };

	static final String kernel_type_table[] = { "linear" };

	// method untuk membaca header pada kelas model_data
	private static boolean read_model_header(svm_model model) {
		svm_parameter param = new svm_parameter();
		model.param = param;

		try {
			for (int x = 0; x < model_data.header.length; x++) {
				String cmd = model_data.header[x];
				String arg = cmd.substring(cmd.indexOf(' ') + 1);
				if (cmd.startsWith("svm_type")) {
					int i;
					for (i = 0; i < svm_type_table.length; i++) {
						if (arg.indexOf(svm_type_table[i]) != -1) {
							param.svm_type = i;
							break;
						}
					}
					if (i == svm_type_table.length) {
						System.err.print("unknown svm type.\n");
						return false;
					}
				} else if (cmd.startsWith("kernel_type")) {
					int i;
					for (i = 0; i < kernel_type_table.length; i++) {
						if (arg.indexOf(kernel_type_table[i]) != -1) {
							param.kernel_type = i;
							break;
						}
					}
					if (i == kernel_type_table.length) {
						System.err.print("unknown kernel function.\n");
						return false;
					}
				} else if (cmd.startsWith("nr_class")) {
					model.nr_class = Integer.parseInt(arg);
				} else if (cmd.startsWith("total_sv")) {
					model.l = Integer.parseInt(arg);
				} else if (cmd.startsWith("rho")) {
					int n = model.nr_class * (model.nr_class - 1) / 2;
					model.rho = new double[n];
					String value = StringUtils.split(cmd, " ")[1];
					for (int i = 0; i < n; i++) {
						model.rho[i] = Double.parseDouble(value);
					}
				} else if (cmd.startsWith("label")) {
					int n = model.nr_class;
					model.label = new int[n];
					String[] value = StringUtils.split(cmd, " ");
					for (int i = 0; i < n; i++) {
						model.label[i] = Integer.parseInt(value[i + 1]);
					}
				} else if (cmd.startsWith("nr_sv")) {
					int n = model.nr_class;
					model.nSV = new int[n];
					String[] value = StringUtils.split(cmd, " ");
					for (int i = 0; i < n; i++) {
						model.nSV[i] = Integer.parseInt(value[i + 1]);
					}
				} else if (cmd.startsWith("SV")) {
					break;
				}
			}

		} catch (Exception e) {
			return false;
		}
		return true;

	}

	// method untuk membaca label/coefisien dan value dari model_data
	public static svm_model svm_load_model() {

		// read parameters
		svm_model model = new svm_model();

		model_data md = new model_data();
		model.label = null;
		model.nSV = null;

		if (read_model_header(model) == false) {
			System.err.print("ERROR: failed to read model\n");
			return null;
		}

		// read sv_coef and SV
		int l = model.l + 1;
		model.SV = new svm_node[l][];
		// mengambil isi coefisien atau label dari satu baris model value
		model.sv_coef = model_data.sv_coef;
		int position = 0;
		for (int i = 0; i < model_data.sv_string.length; i++) {
			// split setiap index dan value dari model value
			String[] idx_values = StringUtils.split(model_data.sv_string[i], " ");
			int n = idx_values.length;
			model.SV[position] = new svm_node[n];
			for (int j = 0; j < idx_values.length; j++) {
				// split index dan value dalam 1 baris model value
				String[] idx_value = StringUtils.split(idx_values[j], ":");
				model.SV[position][j] = new svm_node();
				model.SV[position][j].index = Integer.parseInt(idx_value[0]);
				model.SV[position][j].value = Double.parseDouble(idx_value[1]);
			}
			position++;
		}
		return model;
	}
}

class svm_model {

	public svm_parameter param; // parameter
	public int nr_class; // number of classes, = 2 in regression/one class svm
	public int l; // total #SV
	public svm_node[][] SV; // SVs (SV[l])
	public double[][] sv_coef; // coefficients for SVs in decision functions (sv_coef[k-1][l])
	public double[] rho; // constants in decision functions (rho[k*(k-1)/2])

	// for classification only
	public int[] label; // label of each class (label[k])
	public int[] nSV; // number of SVs for each class (nSV[k])
	// nSV[0] + nSV[1] + ... + nSV[k-1] = l
};

class svm_node {
	public int index;
	public double value;
}
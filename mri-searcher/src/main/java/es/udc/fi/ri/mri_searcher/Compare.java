package es.udc.fi.ri.mri_searcher;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

public class Compare {
	
	public static double[] readCSV(String name){
		ArrayList<Double> valuesArray = new ArrayList<Double>();
		String line = null;
		int i = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(name))){
			line = br.readLine();
			while(line != null && !line.isEmpty()) {
				if(Character.isDigit(line.charAt(0)) || line.startsWith("Promedio")) {
					valuesArray.add(Double.parseDouble(line.split("\t")[1]));
				}
				line = br.readLine();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		double[] values = new double[valuesArray.size()];
		for(int j=0;i<valuesArray.size();i++){
	        values[j] = valuesArray.get(i);
	    }
		return values;
	}
	
	public static void main(String[] args) throws ParseException, IOException {
		String usage = "Compare [-results FILE1.csv FILE2.csv] [-test t|wilcoxon ALPHA]";
		String fileName1 = null;
		String fileName2 = null;
		String test = null;
		Double alpha = null;
		double pValue = 0d;
		double result = 0d;
		for(int i=0;i<args.length;i++) {
			switch(args[i]) {
			case "-results":
				fileName1 = args[++i];
				fileName2 = args[++i];
				break;
			case "-test":
				test = args[++i];
				alpha = Double.parseDouble(args[++i]);
				break;
			default:
		        throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}
		System.out.println("Compare \tdoc1: "+fileName1+"\t doc2: "+fileName2);
		System.out.println("\t\tTest ("+test+"), Alpha: "+alpha);
		
		if(fileName1 == null || fileName2 == null || test == null) {
			System.err.println("Usage: " + usage);
		    System.exit(1);
		}
		if(!fileName1.split(".test.")[1].equals(fileName2.split(".test.")[1])) {
	      System.err.println("Ambos ficheros deben estar construidos con los mismos parámetros.");
	      System.exit(1);
	    }
		
		double[] array1 = readCSV(MedlineUtils.generated+fileName1);
		double[] array2 = readCSV(MedlineUtils.generated+fileName2);
		if(test.equals("t")) {
			TTest ttest = new TTest();
			pValue = ttest.tTest(array1, array2);
			result =  ttest.pairedT(array1, array2);
		} else {
			WilcoxonSignedRankTest wilcoxon = new WilcoxonSignedRankTest();
			result = wilcoxon.wilcoxonSignedRank(array1, array2);
			pValue = wilcoxon.wilcoxonSignedRankTest(array1, array2, true);
		}
		System.out.println("P-Valor: "+pValue);
		System.out.println("Resultado de test: "+result);
		if(pValue>alpha) {
			System.out.println("El resultado del test no es significativo ("+pValue+" > "+alpha+")");
		} else {
			System.out.println("El resultado del test es significativo ("+pValue+" < "+alpha+")");
		}
	}
}
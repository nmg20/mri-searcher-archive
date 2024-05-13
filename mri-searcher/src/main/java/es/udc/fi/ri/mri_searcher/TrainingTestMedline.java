package es.udc.fi.ri.mri_searcher;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class TrainingTestMedline {
	
	//Función de entrennamiento: obtiene los valores de la métrica para cada valor de lambda
	public static Float train(int min, int max, int cut, IndexReader reader, IndexSearcher searcher, QueryParser parser, String metrica, String name) throws IOException {
		float metr, bestMetr = 0, bestLambda = 0;
//		MedlineUtils.setCabecera(1f, metrica,cut);
		for(int i=1;i<=9;i++) {
			searcher.setSimilarity(new LMJelinekMercerSimilarity(i/10f));
			MedlineUtils.setCabecera(i/10f, metrica,cut);
			metr = MedlineUtils.processQueries(min, max, cut, cut, reader, searcher, parser, metrica).get(metrica);
			MedlineUtils.printLines(MedlineUtils.getCSV());
			if(metr>bestMetr) {
				bestMetr = metr;
				bestLambda = i/10f;
			}
		}
		//Escribimos el fichero .csv del training
		MedlineUtils.writeToFile(name, MedlineUtils.getCSV());
		MedlineUtils.clearArrays();
		return bestLambda;
	}
	
	
	public static void main(String[] args) throws ParseException, IOException {
		String usage = "TrainingTestMedline [-evaljm int1-int2 int3-int4]/[-evaltfidf] [-cut N] [-metrica P/R/MAP]"
				+"[-indexin PATHNAME]";
		String model = null;
		String indexPath = null;
		Integer cut = null;
		String metrica = null;
		String trainingRange = null, testRange = null;
		String trainingFile = null, testFile = null;
		Integer minTest = 0, maxTest = 0;
		Integer minTraining = null, maxTraining = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-evaljm":
				if(model==null) {
					model = "jm";
					trainingRange = args[++i];
					minTraining = Integer.parseInt(trainingRange.split("-")[0]);
					maxTraining = Integer.parseInt(trainingRange.split("-")[1]);
					testRange = args[++i];
					minTest = Integer.parseInt(testRange.split("-")[0]);
					maxTest = Integer.parseInt(testRange.split("-")[1]);
				} else {
					throw new IllegalArgumentException("Los parámetros -evaljm y -evaltfidf son incompatibles\n");
				}
				break;
			case "-evaltfidf":
				if(model==null) {
					model = "tfidf";
					testRange = args[++i];
					minTest = Integer.parseInt(testRange.split("-")[0]);
					maxTest = Integer.parseInt(testRange.split("-")[1]);
				} else {
					throw new IllegalArgumentException("Los parámetros -evaljm y -evaltfidf son incompatibles\n");
				}
				break;
			case "-cut":
				cut = Integer.parseInt(args[++i]);
				break;
			case "-indexin":
				indexPath = args[++i];
				break;
			case "-metrica":
				metrica = args[++i].toUpperCase();
				break;
			default:
				throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}
		
		System.out.println("TrainingTestMedline \tmodel: "+model+", cut: "+cut+", index: "+indexPath+", métrica: "+metrica);
		System.out.println("\tQueries(Training): "+minTraining+"-"+maxTraining+"\tQueries(Test): "+minTest+"-"+maxTest);
		
		if(model==null || indexPath==null || cut==null || metrica==null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}
		
		trainingFile = "medline."+model+".training."+trainingRange+".test."+testRange+"."+metrica.toLowerCase()+cut+".training.csv";
		testFile = "medline."+model+".training."+trainingRange+".test."+testRange+"."+metrica.toLowerCase()+cut+".test.csv";
		
		IndexReader reader = null;
		Directory dir = null;
		IndexSearcher searcher = null;
		ComplexPhraseQueryParser parser;
		
		try {
			dir = FSDirectory.open(Paths.get(indexPath));
			reader = DirectoryReader.open(dir);

		} catch (CorruptIndexException e1) {
			System.out.println("Graceful message: exception " + e1);
			e1.printStackTrace();
		} catch (IOException e1) {
			System.out.println("Graceful message: exception " + e1);
			e1.printStackTrace();
		}
		
		parser = new ComplexPhraseQueryParser("contents", new StandardAnalyzer());
		float lambda = 0f;
		searcher = new IndexSearcher(reader);
		if(model.equals("jm")) {
			System.out.println("[TRAINING]");
//			linesToTrn.add("Query\tLambda\t"+metrica+"@"+cut+"\tCorte");
			lambda = train(minTraining, maxTraining, cut, reader, searcher, parser, metrica, trainingFile);
			System.out.println("---------------------------------");
			searcher.setSimilarity(new LMJelinekMercerSimilarity(lambda));
		} else {
			searcher.setSimilarity(new ClassicSimilarity());
		}
		System.out.println("[TESTING]");
		MedlineUtils.setCabecera(lambda, metrica, cut);
		MedlineUtils.processQueries(minTest, maxTest, cut, cut, reader, searcher, parser, metrica).get(metrica);
		MedlineUtils.printLines(MedlineUtils.getCSV());
		//Escribimos el fichero .csv del test
		MedlineUtils.writeToFile(testFile, MedlineUtils.getCSV());
		MedlineUtils.clearArrays();
		reader.close();
		dir.close();
	}
	
}

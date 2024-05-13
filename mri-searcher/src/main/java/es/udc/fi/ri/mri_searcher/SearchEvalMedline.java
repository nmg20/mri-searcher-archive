package es.udc.fi.ri.mri_searcher;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


public class SearchEvalMedline {
    
  public static void main(String[] args) throws ParseException, IOException {
    String usage = "SearchEvalMedline [-search jm lambda/tfidf] [-indexin PATHNAME] "
        +"[-cut N] [-top M] [-queries all/int1/int1-int2]";
    String model = null;
    String indexPath = null;
    Similarity similarity = null;
    Integer cut = null;
    Integer top = null;
    String queriesStr = null;
    Integer queriesMin = null;
    Integer queriesMax = null;
    Float  lambda = 0f;
    String outputTxt = null;
    String outputCSV = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
      case "-search":
        model = args[++i];
        if(model.equals("jm")) {
          lambda = Float.parseFloat(args[++i]);
          similarity = new LMJelinekMercerSimilarity(lambda);
        } 
        if(model.equals("tfidf")){
          similarity = new ClassicSimilarity();
        }
        break;
      case "-indexin":
        indexPath = args[++i];
        break;
      case "-cut":
        cut = Integer.parseInt(args[++i]);
        break;
      case "-top":
        top = Integer.parseInt(args[++i]);
        break;
      case "-queries":
        queriesStr = args[++i];
        if(queriesStr.equals("all")) {
          queriesMin = 1;
          queriesMax = -1;
        } else {
          if(queriesStr.contains("-")) {
            String[] ints = queriesStr.split("-");
            queriesMin = Integer.parseInt(ints[0]);
            queriesMax = Integer.parseInt(ints[1]);
          } else {
            queriesMin = Integer.parseInt(queriesStr);
            queriesMax = Integer.parseInt(queriesStr);
          }
        }
        break;
      default:
        throw new IllegalArgumentException("unknown parameter " + args[i]);
      }
    }

    if (similarity == null || indexPath == null || cut == null || top == null || queriesStr == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    if(model.equals("jm")) {
      outputTxt = "medline.jm."+top+".hits.lambda."+lambda.toString()+".q"+queriesStr+".txt";
      outputCSV = "medline.jm."+cut+".cut.lambda."+lambda.toString()+".q"+queriesStr+".csv";
    } else {
      outputTxt = "medline.tfidf."+top+".hits.q"+queriesStr+".txt";
      outputCSV = "medline.tfidf."+cut+".cut.q"+queriesStr+".csv";
    }
    
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
    
    searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);
    parser = new ComplexPhraseQueryParser("contents", new StandardAnalyzer());
    MedlineUtils.setCabecera(lambda, "ALL", cut);
    MedlineUtils.processQueries(queriesMin, queriesMax, cut, top, reader, searcher, parser, "ALL");
    //Escribimos los dos ficheros
    MedlineUtils.writeToFile(outputCSV, MedlineUtils.getCSV());
    MedlineUtils.printLines(MedlineUtils.getCSV());
    MedlineUtils.writeToFile(outputTxt, MedlineUtils.getTXT());
    MedlineUtils.printLines(MedlineUtils.getTXT());
    MedlineUtils.clearArrays();
    reader.close();
	dir.close();
  } 

}

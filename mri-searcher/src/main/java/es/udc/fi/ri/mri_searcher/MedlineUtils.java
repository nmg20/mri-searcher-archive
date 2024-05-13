package es.udc.fi.ri.mri_searcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

public class MedlineUtils {
		
	public static String generated = "C:/software/Workspace/generated/";
	private static String queriesPath = "C:/software/Workspace/MEDLINE/MED.QRY";
	private static String relevancesPath = "C:/software/Workspace/MEDLINE/MED.REL";
//	private static String generated = "/home/rainor/Escritorio/RI2/medline/indices/";
//	private static String queriesPath = "/home/rainor/Escritorio/RI2/medline/MED.QRY";
//  private static String relevancesPath = "/home/rainor/Escritorio/RI2/medline/MED.REL";

	private static ArrayList<String> linesTXT = new ArrayList<String>();
	private static ArrayList<String> linesCSV = new ArrayList<String>();
	
	public static ArrayList<String> getTXT(){
		return linesTXT;
	}
	
	public static ArrayList<String> getCSV(){
		return linesCSV;
	}
	
	public static void clearArrays() {
		linesTXT.clear();
		linesCSV.clear();
	}
  	
	public static HashMap<Integer, String> getDocs(String filePath) {
		String line;
		int doc = 1;
		boolean add = false;
		HashMap<Integer, String> docs = new HashMap<Integer,String>();
		String line_aux = "";
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))){		    
		    while ((line = br.readLine()) != null) {
		    	if(line.startsWith(".I")) {
		    		doc = Integer.parseInt(line.split(" ",0)[1]);
//		    		System.out.println("Doc: "+doc);
		    		add = false;
		    	} else {
		    		if(line.startsWith(".W")||line.startsWith(".")) {
		    			add = false;
		    		} else {
		    			if(add) {
		    				line_aux = line_aux+" "+line;
		    			} else {
		    				line_aux = line;
		    			}
//		    			System.out.println("\tContent: "+line_aux);
			    		docs.put(doc, line_aux.trim().replaceAll("\\s+", " ").replaceAll("[^a-zA-Z0-9\\.\\,]", " "));
			    		add = true;
		    		}
		    	}
		    }
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return docs;
	}
	
	public static HashMap<Integer, ArrayList<Integer>> getRels(){
		String line = null;
	    String[] lineSplit = null;
	    Integer query = null;
	    Integer lastQuery = 1;
	    HashMap<Integer, ArrayList<Integer>> rels = new HashMap<Integer, ArrayList<Integer>>();
	    ArrayList<Integer> lastDocs = new ArrayList<Integer>();
	    try (BufferedReader br = new BufferedReader(new FileReader(relevancesPath))){       
	        while ((line = br.readLine()) != null) {
	          lineSplit = line.split(" ");
	          query = Integer.parseInt(lineSplit[0]);
	          if(!query.equals(lastQuery)) {
	            rels.put(lastQuery, lastDocs);
	            lastDocs = new ArrayList<Integer>();
	          }
	          lastDocs.add(Integer.parseInt(lineSplit[2]));
	          lastQuery = query;
	        }
	        rels.put(lastQuery, lastDocs);
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return rels;  
	  }
	  
	  public static HashMap<Integer, String> getQueries() {
	    String line;
	    int query = 1;
	    boolean add = false;
	    HashMap<Integer, String> queries = new HashMap<Integer,String>();
	    String line_aux = null;
	    try (BufferedReader br = new BufferedReader(new FileReader(queriesPath))){        
	        while ((line = br.readLine()) != null) {
	          if(line.startsWith(".I")) {
	        query = Integer.parseInt(line.split(" ",0)[1]);
	        add = false;
	      } else {
	        if(line.startsWith(".W")) {
	          add = false;
	        } else {
	          if(line.startsWith(" ")) {
	            line = line.substring(1);
	          }
	          if(add) {
	            line_aux = line_aux+" "+line;
	          } else {
	            line_aux = line;
	          }
	          queries.put(query, line_aux.replaceAll("\s+", " "));
	              add = true;
	            }
	          }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return queries;
	  }
	  
	  public static void writeToFile(String name, ArrayList<String> lines)  throws IOException {
		    try (BufferedWriter wr = new BufferedWriter(new FileWriter(generated+name, StandardCharsets.UTF_8))){
		      for(String line : lines) {
		        wr.write(line);
	//		        System.out.println(line);
	        wr.newLine();
	      }
	    } catch (IOException e) {
	      System.out.println("Graceful message: exception " + e);
		      e.printStackTrace();
		    }
		}
	  
	  public static void setCabecera(float lambda, String metrica, int cut) {
		String cab = "Query\t";
	if(lambda>0f) {
		linesCSV.add("Lambda: "+lambda);
	}
	if(metrica.equals("P") || metrica.equals("ALL")) {
		cab = cab + "P@"+cut+"\t";
	}
	if(metrica.equals("R") || metrica.equals("ALL")) {
		cab = cab + "Recall@"+cut+"\t";
	}
	if(metrica.contains("AP") || metrica.equals("ALL")) {
		cab = cab + "AP@"+cut+"\t";
	}
	//		System.out.println(cab);
		linesCSV.add(cab);
	  }
	  
	  public static void printLines(ArrayList<String> lines) {
		  for(String line : lines) {
			  System.out.println(line);
		  }
	  }
	  
	  public static HashMap<String, Float> processQueries(Integer min, Integer max, Integer cut, Integer top, IndexReader reader, IndexSearcher searcher, QueryParser parser, String metrica) throws IOException{
		float pn=0, recall=0, apn=0;
		float mpn=0, mrecall=0, map=0;
		HashMap<Integer, String> queries = MedlineUtils.getQueries();
		HashMap<Integer, ArrayList<Integer>> relevances = MedlineUtils.getRels();
		ArrayList<Integer> docsRelevantes =  new ArrayList<Integer>();
		Query query = null;
		HashMap<String, Float> medias = new HashMap<String, Float>();
		String line = null;
		if(max.equals(-1)) {
			//Caso de todas las queries
			max = queries.size();
		}
		Float queryNum = ((float) max - (float) min)+1f;
		for(int i=min;i<=max; i++) {
			line = "";
			try {
				query = parser.parse(queries.get(i));
				linesTXT.add("QUERY "+i+": "+queries.get(i));
				line = i+"\t";
				docsRelevantes = relevances.get(i);
			} catch (org.apache.lucene.queryparser.classic.ParseException e) {
				e.printStackTrace();
			}
			TopDocs topDocs = null;
			try {
				topDocs = searcher.search(query, top);
			} catch (IOException e1) {
				System.out.println("Graceful message: exception " + e1);
				e1.printStackTrace();
			}
			linesTXT.add("\t" + topDocs.totalHits + " results for query \"" + queries.get(i) + "\" showing for the first " + top
			          + " documents the doc id, score and the content of the contents field");
			int relevantes = 0;
		      for (int j = 0; j < Math.min(top, topDocs.totalHits.value); j++) {
			      if(docsRelevantes.contains(topDocs.scoreDocs[j].doc+1)) {
		    		  relevantes++;
		    		  apn += (float) relevantes/ (float) (j+1); 
			        linesTXT.add(topDocs.scoreDocs[j].doc+1 + "[R] -- score: " + topDocs.scoreDocs[j].score + " -- "
				            + reader.document(topDocs.scoreDocs[j].doc).get("contents")+"\n");
			      } else {
			    	  linesTXT.add(topDocs.scoreDocs[j].doc+1 + "[N] -- score: " + topDocs.scoreDocs[j].score + " -- "
					            + reader.document(topDocs.scoreDocs[j].doc).get("contents")+"\n");
			      }
		      }
		      if(relevantes==0) {
		    	  queryNum--;
		      } else {
			      //Generamos las métricas
			      pn = (float)relevantes/(float)cut;
			      recall = (float)relevantes/(float)docsRelevantes.size();
			      apn = apn/(float)docsRelevantes.size();
			      linesTXT.add("\tP@"+cut+": "+pn+"\tRecall@"+cut+": "+recall+"\tAP@"+cut+": "+apn+"\n");
			      if(metrica.equals("P") || metrica.equals("ALL")) {
			    	  line = line + pn + "\t";
			      }
			      if(metrica.equals("R") || metrica.equals("ALL")) {
			    	  line = line + recall + "\t";
			      }
			      if(metrica.contains("AP") || metrica.equals("ALL")) {
			    	  line = line + apn + "\t";
			      }
			      //Promedios de las métricas
			      mpn += pn;
			      mrecall += recall;
			      map += apn;
			      linesCSV.add(line);
		      }
//		      System.out.println(line);
		      
		}
		mpn = mpn/queryNum;
		mrecall = mrecall/queryNum;
		map = map/queryNum;
		line = "Promedio: \t";
		if(metrica.equals("P") || metrica.equals("ALL")) {
			line = line + mpn + "\t";
	    }
	    if(metrica.equals("R") || metrica.equals("ALL")) {
	    	line = line + mrecall + "\t";
	    }
	    if(metrica.contains("AP") || metrica.equals("ALL")) {
	    	line = line + map + "\t";
	    }
	    linesCSV.add(line+"\n");
	    linesTXT.add("Promedios\t P@"+cut+": "+mpn+"\tRecall: "+mrecall+"\tMAP@"+cut+": "+map);
		medias.put("P", mpn);
		medias.put("R", mrecall);
		medias.put("MAP", map);
		return medias;
	}
	
}

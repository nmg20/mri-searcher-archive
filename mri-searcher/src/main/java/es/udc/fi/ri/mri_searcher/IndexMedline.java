package es.udc.fi.ri.mri_searcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.demo.knn.DemoEmbeddings;
import org.apache.lucene.demo.knn.KnnVectorDict;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;

public class IndexMedline implements AutoCloseable {
	static final String KNN_DICT = "knn-dict";
	public static HashMap<Integer, String> docs = new HashMap<Integer, String>();

	private final DemoEmbeddings demoEmbeddings;
	private final KnnVectorDict vectorDict;

	private IndexMedline(KnnVectorDict vectorDict) throws IOException {
		if (vectorDict != null) {
			this.vectorDict = vectorDict;
			demoEmbeddings = new DemoEmbeddings(vectorDict);
		} else {
			this.vectorDict = null;
			demoEmbeddings = null;
		}
	}

	public static void main(String[] args) throws Exception {
		String usage = "IndexMedline [-openmode APPEND/CREATE/CREATE_OR_APPEND] [-index INDEX] [-docs DOCS] [-indexingmodel jm lambda/tfidf]";
		String indexPath = null;
		String docsPath = null;
		String mode = null;
		String model = null;
		Similarity similarity = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-index":
				indexPath = args[++i];
				break;
			case "-docs":
				//Path a MED.ALL
				docsPath = args[++i];
				break;
			case "-openmode":
				mode = args[++i].toUpperCase();
				break;
			case "-indexingmodel":
				model = args[++i];
				if(model.equals("jm")) {
					similarity = new LMJelinekMercerSimilarity(Float.parseFloat(args[++i]));
				} else {
					similarity = new ClassicSimilarity();
				}
				break;
			default:
				throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}

		if (indexPath == null || mode == null || docsPath == null || model == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}

		final Path docDir = Paths.get(docsPath).toAbsolutePath();
		if (!Files.isReadable(docDir)) {
			System.out.println("Document directory '" + docDir.toAbsolutePath()
					+ "' does not exist or is not readable, please check the path");
			System.exit(1);
		}
		
		Date start = new Date();
		docs = MedlineUtils.getDocs(docsPath.toString());
		try {
			System.out.println("Indexing to directory '" + indexPath + "'...");

			Directory dir = FSDirectory.open(Paths.get(indexPath));
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			iwc.setSimilarity(similarity);
			iwc.setOpenMode(OpenMode.valueOf(mode));
			
			KnnVectorDict vectorDictInstance = null;
			long vectorDictSize = 0;

			try (IndexWriter writer = new IndexWriter(dir, iwc);
				IndexMedline indexFiles = new IndexMedline(vectorDictInstance)) {
				indexFiles.indexDocs(writer, docDir);
			} finally {
				IOUtils.close(vectorDictInstance);
			}

			Date end = new Date();
			try (IndexReader reader = DirectoryReader.open(dir)) {
				System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - start.getTime())
						+ " milliseconds");
				if (reader.numDocs() > 1033 && vectorDictSize < 1_000_000 && System.getProperty("smoketester") == null) {
					throw new RuntimeException(
							"Are you (ab)using the toy vector dictionary? See the package javadocs to understand why you got this exception.");
				}
			}
		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}
	}

	void indexDocs(final IndexWriter writer, Path path) throws IOException {
		for(HashMap.Entry<Integer, String> entry :
            docs.entrySet()) {
			indexDoc(writer, path, entry.getKey());
		}
	}

	void indexDoc(IndexWriter writer, Path file, Integer docID) throws IOException {
		Document doc = new Document();
		FieldType type = new FieldType();
		type.setTokenized(true);
		type.setStored(true);
		type.setOmitNorms(true);
		type.setStoreTermVectors(true);
		type.setStoreTermVectorOffsets(true);
		type.setStoreTermVectorPositions(true);
		type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		type.freeze();
		doc.add(new Field("DocIDMedline", docID.toString(), type));
		doc.add(new Field("contents", docs.get(docID), type));
		if (demoEmbeddings != null) {
			try (InputStream in = Files.newInputStream(file)) {
				float[] vector = demoEmbeddings
						.computeEmbedding(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
				doc.add(new KnnVectorField("contents-vector", vector, VectorSimilarityFunction.DOT_PRODUCT));
			}
		}
		if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
			System.out.println("adding " + file + " " + docID);
			writer.addDocument(doc);
		} else {
			System.out.println("updating " + file + " " + docID);
			writer.updateDocument(new Term("contents",docs.get(docID)),doc);
		}
	}

	@Override
	public void close() throws IOException {
		IOUtils.close(vectorDict);
	}
}

package edu.kaist.mrlab.modules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import edu.kaist.mrlab.data.OntoProperty;

public class DSGenerator {
	private Path inputPath;
	private ArrayList<Path> filePathList;
	private HashMap<String, Set<String>> tripleMap = new HashMap<>();
	private HashMap<String, Integer> propertyCount = new HashMap<>();

	private static String outputFilePath = "data/ds/kowiki-20170701-kbox_initial-wikilink-sample";
	private static String logFilePath = "data/ds/kowiki-20170701-kbox_initial.log";

	public void loadEntityPair(OntoProperty DBOProperty) throws Exception {

		String property = DBOProperty.toString();
		if (property.equals("classP")) {
			property = "class";
		} else if (property.equals("nativeP")) {
			property = "native";
		} else if (property.equals("runners_up")) {
			property = "runners-up";
		}

		File f = new File("data/sopair/dbpedia/" + property);
		if (!f.exists()) {
			return;
		}

		BufferedReader br = Files.newBufferedReader(Paths.get("data/sopair/dbpedia/" + property));
		String input = null;
		while ((input = br.readLine()) != null) {

			if (!tripleMap.containsKey(input)) {
				Set<String> set = new HashSet<String>();
				set.add(property.toString());
				tripleMap.put(input, set);
			} else {
				Set<String> arr = tripleMap.get(input);
				arr.add(property.toString());
				tripleMap.put(input, arr);
			}
		}
	}

	public void loadCorpus() throws Exception {
		this.filePathList = Files.walk(this.inputPath).filter(p -> Files.isRegularFile(p))
				.collect(Collectors.toCollection(ArrayList::new));
		System.out.println("done!");
		System.out.println("Number of file paths: " + filePathList.size());
	}

	public void collectAll() {
		try {
			Path inputPath;
			while ((inputPath = DSGenerator.this.extractInputPath()) != null) {
				BufferedReader br = Files.newBufferedReader(inputPath);

				String input = null;

				if (inputPath.toString().contains(".DS_Store")) {
					continue;
				}

				while ((input = br.readLine()) != null) {

					List<String> entitiesInSentence = new ArrayList<String>();

					int linkStartOffset = -1;
					int linkEndOffset = -1;
					int barOffset = -1;

					input = input.replaceAll("</link»>", "</link>");
					input = input.replaceAll("«<link>", "<link>");
					input = input.replaceAll("«link>", "<link>");
					input = input.replaceAll("</link»", "</link>");

					if (input.startsWith("- ")) {
						input = input.replaceFirst("- ", "");
					}

					linkStartOffset = input.indexOf("<link>", linkStartOffset);

					while (linkStartOffset > 0) {
						linkEndOffset = input.indexOf("</link>", linkStartOffset);
						barOffset = input.indexOf("|", linkStartOffset);
						String entity = null;
						if (barOffset > linkStartOffset && linkEndOffset > barOffset) {
							entity = input.substring(barOffset + 1, linkEndOffset);
							entitiesInSentence.add(entity.replaceAll(" ", "_"));
						}

						linkStartOffset = input.indexOf("<link>", linkStartOffset + 1);
					}

					Set<String> combination = getCombination(entitiesInSentence);

					Iterator<String> it = combination.iterator();
					while (it.hasNext()) {
						String entityPair = it.next();

						if (tripleMap.containsKey(entityPair)) {

							StringTokenizer st = new StringTokenizer(entityPair, "\t");
							String subject = st.nextToken().replaceAll("_", " ");
							String object = st.nextToken().replaceAll("_", " ");

							int target = input.indexOf("|" + subject + "</link>");
							if (target < 0) {
								appendContents(logFilePath, subject + "\t" + object + "\t" + input + "\n");
								continue;
							}
							int pre = input.lastIndexOf("<link>", target);

							String sbjStc = input.substring(0, pre);
							sbjStc += " << _sbj_ >> ";
							sbjStc += input.substring(target + subject.length() + 8, input.length());

							target = sbjStc.indexOf("|" + object + "</link>");

							if (target < 0) {
								appendContents(logFilePath, subject + "\t" + object + "\t" + input + "\n");
								continue;
							}

							pre = sbjStc.lastIndexOf("<link>", target);

							String sbjObjStc = sbjStc.substring(0, pre);
							sbjObjStc += " << _obj_ >> ";
							sbjObjStc += sbjStc.substring(target + object.length() + 8, sbjStc.length());

							String endStc = null;
							String temp = sbjObjStc;
							linkStartOffset = temp.indexOf("<link>", linkStartOffset);
							while (linkStartOffset > 0) {
								linkEndOffset = temp.indexOf("</link>", linkStartOffset);
								barOffset = temp.indexOf("|", linkStartOffset);
								String entity = null;
								if (barOffset > linkStartOffset && linkEndOffset > barOffset) {
									entity = temp.substring(barOffset + 1, linkEndOffset) + " ";
								} else {
									break;
								}

								endStc = temp.substring(0, linkStartOffset);
								endStc += " << " + entity.trim().replace(" ", "_") + " >> ";
								endStc += temp.substring(linkEndOffset + 7, temp.length());

								linkStartOffset = endStc.indexOf("<link>", linkStartOffset + 1);
								temp = endStc;
							}

							Set<String> properties = tripleMap.get(entityPair);
							for (String property : properties) {

								if (propertyCount.containsKey(property)) {
									int now = propertyCount.get(property);
									propertyCount.put(property, now + 1);
								} else {
									propertyCount.put(property, 1);
								}

								if (temp.contains("_sbj_") && temp.contains("_obj_")) {
									appendContents(outputFilePath, entityPair + "\t" + property + "\t" + temp + "\n");
								} else {
									System.out.println(entityPair + "\t" + property + "\t" + temp + "\n");
								}

							}
						}
					}
				}
				br.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void showStatistics() {

		Set<String> properties = propertyCount.keySet();
		for (String property : properties) {
			System.out.println(property + "\t" + propertyCount.get(property));
		}
	}

	public Set<String> getCombination(List<String> entitiesInSentence) {
		Set<String> combination = new HashSet<>();

		for (int i = 0; i < entitiesInSentence.size(); i++) {
			for (int j = 0; j < entitiesInSentence.size(); j++) {

				if (i == j) {
					continue;
				}

				String combi = entitiesInSentence.get(i) + "\t" + entitiesInSentence.get(j);
				String invConbi = entitiesInSentence.get(j) + "\t" + entitiesInSentence.get(i);
				combination.add(combi);
				combination.add(invConbi);

			}
		}

		return combination;
	}

	public static synchronized void appendContents(String sFileName, String sContent) {
		try {

			File oFile = new File(sFileName);
			if (!oFile.exists()) {
				oFile.createNewFile();
			}
			if (oFile.canWrite()) {
				BufferedWriter oWriter = new BufferedWriter(new FileWriter(sFileName, true));
				oWriter.write(sContent);
				oWriter.close();
			}

		} catch (IOException oException) {
			throw new IllegalArgumentException("Error appending/File cannot be written: \n" + sFileName);
		}
	}

	private synchronized Path extractInputPath() {
		if (this.filePathList.isEmpty()) {
			return null;
		} else {
			return this.filePathList.remove(this.filePathList.size() - 1);
		}
	}

	public DSGenerator setInputPath(Path inputPath) {
		this.inputPath = inputPath;
		return this;
	}

	public static void main(String[] ar) throws Exception {

		DSGenerator dsdg = new DSGenerator();

		File f = new File(outputFilePath);
		f.delete();

		f = new File(logFilePath);
		f.delete();

		dsdg.setInputPath(Paths.get("data/corpus/kowiki-20170701-pages-articles-divided-line/"));
		// dsdg.setInputPath(Paths.get("data/corpus/test/"));
		System.out.print("Loading entities...");
		for (OntoProperty property : OntoProperty.values()) {
			dsdg.loadEntityPair(property);
		}
		System.out.println("done!");

		System.out.print("Loading corpus list...");
		dsdg.loadCorpus();
		dsdg.collectAll();
		dsdg.showStatistics();

	}
}
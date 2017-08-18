package genepi.mut.steps;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.math.MathException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import genepi.hadoop.HdfsUtil;
import genepi.hadoop.common.WorkflowStep;
import genepi.io.FileUtil;
import genepi.io.text.LineReader;
import genepi.mut.align.AlignTool;
import genepi.mut.pileup.PileupTool;
import genepi.mut.sort.SortTool;
import genepi.mut.steps.DetectStepTest.DetectMock;
import genepi.mut.steps.DetectStepTest.PileupMock;
import genepi.mut.util.MultiallelicAnalyser;
import genepi.mut.util.QCMetric;
import genepi.mut.util.RawFileAnalyser;
import genepi.mut.util.TestCluster;
import genepi.mut.util.WorkflowTestContext;
import junit.framework.Assert;

public class PileupStepTest {

	public static final boolean VERBOSE = true;

	@BeforeClass
	public static void setUp() throws Exception {
		TestCluster.getInstance().start();
	}

	@AfterClass
	public static void tearDown() throws Exception {
		TestCluster.getInstance().stop();
	}

	@Test
	public void Pileup1000GBamTest() throws IOException {

		String inputFolder = "test-data/mtdna/bam/input";
		String archive = "test-data/mtdna/bam/reference/rcrs.tar.gz";
		String hdfsFolder = "input";
		String type = "bam";
		
		Set<Integer> expected = new HashSet<Integer>(Arrays.asList(1456,2746,3200,12410,14071,14569,15463,16093,16360,10394,1438,152,15326,15340,16519,263,4769,750,8592,8860));
		
		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(hdfsFolder, archive, type);

		PileupTool pileUp = new PileupMock("files");
		context.setOutput("rawHdfs", "rawHdfs");
		context.setOutput("rawLocal", "rawLocal");
		context.setOutput("variantsHdfs", "variantsHdfs");
		context.setOutput("variantsLocal", "variantsLocal");
		context.setOutput("baq", "true");
		
		boolean result = pileUp.run(context);
		assertTrue(result);

		LineReader reader = new LineReader("variantsLocal");
		HashSet<Integer> results = new HashSet<Integer>();
		
		//header
		reader.next();
		while(reader.next()){
			String[] splits = reader.get().split("\t");
			int pos = Integer.valueOf(splits[1]);
			results.add(pos);
		}
		
		assertEquals(true, results.equals(expected));
		
		reader = new LineReader("rawLocal");
	
		int i = 0;
		while(reader.next()){
			if(i<10){
			System.out.println(reader.get());
			}
			i++;
		}


	}
	
	
	@Test
	public void DetectPipelinemtDNAMixtureBAMTest() throws IOException {

		String inputFolder = "test-data/mtdna/mixtures/input";
		String archive = "test-data/mtdna/mixtures/reference/rcrs.tar.gz";
		String hdfsFolder = "input";
		String type = "bam";
		
		String refPath = "test-data/mtdna/mixtures/reference/rCRS.fasta";
		String sanger = "test-data/mtdna/mixtures/expected/sanger.txt";

		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(hdfsFolder, archive, type);

		PileupTool pileUp = new PileupMock("files");
		context.setOutput("rawHdfs", "rawHdfs");
		context.setOutput("rawLocal", "rawLocal");
		context.setOutput("variantsHdfs", "variantsHdfs");
		context.setOutput("variantsLocal", "variantsLocal");
		context.setOutput("baq", "true");
		
		boolean result = pileUp.run(context);
		assertTrue(result);

		double hetLevel = 0.01;

		RawFileAnalyser analyser = new RawFileAnalyser();

		try {
			ArrayList<QCMetric> list = analyser.analyseFile("rawLocal", refPath, sanger,
					hetLevel);

			assertTrue(list.size() == 1);

			for (QCMetric metric : list) {

				System.out.println(metric.getPrecision());
				System.out.println(metric.getSensitivity());
				System.out.println(metric.getSpecificity());

				assertEquals(100, metric.getPrecision(), 0);
				assertEquals(59.259, metric.getSensitivity(), 0.1);
				assertEquals(100, metric.getSpecificity(), 0);
			}
			assertEquals(true, result);
		} catch (MathException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	@Test
	public void DetectPipelineLPAMultiallelicBAMTestKIV2() throws IOException {

		String inputFolder = "test-data/lpa/lpa-exome-kiv2/input";
		String archive = "test-data/lpa/lpa-exome-kiv2/reference/kiv2_6.tar.gz";
		String hdfsFolder = "input";
		String type = "bam";

		File expected = new File("test-data/lpa/lpa-exome-kiv2/expected/expected.txt");
		File refPath = new File("test-data/lpa/lpa-exome-kiv2/reference/kiv2_6.fasta");

		
		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(hdfsFolder, archive, type);

		PileupTool pileUp = new PileupMock("files");
		context.setOutput("rawHdfs", "rawHdfs");
		context.setOutput("rawLocal", "rawLocal");
		context.setOutput("variantsHdfs", "variantsHdfs");
		context.setOutput("variantsLocal", "variantsLocal");
		context.setOutput("baq", "false");
		
		boolean result = pileUp.run(context);
		assertTrue(result);

		MultiallelicAnalyser analyser = new MultiallelicAnalyser();

		double hetLevel = 0.01;
		ArrayList<QCMetric> list = analyser.analyseFile("rawLocal", expected.getPath(), refPath.getPath(), hetLevel);

		assertTrue(list.size() == 1);

		for (QCMetric metric : list) {

			System.out.println("Precision: " + metric.getPrecision());
			System.out.println("Sensitivity " + metric.getSensitivity());
			System.out.println("Specificity " + metric.getSpecificity());
		}

	}

	class AlignnMock extends AlignTool {

		private String folder;

		public AlignnMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	class SortMock extends SortTool {

		private String folder;

		public SortMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	class PileupMock extends PileupTool {

		private String folder;

		public PileupMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	protected boolean run(WorkflowTestContext context, WorkflowStep step) {
		step.setup(context);
		return step.run(context);
	}

	protected WorkflowTestContext buildContext(String input, String archive, String type) {

		File file = new File("test-data/tmp");
		if (file.exists()) {
			FileUtil.deleteDirectory(file);
		}
		file.mkdirs();

		WorkflowTestContext context = new WorkflowTestContext();

		context.setInput("input", input);
		context.setInput("inType", type);
		context.setVerbose(VERBOSE);
		context.setInput("archive", archive);
		context.setOutput("mapQuality", "20");
		context.setOutput("baseQuality", "20");
		context.setOutput("alignQuality", "30");
		context.setOutput("statistics", "statistics");
		context.setOutput("baq", "true");

		return context;

	}

	private void importInputdata(String folder, String input) {
		System.out.println("Import Data:");
		String[] files = FileUtil.getFiles(folder, "*.*");
		for (String file : files) {
			String target = HdfsUtil.path(input, FileUtil.getFilename(file));
			System.out.println("  Import " + file + " to " + target);
			HdfsUtil.put(file, target);
		}
	}
}
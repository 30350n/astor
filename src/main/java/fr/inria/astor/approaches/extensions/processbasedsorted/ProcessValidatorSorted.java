package fr.inria.astor.approaches.extensions.processbasedsorted;

import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.TestCaseVariantValidationResult;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.stats.Stats.GeneralStatEnum;
import fr.inria.astor.core.validation.ProgramVariantValidator;
import fr.inria.astor.core.validation.results.TestCasesProgramValidationResult;
import fr.inria.astor.core.validation.results.TestResult;
import fr.inria.astor.util.Converters;
import org.apache.log4j.Logger;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import org.xml.sax.*;

import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;

/**
 * 
 * @author Matias Martinez
 *
 */
public class ProcessValidatorSorted extends ProgramVariantValidator {

	protected Logger log = Logger.getLogger(Thread.currentThread().getName());
	static List<coverageResult> coverageResults;
	static boolean coverageSorted = false;
	static List<String> sortedTestCases;

	/**
	 * Process-based validation Advantage: stability, memory consumption, CG
	 * activity Disadvantage: time.
	 * 
	 * @param mutatedVariant
	 * @return
	 */
	@Override
	public TestCaseVariantValidationResult validate(ProgramVariant mutatedVariant, ProjectRepairFacade projectFacade) {

		return this.validate(mutatedVariant, projectFacade,
				Boolean.valueOf(ConfigurationProperties.getProperty("forceExecuteRegression")));

	}

	/**
	 * Run the validation of the program variant in two steps: one the original
	 * failing test, the second the complete test suite (only in case the failing
	 * now passes)ProgramVariantValidator
	 * 
	 * @param mutatedVariant
	 * @param projectFacade
	 * @param forceExecuteRegression
	 * @return
	 */
	public TestCaseVariantValidationResult validate(ProgramVariant mutatedVariant, ProjectRepairFacade projectFacade,
			boolean forceExecuteRegression) {

		try {
			recordTimeStamp("validateStart");

			URL[] bc = createClassPath(mutatedVariant, projectFacade);

			LauncherJUnitProcess testProcessRunner = new LauncherJUnitProcess();
			String jvmPath = ConfigurationProperties.getProperty("jvm4testexecution");
			log.debug("-Running first validation");

			if (coverageResults == null) {
				// generate coverage cache

				recordTimeStamp("cachingStart");
				log.info("Caching coverage...");
				long coverageCacheStart = System.currentTimeMillis();

				try {
					coverageResults = new ArrayList<coverageResult>();

					ProgramVariant origin = mutatedVariant;
					while (origin.getParent() != null) {
						origin = origin.getParent();
						log.info("going up one level");
					}

					URL[] originclasspath = createClassPath(origin, projectFacade);

					for (String testCase : projectFacade.getProperties().getRegressionTestCases()) {

						log.info(testCase);
						long loopStart = System.currentTimeMillis();

						List<String> testcaseList = new ArrayList<String>();
						testcaseList.add(testCase);

						// execute test with attached jacoco agent

						long testStart = System.currentTimeMillis();
						TestResult allTestsResults = testProcessRunner.execute(jvmPath, originclasspath, testcaseList,
								ConfigurationProperties.getPropertyInt("tmax1"), true);
						long testEnd = System.currentTimeMillis();
						
						String classpath = projectFacade.getOutDirWithPrefix(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);
						testProcessRunner.getCoverageResults(jvmPath, ConfigurationProperties.getPropertyInt("tmax1") * 2, classpath);

						// read coverage results from xml file

						File file = new File(ConfigurationProperties.getProperty("location") + "/coverageResults.xml");

						DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
						DocumentBuilder db = dbf.newDocumentBuilder();
						db.setEntityResolver(new EntityResolver() {
							@Override
							public InputSource resolveEntity(String publicId, String systemId)
									throws SAXException, IOException {
								if (systemId.contains("report.dtd")) {
									return new InputSource(new StringReader(""));
								} else {
									return null;
								}
							}
						});

						Document document = db.parse(file);
						NodeList counters = document.getDocumentElement().getElementsByTagName("counter");

						for (int temp = counters.getLength() - 7; temp < counters.getLength(); temp++) {
							Node nNode = counters.item(temp);

							if (nNode.getNodeType() == Node.ELEMENT_NODE) {
								Element eElement = (Element) nNode;
								
								if (eElement.getAttribute("type").equals("INSTRUCTION")) {
									int covered = Integer.parseInt(eElement.getAttribute("covered"));
									int missed = Integer.parseInt(eElement.getAttribute("missed"));

									int sum = covered + missed;
									double coverage = (double) covered / sum;

									log.info("Coverage: " + String.valueOf(coverage) + " Time: " + Double.toString((testEnd - testStart) / 1000.0) + "s");
									coverageResults.add(new coverageResult(testCase, coverage, testEnd - testStart));
									break;
								}
							}
						}

						long loopEnd = System.currentTimeMillis();
						double loopTime = (loopEnd - loopStart) / 1000.0;
						double testTime = (testEnd - testStart) / 1000.0;

						log.debug("Overhead: " + Double.toString(loopTime - testTime) + "s (loop: " + Double.toString(loopTime) + "s test: " + Double.toString(testTime) + "s)");
					}
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}

				long coverageCacheEnd = System.currentTimeMillis();

				log.info("Finished generating cache in " + Long.toString(coverageCacheEnd - coverageCacheStart) + " seconds!");
				
				// sort test cases
				sortedTestCases = this.getsortedTestCases();

				recordTimeStamp("cachingEnd");
			}

			long t1 = System.currentTimeMillis();
			recordTimeStamp("failingTestCaseStart");
			TestResult trfailing = testProcessRunner.execute(jvmPath, bc,
					projectFacade.getProperties().getFailingTestCases(),
					ConfigurationProperties.getPropertyInt("tmax1"));
			recordTimeStamp("failingTestCaseEnd");
			long t2 = System.currentTimeMillis();

			if (trfailing == null) {
				log.debug("**The validation 1 have not finished well**");
				return null;
			}

			log.debug(trfailing);
			TestCaseVariantValidationResult r;
			if (trfailing.wasSuccessful() || forceExecuteRegression) {
				r = runRegression(mutatedVariant, projectFacade, bc);
			} else {
				 r = new TestCasesProgramValidationResult(trfailing,
						trfailing.wasSuccessful(), false);
			}

			recordTimeStamp("validateEnd");

			return r;

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public TestCaseVariantValidationResult runFailing(ProgramVariant mutatedVariant,
			ProjectRepairFacade projectFacade) {

		try {
			URL[] bc = createClassPath(mutatedVariant, projectFacade);

			LauncherJUnitProcess testProcessRunner = new LauncherJUnitProcess();
			String jvmPath = ConfigurationProperties.getProperty("jvm4testexecution");

			TestResult trfailing = testProcessRunner.execute(jvmPath, bc,
					projectFacade.getProperties().getFailingTestCases(),
					ConfigurationProperties.getPropertyInt("tmax1"));
			if (trfailing == null)
				return null;
			else {
				TestCaseVariantValidationResult validationResult = new TestCasesProgramValidationResult(trfailing,
						trfailing.wasSuccessful(), false);
				return validationResult;
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public TestCaseVariantValidationResult runRegression(ProgramVariant mutatedVariant,
			ProjectRepairFacade projectFacade) {
		try {
			URL[] bc = createClassPath(mutatedVariant, projectFacade);
			return this.runRegression(mutatedVariant, projectFacade, bc);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}

	}

	protected TestCaseVariantValidationResult runRegression(ProgramVariant mutatedVariant,
			ProjectRepairFacade projectFacade, URL[] bc) {
		
		LauncherJUnitProcess testProcessRunner = new LauncherJUnitProcess();
		return executeRegressionTestingOneByOne(mutatedVariant, bc, testProcessRunner, projectFacade);
	}

	protected URL[] createClassPath(ProgramVariant mutatedVariant, ProjectRepairFacade projectFacade)
			throws MalformedURLException {

		URL[] defaultSUTClasspath = projectFacade
				.getClassPathURLforProgramVariant(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);
		List<URL> originalURL = new ArrayList<>(Arrays.asList(defaultSUTClasspath));

		String classpath = System.getProperty("java.class.path");

		for (String path : classpath.split(File.pathSeparator)) {

			File f = new File(path);
			originalURL.add(new URL("file://" + f.getAbsolutePath()));

		}

		URL[] bc = null;
		if (mutatedVariant.getCompilation() != null) {

			File variantOutputFile = defineLocationOfCompiledCode(mutatedVariant, projectFacade);

			bc = Converters.redefineURL(variantOutputFile, originalURL.toArray(new URL[0]));
		} else {
			bc = originalURL.toArray(new URL[0]);
		}
		return bc;
	}

	protected File defineLocationOfCompiledCode(ProgramVariant mutatedVariant, ProjectRepairFacade projectFacade) {
		String bytecodeOutput = projectFacade.getOutDirWithPrefix(mutatedVariant.currentMutatorIdentifier());
		File variantOutputFile = new File(bytecodeOutput);

		MutationSupporter.currentSupporter.getOutput().saveByteCode(mutatedVariant.getCompilation(), variantOutputFile);
		return variantOutputFile;
	}

	protected TestCaseVariantValidationResult executeRegressionTestingOneByOne(ProgramVariant mutatedVariant, URL[] bc,
			LauncherJUnitProcess p, ProjectRepairFacade projectFacade) {

		log.debug("-Test Failing is passing, Executing regression, One by one");
		TestResult trregressionall = new TestResult();
		long t1 = System.currentTimeMillis();
		recordTimeStamp("regressionTestStart");

		for (String tc : sortedTestCases) {
			log.info("Running test: " + tc);
			List<String> parcial = new ArrayList<String>();
			parcial.add(tc);
			String jvmPath = ConfigurationProperties.getProperty("jvm4testexecution");

			TestResult singleTestResult = p.execute(jvmPath, bc, parcial,
					ConfigurationProperties.getPropertyInt("tmax2"));
			if (singleTestResult == null) {
				log.debug("The validation 2 have not finished well");
				return null;
			} else {
				trregressionall.getFailures().addAll(singleTestResult.getFailures());
				trregressionall.getSuccessTest().addAll(singleTestResult.getSuccessTest());
				trregressionall.failures += singleTestResult.failures;
				trregressionall.casesExecuted += singleTestResult.getCasesExecuted();

			}
			if (trregressionall.failures > 0) {
				log.info("Testcase Failed, aborting this variant");
				break;
			}
		}
		long t2 = System.currentTimeMillis();
		recordTimeStamp("regressionTestEnd");
		log.debug(trregressionall);
		return new TestCasesProgramValidationResult(trregressionall, trregressionall.wasSuccessful(),true);

	}

	protected List<String> getsortedTestCases() {

		if (!coverageSorted) {
			Collections.sort(coverageResults, new SortCoverage());
			coverageSorted = true;
		}

		List<String> cases = new ArrayList<>();
		for (coverageResult cr : coverageResults) {
			cases.add(cr.name);
		}

		return cases;

	}

	protected void recordTimeStamp(String comment) {
		try {
			File file = new File("./recording.txt");

			if (file.exists()) {
				FileOutputStream output = new FileOutputStream(file, true);
				String message = Long.toString(System.currentTimeMillis()) + " " + comment + "\n";
				output.write(message.getBytes());
				output.flush();
				output.close();
			}
			else {
				log.info("recording.txt does not exist");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class coverageResult {
	String name;
	Double coverage;
	Double time;

	public coverageResult(String name, double coverage, long time) {
		this.coverage = coverage;
		this.name = name;
		this.time = time / 1000.0;
	}
}

class SortCoverage implements Comparator<coverageResult> {
	public int compare(coverageResult a, coverageResult b) {
		return Double.compare(b.coverage, a.coverage);
	}
}

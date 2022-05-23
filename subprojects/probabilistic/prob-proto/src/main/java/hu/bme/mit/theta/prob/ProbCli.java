/*
 * Copyright 2021 Budapest University of Technology and Economics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hu.bme.mit.theta.prob;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Stopwatch;
import hu.bme.mit.theta.c.frontend.dsl.gen.CLexer;
import hu.bme.mit.theta.c.frontend.dsl.gen.CParser;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.config.CfaConfigBuilder;
import hu.bme.mit.theta.cfa.analysis.utils.CfaVisualizer;
import hu.bme.mit.theta.common.CliUtils;
import hu.bme.mit.theta.common.logging.ConsoleLogger;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter;
import hu.bme.mit.theta.core.stmt.HavocStmt;
import hu.bme.mit.theta.core.stmt.NonDetStmt;
import hu.bme.mit.theta.frontend.transformation.ArchitectureConfig;
import hu.bme.mit.theta.frontend.transformation.grammar.function.FunctionVisitor;
import hu.bme.mit.theta.frontend.transformation.model.statements.CProgram;
import hu.bme.mit.theta.frontend.transformation.model.statements.CStatement;
import hu.bme.mit.theta.prob.game.ThresholdType;
import hu.bme.mit.theta.prob.game.analysis.OptimType;
import hu.bme.mit.theta.prob.pcfa.ProbStmt;
import hu.bme.mit.theta.xcfa.model.XCFA;
import hu.bme.mit.theta.xcfa.model.XcfaLabel;
import hu.bme.mit.theta.xcfa.model.utils.FrontendXcfaBuilder;
import hu.bme.mit.theta.xcfa.passes.XcfaPassManager;
import hu.bme.mit.theta.xcfa.passes.procedurepass.*;
import hu.bme.mit.theta.xcfa.passes.processpass.AnalyzeCallGraph;
import hu.bme.mit.theta.xcfa.passes.processpass.FunctionInlining;
import hu.bme.mit.theta.xcfa.passes.xcfapass.RemoveUnusedGlobals;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.*;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class ProbCli {
	private static final String JAR_NAME = "theta-prob-cli.jar";
	private final String[] args;

	//////////// CONFIGURATION OPTIONS BEGIN ////////////

	//////////// input task ////////////

	@Parameter(names = "--input", description = "Path of the input C program", required = true)
	File model;

	@Parameter(names = "--arithmetic-type", description = "Arithmetic type to use when building")
	ArchitectureConfig.ArithmeticType arithmeticType = ArchitectureConfig.ArithmeticType.efficient;

	@Parameter(names = "--parse-only", description = "Do not run analysis, only parse program")
	boolean parseOnly = false;

	@Parameter(names = "--visualize-cfa", description = "Visualize CFA in dot format to the specified path")
	File cfaVis = null;

	@Parameter(names = "--version", description = "Display version", help = true)
	boolean versionInfo = false;

	@Parameter(names = "--loglevel", description = "Detailedness of logging")
	Logger.Level logLevel = Logger.Level.MAINSTEP;

	@Parameter(names = "--domain", description = "Abstract domain")
	CfaConfigBuilder.Domain domain = CfaConfigBuilder.Domain.PRED_BOOL;

	@Parameter(names = "--refinable-selection", description = "Algorithm for choosing a refinable state")
	RefinableSelection refinableSelection = RefinableSelection.NEAREST;

	@Parameter(names = "--precision-locality", description = "Separate precision for each location, or one global precision. Works only with predicate abstraction.")
	PrecisionLocality precisionLocality = PrecisionLocality.GLOBAL;

	@Parameter(names = "--predicate-propagation", description = "Method for propagating predicates to other locations if local precision is used.")
	PredicatePropagation predicatePropagation = PredicatePropagation.NONE;

	@Parameter(names = "--property-threshold", description = "")
	double propertyThreshold = 0.001;

	@Parameter(names = "--property-type", description = "")
    ThresholdType thresholdType = ThresholdType.LESS_THAN;

	@Parameter(names = "--optim-type", description = "")
	OptimType optimType = OptimType.MAX;

	@Parameter(names = "--lbe", description = "Whether to use large-block encoding")
	boolean lbe = false;

	@Parameter(names = "--prop", description = "Path of a property file")
	String prop = "";

	@Parameter(names = "--exact", description = "Whether to compute an approximation of the exact probability itself instead of verifying a threshold property")
	boolean exact = false;

	@Parameter(names = "--tolerance", description = "Tolerance of the \"exact\" computation")
	double tolerance = 1e-7;

	@Parameter(names = "--limit", description = "Enumeration limit when the explicit domain is used. Use 0 for unlimited enumeration.")
	int limit = 0;

	@Parameter(names = "--bvi", description = "Use BVI in the abstract model analysis instead of standard VI.")
	boolean useBvi = false;

	@Parameter(names = "--tvi", description = "Use BVI in the abstract model analysis instead of standard VI.")
	boolean useTvi = false;

	@Parameter(names = "--stats", description = "Output path for stats.")
	String statsPath = "";

	//////////// CONFIGURATION OPTIONS END ////////////

	private final Logger logger = new ConsoleLogger(logLevel);;

	private ProbCli(final String[] args) {
		this.args = args;
	}

	public static void main(final String[] args) {
		final ProbCli mainApp = new ProbCli(args);
		mainApp.run();
	}

	private void run() {
		/// Checking flags
		try {
			JCommander.newBuilder().addObject(this).programName(JAR_NAME).build().parse(args);
		} catch (final ParameterException ex) {
			System.out.println("Invalid parameters, details:");
			System.out.println(ex.getMessage());
			ex.usage();
			return;
		}

		/// version
		if (versionInfo) {
			CliUtils.printVersion(System.out);
			return;
		}

		// set arithmetic - if it is on efficient, the parsing will change it to either integer or bitvector
		ArchitectureConfig.arithmetic = arithmeticType;

		/// Starting frontend
		final Stopwatch sw = Stopwatch.createStarted();

		final CharStream input;
		XCFA.Builder xcfaBuilder = null;
		try {
			input = CharStreams.fromStream(new FileInputStream(model));
			final CLexer lexer = new CLexer(input);
			final CommonTokenStream tokens = new CommonTokenStream(lexer);
			final CParser parser = new CParser(tokens);
			final CParser.CompilationUnitContext context = parser.compilationUnit();

			CStatement program = context.accept(FunctionVisitor.instance);
			checkState(program instanceof CProgram, "Parsing did not return a program!");

			FrontendXcfaBuilder frontendXcfaBuilder = new FrontendXcfaBuilder();

			xcfaBuilder = frontendXcfaBuilder.buildXcfa((CProgram) program);
		} catch (Exception e) {
			e.printStackTrace();
			logger.write(Logger.Level.INFO, "Frontend failed!");
			System.exit(-80);
		}

		try {
			registerProbabilisticPasses();
			if(lbe)
				SimpleLbePass.level = SimpleLbePass.LBELevel.LBE_SEQ;
			else
				SimpleLbePass.level = SimpleLbePass.LBELevel.NO_LBE;
			XCFA xcfa = xcfaBuilder.build();
			CFA cfa = xcfa.createCFA();
			if(cfaVis != null) {
				final String s = GraphvizWriter.getInstance().writeString(CfaVisualizer.visualize(cfa));
				try(BufferedWriter bw = new BufferedWriter(new FileWriter(cfaVis))) {
					bw.write(s);
				}
			}
			logger.write(Logger.Level.INFO, "Parsing done at " + sw.elapsed().toMillis() + "ms");
			if (!parseOnly) {
				handleCfa(cfa);
				logger.write(Logger.Level.INFO, "Analysis done at " + sw.elapsed().toMillis() + "ms");
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
			logger.write(Logger.Level.INFO, "Analysis exited unsuccessfully at " + sw.elapsed().toMillis() + "ms");
		}
	}

	public static void registerProbabilisticPasses() {
		// Remove presets
		XcfaPassManager.clearXCFAPasses();
		XcfaPassManager.clearProcessPasses();
		XcfaPassManager.clearProcedurePasses();

		// Add global XCFA passes
		XcfaPassManager.addXcfaPass(new RemoveUnusedGlobals()); // removes unused global variables from the model

		// Add threadwise XCFA passes
		XcfaPassManager.addProcessPass(new AnalyzeCallGraph()); // add metadata to function calls, analyze recursivity, etc
		XcfaPassManager.addProcessPass(new FunctionInlining()); // inline functions that have definitions in the source

		// Add procedurewise XCFA passes
//		XcfaPassManager.addProcedurePass(new ReferenceToMemory());			// If pointers are needed, uncomment
//		XcfaPassManager.addProcedurePass(new InitMemory());					// If pointers are needed, uncomment

//		XcfaPassManager.addProcedurePass(new FpFunctionsToExprs());			// If floating point functions are needed, uncomment

		XcfaPassManager.addProcedurePass(new CallsToFinalLocs(
				List.of("abort", "exit"),
				List.of("reach_error")));									// map abort, exit and reach_error to final and error locations
		XcfaPassManager.addProcedurePass(new ProbabilisticMapper());		// map probabilistic functions to ProbStmts

//		XcfaPassManager.addProcedurePass(new CallsToHavocs());				// If traditional havocs (__VERIFIER_nondet_<type>()) are needed, uncomment [maps functions]
		XcfaPassManager.addProcedurePass(new AddHavocRange());				// If traditional havocs (__VERIFIER_nondet_<type>()) are needed, uncomment [adds range constraint]

		XcfaPassManager.addProcedurePass(new SimplifyExprs());				// Simplifies expressions
//		XcfaPassManager.addProcedurePass(new UnusedVarRemovalPass());		// Variables with no usages are removed
//		XcfaPassManager.addProcedurePass(new NoReadVarRemovalPass());		// Variables without a consumer are removed
		XcfaPassManager.addProcedurePass(new RemoveUnreachable());			// Remove paths that are unreachable

		XcfaPassManager.addProcedurePass(new SimplifyAssumptions());
		XcfaPassManager.addProcedurePass(new VerySimpleLbePass());
		XcfaPassManager.addProcedurePass(new EmptyEdgeRemovalPass());		// Removes empty edges by merging them
		XcfaPassManager.addProcedurePass(new SeparatorPass(
				(l) -> l instanceof XcfaLabel.StmtXcfaLabel &&
						(l.getStmt() instanceof HavocStmt || l.getStmt() instanceof NonDetStmt || l.getStmt() instanceof ProbStmt) //includes probstmt
				));
	}

	private void handleCfa(final CFA cfa) {
		if(!prop.isBlank()) {
			String read;
			try {
				try(BufferedReader bufferedReader = new BufferedReader(new FileReader(prop))) {
					read = bufferedReader.readLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			String[] split = read.split(" ");
			optimType = OptimType.valueOf(split[0]);
			switch (split[1]) {
				case "exact":
					exact = true;
					tolerance = Double.parseDouble(split[2]);
					break;
				default:
					thresholdType = ThresholdType.valueOf(split[1]);
					propertyThreshold = Double.parseDouble(split[2]);
					break;
			}
		}

		var config = new PCFAConfig(
				domain,
				refinableSelection,
				precisionLocality,
				predicatePropagation,
				thresholdType,
				propertyThreshold,
				optimType,
				lbe,
				exact,
				tolerance,
				limit,
				useBvi,
				useTvi,
				statsPath.isEmpty() ? () -> System.out : () -> {
					try {
						return new FileOutputStream(statsPath);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					return System.out;
				}
		);
		System.err.flush();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		ProbCheckerCLIKt.handlePCFA(cfa, config);
	}

}
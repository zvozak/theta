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
import hu.bme.mit.theta.cfa.analysis.utils.CfaVisualizer;
import hu.bme.mit.theta.common.CliUtils;
import hu.bme.mit.theta.common.logging.ConsoleLogger;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter;
import hu.bme.mit.theta.frontend.transformation.ArchitectureConfig;
import hu.bme.mit.theta.frontend.transformation.grammar.function.FunctionVisitor;
import hu.bme.mit.theta.frontend.transformation.model.statements.CProgram;
import hu.bme.mit.theta.frontend.transformation.model.statements.CStatement;
import hu.bme.mit.theta.xcfa.model.XCFA;
import hu.bme.mit.theta.xcfa.model.utils.FrontendXcfaBuilder;
import hu.bme.mit.theta.xcfa.passes.XcfaPassManager;
import hu.bme.mit.theta.xcfa.passes.procedurepass.CallsToFinalLocs;
import hu.bme.mit.theta.xcfa.passes.procedurepass.EmptyEdgeRemovalPass;
import hu.bme.mit.theta.xcfa.passes.procedurepass.RemoveDeadEnds;
import hu.bme.mit.theta.xcfa.passes.procedurepass.SimplifyExprs;
import hu.bme.mit.theta.xcfa.passes.processpass.AnalyzeCallGraph;
import hu.bme.mit.theta.xcfa.passes.processpass.FunctionInlining;
import hu.bme.mit.theta.xcfa.passes.xcfapass.RemoveUnusedGlobals;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

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
		} catch (final Throwable ex) {
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

		XcfaPassManager.addProcedurePass(new CallsToFinalLocs());			// map abort, exit and reach_error to final and error locations
		XcfaPassManager.addProcedurePass(new ProbabilisticMapper());		// map probabilistic functions to ProbStmts

//		XcfaPassManager.addProcedurePass(new CallsToHavocs());				// If traditional havocs (__VERIFIER_nondet_<type>()) are needed, uncomment [maps functions]
//		XcfaPassManager.addProcedurePass(new AddHavocRange());				// If traditional havocs (__VERIFIER_nondet_<type>()) are needed, uncomment [adds range constraint]

		XcfaPassManager.addProcedurePass(new SimplifyExprs());				// Simplifies expressions
//		XcfaPassManager.addProcedurePass(new UnusedVarRemovalPass());		// Variables with no usages are removed
//		XcfaPassManager.addProcedurePass(new NoReadVarRemovalPass());		// Variables without a consumer are removed
		XcfaPassManager.addProcedurePass(new RemoveUnreachable());			// Remove paths that are unreachable
		XcfaPassManager.addProcedurePass(new EmptyEdgeRemovalPass());		// Removes empty edges by merging them
	}

	private void handleCfa(final CFA cfa) {
	}

}
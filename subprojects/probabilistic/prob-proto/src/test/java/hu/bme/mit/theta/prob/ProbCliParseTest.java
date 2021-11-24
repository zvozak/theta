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

import hu.bme.mit.theta.c.frontend.dsl.gen.CLexer;
import hu.bme.mit.theta.c.frontend.dsl.gen.CParser;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.utils.CfaVisualizer;
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter;
import hu.bme.mit.theta.frontend.FrontendMetadata;
import hu.bme.mit.theta.frontend.transformation.ArchitectureConfig;
import hu.bme.mit.theta.frontend.transformation.grammar.function.FunctionVisitor;
import hu.bme.mit.theta.frontend.transformation.model.statements.CProgram;
import hu.bme.mit.theta.frontend.transformation.model.statements.CStatement;
import hu.bme.mit.theta.xcfa.model.XCFA;
import hu.bme.mit.theta.xcfa.model.utils.FrontendXcfaBuilder;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

@RunWith(Parameterized.class)
public class ProbCliParseTest {
	@Parameterized.Parameter(0)
	public File file;

	@Parameterized.Parameters()
	public static Collection<Object[]> data() throws URISyntaxException {
		final File path = new File(Objects.requireNonNull(ProbCliParseTest.class.getResource("/c")).toURI());
		return Arrays.stream(Objects.requireNonNull(path.listFiles((file, s) -> s.endsWith(".c")))).map(f -> new Object[]{f}).collect(Collectors.toList());
	}

	@Test
	public void test() throws IOException {
		ArchitectureConfig.arithmetic = ArchitectureConfig.ArithmeticType.efficient;
		ArchitectureConfig.multiThreading = false;
		FrontendMetadata.clear();

		// BEGIN TEST

		final CharStream input = CharStreams.fromStream(new FileInputStream(file));
		final CLexer lexer = new CLexer(input);
		final CommonTokenStream tokens = new CommonTokenStream(lexer);
		final CParser parser = new CParser(tokens);
		final CParser.CompilationUnitContext context = parser.compilationUnit();

		CStatement program = context.accept(FunctionVisitor.instance);
		checkState(program instanceof CProgram, "Parsing did not return a program!");

		FrontendXcfaBuilder frontendXcfaBuilder = new FrontendXcfaBuilder();

		XCFA.Builder xcfaBuilder = frontendXcfaBuilder.buildXcfa((CProgram) program);

		ProbCli.registerProbabilisticPasses();
		XCFA xcfa = xcfaBuilder.build();
		CFA cfa = xcfa.createCFA();

		// END TEST

		final String s = GraphvizWriter.getInstance().writeString(CfaVisualizer.visualize(cfa));
		System.err.println("#" + file.getName() + ":");
		System.err.println(s);
	}
}

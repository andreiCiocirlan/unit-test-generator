package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

@SpringBootApplication
public class TestGenerationApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestGenerationApplication.class, args);

		// Point this at any .java file you want to inspect.
		Path source = Path.of(
				"src/test/resources/fixtures/OrderService.java"
		);

		// Manual wiring — replace with however you construct these today.
		var classifier = new SpringTypeClassifier();
		var constructorResolver = new ConstructorResolver();
		StatementContextResolver contextResolver = new StatementContextResolver();
		var callAnalyzer = new JavaParserMethodCallAnalyzer(contextResolver);
		var conditionAnalyzer = new JavaParserConditionAnalyzer(callAnalyzer, contextResolver);
		var analyzer = new JavaParserAnalyzer(
				classifier,
				constructorResolver,
				callAnalyzer,
				conditionAnalyzer
		);

		ClassModel model = analyzer.analyze(source);

		System.out.println("Class : " + model.className());
		System.out.println("Type  : " + model.springType());
		System.out.println();

		System.out.println("Dependencies:");
		model.dependencies().forEach(d ->
				System.out.printf("  type=%-25s name=%-20s kind=%s%n",
						d.type(), d.name(), d.kind()));
		System.out.println();

		for (MethodModel method : model.methods()) {

			System.out.println("=== Method: " + method.name() + " ===");
			System.out.println("  returnType      : " + method.returnType());
			System.out.println("  annotations     : " + method.annotations());
			System.out.println("  declaredThrows  : " + method.declaredThrows());
			System.out.println("  parameters      : " + method.parameters());
			System.out.println();

			System.out.println("  --- Calls (" + method.methodCalls().size() + ") ---");
			for (MethodCallModel call : method.methodCalls()) {
				System.out.printf("    %-15s kind=%-11s target=%-15s targetType=%-18s ctx=%s%n",
						call.methodName(),
						call.kind(),
						quote(call.target()),
						quote(call.targetType()),
						shortCtx(call.context()));
			}
			System.out.println();

			System.out.println("  --- Conditions (" + method.conditions().size() + ") ---");
			for (ConditionModel c : method.conditions()) {
				System.out.printf("    expr=%s  calls=%s  throws=%s%n",
						c.expression(), c.methodCalls().size(), c.thrownExceptions());
			}
			System.out.println();

			System.out.println("  --- Returns (" + method.returns().size() + ") ---");
			for (ReturnModel r : method.returns()) {
				System.out.printf("    expr=%-40s ctx=%s%n",
						quote(r.expression()), shortCtx(r.context()));
			}
			System.out.println();

			System.out.println("  --- Throws (" + method.throwsStatements().size() + ") ---");
			for (ThrowModel t : method.throwsStatements()) {
				System.out.printf("    type=%-25s expr=%-40s ctx=%s%n",
						quote(t.exceptionType()), quote(t.expression()), shortCtx(t.context()));
			}
			System.out.println();

			System.out.println("  --- Assignments (" + method.assignments().size() + ") ---");
			for (AssignmentModel a : method.assignments()) {
				System.out.printf("    %s %s = %-30s ctx=%s%n",
						a.variableType(),
						a.variableName(),
						quote(a.expression()),
						shortCtx(a.context()));
			}
			System.out.println();

			System.out.println("  --- Tries (" + method.tries().size() + ") ---");
			for (TryModel t : method.tries()) {
				System.out.println("    Try body:");
				System.out.println("      calls : " + t.bodyCalls().size());
				System.out.println("      returns: " + t.bodyReturns().size());
				System.out.println("      throws : " + t.bodyThrows().size());
				for (CatchModel c : t.catches()) {
					System.out.printf("    catch (%s %s):%n",
							c.exceptionType(), c.variableName());
					System.out.println("      calls : " + c.methodCalls().size());
					System.out.println("      returns: " + c.returns().size());
					System.out.println("      throws : " + c.throwsStatements().size());
				}
			}
			System.out.println();
			System.out.println();
		}
	}

	private static String quote(String s) {
		return "\"" + (s == null ? "" : s) + "\"";
	}

	private static String shortCtx(StatementContext ctx) {
		return String.format(
				"{if=%s, try=%d, loop=%d, catch=%s}",
				ctx.ifConditions(),
				ctx.tryDepth(),
				ctx.loopDepth(),
				ctx.insideCatch() ? ctx.caughtExceptionType() : "-"
		);
	}

}

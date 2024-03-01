module antlr.annotations {
	requires java.compiler;

	exports org.antlr.v4.runtime.annotations;
	exports org.antlr.v4.runtime.processors;

	provides javax.annotation.processing.Processor with org.antlr.v4.runtime.processors.NullUsageProcessor;
}

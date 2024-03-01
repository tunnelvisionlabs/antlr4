module antlr.runtime {
	requires antlr.annotations;
	requires java.compiler;
	requires java.logging;

	exports org.antlr.v4.runtime;
	exports org.antlr.v4.runtime.atn;
	exports org.antlr.v4.runtime.dfa;
	exports org.antlr.v4.runtime.misc;
	exports org.antlr.v4.runtime.tree;
	exports org.antlr.v4.runtime.tree.pattern;
	exports org.antlr.v4.runtime.tree.xpath;
}

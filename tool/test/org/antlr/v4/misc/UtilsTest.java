package org.antlr.v4.misc;

import org.antlr.runtime.Token;
import org.antlr.v4.runtime.misc.Func1;
import org.antlr.v4.runtime.misc.Predicate;
import org.antlr.v4.tool.ast.GrammarAST;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class UtilsTest {

	@Test
	public void testStripFileExtension() {
		Assert.assertNull(Utils.stripFileExtension(null));
		Assert.assertEquals("foo", Utils.stripFileExtension("foo"));
		Assert.assertEquals("foo", Utils.stripFileExtension("foo.txt"));
	}

	@Test
	public void testJoin() {
		Assert.assertEquals("foobbar",
			Utils.join(new String[]{"foo", "bar"}, "b"));
		Assert.assertEquals("foo,bar",
			Utils.join(new String[]{"foo", "bar"}, ","));
	}

	@Test
	public void testSortLinesInString() {
		Assert.assertEquals("bar\nbaz\nfoo\n",
			Utils.sortLinesInString("foo\nbar\nbaz"));
	}

	@Test
	public void testNodesToStrings() {
		ArrayList<GrammarAST> values = new ArrayList<GrammarAST>();
		values.add(new GrammarAST(Token.EOR_TOKEN_TYPE));
		values.add(new GrammarAST(Token.DOWN));
		values.add(new GrammarAST(Token.UP));

		Assert.assertNull(Utils.nodesToStrings(null));
		Assert.assertNotNull(Utils.nodesToStrings(values));
	}

	@Test
	public void testCapitalize() {
		Assert.assertEquals("Foo", Utils.capitalize("foo"));
	}

	@Test
	public void testDecapitalize() {
		Assert.assertEquals("fOO", Utils.decapitalize("FOO"));
	}

	@Test
	public void testSelect() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("foo");
		strings.add("bar");

		Func1<String, String> func1 = new Func1<String, String>() {
			@Override
			public String eval(String arg1) {
				return "baz";
			}
		};

		ArrayList<String> retval = new ArrayList<String>();
		retval.add("baz");
		retval.add("baz");

		Assert.assertEquals(retval, Utils.select(strings, func1));
		Assert.assertNull(Utils.select(null, null));
	}

	@Test
	public void testFind() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("foo");
		strings.add("bar");
		Assert.assertEquals("foo", Utils.find(strings, String.class));

		Assert.assertNull(Utils.find(new ArrayList<Object>(), String.class));
	}

	@Test
	public void testIndexOf() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("foo");
		strings.add("bar");
		Predicate<Object> filter = new Predicate<Object>() {
			@Override
			public boolean eval(Object o) {
				return true;
			}
		};
		Assert.assertEquals(0, Utils.indexOf(strings, filter));
		Assert.assertEquals(-1, Utils.indexOf(new ArrayList<Object>(), null));
	}

	@Test
	public void testLastIndexOf() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("foo");
		strings.add("bar");
		Predicate<Object> filter = new Predicate<Object>() {
			@Override
			public boolean eval(Object o) {
				return true;
			}
		};
		Assert.assertEquals(1, Utils.lastIndexOf(strings, filter));
		Assert.assertEquals(-1, Utils.lastIndexOf(new ArrayList<Object>(), null));
	}

	@Test
	public void testSetSize() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("foo");
		strings.add("bar");
		strings.add("baz");
		Assert.assertEquals(3, strings.size());

		Utils.setSize(strings, 2);
		Assert.assertEquals(2, strings.size());

		Utils.setSize(strings, 4);
		Assert.assertEquals(4, strings.size());
	}
}

/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.test.tool;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.misc.Utils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import static org.junit.Assert.assertEquals;

public class TestCharStreams {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void fileEncodingShouldBeUTF8() {
		assertEquals("UTF-8", System.getProperty("file.encoding"));
	}

	@Test
	public void fromBMPStringHasExpectedSize() {
		CharStream s = CharStreams.fromString("hello");
		assertEquals(5, s.size());
		assertEquals(0, s.index());
		assertEquals("hello", s.toString());
	}

	@Test
	public void fromSMPStringHasExpectedSize() {
		CharStream s = CharStreams.fromString(
				"hello \uD83C\uDF0E");
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
	}

	@Test
	public void fromBMPUTF8PathHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		CharStream s = CharStreams.fromFile(p);
		assertEquals(5, s.size());
		assertEquals(0, s.index());
		assertEquals("hello", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}

	@Test
	public void fromSMPUTF8PathHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		CharStream s = CharStreams.fromFile(p);
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}

	@Test
	public void fromBMPUTF8InputStreamHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		InputStream is = new FileInputStream(p);
		try {
			CharStream s = CharStreams.fromStream(is);
			assertEquals(5, s.size());
			assertEquals(0, s.index());
			assertEquals("hello", s.toString());
		}
		finally {
			is.close();
		}
	}

	@Test
	public void fromSMPUTF8InputStreamHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		InputStream is = new FileInputStream(p);
		try {
			CharStream s = CharStreams.fromStream(is);
			assertEquals(7, s.size());
			assertEquals(0, s.index());
			assertEquals("hello \uD83C\uDF0E", s.toString());
		}
		finally {
			is.close();
		}
	}

	@Test
	public void fromBMPUTF8ChannelHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CharStream s = CharStreams.fromChannel(
					c, 4096, CodingErrorAction.REPLACE, "foo");
			assertEquals(5, s.size());
			assertEquals(0, s.index());
			assertEquals("hello", s.toString());
			assertEquals("foo", s.getSourceName());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void fromSMPUTF8ChannelHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CharStream s = CharStreams.fromChannel(
					c, 4096, CodingErrorAction.REPLACE, "foo");
			assertEquals(7, s.size());
			assertEquals(0, s.index());
			assertEquals("hello \uD83C\uDF0E", s.toString());
			assertEquals("foo", s.getSourceName());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void fromInvalidUTF8BytesChannelReplacesWithSubstCharInReplaceMode()
		throws Exception {
		File p = folder.newFile();
		byte[] toWrite = new byte[] { (byte)0xCA, (byte)0xFE, (byte)0xFE, (byte)0xED };
		Utils.writeFile(p, toWrite);
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CharStream s = CharStreams.fromChannel(
					c, 4096, CodingErrorAction.REPLACE, "foo");
			assertEquals(4, s.size());
			assertEquals(0, s.index());
			assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD", s.toString());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void fromInvalidUTF8BytesThrowsInReportMode() throws Exception {
		File p = folder.newFile();
		byte[] toWrite = new byte[] { (byte)0xCA, (byte)0xFE };
		Utils.writeFile(p, toWrite);
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			thrown.expect(CharacterCodingException.class);
			CharStreams.fromChannel(c, 4096, CodingErrorAction.REPORT, "foo");
		}
		finally {
			c.close();
		}
	}

	@Test
	public void fromSMPUTF8SequenceStraddlingBufferBoundary() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CharStream s = CharStreams.fromChannel(
					c,
					// Note this buffer size ensures the SMP code point
					// straddles the boundary of two buffers
					8,
					CodingErrorAction.REPLACE,
					"foo");
			assertEquals(7, s.size());
			assertEquals(0, s.index());
			assertEquals("hello \uD83C\uDF0E", s.toString());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void fromFileName() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		CharStream s = CharStreams.fromFileName(p.toString());
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
		assertEquals(p.toString(), s.getSourceName());

	}

	@Test
	public void fromFileNameWithLatin1() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \u00CA\u00FE".getBytes(Charset.forName("ISO-8859-1")));
		CharStream s = CharStreams.fromFileName(p.toString(), Charset.forName("ISO-8859-1"));
		assertEquals(8, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \u00CA\u00FE", s.toString());
		assertEquals(p.toString(), s.getSourceName());

	}

	@Test
	public void fromReader() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		Reader r = new InputStreamReader(new FileInputStream(p), Charset.forName("UTF-8"));
		try {
			CharStream s = CharStreams.fromReader(r);
			assertEquals(7, s.size());
			assertEquals(0, s.index());
			assertEquals("hello \uD83C\uDF0E", s.toString());
		}
		finally {
			r.close();
		}
	}

	@Test
	public void fromSMPUTF16LEPathSMPHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-16LE")));
		CharStream s = CharStreams.fromFile(p, Charset.forName("UTF-16LE"));
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}

	@Test
	public void fromSMPUTF32LEPathSMPHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-32LE")));
		CharStream s = CharStreams.fromFile(p, Charset.forName("UTF-32LE"));
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}
}

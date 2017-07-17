/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.test.tool;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.misc.Utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

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
	public void createWithBMPStringHasExpectedSize() {
		CodePointCharStream s = CharStreams.createWithString("hello");
		assertEquals(5, s.size());
		assertEquals(0, s.index());
		assertEquals("hello", s.toString());
	}

	@Test
	public void createWithSMPStringHasExpectedSize() {
		CodePointCharStream s = CharStreams.createWithString(
				"hello \uD83C\uDF0E");
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
	}

	@Test
	public void createWithBMPUTF8PathHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		CodePointCharStream s = CharStreams.createWithUTF8(p);
		assertEquals(5, s.size());
		assertEquals(0, s.index());
		assertEquals("hello", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}

	@Test
	public void createWithSMPUTF8PathHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		CodePointCharStream s = CharStreams.createWithUTF8(p);
		assertEquals(7, s.size());
		assertEquals(0, s.index());
		assertEquals("hello \uD83C\uDF0E", s.toString());
		assertEquals(p.toString(), s.getSourceName());
	}

	@Test
	public void createWithBMPUTF8InputStreamHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		InputStream is = new FileInputStream(p);
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Stream(is);
			assertEquals(5, s.size());
			assertEquals(0, s.index());
			assertEquals("hello", s.toString());
		}
		finally {
			is.close();
		}
	}

	@Test
	public void createWithSMPUTF8InputStreamHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		InputStream is = new FileInputStream(p);
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Stream(is);
			assertEquals(7, s.size());
			assertEquals(0, s.index());
			assertEquals("hello \uD83C\uDF0E", s.toString());
		}
		finally {
			is.close();
		}
	}

	@Test
	public void createWithBMPUTF8ChannelHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Channel(
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
	public void createWithSMPUTF8ChannelHasExpectedSize() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Channel(
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
	public void createWithInvalidUTF8BytesChannelReplacesWithSubstCharInReplaceMode()
		throws Exception {
		File p = folder.newFile();
		byte[] toWrite = new byte[] { (byte)0xCA, (byte)0xFE, (byte)0xFE, (byte)0xED };
		Utils.writeFile(p, toWrite);
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Channel(
					c, 4096, CodingErrorAction.REPLACE, "foo");
			assertEquals(3, s.size());
			assertEquals(0, s.index());
			assertEquals("\uFFFD\uFFFD\uFFFD", s.toString());
		}
		finally {
			c.close();
		}
	}

	@Test
	public void createWithInvalidUTF8BytesThrowsInReportMode() throws Exception {
		File p = folder.newFile();
		byte[] toWrite = new byte[] { (byte)0xCA, (byte)0xFE };
		Utils.writeFile(p, toWrite);
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			thrown.expect(CharacterCodingException.class);
			CharStreams.createWithUTF8Channel(c, 4096, CodingErrorAction.REPORT, "foo");
		}
		finally {
			c.close();
		}
	}

	@Test
	public void createWithSMPUTF8SequenceStraddlingBufferBoundary() throws Exception {
		File p = folder.newFile();
		Utils.writeFile(p, "hello \uD83C\uDF0E".getBytes(Charset.forName("UTF-8")));
		ReadableByteChannel c = Channels.newChannel(new FileInputStream(p));
		try {
			CodePointCharStream s = CharStreams.createWithUTF8Channel(
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
}

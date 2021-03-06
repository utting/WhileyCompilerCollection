// Copyright 2011 The Whiley Project Developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wybs.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.jar.Attributes;

import wyfs.lang.Path;
import wybs.util.AbstractCompilationUnit.Attribute;

/**
 * This exception is thrown when a syntax error occurs in the parser.
 *
 * @author David J. Pearce
 *
 */
public class SyntaxError extends RuntimeException {
	/**
	 * The file entry to which this error applies
	 */
	private Path.Entry<?> entry;

	/**
	 * The SyntacticElement to which this error refers
	 */
	private SyntacticItem element;

	/**
	 * Identify a syntax error at a particular point in a file.
	 *
	 * @param msg
	 *            Message detailing the problem.
	 * @param entry
	 *            The path entry for the compilation unit this error refers to
	 * @param element
	 *            The syntactic element to this error refers
	 */
	public SyntaxError(String msg, Path.Entry<?> entry, SyntacticItem element) {
		super(msg);
		this.entry = entry;
		this.element = element;
	}

	/**
	 * Identify a syntax error at a particular point in a file.
	 *
	 * @param msg
	 *            Message detailing the problem.
	 * @param entry
	 *            The path entry for the compilation unit this error refers to
	 * @param element
	 *            The syntactic element to this error refers
	 */
	public SyntaxError(String msg, Path.Entry<?> entry, SyntacticItem element, Throwable ex) {
		super(msg,ex);
		this.entry = entry;
		this.element = element;
	}

	/**
	 * Get the syntactic element to which this error is attached.
	 *
	 * @return
	 */
	public SyntacticItem getElement() {
		return element;
	}

	/**
	 * Get the syntactic entry to which this error refers
	 * @return
	 */
	public Path.Entry<?> getEntry() {
		return entry;
	}

	/**
	 * Output the syntax error to a given output stream in full form. In full
	 * form, contextual information from the originating source file is
	 * included.
	 */
	public void outputSourceError(PrintStream output) {
		outputSourceError(output,true);
	}

	/**
	 * Output the syntax error to a given output stream in either full or brief
	 * form. Brief form is intended to be used by 3rd party tools and is easier
	 * to parse. In full form, contextual information from the originating
	 * source file is included.
	 */
	public void outputSourceError(PrintStream output, boolean brief) {
		Attribute.Span span;
		if (entry == null || element == null) {
			output.println("syntax error: " + getMessage());
			return;
		} else if(element instanceof Attribute.Span) {
			span = (Attribute.Span) element;
		} else  {
			SyntacticHeap parent = element.getHeap();
			span = parent.getParent(element,Attribute.Span.class);
			if(span == null) {
				// FIXME: This is a terrible hack. Basically, we attempt to convert from the
				// old-style attributes to the new style spans.
				wybs.lang.Attribute.Source src = element.attribute(wybs.lang.Attribute.Source.class);
				if(src != null) {
					span = new Attribute.Span(null, src.start, src.end);
				}
			}
		}
		//
		EnclosingLine enclosing = (span == null) ? null : readEnclosingLine(entry, span);
		if(enclosing == null) {
			output.println("syntax error: " + getMessage());
		} else if(brief) {
			printBriefError(output,entry,enclosing,getMessage());
		} else {
			printFullError(output,entry,enclosing,getMessage());
		}
	}

	private void printBriefError(PrintStream output, Path.Entry<?> entry, EnclosingLine enclosing, String message) {
		output.print(entry.location() + ":" + enclosing.lineNumber + ":"
				+ enclosing.columnStart() + ":"
				+ enclosing.columnEnd() + ":\""
				+ escapeMessage(message) + "\"");

		// Now print contextual information (if applicable)
//		if(context != null && context.length > 0) {
//			output.print(":");
//			boolean firstTime=true;
//			for(Attribute.Origin o : context) {
//				if(!firstTime) {
//					output.print(",");
//				}
//				firstTime=false;
//				enclosing = readEnclosingLine(o.filename, o.start, o.end);
//				output.print(filename + ":" + enclosing.lineNumber + ":"
//						+ enclosing.columnStart() + ":"
//						+ enclosing.columnEnd());
//			}
//		}

		// Done
		output.println();
	}

	private void printFullError(PrintStream output, Path.Entry<?> entry, EnclosingLine enclosing, String message) {

		output.println(entry.location() + ":" + enclosing.lineNumber + ": " + message);

		printLineHighlight(output,enclosing);

		// Now print contextual information (if applicable)
//		if(context != null && context.length > 0) {
//			for(Attribute.Origin o : context) {
//				output.println();
//				enclosing = readEnclosingLine(o.filename, o.start, o.end);
//				output.println(o.filename + ":" + enclosing.lineNumber + " (context)");
//				printLineHighlight(output,enclosing);
//			}
//		}
	}

	private void printLineHighlight(PrintStream output,
			EnclosingLine enclosing) {
		// NOTE: in the following lines I don't print characters
		// individually. The reason for this is that it messes up the
		// ANT task output.
		String str = enclosing.lineText;

		if (str.length() > 0 && str.charAt(str.length() - 1) == '\n') {
			output.print(str);
		} else {
			// this must be the very last line of output and, in this
			// particular case, there is no new-line character provided.
			// Therefore, we need to provide one ourselves!
			output.println(str);
		}
		str = "";
		for (int i = 0; i < enclosing.columnStart(); ++i) {
			if (enclosing.lineText.charAt(i) == '\t') {
				str += "\t";
			} else {
				str += " ";
			}
		}
		for (int i = enclosing.columnStart(); i <= enclosing.columnEnd(); ++i) {
			str += "^";
		}
		output.println(str);
	}

	private static int parseLine(StringBuilder buf, int index) {
		while (index < buf.length() && buf.charAt(index) != '\n') {
			index++;
		}
		return index + 1;
	}

	private static class EnclosingLine {
		private int lineNumber;
		private int start;
		private int end;
		private int lineStart;
		private int lineEnd;
		private String lineText;

		public EnclosingLine(int start, int end, int lineNumber, int lineStart, int lineEnd, String lineText) {
			this.start = start;
			this.end = end;
			this.lineNumber = lineNumber;
			this.lineStart = lineStart;
			this.lineEnd = lineEnd;
			this.lineText = lineText;
		}

		public int columnStart() {
			return start - lineStart;
		}

		public int columnEnd() {
			return Math.min(end, lineEnd) - lineStart;
		}
	}

	private static EnclosingLine readEnclosingLine(Path.Entry<?> entry, Attribute.Span location) {
		int spanStart = location.getStart().get().intValue();
		int spanEnd = location.getEnd().get().intValue();
		int line = 0;
		int lineStart = 0;
		int lineEnd = 0;
		StringBuilder text = new StringBuilder();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(entry.inputStream(), "UTF-8"));

			// first, read whole file
			int len = 0;
			char[] buf = new char[1024];
			while ((len = in.read(buf)) != -1) {
				text.append(buf, 0, len);
			}

			while (lineEnd < text.length() && lineEnd <= spanStart) {
				lineStart = lineEnd;
				lineEnd = parseLine(text, lineEnd);
				line = line + 1;
			}
		} catch (IOException e) {
			return null;
		}
		lineEnd = Math.min(lineEnd, text.length());

		return new EnclosingLine(spanStart, spanEnd, line, lineStart, lineEnd,
				text.substring(lineStart, lineEnd));
	}

	public static final long serialVersionUID = 1l;

	/**
	 * An internal failure is a special form of syntax error which indicates
	 * something went wrong whilst processing some piece of syntax. In other
	 * words, is an internal error in the compiler, rather than a mistake in the
	 * input program.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class InternalFailure extends SyntaxError {
		public InternalFailure(String msg, Path.Entry<? extends CompilationUnit> entry, SyntacticItem element) {
			super(msg, entry, element);
		}

		public InternalFailure(String msg, Path.Entry<? extends CompilationUnit> entry, SyntacticItem element,
				Throwable ex) {
			super(msg, entry, element, ex);
		}

		@Override
		public String getMessage() {
			String msg = super.getMessage();
			if (msg == null || msg.equals("")) {
				return "internal failure";
			} else {
				return "internal failure, " + msg;
			}
		}
	}

	private static String escapeMessage(String message) {
		message = message.replace("\n", "\\n");
		message = message.replace("\"", "\\\"");
		return message;
	}

}

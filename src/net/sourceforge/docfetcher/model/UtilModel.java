/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.docfetcher.base.Util;
import net.sourceforge.docfetcher.base.annotations.ImmutableCopy;
import net.sourceforge.docfetcher.base.annotations.NotNull;
import net.sourceforge.docfetcher.base.annotations.Nullable;
import net.sourceforge.docfetcher.base.annotations.RecursiveMethod;
import net.sourceforge.docfetcher.base.annotations.VisibleForPackageGroup;
import net.sourceforge.docfetcher.model.index.DiskSpaceException;
import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.model.index.IndexingException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;

import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;

/**
 * @author Tran Nam Quang
 */
@VisibleForPackageGroup
public final class UtilModel {

	private UtilModel() {
	}

	// TODO Check: Can any of these methods be replaced by more efficient
	// Guava or Commons Lang 3 functionality?

	// TODO Check: Are any of these methods only used locally? If so,
	// move them to the call site.

	/**
	 * Returns an immutable list containing the values of the given map, or an
	 * immutable empty list if null was given.
	 */
	@ImmutableCopy
	@NotNull
	static <K, V> List<V> nullSafeImmutableList(@Nullable Map<K, V> map) {
		if (map == null || map.isEmpty())
			return Collections.emptyList();
		return ImmutableList.copyOf(map.values());
	}

	/**
	 * Returns an immutable copy of the given map, or an immutable empty map if
	 * null was given.
	 */
	@ImmutableCopy
	@NotNull
	static <K, V> Map<K, V> nullSafeImmutableMap(@Nullable Map<K, V> map) {
		if (map == null || map.isEmpty())
			return Collections.emptyMap();
		return ImmutableMap.copyOf(map);
	}
	
	private static Pattern drivePattern = Pattern.compile("([a-zA-Z]):.*");

	/**
	 * Returns the drive letter at the beginning of the given Windows path, or
	 * null if the path doesn't start with a drive letter.
	 * <p>
	 * Example: For "C:\Windows" this method returns "C".
	 */
	@VisibleForPackageGroup
	public static String getDriveLetter(String path) {
		Matcher m = drivePattern.matcher(path);
		if (m.matches())
			return m.group(1);
		return null;
	}

	/**
	 * Returns a relative path that allows navigation from the absolute source
	 * path {@code srcPath} to the absolute destination path {@code dstPath}.
	 * Example outputs:
	 * 
	 * <pre>
	 * ../..
	 * ../../path/to/adjacent/file
	 * path/to/subfolder
	 * </pre>
	 * 
	 * The returned paths always use "/" as path separator, and there are no
	 * leading or trailing path separators.
	 */
	@NotNull
	@VisibleForPackageGroup
	public static String getRelativePath(	@NotNull String srcPath,
											@NotNull String dstPath) {
		Iterator<String> srcIt = Util.splitPath(srcPath).iterator();
		Iterator<String> dstIt = Util.splitPath(dstPath).iterator();
		List<String> srcOverhang = new ArrayList<String>();
		List<String> dstOverhang = new ArrayList<String>();
		List<String> dstList = new ArrayList<String>();
		boolean stillEqual = true;
		for (int i = 0;; i++) {
			String srcPart = null;
			String dstPart = null;
			if (srcIt.hasNext())
				srcPart = srcIt.next();
			if (dstIt.hasNext()) {
				dstPart = dstIt.next();
				dstList.add(dstPart);
			}
			if (srcPart == null && dstPart == null)
				break;
			if (stillEqual && !Objects.equal(srcPart, dstPart))
				stillEqual = false;
			if (stillEqual)
				continue;
			if (srcPart != null)
				srcOverhang.add("..");
			if (dstPart != null)
				dstOverhang.add(dstPart);
		}
		String path = null;
		if (srcOverhang.isEmpty()) {
			if (dstOverhang.isEmpty())
				path = Util.joinPath(dstList);
			else
				path = Util.joinPath(dstOverhang);
		} else if (dstOverhang.isEmpty())
			path = Util.joinPath(srcOverhang);
		else
			path = Util.joinPath(srcOverhang) + "/"
					+ Util.joinPath(dstOverhang);
		return path;
	}

	/**
	 * Returns a relative path that allows navigation from the absolute path of
	 * the source file {@code srcFile} to the absolute path of the destination
	 * file {@code dstFile}. Example outputs:
	 * 
	 * <pre>
	 * ../..
	 * ../../path/to/adjacent/file
	 * path/to/subfolder
	 * </pre>
	 * 
	 * The returned paths always use "/" as path separator, and there are no
	 * leading or trailing path separators.
	 */
	@NotNull
	@VisibleForPackageGroup
	public static String getRelativePath(	@NotNull File srcFile,
											@NotNull File dstFile) {
		return getRelativePath(
				Util.getSystemAbsPath(srcFile),
				Util.getSystemAbsPath(dstFile)
		);
	}
	
	@VisibleForPackageGroup
	public static void assertDocCount(	Directory luceneDir,
										int expectedCount) throws Exception {
		IndexReader reader = IndexReader.open(luceneDir);
		assertEquals(expectedCount, reader.numDocs());
		Closeables.closeQuietly(reader);
	}

	@VisibleForPackageGroup
	public static void assertResultCount(	Directory luceneDir,
											String query,
											int expectedCount) throws Exception {
		IndexSearcher searcher = new IndexSearcher(luceneDir, true);
		QueryParser parser = new QueryParser(
				IndexRegistry.LUCENE_VERSION,
				Fields.CONTENT.key(),
				IndexRegistry.analyzer
		);
		Query queryObject = parser.parse(query);
		TopDocs topDocs = searcher.search(queryObject, Integer.MAX_VALUE);
		assertEquals(expectedCount, topDocs.totalHits);
		Closeables.closeQuietly(searcher);
	}

	/**
	 * Returns true if the given TrueZIP file is a zip archive, false otherwise.
	 */
	@VisibleForPackageGroup
	public static boolean isZipArchive(@NotNull TFile file) {
		/*
		 * TODO later: Replace this hack if a future TrueZIP version ever
		 * provides a way to distinguish between directories and zip archives.
		 */
		if (file.isFile())
			return false;
		if (! file.isEntry())
			return new java.io.File(file.getPath()).isFile();
		return new TFile(
				Util.getParentFile(file),
				file.getName(),
				TArchiveDetector.NULL
		).isFile();
	}

	/**
	 * Tries to unpack the given zip archive entry. Returns null if the given
	 * file is not a zip archive entry.
	 */
	@Nullable
	@VisibleForPackageGroup
	public static File maybeUnpackZipEntry(	@NotNull IndexingConfig config,
											@NotNull File file)
			throws DiskSpaceException, IndexingException {
		if (! (file instanceof TFile))
			return null;
		TFile tzFile = (TFile) file;
		if (! tzFile.isEntry())
			return null;
		long requiredSpace = tzFile.length();
		config.checkDiskSpaceInTempDir(requiredSpace);
		File unpackedFile = config.createDerivedTempFile(file.getName());
		try {
			tzFile.cp(unpackedFile);
			return unpackedFile;
		} catch (IOException e) {
			throw new IndexingException(e);
		}
	}
	
	@NotNull
	@RecursiveMethod
	@VisibleForPackageGroup
	public static String[] splitAtExisting(	@NotNull String left,
											@NotNull String right)
			throws FileNotFoundException {
		if (new File(left).isFile())
			return new String[] { left, right };
		
		// Reached left end of a relative path
		if (!left.contains("/") && !left.contains("\\"))
			throw new FileNotFoundException();
		
		String[] leftParts = Util.splitPathLast(left);
		
		// Reached Unix root
		if (leftParts[0].length() == 0)
			throw new FileNotFoundException();
		
		// Reached Windows root
		if (leftParts[0].matches("[a-zA-Z]:"))
			throw new FileNotFoundException();
		
		// Move by one path part to the left and recurse
		if (right.length() == 0)
			return splitAtExisting(leftParts[0], leftParts[1]);
		String newRight = Util.joinPath(leftParts[1], right);
		return splitAtExisting(leftParts[0], newRight);
	}

	// TODO also check preceding slashes?
	public static boolean noTrailingSlash(@NotNull String path) {
		int length = path.length();
		if (length == 0) return true;
		char lastChar = path.charAt(length - 1);
		return lastChar != '/' && lastChar != '\\';
	}
	
}
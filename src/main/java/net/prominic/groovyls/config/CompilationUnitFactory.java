////////////////////////////////////////////////////////////////////////////////
// Copyright 2022 Prominic.NET, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License
//
// Author: Prominic.NET, Inc.
// No warranty of merchantability or fitness of any kind.
// Use this software at your own risk.
////////////////////////////////////////////////////////////////////////////////
package net.prominic.groovyls.config;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

import groovy.lang.GroovyClassLoader;
import net.prominic.groovyls.compiler.control.GroovyLSCompilationUnit;
import net.prominic.groovyls.compiler.control.io.StringReaderSourceWithURI;
import net.prominic.groovyls.util.FileContentsTracker;

public class CompilationUnitFactory implements ICompilationUnitFactory {
	private static final String FILE_EXTENSION_GROOVY = ".groovy";

	private GroovyLSCompilationUnit compilationUnit;
	private CompilerConfiguration config;
	private GroovyClassLoader classLoader;
	private List<String> additionalClasspathList;

	public CompilationUnitFactory() {
	}

	public List<String> getAdditionalClasspathList() {
		return additionalClasspathList;
	}

	public void setAdditionalClasspathList(List<String> additionalClasspathList) {
		this.additionalClasspathList = additionalClasspathList;
		invalidateCompilationUnit();
	}

	public void invalidateCompilationUnit() {
		compilationUnit = null;
		config = null;
		classLoader = null;
	}

	public GroovyLSCompilationUnit create(Path workspaceRoot, FileContentsTracker fileContentsTracker) {
		if (config == null) {
			config = getConfiguration(workspaceRoot);
		}

		if (classLoader == null) {
			classLoader = new GroovyClassLoader(CompilationUnitFactory.class.getClassLoader(), config, true);
		}

		Set<URI> changedUris = fileContentsTracker.getChangedURIs();
		if (compilationUnit == null) {
			compilationUnit = new GroovyLSCompilationUnit(config, null, classLoader);
			// we don't care about changed URIs if there's no compilation unit yet
			changedUris = null;
		} else {
			compilationUnit.setClassLoader(classLoader);
			final Set<URI> urisToRemove = changedUris;
			List<SourceUnit> sourcesToRemove = new ArrayList<>();
			compilationUnit.iterator().forEachRemaining(sourceUnit -> {
				if (sourceUnit == null || sourceUnit.getSource() == null) {
					return;
				}
				URI uri = sourceUnit.getSource().getURI();
				if (urisToRemove.contains(uri)) {
					sourcesToRemove.add(sourceUnit);
				}
			});
			// if an URI has changed, we remove it from the compilation unit so
			// that a new version can be built from the updated source file
			compilationUnit.removeSources(sourcesToRemove);
		}

		if (workspaceRoot != null) {
			addDirectoryToCompilationUnit(workspaceRoot, compilationUnit, fileContentsTracker, changedUris);
		} else {
			final Set<URI> urisToAdd = changedUris;
			fileContentsTracker.getOpenURIs().forEach(uri -> {
				// if we're only tracking changes, skip all files that haven't
				// actually changed
				if (urisToAdd != null && !urisToAdd.contains(uri)) {
					return;
				}
				String contents = fileContentsTracker.getContents(uri);
				addOpenFileToCompilationUnit(uri, contents, compilationUnit);
			});
		}

		return compilationUnit;
	}

	protected CompilerConfiguration getConfiguration(Path workspaceRoot) {
		CompilerConfiguration config = new CompilerConfiguration();

		List<String> classpathList = new ArrayList<>();
		getClasspathList(classpathList, workspaceRoot);
		config.setClasspathList(classpathList);

		return config;
	}

	protected void getClasspathList(List<String> result, Path workspaceRoot) {
		List<String> entries = getConfiguredClasspathList(workspaceRoot);
		if (entries == null) {
			return;
		}

		for (String entry : entries) {
			boolean mustBeDirectory = false;
			if (entry.endsWith("*")) {
				entry = entry.substring(0, entry.length() - 1);
				mustBeDirectory = true;
			}

			File file = new File(entry);
			if (!file.exists()) {
				continue;
			}
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					if (!child.getName().endsWith(".jar") || !child.isFile()) {
						continue;
					}
					result.add(child.getPath());
				}
			} else if (!mustBeDirectory && file.isFile()) {
				if (file.getName().endsWith(".jar")) {
					result.add(entry);
				}
			}
		}
	}

	protected List<String> getConfiguredClasspathList(Path workspaceRoot) {
		if (additionalClasspathList != null) {
			return additionalClasspathList;
		}

		String classpathFile = System.getProperty("groovy.classpath.file");
		if (classpathFile == null || classpathFile.trim().isEmpty()) {
			classpathFile = System.getenv("GROOVY_LS_CLASSPATH_FILE");
		}
		Path classpathFilePath = null;
		if (classpathFile != null && !classpathFile.trim().isEmpty()) {
			classpathFilePath = Paths.get(classpathFile.trim());
		} else if (workspaceRoot != null) {
			classpathFilePath = workspaceRoot.resolve(".groovy-language-server-classpath");
		}
		if (classpathFilePath == null || !Files.isRegularFile(classpathFilePath)) {
			return null;
		}

		try {
			List<String> entries = new ArrayList<>();
			for (String line : Files.readAllLines(classpathFilePath)) {
				for (String entry : line.split(Pattern.quote(File.pathSeparator))) {
					entry = entry.trim();
					if (!entry.isEmpty()) {
						entries.add(entry);
					}
				}
			}
			return entries;
		} catch (IOException e) {
			System.err.println("Failed to read Groovy classpath file: " + classpathFilePath);
			return null;
		}
	}

	protected void addDirectoryToCompilationUnit(Path dirPath, GroovyLSCompilationUnit compilationUnit,
			FileContentsTracker fileContentsTracker, Set<URI> changedUris) {
		try {
			if (Files.exists(dirPath)) {
				Files.walk(dirPath).forEach((filePath) -> {
					if (shouldSkipPath(dirPath, filePath) || !filePath.toString().endsWith(FILE_EXTENSION_GROOVY)) {
						return;
					}
					URI fileURI = filePath.toUri();
					if (!fileContentsTracker.isOpen(fileURI)) {
						File file = filePath.toFile();
						if (file.isFile()) {
							if (changedUris == null || changedUris.contains(fileURI)) {
								compilationUnit.addSource(file);
							}
						}
					}
				});
			}

		} catch (IOException e) {
			System.err.println("Failed to walk directory for source files: " + dirPath);
		}
		fileContentsTracker.getOpenURIs().forEach(uri -> {
			Path openPath = Paths.get(uri);
			if (!openPath.normalize().startsWith(dirPath.normalize())) {
				return;
			}
			if (changedUris != null && !changedUris.contains(uri)) {
				return;
			}
			String contents = fileContentsTracker.getContents(uri);
			addOpenFileToCompilationUnit(uri, contents, compilationUnit);
		});
	}

	protected boolean shouldSkipPath(Path workspaceRoot, Path filePath) {
		Path relativePath = workspaceRoot.normalize().relativize(filePath.normalize());
		for (String pattern : getExcludedPathPatterns(workspaceRoot)) {
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
			if (matcher.matches(relativePath)) {
				return true;
			}
		}
		return false;
	}

	protected List<String> getExcludedPathPatterns(Path workspaceRoot) {
		List<String> patterns = new ArrayList<>();
		patterns.add(".git/**");
		patterns.add(".gradle/**");
		patterns.add("build/**");
		patterns.add("target/**");
		patterns.add("node_modules/**");

		String excludesFile = System.getProperty("groovy.excludes.file");
		if (excludesFile == null || excludesFile.trim().isEmpty()) {
			excludesFile = System.getenv("GROOVY_LS_EXCLUDES_FILE");
		}
		Path excludesFilePath = null;
		if (excludesFile != null && !excludesFile.trim().isEmpty()) {
			excludesFilePath = Paths.get(excludesFile.trim());
		} else if (workspaceRoot != null) {
			excludesFilePath = workspaceRoot.resolve(".groovy-language-server-excludes");
		}
		if (excludesFilePath == null || !Files.isRegularFile(excludesFilePath)) {
			return patterns;
		}

		try {
			for (String line : Files.readAllLines(excludesFilePath)) {
				String pattern = line.trim();
				if (!pattern.isEmpty() && !pattern.startsWith("#")) {
					patterns.add(pattern);
				}
			}
		} catch (IOException e) {
			System.err.println("Failed to read Groovy excludes file: " + excludesFilePath);
		}
		return patterns;
	}

	protected void addOpenFileToCompilationUnit(URI uri, String contents, GroovyLSCompilationUnit compilationUnit) {
		Path filePath = Paths.get(uri);
		SourceUnit sourceUnit = new SourceUnit(filePath.toString(),
				new StringReaderSourceWithURI(contents, uri, compilationUnit.getConfiguration()),
				compilationUnit.getConfiguration(), compilationUnit.getClassLoader(),
				compilationUnit.getErrorCollector());
		compilationUnit.addSource(sourceUnit);
	}
}
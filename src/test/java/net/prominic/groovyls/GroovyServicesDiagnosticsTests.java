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
package net.prominic.groovyls;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesDiagnosticsTests {
	private static final String LANGUAGE_GROOVY = "groovy";
	private static final String PATH_WORKSPACE = "./build/test_diagnostics_workspace/";

	private GroovyServices services;
	private Path workspaceRoot;
	private CapturingLanguageClient languageClient;

	@BeforeEach
	void setup() throws Exception {
		workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
		Files.createDirectories(workspaceRoot);

		languageClient = new CapturingLanguageClient();
		services = new GroovyServices(new CompilationUnitFactory());
		services.setWorkspaceRoot(workspaceRoot);
		services.connect(languageClient);
	}

	@Test
	void testDiagnosticsAreUpdatedAfterTextChange() throws Exception {
		Path filePath = workspaceRoot.resolve("Diagnostics.groovy");
		String uri = filePath.toUri().toString();
		String originalContents = "class Diagnostics {\n  def valid() {\n    return 1\n  }\n}\n";
		String brokenContents = "class Diagnostics {\n  def broken() {\n    if (true) {\n  }\n}\n";

		services.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, originalContents)));
		languageClient.diagnostics.clear();

		services.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 2),
				Collections.singletonList(new TextDocumentContentChangeEvent(brokenContents))));
		PublishDiagnosticsParams brokenDiagnostics = languageClient.findDiagnostics(uri);
		Assertions.assertNotNull(brokenDiagnostics);
		Assertions.assertFalse(brokenDiagnostics.getDiagnostics().isEmpty());

		services.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 3),
				Collections.singletonList(new TextDocumentContentChangeEvent(originalContents))));
		PublishDiagnosticsParams fixedDiagnostics = languageClient.findLastDiagnostics(uri);
		Assertions.assertNotNull(fixedDiagnostics);
		Assertions.assertTrue(fixedDiagnostics.getDiagnostics().isEmpty());
	}

	private static class CapturingLanguageClient implements LanguageClient {
		private final List<PublishDiagnosticsParams> diagnostics = new ArrayList<>();

		@Override
		public void telemetryEvent(Object object) {
		}

		@Override
		public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
			return null;
		}

		@Override
		public void showMessage(MessageParams messageParams) {
		}

		@Override
		public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
			this.diagnostics.add(diagnostics);
		}

		@Override
		public void logMessage(MessageParams message) {
		}

		private PublishDiagnosticsParams findDiagnostics(String uri) {
			return diagnostics.stream().filter(params -> uri.equals(params.getUri())).findFirst().orElse(null);
		}

		private PublishDiagnosticsParams findLastDiagnostics(String uri) {
			for (int i = diagnostics.size() - 1; i >= 0; i--) {
				PublishDiagnosticsParams params = diagnostics.get(i);
				if (uri.equals(params.getUri())) {
					return params;
				}
			}
			return null;
		}
	}
}

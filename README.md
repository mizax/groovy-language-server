# Groovy Language Server

A [language server](https://microsoft.github.io/language-server-protocol/) for [Groovy](http://groovy-lang.org/).

## About this fork

This fork keeps the language server usable for legacy Grails 4 / Groovy 2.5 workspaces. The upstream version targets newer Groovy releases, which can reject valid Groovy 2.5 syntax and make large Grails projects noisy or unresponsive.

The fork currently:

- builds against Groovy 2.5.x,
- supports reading a large Gradle/Grails classpath from a local file,
- supports workspace-specific source exclusion patterns, and
- avoids a few Groovy 2.5 null-source edge cases while walking compilation units.

The following language server protocol requests are currently supported:

- completion
- definition
- documentSymbol
- hover
- references
- rename
- signatureHelp
- symbol
- typeDefinition

The following configuration options are supported:

- groovy.java.home (`string` - sets a custom JDK path)
- groovy.classpath (`string[]` - sets a custom classpath to include _.jar_ files)

This fork also supports two file-based configuration helpers for clients that cannot easily send LSP settings:

- `groovy.classpath.file` JVM system property, `GROOVY_LS_CLASSPATH_FILE` environment variable, or workspace `.groovy-language-server-classpath`
  - file contents may be one entry per line or a platform path-separated classpath
- `groovy.excludes.file` JVM system property, `GROOVY_LS_EXCLUDES_FILE` environment variable, or workspace `.groovy-language-server-excludes`
  - file contents are Java glob patterns relative to the workspace root
  - blank lines and lines starting with `#` are ignored
  - default exclusions are `.git/**`, `.gradle/**`, `build/**`, `target/**`, and `node_modules/**`

Example `.groovy-language-server-excludes` for a large Grails workspace:

```text
# Java glob patterns relative to the workspace root
grails-app/migrations/**
src/test/**
src/integration-test/**
```

## Build

To build from the command line, run the following command:

```sh
./gradlew build
```

This will create _build/libs/groovy-language-server-all.jar_.

## Run

To run the language server, use the following command:

```sh
java -jar groovy-language-server-all.jar
```

Language server protocol messages are passed using standard I/O.

## Editors and IDEs

A sample language extension for Visual Studio Code is available in the _vscode-extension_ directory. There are no plans to release this extension to the VSCode Marketplace at this time.

Instructions for setting up the language server in Sublime Text is available in the _sublime-text_ directory.

Moonshine IDE natively provides a Grails project type that automatically configures the language server.

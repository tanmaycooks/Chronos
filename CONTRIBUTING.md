# Contributing to Chronos

Thank you for your interest in contributing to Chronos! We welcome contributions from the community to help make Android debugging more trustworthy and deterministic.

## Code of Conduct

Please note that this project is released with a [Contributor Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project you agree to abide by its terms.

## How to Contribute

### Reporting Bugs

If you find a bug, please check the [Issues](https://github.com/yourusername/chronos/issues) to see if it has already been reported. If not, please open a new issue with the following details:

-   **Version**: Which version of Chronos are you using?
-   **Steps to reproduce**: Clean, minimal steps to reproduce the issue.
-   **Expected behavior**: What did you expect to happen?
-   **Actual behavior**: What actually happened?
-   **Logs/Screenshots**: Any relevant logs (Logcat) or screenshots.

### Suggesting Enhancements

We love new ideas! If you have an idea for a feature or enhancement:

1.  Check existing issues/discussions.
2.  Open a new issue describing your idea, use case, and potential implementation.

### Pull Requests

1.  **Fork the repository**.
2.  **Create a branch** for your feature or fix: `git checkout -b feature/amazing-feature`.
3.  **Make your changes**. Ensure you follow the project's coding style (Kotlin/Android).
4.  **Run tests**: Run `./gradlew test` to ensure everything is working.
5.  **Commit your changes**: Use clear, descriptive commit messages.
6.  **Push to your branch**: `git push origin feature/amazing-feature`.
7.  **Open a Pull Request**: Describe your changes and reference any related issues.

## Development Setup

1.  Clone the repository.
2.  Open in Android Studio (Giraffe or newer recommended).
3.  Sync Gradle.
4.  Run tests: `./gradlew test`.

### Architecture

Familiarize yourself with the architecture described in [README.md](../README.md). Key modules:

-   `chronos-agent`: The core Android library (debug only).
-   `chronos-agent-noop`: The no-op library (release).
-   `chronos-protocol`: Protocol buffers and shared definitions.
-   `chronos-studio-plugin`: The IntelliJ/Android Studio plugin.

## Security

If you discover a security vulnerability, please do NOT open a public issue. Email `security@yourdomain.com` (or generic placeholder if none) instead.

## License

By contributing, you agree that your contributions will be licensed under its Apache License 2.0.

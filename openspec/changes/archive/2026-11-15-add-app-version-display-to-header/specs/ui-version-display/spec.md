# Spec Delta

## Purpose

Display the current application version number in the UI header for version visibility and troubleshooting.

## ADDED Requirements

### Requirement: Version is displayed in header
The system SHALL display the current application version number in the UI header, immediately after the "SOHT2 Server" title.

#### Scenario: Version displayed in header
- **WHEN** user views any page in the application
- **THEN** the header displays "SOHT2 Server" followed by the version number

### Requirement: Version sourced from package.json
The system SHALL read the application version from the package.json file.

#### Scenario: Version read from package.json
- **WHEN** the application loads
- **THEN** the version number is read from package.json

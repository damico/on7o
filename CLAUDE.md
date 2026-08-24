# Project Guidelines

## Backend
Java Spring Boot.

## Frontend
Within the Spring Boot project, using Thymeleaf and Bootstrap with no external CDNs. CSS and JavaScript files must be stored within the project.
The design must be minimalist, inspired by the Resend website style: dark background, clean typography, generous whitespace, and restrained use of color.

Ontology diagrams must use the same technology as the sibling project `../infinitestack-ontology`: hand-rolled inline SVG built with vanilla JavaScript (`document.createElementNS`), with manual pan/zoom (CSS transform on a `<g>` group) and manual node/edge state (plain JS `Map` objects). No third-party diagramming or graph library (no D3, Cytoscape, vis.js, mermaid, JointJS, GoJS, etc).

## Firmware
New firmware must be installed on the StickS3 using manual download mode (unplug, hold the side button, plug in while still holding, release after about 3 seconds), then flashed with `pio run -t upload`. After flashing, verify the firmware by monitoring the serial output (see the "Reading the serial output" section in `m5sitcks3/README.md`).

## Commits
With every commit, the `bridge.md` file must be updated.
Commit messages must not mention Claude or Anthropic.

## General
The em dash character (—) must never be used in any text, documentation, or source code.

## Development
- Follow the KISS principle.
- REST endpoints → Controllers.
- No ORM frameworks such as Hibernate/JPA.
- Make objective and consistent use of OOP with Design Patterns (GoF).
- All classes must have documentation (Javadoc) in English.
- All code and comments must be written in English.

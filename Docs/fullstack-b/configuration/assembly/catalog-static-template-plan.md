# B10b Catalog static template boundary

**Goal:** Preview candidates may come only from a manifest-verified Catalog bundle whose computed hash matches a separately supplied approved hash.

**Design:** `templates/index.json` v1 maps a known tool key to one verified `templates/` or `guides/` source, a logical target key, and a safe relative output path. The renderer loads the bundle, runs the existing semantic parser, checks the external approved hash, and copies the selected static text verbatim into `PreviewInputFile`. It rejects missing/unlisted/orphan sources, duplicate output paths, unknown tool keys, malformed index fields, empty selections, and any selected tool with no indexed output. The comparator validates output paths and target uniqueness. No placeholders, arbitrary commands, file writes, or Client-specific configuration are generated here.

The manifest hash proves integrity against its own manifest, not human review. The approved hash must be provisioned independently after review; accepting a hash from the same release or from the browser would defeat the boundary. The real release and approval source are pending team verification.

## Implementation steps

- [x] Add failing tests for approved exact-copy rendering and rejected wrong hash, unknown tools, orphan sources, duplicate/unsafe output, and tampered on-disk content.
- [x] Add a strict parser/renderer in `catalog` using the current manifest loader and semantic parser.
- [x] Run targeted tests, then full `mvn package` and `git diff --check`. Final full build: 59 tests, 0 failures, 0 errors, 1 skipped (Windows symlink privilege).
- [x] Record the partial B10 milestone and remaining approval/API dependencies in the B tracker.

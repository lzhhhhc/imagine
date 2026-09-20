# Director workspace and video production — v1.4

## Behavior

The director header now occupies a title row and a compact engine/stage row. Engine selection, confirmed-stage revisits and restart live in a menu. The conversation retains the remaining height and in-page IME behavior.

Interview completion requests a structured storyboard. The parser validates nonblank per-shot prompts, positive integer durations, their exact sum and the confirmed aspect ratio. Only a valid complete result replaces the editable cards; replacing existing cards requires an explicit UI action. Manual storyboard refinement preserves card IDs, frame attachments, count and per-shot durations.

Assets bind by stable ID. Selected order is shared by the preview, reference legends and serialized payloads. Interview and storyboard LLM calls attach the JPEG image bytes as multimodal parts. Deleted or corrupt selected images block the request until the user corrects the selection. First-frame generation includes all selected references through multipart `image[]`; the UI reports incompatible image protocols. All eight editor aspect ratios have explicit first-frame dimensions.

The storyboard dialog has independently scrolling cards and a fixed production action. Production previews each selected asset and first/last frame, the exact model, service address, duration, ratio and resolution. Each confirmation submits one shot. Generated shots are independent videos; concatenation is not implemented.

## Explicit video protocols

| Protocol | Submit | Query | Input controls |
| --- | --- | --- | --- |
| Grok standard / Grok 1.5 | `POST /v1/videos/generations` | `GET /v1/videos/{request_id}` | `prompt`, `reference_images[].url`, `image.url`, `last_frame.url`, `duration`, `aspect_ratio`, `resolution` |
| Seedance 2.x Ark | `POST /api/v3/contents/generations/tasks` | `GET /api/v3/contents/generations/tasks/{id}` | `content[]` with text and `image_url`/role, `duration`, `ratio`, `resolution` |

Protocol is chosen explicitly and saved with the video preset. It is never guessed from a model name. Existing v1.3 settings require selecting the appropriate protocol once. H3 and Kling remain prompt authoring targets, without a direct generation adapter.

Grok accepts 1–15 seconds and up to seven references. First/last frame pins combined with references require the explicitly selected 1.5 capability. Seedance accepts 4–15 seconds and up to nine references in this entry point; reference mode and frame mode are validated separately. The UI allows 480p/720p output and blocks unsupported aspect ratios, frame combinations and image counts. No image or parameter is silently removed and no alternative provider or old endpoint is tried.

## Task lifecycle

A credential-free task journal is saved before the POST and records the accepted task ID before proceeding to polling. Automatic POST retries and API redirects are disabled. Unknown submission results remain visible and block an accidental repeat for the same shot; users can enter a known task ID or explicitly confirm that no task exists on the provider side. Stop-wait cancels local polling; it does not cancel a server task.

Task IDs and result URLs survive reopening. Explicit resume uses GET on the original service; it never submits a new task. Result downloads carry no API key, are written through a temporary file, and become local MP4 copies under `filesDir/director_videos`. A narrowly scoped FileProvider supports opening and sharing. Polling runs while the production dialog is active, with a bounded wait; the task remains queryable afterward. This is not a foreground background-render service.

## Verification and limitations

- `tools/release.sh 1.4 dist/RELEASE_NOTES-1.4.md --local-only`: debug assembly and full suite passed, 224 tests, zero failures/errors, one pre-existing ComfyUI live integration test intentionally skipped.
- 21 new tests cover structured storyboard validation, ID-based selection, every editor ratio, multimodal and multipart image wire payloads, Grok/Seedance wire formats, protocol/preset persistence, input rejection before network I/O, API redirect and connection failure behavior, task journal recovery and atomic credential-free downloads.
- Installed APK signing certificate matches the previously installed application. Android PackageManager reports versionName 1.4 and versionCode 5 after successful `pm install -r`.
- No live billable video request was made and no application screen was opened for visual validation. Real-provider capabilities, account permissions, image fidelity, keyboard layout and visual polish remain user acceptance items.
- APK SHA-256: `81575b9370e46493ff515c7e9af78f71f817db3a98257dcf5a4392136fd11f09`.
- Remote push/release was not performed. Previously exposed credentials must be revoked and replaced through secure authentication.

## Protocol references

Checked against public provider documentation during implementation:

- [xAI video generation](https://docs.x.ai/developers/model-capabilities/video/generation)
- [xAI references and first/last frame](https://docs.x.ai/developers/model-capabilities/video/reference-to-video)
- [BytePlus ModelArk create video task](https://docs.byteplus.com/en/docs/ModelArk/1520757)
- ModelArk retrieve-task reference, linked from the create-task documentation, verified `succeeded` and `content.video_url` response fields.

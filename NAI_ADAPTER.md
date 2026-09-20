# NAI 4.5 / 5 text adapter

Entry: 创作 → NAI 提示词工程 (above 画师串).

- Auto resolves versioned NAI model names. Manual profile selection changes prompt rules only, never API model.
- Profiles: 4.5 Full / Curated, 5 Full / Curated.
- Local quality/UC defaults off because relay defaults are unknown. Resolution and quality tags are independent.
- Native seed, sampler, guidance and character payloads are not implemented or claimed.
- Shared assembleNaiPrompt is used for preview and generation. Validation errors stop submission; there is no error fallback to old NAI assembly.
- Artist recipes preserve emphasis groups and parentheses. Exact whole segments are deduplicated; semantic duplicates or mixed weights are not silently rewritten.
- Artist activation and ordering are editable. Weights remain in the persisted raw recipe. Disabling injection does not remove artists explicitly written in the main prompt.
- NAI polish instructions replace the old fixed 40–60-tag template. Numeric emphasis is preserved through NAI cleaning.
- Unknown model versions are not silently assumed to be 4.5 or 5.

## Pre-prompt (前置提示词)

- Entry: 创作 → 前置提示词. One user-authored text, no presets, no JSON import, no sampling override.
- Injected as a **separate** leading `system` message on every LLM call (polish / translate / reverse / director / storyboard), so it stays separable from business instructions.
- Persisted via debounced auto-save in `StudioScreen` (`LaunchedEffect(StudioState.prePrompt)`), not by a dialog-scoped coroutine. Closing the dialog cannot cancel the write.
- State lives in `StudioState.prePrompt`; restored once per process via `prePromptRestored` to avoid clobbering an in-flight edit.
- SillyTavern preset import and all preset storage methods were removed.

Sources reviewed 2026-09-09:
https://docs.novelai.net/en/image/qualitytags/
https://docs.novelai.net/en/image/strengthening-weakening/
https://docs.novelai.net/en/image/tags/
https://docs.novelai.net/en/image/undesiredcontent/
https://docs.novelai.net/en/image/models/

Validation: 19 JUnit tests passed (11 NAI + 8 pre-prompt), no skipped tests. Debug APK assembled and installed. Real device confirmed: NAI mode toggle gates the panel, pre-prompt dialog loads persisted text after close/reopen, and DataStore contains `"enabled":true,"text":...` under `pre_prompt_json`. No paid LLM/generation calls or image-quality comparison performed.

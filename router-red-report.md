# Task 3 RED report

- Scope: Router word/sentence semantic boundary tests only.
- Modified: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterTest.kt`.
- Production files unchanged.
- Added tests for American/British Kokoro-only words, MiMo→System sentence fallback, Kokoro failure without fallback, and legacy OpenAI preference not invoking remote OpenAI.
- Command: `:app:compileDebugUnitTestKotlin --no-daemon --no-build-cache --console=plain`.
- Result: expected RED, exit 1. Compilation fails because `PronunciationRouter` has no `kokoroProvider` constructor parameter and no `speakWord`/`speakSentence`; it still requires `openAiFactory`. This confirms the tests exercise the missing production contract rather than passing accidentally.
- No commit, push, uninstall, clear-data, rollback, or destructive cleanup performed.

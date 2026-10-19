# Phase 6 Real Source Results

Date: 2026-06-04

## Purpose

This file is the working evidence sheet for Phase 6 real-source validation. It is intentionally separate from `README.md`: the README describes how to validate, while this file records what actually happened on a phone.

Use this file during the final manual acceptance batch after running:

```powershell
Set-Location '<REPO_ROOT>'
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File tools\phase6-device-prepare.ps1
```

Per the current acceptance plan, incremental builds do not need to be installed every time. Run the script once when the Phase 6 batch is ready, then use the tables below for unified manual validation.

## Samples

| Sample | Expected result |
| --- | --- |
| `sample.html` | Opens as HTML preview; external `http`/`https` links delegate to the system browser/app. |
| `sample.md` | Opens as readable Markdown preview. |
| `sample.txt` | Opens as readable TXT preview with Chinese text and line breaks. |
| `sample-site.zip` | Opens ZIP web package entry; relative CSS and the blue/orange PNG block are visible. |
| `unsafe-traversal.zip` | Does not preview; shows `ZIP 文件不安全，已阻止预览`. |

## Evidence Fields

After opening a sample from a real source, return to the 即阅 home screen and copy the import diagnostics summary if it is shown. Record these fields when available:

- source app;
- entry path, such as App picker, system open, share sheet, browser downloads, or downloads app;
- sample file;
- entry action, such as App picker, `android.intent.action.VIEW`, `android.intent.action.SEND`, or `android.intent.action.SEND_MULTIPLE`;
- entry Uri source, such as `data`, `extraStream`, `clipData`, or `extraStreamList`;
- Uri scheme;
- Uri authority;
- path shape or path segment count, without storing the full external path;
- `Intent.type`;
- provider MIME from `ContentResolver.getType(uri)`;
- display name;
- display extension visible to `即阅`;
- resolved file type;
- read or preview result;
- user-facing error, if any;
- pass/fail and notes.

## Final Acceptance Batch

Fill this section from `apps/android-instantread/app/build/outputs/phase6-device-smoke/phase6-device-run-summary.md` and the connected-device smoke artifacts before recording source-by-source rows.

| Field | Value |
| --- | --- |
| Script run time | 2026-06-04 18:50:56 +08:00 |
| Git HEAD | 20163f9cc849513615c88bc44c85ec7058281f74 |
| Git status | ## master |
| Debug APK path | <REPO_ROOT>\apps\android-instantread\app\build\outputs\apk\debug\app-debug.apk |
| APK size | 866159 bytes |
| APK SHA-256 | 8FFBE31010E93C26B0E1803349E9A9E1EDD3F47015D6A2AA4B511BF315AE6841 |
| ADB serial | TEST-DEVICE-SERIAL |
| Device model | Test-Device |
| Android SDK | 36 |
| Device validation directory | `/sdcard/Download/android-instantread-validation` |
| Smoke artifact directory | `apps/android-instantread/app/build/outputs/phase6-device-smoke/` |
| `phase6-device-run-summary.md` | apps/android-instantread/app/build/outputs/phase6-device-smoke/phase6-device-run-summary.md |
| `phase6-manual-next-steps.md` | apps/android-instantread/app/build/outputs/phase6-device-smoke/phase6-manual-next-steps.md |
| `adb-devices.txt` | apps/android-instantread/app/build/outputs/phase6-device-smoke/adb-devices.txt |
| `device-samples.txt` | apps/android-instantread/app/build/outputs/phase6-device-smoke/device-samples.txt |
| Tester | User-confirmed manual acceptance |
| Overall batch result | Passed |

## App Picker Baseline

Initial user testing has already confirmed the App picker path is usable for the committed samples. Re-run this baseline only if a later Phase 6 change touches import, preview routing, ZIP extraction, WebView policy, or history behavior.

| Sample | Result | Diagnostics summary | Notes |
| --- | --- | --- | --- |
| `sample.html` | Passed in initial manual testing | Not captured here yet | External links delegate to system browser/app. |
| `sample.md` | Passed in initial manual testing | Not captured here yet |  |
| `sample.txt` | Passed in initial manual testing | Not captured here yet |  |
| `sample-site.zip` | Passed in initial manual testing | Not captured here yet | Blue/orange relative PNG block was visible after sample refresh. |
| `unsafe-traversal.zip` | Passed after unsafe ZIP message fix | Not captured here yet | Expected to fail safely, not preview. |

## System File Manager

Use the device's built-in file manager or Downloads app. Do not use direct shell-launched `file:// /sdcard` as the main pass/fail path.

| Sample | Source app | Entry path | Entry action | Entry Uri source | Uri scheme | Uri authority | Path shape | Intent MIME | Provider MIME | Display name | Extension visible | Resolved type | Result | User error | Pass/Fail | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `sample.html` | System file manager | System open | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.html | Yes | HTML | Preview opened; external links delegate to system browser/app | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.md` | System file manager | System open | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.md | Yes | MARKDOWN | Preview opened as readable Markdown | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.txt` | System file manager | System open | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.txt | Yes | TEXT | Preview opened as readable text | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample-site.zip` | System file manager | System open | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample-site.zip | Yes | ZIP | ZIP web package preview opened; blue/orange image block visible | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |

## Chat Or Share Source

Use a real chat or share source such as WeChat, QQ, or another app that can send one local file to `即阅`. True multi-file shares remain outside MVP scope; only a one-file `ACTION_SEND_MULTIPLE` should be treated as a compatibility case.

| Sample | Source app | Entry path | Entry action | Entry Uri source | Uri scheme | Uri authority | Path shape | Intent MIME | Provider MIME | Display name | Extension visible | Resolved type | Result | User error | Pass/Fail | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `sample.html` | Chat/share source | Share sheet | android.intent.action.SEND | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.html | Yes | HTML | Preview opened; external links delegate to system browser/app | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.md` | Chat/share source | Share sheet | android.intent.action.SEND | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.md | Yes | MARKDOWN | Preview opened as readable Markdown | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.txt` | Chat/share source | Share sheet | android.intent.action.SEND | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.txt | Yes | TEXT | Preview opened as readable text | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample-site.zip` | Chat/share source | Share sheet | android.intent.action.SEND | User-confirmed; diagnostics not captured | content or provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample-site.zip | Yes | ZIP | ZIP web package preview opened; blue/orange image block visible | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |

## Browser Download Source

Use files opened from a browser download list, the system Downloads app, or the browser's "open with" path after downloading the samples.

| Sample | Source app | Entry path | Entry action | Entry Uri source | Uri scheme | Uri authority | Path shape | Intent MIME | Provider MIME | Display name | Extension visible | Resolved type | Result | User error | Pass/Fail | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `sample.html` | Browser download source | Browser download | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or download provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.html | Yes | HTML | Preview opened; external links delegate to system browser/app | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.md` | Browser download source | Browser download | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or download provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.md | Yes | MARKDOWN | Preview opened as readable Markdown | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample.txt` | Browser download source | Browser download | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or download provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample.txt | Yes | TEXT | Preview opened as readable text | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |
| `sample-site.zip` | Browser download source | Browser download | android.intent.action.VIEW | User-confirmed; diagnostics not captured | content or download provider Uri | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | User-confirmed; diagnostics not captured | sample-site.zip | Yes | ZIP | ZIP web package preview opened; blue/orange image block visible | None | Pass | User confirmed this source passed during prior manual acceptance; Uri/MIME diagnostics were not copied. |

## Unsupported Or Unsafe Path Checks

| Case | Source app | Entry path | Expected result | Actual result | Pass/Fail | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `unsafe-traversal.zip` through system file manager | System file manager | System open | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Pass | User confirmed unsafe ZIP blocking during manual acceptance. |
| `unsafe-traversal.zip` through share source | Chat/share source | Share sheet | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Pass | User confirmed unsafe ZIP blocking during manual acceptance. |
| `unsafe-traversal.zip` through browser download source | Browser download source | Browser download | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Shows `ZIP 文件不安全，已阻止预览`; no normal preview | Pass | User confirmed unsafe ZIP blocking during manual acceptance. |
| True multi-file share | Share sheet | Share sheet | Not treated as a valid MVP import | Not treated as a valid MVP import | Diagnostic only | Outside MVP scope; one-file share paths were validated. |
| Direct shell `file:// /sdcard` probe | ADB shell | Diagnostic only | May fail with scoped-storage `EACCES`; does not drive product permissions | Scoped-storage `EACCES` observed previously; not used as product success path | Diagnostic only | Product validation uses user-granted file manager/share/download sources. |

## Acceptance Summary

Phase 6 can be closed only after the following evidence exists:

- App picker baseline remains usable after the final Phase 6 build.
- At least one system file manager or Downloads source has complete rows for HTML, Markdown, TXT, and ZIP.
- At least one chat/share source has complete rows for HTML, Markdown, TXT, and ZIP.
- At least one browser download source has complete rows for HTML, Markdown, TXT, and ZIP.
- `application/octet-stream` inputs with a visible extension still resolve to the matching supported type.
- Unsafe ZIP is shown as a safe blocking error, not as a normal preview.
- Any failure is classified as source permission, MIME/display name, read failure, Manifest mismatch, ZIP rendering, or user-facing copy/recovery issue.
- Automated tests cover any newly discovered boundary that can be reproduced without a specific third-party app.

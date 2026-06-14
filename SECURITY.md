# Security Policy / 安全策略

## Supported Versions / 支持的版本

| Version | Supported / 支持 |
|---------|------------------|
| 1.0.3   | ✅ Active         |
| 1.0.2   | ⚠️ 升级到 1.0.3  |
| 1.0.1   | ⚠️ 升级到 1.0.3  |
| 1.0.0   | ❌ End of life   |

**Policy / 策略**: only the latest release receives security updates. Older versions are EOL.

**策略**: 只有最新版本收安全更新,旧版本 EOL。

## Reporting a Vulnerability / 报告漏洞

**English**: If you discover a security vulnerability in Yawn Lock, **please do NOT open a public issue**. Instead, report it privately:

- **GitHub Security Advisories**: [Create a private security advisory](https://github.com/woaiCarlos/yawn-lock/security/advisories/new)
- **Email**: see my GitHub profile `@woaiCarlos` for contact (security@ address TBD)

Please include:
- Description of the vulnerability
- Steps to reproduce
- Affected versions
- Potential impact
- Any known mitigations

**Response timeline**:
- Initial acknowledgement: within 7 days
- Status update: every 14 days
- Fix timeline: depends on severity; critical issues get a patch release ASAP

**中文**: 如果你发现打哈欠锁屏的安全漏洞,**请不要**在公开 issue 提。私下联系我:

- **GitHub Security Advisories**: [创建私有安全建议](https://github.com/woaiCarlos/yawn-lock/security/advisories/new)
- **Email**: 见我 GitHub 个人页 `@woaiCarlos` 的联系方式

请附上:
- 漏洞描述
- 复现步骤
- 受影响版本
- 潜在影响
- 任何已知的缓解措施

**响应时间表**:
- 初次确认: 7 天内
- 进展更新: 每 14 天
- 修复时间: 取决于严重程度,关键问题立即发补丁

## Scope / 范围

In scope:
- The Android app (`app/`) shipped via Play Store / APK
- The build process (`scripts/`, `.github/`)

Out of scope:
- Third-party dependencies (report to upstream; we may bump versions in response)
- Theoretical attacks requiring physical device access + sophisticated adversary (out of threat model for a personal-use app)

## Security Considerations / 安全考虑

This app uses:
- `DevicePolicyManager.lockNow()` — requires explicit user consent via device admin grant; can be revoked in Settings
- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` — requires `SYSTEM_ALERT_WINDOW` permission; user can revoke
- `SCHEDULE_EXACT_ALARM` — Android 13+ requires user grant; can be revoked
- Foreground service with `specialUse` type — required for background countdown

The app does **NOT**:
- Read your contacts, photos, or other personal data
- Make network requests (no telemetry, no ads, no analytics)
- Run in the background after countdown ends (foreground service is destroyed on stop/finish)
- Access your camera, microphone, or location

## Bug Bounty / 漏洞赏金

There is no formal bug bounty program. Significant, responsibly-disclosed vulnerabilities will be credited in the release notes (with your permission).

目前没有正式的漏洞赏金计划。重大且负责任披露的漏洞会在 release notes 里致谢(经你同意)。

## Past Advisories / 过往安全建议

_None yet — this is a new public release._
